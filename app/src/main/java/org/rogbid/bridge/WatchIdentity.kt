package org.rogbid.bridge

import android.content.Context
import java.util.UUID

/**
 * Persistent MQTT identity. Never use a shared default such as "king":
 * two watches with the same topic would overwrite each other's retained state.
 */
object WatchIdentity {
 private const val PREFS = "rogbid_identity"
 private const val KEY = "device_id"

 @Synchronized
 fun id(context: Context): String {
  val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  val existing = prefs.getString(KEY, null)
  if (existing != null && existing.matches(Regex("z10_[a-f0-9]{32}"))) return existing
  val generated = "z10_" + UUID.randomUUID().toString().replace("-", "")
  check(prefs.edit().putString(KEY, generated).commit()) {
   "Cannot persist watch identity; refusing MQTT publication"
  }
  return generated
 }
}
