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
 private var sleepPollGeneration = 0
 private val sleepIntervalMs = 5 * 60_000L
 private var sleepRequestDate: String? = null
 private fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
 private val sleepSchedule = object : Runnable {
  override fun run() {
   if (!running) return
   sleepBeforeRequest = try { SleepCollector(this@SyncService).read()?.let { snapshotKey(it) } }
    catch (e: Exception) { android.util.Log.w("RogbidSleep", "Baseline read failed", e); null }
   sleepRequestDate = today()
   val generation = ++sleepPollGeneration
   android.util.Log.i("RogbidSleep", "Requesting sleep for ${sleepRequestDate}")
   sendBroadcast(Intent("com.wiite.get.sleep.data"))
   handler.postDelayed({ pollSleep(generation) }, 3000L)
   if (running) handler.postDelayed(this, sleepIntervalMs)
  }
 }
 private fun snapshotKey(s: SleepCollector.Snapshot): String =
  listOf(s.start, s.end, s.duration.toString(), s.stages).joinToString("|")

 // The vendor provider has no date. A sleep session is eligible only if its
 // reported wake-up time falls within the last 24 hours.
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
  if (now - wake.timeInMillis > 24L * 60 * 60 * 1000) return null
  return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(wake.time)
 }

 // The vendor may write its provider late or send no public response broadcast.
 // Poll for changes and log each failed read without losing the next sync.
 private fun pollSleep(generation: Int, attempt: Int = 0) {
  if (!running || generation != sleepPollGeneration) return
  try {
   val snapshot = SleepCollector(this).read()
   if (snapshot != null) {
    val key = snapshotKey(snapshot)
    val changed = sleepBeforeRequest != key
    val wakeDate = recentWakeDate(snapshot.end)
    if (wakeDate != null && wakeDate == sleepRequestDate) {
     // The provider has no date. Do not treat yesterday's unchanged snapshot as new.
     val repo = SleepRepository(this)
     val previous = try { repo.latest() } finally { repo.close() }
     if (previous != null && previous.optString("sleep_date") != wakeDate &&
         previous.optString("start_time") == snapshot.start &&
         previous.optString("end_time") == snapshot.end &&
         previous.optString("stages_json") == snapshot.stages) {
      android.util.Log.i("RogbidSleep", "Previous day's snapshot unchanged; waiting")
      return
     }
     if (previous != null && previous.optString("sleep_date") == wakeDate &&
         previous.optString("start_time") == snapshot.start &&
         previous.optString("end_time") == snapshot.end &&
         previous.optString("stages_json") == snapshot.stages) {
      android.util.Log.d("RogbidSleep", "Sleep snapshot unchanged; no archive update")
      return
     }
     val inserted = SleepCollector(this).archive(snapshot, wakeDate)
     android.util.Log.i("RogbidSleep",
      "Sleep attempt=$attempt changed=$changed wakeDate=$wakeDate inserted=$inserted")
     if (inserted || !changed) return
    } else {
     android.util.Log.i("RogbidSleep",
      "Sleep attempt=$attempt changed=$changed: wake time outside 24h")
    }
   } else android.util.Log.i("RogbidSleep", "Sleep attempt=$attempt: no valid snapshot")
  } catch (e: Exception) {
   android.util.Log.w("RogbidSleep", "Sleep attempt=$attempt failed", e)
  }
  if (attempt < 10) handler.postDelayed({ pollSleep(generation, attempt + 1) }, 3000L)
 }

 private val sleepReceiver = object : BroadcastReceiver() {
  override fun onReceive(context: Context?, intent: Intent?) {
   if (intent?.action != "com.wiite.sleep.data" || !running) return
   // Log metadata only: never dump sleep history or raw broadcast payloads.
   val extras = intent.extras
   val summary = extras?.keySet()?.sorted()?.joinToString { key ->
    val value = try { extras.get(key) } catch (e: Exception) { null }
    "$key:${value?.javaClass?.simpleName ?: "null"}"
   } ?: "none"
   val rawSleep = intent.getByteArrayExtra("data")
   android.util.Log.i("RogbidSleep", "Vendor sleep broadcast received; extras=$summary; dataBytes=${rawSleep?.size ?: 0}")
   if (rawSleep != null) {
    val decoded = VendorSleepDecoder.decode(rawSleep)
    android.util.Log.i("RogbidSleep",
     "Raw sleep diagnostic: points=${decoded?.points?.size ?: 0} " +
     "code=${decoded?.code} first=${decoded?.firstSleep} " +
     "wake=${decoded?.lastWake} duration=${decoded?.durationMinutes} " +
     "stageMinutes=${decoded?.stageMinutes}")
   }
   // The scheduled retry chain already checks the provider. Starting another
   // chain here caused duplicate attempts every three seconds.
  }
 }
 private fun intervalMs(): Long = getSharedPreferences("mqtt", MODE_PRIVATE)
  .getInt("interval_minutes", 1).coerceIn(1, 60) * 60_000L
 private val sync = object : Runnable {
  override fun run() {
   if (!running) return
   val prefs = getSharedPreferences("mqtt", MODE_PRIVATE)
   prefs.edit().putString("last_attempt", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())).apply()
   try {
    if (prefs.getBoolean("auto", false)) {
     publish()
     prefs.edit().putString("last_sync", "OK: " + java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())).apply()
    }
   } catch (e: Exception) {
    prefs.edit().putString("last_sync", "ERROR: " + e.javaClass.simpleName + ": " + e.message).apply()
    android.util.Log.e("RogbidBridge", "MQTT sync failed", e)
   }
   if (running && prefs.getBoolean("auto", false)) handler.postDelayed(this, intervalMs())
  }
 }
 override fun onCreate() {
  super.onCreate()
  L10n.init(this)
  worker.start()
  handler = Handler(worker.looper)
  val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
  manager.createNotificationChannel(NotificationChannel("sync", "Rogbid Bridge sync", NotificationManager.IMPORTANCE_LOW))
  val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
   PendingIntent.FLAG_UPDATE_CURRENT)
  val notification = Notification.Builder(this, "sync")
   .setSmallIcon(android.R.drawable.stat_notify_sync)
   .setContentTitle("Rogbid Bridge")
   .setContentText(L10n.text("sync_notification"))
   .setContentIntent(open)
   .setOngoing(true)
   .build()
  startForeground(1001, notification)
  registerReceiver(sleepReceiver, IntentFilter("com.wiite.sleep.data"))
  sleepReceiverRegistered = true
 }
 override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
  if (intent?.action == "STOP") {
   // STOP now disables MQTT publishing, not local sleep collection.
   getSharedPreferences("mqtt", MODE_PRIVATE).edit().putBoolean("auto", false).apply()
  }
  if (!running) running = true
  handler.removeCallbacks(sync)
  if (getSharedPreferences("mqtt", MODE_PRIVATE).getBoolean("auto", false)) handler.post(sync)
  handler.removeCallbacks(sleepSchedule)
  handler.post(sleepSchedule)
  return START_STICKY
 }
 private fun publish() {
  val prefs = getSharedPreferences("mqtt", MODE_PRIVATE)
  val host = prefs.getString("host", "") ?: ""
  val port = prefs.getString("port", "1883")?.toIntOrNull() ?: 1883
  val id = WatchIdentity.id(this) ?: "king"
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
   // Replay bounded local history on every sync; stable IDs deduplicate on HA.
   HistoryPublisher.publish(this, client, id)
  } finally {
   if (client.isConnected) client.disconnect()
   client.close()
  }
 }
 override fun onDestroy() {
  running = false
  handler.removeCallbacks(sync)
  handler.removeCallbacks(sleepSchedule)
  ++sleepPollGeneration
  if (sleepReceiverRegistered) {
   unregisterReceiver(sleepReceiver)
   sleepReceiverRegistered = false
  }
  worker.quitSafely()
  super.onDestroy()
 }
 override fun onBind(intent: Intent?): IBinder? = null
}
