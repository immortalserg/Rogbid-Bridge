package org.rogbid.bridge

import android.app.*
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.net.Uri
import android.os.*
import android.provider.Settings
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale

class SyncService : Service() {
 private val worker = HandlerThread("RogbidMqttSync")
 private lateinit var handler: Handler
 @Volatile private var running = false
 private var sleepReceiverRegistered = false
 private var sleepBeforeRequest: String? = null
 private var sleepRequestAt = 0L
 private fun snapshotKey(s: SleepCollector.Snapshot): String =
  listOf(s.start, s.end, s.duration.toString(), s.stages).joinToString("|")

 // Accept only a newly changed provider record with a wake time close to now.
 // The provider has no date; a time-only record cannot otherwise be dated safely.
 private fun recentWakeDate(wakeTime: String): String? {
  val parts = wakeTime.split(":")
  if (parts.size != 2) return null
  val hour = parts[0].toIntOrNull() ?: return null
  val minute = parts[1].toIntOrNull() ?: return null
  if (hour !in 0..23 || minute !in 0..59) return null
  val now = System.currentTimeMillis()
  val wake = Calendar.getInstance().apply {
   set(Calendar.HOUR_OF_DAY, hour)
   set(Calendar.MINUTE, minute)
   set(Calendar.SECOND, 0)
   set(Calendar.MILLISECOND, 0)
   if (timeInMillis > now) add(Calendar.DAY_OF_MONTH, -1)
  }
  if (now - wake.timeInMillis > 3 * 60 * 60 * 1000L) return null
  return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(wake.time)
 }
 private val sleepReceiver = object : BroadcastReceiver() {
  override fun onReceive(context: Context?, intent: Intent?) {
   if (intent?.action != "com.wiite.sleep.data" || !running) return
   // The stock sleep app receives the same broadcast and updates its provider
   // asynchronously. Read only after it has had time to process the packet.
   handler.postDelayed({
    if (!running) return@postDelayed
    try {
     val snapshot = SleepCollector(this@SyncService).read()
     if (snapshot != null) {
      val key = snapshotKey(snapshot)
      val wakeDate = recentWakeDate(snapshot.end)
      if (sleepRequestAt > 0L && wakeDate != null) {
       val saved = SleepCollector(this@SyncService).archive(snapshot, wakeDate)
       android.util.Log.i("RogbidSleep",
        "Fresh sleep snapshot: wakeDate=" + wakeDate + ", inserted=" + saved)
      } else {
       android.util.Log.i("RogbidSleep",
        "Sleep snapshot not archived: missing recent wake date")
      }
     } else {
      android.util.Log.w("RogbidSleep", "Sleep response received but provider has no valid snapshot")
     }
    } catch (e: Exception) {
     android.util.Log.e("RogbidSleep", "Cannot read sleep snapshot", e)
    }
   }, 2000L)
  }
 }
 private fun intervalMs(): Long = (getSharedPreferences("mqtt", MODE_PRIVATE)
  .getInt("interval_minutes", 1).coerceIn(1, 60) * 60_000L)
 private val sync = object : Runnable {
  override fun run() {
   if (!running) return
   val prefs = getSharedPreferences("mqtt", MODE_PRIVATE)
   prefs.edit().putString("last_attempt", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())).apply()
   try {
    // Request a fresh UART sleep packet. The reply is handled asynchronously.
    sleepBeforeRequest = try {
     SleepCollector(this@SyncService).read()?.let { snapshotKey(it) }
    } catch (e: Exception) {
     android.util.Log.w("RogbidSleep", "Cannot read baseline snapshot", e)
     null
    }
    sleepRequestAt = System.currentTimeMillis()
    sendBroadcast(Intent("com.wiite.get.sleep.data"))
    publish()
    prefs.edit().putString("last_sync", "OK: " + java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())).apply()
   } catch (e: Exception) {
    prefs.edit().putString("last_sync", "ERROR: " + e.javaClass.simpleName + ": " + e.message).apply()
    android.util.Log.e("RogbidBridge", "MQTT sync failed", e)
   }
   if (running) handler.postDelayed(this, intervalMs())
  }
 }
 override fun onCreate() {
  super.onCreate()
  worker.start()
  handler = Handler(worker.looper)
  val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
  manager.createNotificationChannel(NotificationChannel("sync", "Rogbid Bridge sync", NotificationManager.IMPORTANCE_LOW))
  val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
   PendingIntent.FLAG_UPDATE_CURRENT)
  val notification = Notification.Builder(this, "sync")
   .setSmallIcon(android.R.drawable.stat_notify_sync)
   .setContentTitle("Rogbid Bridge")
   .setContentText("MQTT background synchronization active")
   .setContentIntent(open)
   .setOngoing(true)
   .build()
  startForeground(1001, notification)
  registerReceiver(sleepReceiver, IntentFilter("com.wiite.sleep.data"))
  sleepReceiverRegistered = true
 }
 override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
  if (intent?.action == "STOP") {
   getSharedPreferences("mqtt", MODE_PRIVATE).edit().putBoolean("auto", false).apply()
   stopSelf()
   return START_NOT_STICKY
  }
  if (!running) running = true
  handler.removeCallbacks(sync)
  handler.post(sync)
  return START_STICKY
 }
 private fun publish() {
  val prefs = getSharedPreferences("mqtt", MODE_PRIVATE)
  val host = prefs.getString("host", "") ?: ""
  val port = prefs.getString("port", "1883")?.toIntOrNull() ?: 1883
  val id = prefs.getString("device", "king") ?: "king"
  if (host.isBlank() || !id.matches(Regex("[A-Za-z0-9_-]+"))) throw IllegalStateException("MQTT host or device ID missing")
  val data = JSONObject()
  data.put("steps", Settings.System.getInt(contentResolver, "today_steps"))
  contentResolver.query(Uri.parse("content://com.watchHealth.Walk/query"),
   arrayOf("date", "steps", "diatance", "kcal", "targetsteps"), null, null, "date DESC")?.use { cursor ->
   if (cursor.moveToFirst()) {
    for (name in arrayOf("date", "steps", "diatance", "kcal", "targetsteps")) {
     val i = cursor.getColumnIndex(name)
     if (i >= 0 && !cursor.isNull(i)) {
      if (name == "date") data.put(name, cursor.getString(i))
      else data.put(name, cursor.getLong(i))
     }
    }
   }
  }
  val scheme = if (port == 8883) "ssl" else "tcp"
  val client = MqttClient(scheme + "://" + host + ":" + port,
   "rogbid-auto-" + id + "-" + System.currentTimeMillis(), MemoryPersistence())
  try {
   val options = MqttConnectOptions().apply {
    isCleanSession = true
    connectionTimeout = 10
    val user = prefs.getString("user", "") ?: ""
    if (user.isNotBlank()) userName = user
    val secret = prefs.getString("password", "") ?: ""
    if (secret.isNotEmpty()) password = secret.toCharArray()
   }
   client.connect(options)
   client.publish("rogbid/" + id + "/state",
    MqttMessage(data.toString().toByteArray(Charsets.UTF_8)).apply {
     qos = 1
     isRetained = true
    })
  } finally {
   if (client.isConnected) client.disconnect()
   client.close()
  }
 }
 override fun onDestroy() {
  running = false
  handler.removeCallbacks(sync)
  if (sleepReceiverRegistered) {
   unregisterReceiver(sleepReceiver)
   sleepReceiverRegistered = false
  }
  worker.quitSafely()
  super.onDestroy()
 }
 override fun onBind(intent: Intent?): IBinder? = null
}
