package org.rogbid.bridge

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

/**
 * Local archive of sleep sessions. The vendor provider exposes only the latest record.
 * sleep_date is the wake-up date, established by the collector after freshness checks.
 */
class SleepRepository(context: Context) :
 SQLiteOpenHelper(context, "rogbid_sleep.db", null, 1) {

 override fun onCreate(db: SQLiteDatabase) {
  db.execSQL("""
   CREATE TABLE sleep_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    sleep_date TEXT NOT NULL,
    start_time TEXT NOT NULL,
    end_time TEXT NOT NULL,
    duration_minutes INTEGER NOT NULL,
    deep_minutes INTEGER NOT NULL,
    light_minutes INTEGER NOT NULL,
    fast_minutes INTEGER NOT NULL,
    score INTEGER,
    stages_json TEXT NOT NULL,
    first_seen INTEGER NOT NULL,
    UNIQUE(sleep_date, start_time, end_time, stages_json)
   )
  """.trimIndent())
 }

 override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
  // Add explicit migrations when the schema changes.
 }

 fun save(
  date: String, start: String, end: String, duration: Int,
  deep: Int, light: Int, fast: Int, score: Int?, stages: String
 ): Boolean {
  val values = ContentValues().apply {
   put("sleep_date", date)
   put("start_time", start)
   put("end_time", end)
   put("duration_minutes", duration)
   put("deep_minutes", deep)
   put("light_minutes", light)
   put("fast_minutes", fast)
   if (score != null) put("score", score)
   put("stages_json", stages)
   put("first_seen", System.currentTimeMillis())
  }
  return writableDatabase.insertWithOnConflict(
   "sleep_history", null, values, SQLiteDatabase.CONFLICT_IGNORE
  ) != -1L
 }

 fun history(limit: Int = 100): List<JSONObject> {
  val records = mutableListOf<JSONObject>()
  readableDatabase.rawQuery(
   "SELECT * FROM sleep_history ORDER BY sleep_date DESC, id DESC LIMIT " +
    limit.coerceIn(1, 500), null
  ).use { cursor ->
   while (cursor.moveToNext()) records.add(rowJson(cursor))
  }
  return records
 }

 private fun rowJson(cursor: android.database.Cursor): JSONObject =
  JSONObject().apply {
   for (i in 0 until cursor.columnCount) {
    if (cursor.isNull(i)) continue
    val name = cursor.getColumnName(i)
    when (name) {
     "id", "duration_minutes", "deep_minutes", "light_minutes",
     "fast_minutes", "score", "first_seen" -> put(name, cursor.getLong(i))
     else -> put(name, cursor.getString(i))
    }
   }
  }

 fun latest(): JSONObject? {
  readableDatabase.rawQuery(
   "SELECT * FROM sleep_history ORDER BY sleep_date DESC, id DESC LIMIT 1", null
  ).use { cursor ->
   if (!cursor.moveToFirst()) return null
   return rowJson(cursor)
  }
 }
}
