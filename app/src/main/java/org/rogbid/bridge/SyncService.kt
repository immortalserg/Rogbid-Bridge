package org.rogbid.bridge

import android.app.*
import android.content.Intent
import android.net.Uri
import android.os.*
import android.provider.Settings
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject

class SyncService : Service() {
 private val worker = HandlerThread("RogbidMqttSync")
 private lateinit var handler: Handler
 @Volatile private var running = false
 private val intervalMs = 5 * 60 * 1000L
 private val sync = object : Runnable {
  override fun run() {
   if (!running) return
   try {
    publish()
    getSharedPreferences("mqtt", MODE_PRIVATE).edit()
     .putString("last_sync", "OK: " + System.currentTimeMillis()).apply()
   } catch (e: Exception) {
    getSharedPreferences("mqtt", MODE_PRIVATE).edit()
     .putString("last_sync", "ERROR: " + e.javaClass.simpleName + ": " + e.message).apply()
   }
   if (running) handler.postDelayed(this, intervalMs)
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
   .setContentText("MQTT sync every 5 minutes")
   .setContentIntent(open)
   .setOngoing(true)
   .build()
  startForeground(1001, notification)
 }
 override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
  if (intent?.action == "STOP") {
   getSharedPreferences("mqtt", MODE_PRIVATE).edit().putBoolean("auto", false).apply()
   stopSelf()
   return START_NOT_STICKY
  }
  if (!running) {
   running = true
   handler.post(sync)
  }
  return START_STICKY
 }
 private fun publish() {
  val prefs = getSharedPreferences("mqtt", MODE_PRIVATE)
  val host = prefs.getString("host", "") ?: ""
  val port = prefs.getString("port", "1883")?.toIntOrNull() ?: 1883
  val id = prefs.getString("device", "king") ?: "king"
  if (host.isBlank() || !id.matches(Regex("[A-Za-z0-9_-]+"))) return
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
  worker.quitSafely()
  super.onDestroy()
 }
 override fun onBind(intent: Intent?): IBinder? = null
}
