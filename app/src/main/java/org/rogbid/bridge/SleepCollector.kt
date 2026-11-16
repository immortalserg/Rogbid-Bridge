package org.rogbid.bridge

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reads the vendor's current sleep snapshot.
 *
 * IMPORTANT: the provider has no date or freshness timestamp. Do not infer the
 * wake-up date from the current clock: an old record may remain in the provider.
 * A caller must establish the wake-up date independently before archiving.
 */
class SleepCollector(private val context: Context) {
 data class Snapshot(
  val start: String,
  val end: String,
  val duration: Int,
  val deep: Int,
  val light: Int,
  val fast: Int,
  val score: Int?,
  val stages: String
 ) {
  fun asJson(): JSONObject = JSONObject().apply {
   put("sleep_start", start)
   put("sleep_end", end)
   put("sleep_duration", duration)
   put("sleep_deep", deep)
   put("sleep_light", light)
   put("sleep_fast", fast)
   if (score != null) put("sleep_score", score)
   put("sleep_stages", JSONArray(stages))
  }
 }

 fun read(): Snapshot? {
  val uri = Uri.parse("content://com.wiite.provider/sleep")
  fun reject(reason: String): Snapshot? {
   Log.i("RogbidSleep", "Snapshot rejected: $reason")
   return null
  }
  val cursor = try {
   context.contentResolver.query(uri, null, null, null, "_id DESC")
  } catch (e: Exception) {
   Log.w("RogbidSleep", "Provider query failed: ${e.javaClass.simpleName}: ${e.message}")
   return null
  } ?: return reject("provider returned null cursor")

  cursor.use {
   Log.i("RogbidSleep", "Provider rows=${it.count} columns=${it.columnNames.joinToString()}")
   if (!it.moveToFirst()) return reject("provider has zero rows")

   fun value(name: String): String? {
    val index = it.getColumnIndex(name)
    return if (index >= 0 && !it.isNull(index)) it.getString(index) else null
   }
   fun minutes(prefix: String): Int? {
    val hours = value("${prefix}HH")?.toIntOrNull() ?: return null
    val mins = value("${prefix}MM")?.toIntOrNull() ?: return null
    if (hours < 0 || mins !in 0..59) return null
    return hours * 60 + mins
   }
   val start = value("sleeptime") ?: return reject("missing sleeptime")
   val end = value("wakeuptime") ?: return reject("missing wakeuptime")
   val stages = value("dataList") ?: return reject("missing dataList")
   val duration = minutes("sum") ?: return reject("invalid sumHH/sumMM")
   val deep = minutes("deep") ?: return reject("invalid deepHH/deepMM")
   val light = minutes("light") ?: return reject("invalid lightHH/lightMM")
   val fast = minutes("fast") ?: return reject("invalid fastHH/fastMM")
   Log.i("RogbidSleep", "Snapshot times start=$start end=$end duration=$duration deep=$deep light=$light fast=$fast")
   if (duration !in 1..1440) return reject("duration outside 1..1440")
   if (deep + light + fast != duration) return reject("stage totals differ from duration")
   val items = try { JSONArray(stages) }
    catch (e: Exception) { return reject("dataList invalid JSON array") }
   if (items.length() < 2) return reject("fewer than two stage points")
   for (i in 0 until items.length()) {
    val stage = items.optJSONObject(i) ?: return reject("stage $i not an object")
    val type = stage.optInt("type", -1)
    if (type !in 1..3) return reject("stage $i invalid type=$type")
    if (!validClock(stage.optString("time"))) return reject("stage $i invalid clock")
   }
   if (items.getJSONObject(0).optString("time") != start)
    return reject("first stage time differs from sleep start")
   if (items.getJSONObject(items.length() - 1).optString("time") != end)
    return reject("last stage time differs from wake time")
   if (items.getJSONObject(items.length() - 1).optInt("type") != 3)
    return reject("last stage type is not wake")
   return Snapshot(start, end, duration, deep, light, fast,
    value("code")?.toIntOrNull(), stages)
  }
 }

 /** Call only when the wake-up date has been verified from a trusted source. */
 fun archive(snapshot: Snapshot, verifiedWakeDate: String): Boolean {
  require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(verifiedWakeDate)) {
   "Expected verified wake date in yyyy-MM-dd format"
  }
  val repo = SleepRepository(context)
  return try {
   repo.save(
    verifiedWakeDate, snapshot.start, snapshot.end, snapshot.duration,
    snapshot.deep, snapshot.light, snapshot.fast, snapshot.score, snapshot.stages
   )
  } finally { repo.close() }
 }

 private fun validClock(value: String): Boolean {
  val parts = value.split(":")
  if (parts.size != 2) return false
  val hour = parts[0].toIntOrNull() ?: return false
  val minute = parts[1].toIntOrNull() ?: return false
  return hour in 0..23 && minute in 0..59
 }
}
