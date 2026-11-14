package org.watchbridge.wearos

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

/** Local journal; unsent events survive restarts and temporary network outages. */
class HealthJournal(context: Context) : SQLiteOpenHelper(context, "watch_bridge_health.db", null, 1) {
 override fun onCreate(db: SQLiteDatabase) {
  db.execSQL("CREATE TABLE events (kind TEXT NOT NULL, record_id TEXT NOT NULL, measured_at TEXT NOT NULL, payload TEXT NOT NULL, sent INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(kind, record_id))")
  db.execSQL("CREATE INDEX idx_events_sent ON events(sent, measured_at)")
 }
 override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

 @Synchronized fun save(kind: String, id: String, measuredAt: String, payload: JSONObject) {
  require(kind in setOf("heart", "steps"))
  val db = writableDatabase
  val existing = db.rawQuery("SELECT payload FROM events WHERE kind=? AND record_id=?", arrayOf(kind, id)).use {
   if (it.moveToFirst()) it.getString(0) else null
  }
  if (existing == payload.toString()) return
  val values = ContentValues().apply {
   put("kind", kind)
   put("record_id", id)
   put("measured_at", measuredAt)
   put("payload", payload.toString())
   put("sent", 0)
  }
  db.insertWithOnConflict("events", null, values, SQLiteDatabase.CONFLICT_REPLACE)
  // Keep bounded storage on a watch: never remove unsent events here.
  db.execSQL("DELETE FROM events WHERE sent=1 AND measured_at < datetime('now','-30 days')")
 }

 data class Event(val kind: String, val recordId: String, val payload: String)

 @Synchronized fun unsent(limit: Int = 300): List<Event> {
  val items = mutableListOf<Event>()
  readableDatabase.rawQuery(
   "SELECT kind, record_id, payload FROM events WHERE sent=0 ORDER BY measured_at ASC LIMIT ?",
   arrayOf(limit.toString())
  ).use { cursor ->
   while (cursor.moveToNext()) items.add(Event(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
  }
  return items
 }

 @Synchronized fun markSent(event: Event) {
  // Guard against a step total changing while the previous version is in flight.
  val values = ContentValues().apply { put("sent", 1) }
  writableDatabase.update(
   "events", values, "kind=? AND record_id=? AND payload=?",
   arrayOf(event.kind, event.recordId, event.payload)
  )
 }
}
