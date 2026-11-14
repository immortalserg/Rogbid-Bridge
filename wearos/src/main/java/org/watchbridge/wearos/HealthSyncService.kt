package org.watchbridge.wearos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Explicitly user-started foreground health collection.
 * One background thread handles MQTT and replays unsent SQLite events.
 */
class HealthSyncService : Service() {
 private val worker = HandlerThread("WatchBridgeMqtt")
 private lateinit var handler: Handler
 private lateinit var journal: HealthJournal
 private lateinit var reader: WearHealthReader
 private val metrics = ConcurrentHashMap<String, Double>()
 private var started = false

 private val sync = object : Runnable {
  override fun run() {
   if (!started) return
   try {
    val snapshot = JSONObject().put("model", Build.MODEL).put("platform", "wearos")
    // The HA sensor currently divides kcal by 1000 and distance by 1000.
    for ((key, value) in metrics) {
     snapshot.put(key, if (key == "kcal") value * 1000.0 else value)
    }
    MqttSync(this@HealthSyncService, journal).publish(snapshot)
   } catch (error: Exception) {
    Log.e("WatchBridgeSync", "MQTT synchronization failed", error)
   }
   if (started) handler.postDelayed(this, 60_000L)
  }
 }

 override fun onCreate() {
  super.onCreate()
  worker.start()
  handler = Handler(worker.looper)
  journal = HealthJournal(this)
  reader = WearHealthReader(this, journal) { key, value -> metrics[key] = value }
  val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
  manager.createNotificationChannel(
   NotificationChannel("watch_bridge_sync", "Watch Bridge health sync", NotificationManager.IMPORTANCE_LOW)
  )
 }

 override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
  if (intent?.action == "STOP") {
   stopSelf()
   return START_NOT_STICKY
  }
  if (!started) {
   val open = PendingIntent.getActivity(
    this, 0, Intent(this, MainActivity::class.java),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
   )
   val notification = Notification.Builder(this, "watch_bridge_sync")
    .setSmallIcon(android.R.drawable.stat_notify_sync)
    .setContentTitle("Watch Bridge")
    .setContentText("Health monitoring and MQTT synchronization")
    .setContentIntent(open)
    .setOngoing(true)
    .build()
   if (Build.VERSION.SDK_INT >= 34) {
    startForeground(1002, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
   } else {
    startForeground(1002, notification)
   }
   started = true
   reader.start()
  }
  handler.removeCallbacks(sync)
  handler.post(sync)
  return START_STICKY
 }

 override fun onDestroy() {
  started = false
  handler.removeCallbacks(sync)
  reader.stop()
  journal.close()
  worker.quitSafely()
  super.onDestroy()
 }
 override fun onBind(intent: Intent?): IBinder? = null
}
