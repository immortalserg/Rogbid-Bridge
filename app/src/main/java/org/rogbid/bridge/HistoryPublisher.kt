package org.rogbid.bridge

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/**
 * Replays local journals on every scheduled sync. QoS 1 and stable IDs make
 * replay safe after disconnections; the HA SQLite journal deduplicates.
 * Never retain history messages (retained history would only keep one record).
 */
object HistoryPublisher {
 private fun send(client: MqttClient, device: String, kind: String, record: JSONObject) {
  val message = MqttMessage(record.toString().toByteArray(Charsets.UTF_8)).apply {
   qos = 1
   isRetained = false
  }
  client.publish("rogbid/$device/history/$kind", message)
 }

 fun publish(context: Context, client: MqttClient, device: String) {
  publishMeasurements(context, client, device)
  publishSteps(context, client, device)
  publishSleep(context, client, device)
 }

 private fun publishMeasurements(context: Context, client: MqttClient, device: String) {
  val file = context.getDatabasePath("measurements.db")
  if (!file.exists()) return
  val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
  try {
   db.rawQuery(
    "SELECT id, measured_at, sensor_type, heart_rate, spo2 FROM measurements ORDER BY id DESC LIMIT 500",
    null
   ).use { cursor ->
    while (cursor.moveToNext()) {
     val type = cursor.getInt(2)
     val kind = when (type) { 21 -> "heart"; 31 -> "oxygen"; else -> continue }
     val timestamp = cursor.getString(1) ?: continue
     val record = JSONObject()
      .put("kind", kind)
      .put("id", "$kind:${cursor.getLong(0)}")
      .put("measured_at", timestamp)
     val index = if (type == 21) 3 else 4
     if (cursor.isNull(index)) continue
     record.put(if (type == 21) "bpm" else "spo2", cursor.getInt(index))
     send(client, device, kind, record)
    }
   }
  } finally { db.close() }
 }

 private fun publishSteps(context: Context, client: MqttClient, device: String) {
  context.contentResolver.query(
   Uri.parse("content://com.watchHealth.Walk/query"),
   arrayOf("date", "steps", "kcal", "diatance"),
   null, null, "date DESC"
  )?.use { cursor ->
   val dateIndex = cursor.getColumnIndex("date")
   if (dateIndex < 0) return
   var count = 0
   while (cursor.moveToNext() && count++ < 100) {
    val date = cursor.getString(dateIndex) ?: continue
    val day = try { LocalDate.parse(date.take(10)) } catch (_: Exception) { continue }
    val record = JSONObject()
     .put("kind", "steps")
     .put("id", "steps:${day}")
     .put("measured_at", day.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime().toString())
     .put("date", day.toString())
    for (column in arrayOf("steps", "kcal", "diatance")) {
     val index = cursor.getColumnIndex(column)
     if (index >= 0 && !cursor.isNull(index)) record.put(column, cursor.getDouble(index))
    }
    send(client, device, "steps", record)
   }
  }
 }

 private fun publishSleep(context: Context, client: MqttClient, device: String) {
  val repo = SleepRepository(context)
  try {
   for (row in repo.history(500)) {
    val date = row.optString("sleep_date")
    val start = row.optString("start_time")
    val end = row.optString("end_time")
    val stages = row.optString("stages_json")
    if (date.isBlank() || start.isBlank() || end.isBlank()) continue
    // The unique SQLite tuple includes stages_json; hash is stable across replays.
    val key = "$date|$start|$end|$stages"
    val digest = java.security.MessageDigest.getInstance("SHA-256")
     .digest(key.toByteArray(Charsets.UTF_8))
     .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    val wakeDate = try { LocalDate.parse(date) } catch (_: Exception) { continue }
    val record = JSONObject(row.toString())
     .put("kind", "sleep")
     .put("id", "sleep:$digest")
     .put("measured_at", wakeDate.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime().toString())
    send(client, device, "sleep", record)
   }
  } finally { repo.close() }
 }
}
