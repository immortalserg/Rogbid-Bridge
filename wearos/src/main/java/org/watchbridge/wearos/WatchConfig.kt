package org.watchbridge.wearos

import android.content.Context
import java.util.UUID

/** Stable identity and device-local MQTT settings. */
object WatchConfig {
 private const val FILE = "watch_bridge"
 private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

 @Synchronized fun deviceId(context: Context): String {
  val p = prefs(context)
  val existing = p.getString("device_id", null)
  if (existing != null && existing.matches(Regex("wearos_[a-f0-9]{32}"))) return existing
  val id = "wearos_" + UUID.randomUUID().toString().replace("-", "")
  check(p.edit().putString("device_id", id).commit()) { "Unable to persist watch identity" }
  return id
 }
 fun broker(context: Context) = prefs(context).getString("mqtt_broker", "")?.trim().orEmpty()
 fun username(context: Context) = prefs(context).getString("mqtt_username", "").orEmpty()
 fun password(context: Context) = prefs(context).getString("mqtt_password", "").orEmpty()

 fun save(context: Context, broker: String, username: String, password: String) {
  val address = broker.trim()
  require(address.isEmpty() || (address.startsWith("tcp://") || address.startsWith("ssl://"))) {
   "Use tcp://host:1883 or ssl://host:8883"
  }
  prefs(context).edit()
   .putString("mqtt_broker", address)
   .putString("mqtt_username", username.trim())
   .putString("mqtt_password", password)
   .apply()
 }
 fun status(context: Context) = prefs(context).getString("last_status", "Not synced yet").orEmpty()
 fun setStatus(context: Context, value: String) {
  prefs(context).edit().putString("last_status", value.take(250)).apply()
 }
}
