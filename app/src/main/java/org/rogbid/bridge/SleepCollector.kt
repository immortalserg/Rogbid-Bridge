package org.rogbid.bridge

import android.content.Context
import android.net.Uri
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
  context.contentResolver.query(
   Uri.parse("content://com.wiite.provider/sleep"), null, null, null, null
  )?.use { cursor ->
   if (!cursor.moveToFirst()) return null

   fun value(name: String): String? {
    val index = cursor.getColumnIndex(name)
    return if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
   }

   fun minutes(prefix: String): Int? {
    val hours = value("${prefix}HH")?.toIntOrNull() ?: return null
    val mins = value("${prefix}MM")?.toIntOrNull() ?: return null
    if (hours < 0 || mins !in 0..59) return null
    return hours * 60 + mins
   }

   val start = value("sleeptime") ?: return null
   val end = value("wakeuptime") ?: return null
   val stages = value("dataList") ?: return null
   val duration = minutes("sum") ?: return null
   val deep = minutes("deep") ?: return null
   val light = minutes("light") ?: return null
   val fast = minutes("fast") ?: return null

   if (duration !in 1..1440 || deep + light + fast != duration) return null
   val items = JSONArray(stages)
   if (items.length() < 2) return null
   for (i in 0 until items.length()) {
    val stage = items.optJSONObject(i) ?: return null
    val type = stage.optInt("type", -1)
    if (type !in 1..3 || !validClock(stage.optString("time"))) return null
   }
   if (items.getJSONObject(0).optString("time") != start ||
       items.getJSONObject(items.length() - 1).optString("time") != end ||
       items.getJSONObject(items.length() - 1).optInt("type") != 3) return null

   return Snapshot(start, end, duration, deep, light, fast,
    value("code")?.toIntOrNull(), stages)
  }
  return null
 }

 /** Call only when the wake-up date has been verified from a trusted source. */
 fun archive(snapshot: Snapshot, verifiedWakeDate: String): Boolean {
  require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(verifiedWakeDate)) {
   "Expected verified wake date in yyyy-MM-dd format"
  }
  SleepRepository(context).use { repo ->
   return repo.save(
    verifiedWakeDate, snapshot.start, snapshot.end, snapshot.duration,
    snapshot.deep, snapshot.light, snapshot.fast, snapshot.score, snapshot.stages
   )
  }
 }

 private fun validClock(value: String): Boolean {
  val parts = value.split(":")
  if (parts.size != 2) return false
  val hour = parts[0].toIntOrNull() ?: return false
  val minute = parts[1].toIntOrNull() ?: return false
  return hour in 0..23 && minute in 0..59
 }
}
