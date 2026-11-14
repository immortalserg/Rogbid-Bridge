package org.watchbridge.wearos

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.UUID

/**
 * Wear OS entry point. Sensor collection and MQTT setup will be added in later steps.
 * Do not claim to publish data until permissions and a transport are implemented.
 */
class MainActivity : Activity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val prefs = getSharedPreferences("watch_bridge", MODE_PRIVATE)
  var id = prefs.getString("device_id", null)
  if (id == null) {
   id = "wearos_" + UUID.randomUUID().toString().replace("-", "")
   prefs.edit().putString("device_id", id).apply()
  }
  val root = LinearLayout(this).apply {
   orientation = LinearLayout.VERTICAL
   gravity = Gravity.CENTER
   setPadding(24, 24, 24, 24)
  }
  fun line(value: String, size: Float) {
   root.addView(TextView(this).apply {
    text = value
    textSize = size
    gravity = Gravity.CENTER
    setPadding(0, 12, 0, 12)
   })
  }
  line("Watch Bridge", 20f)
  line("Wear OS prototype", 14f)
  line("Device ID: " + id.takeLast(10), 12f)
  line("MQTT and health sensors are not connected yet", 12f)
  setContentView(ScrollView(this).apply { addView(root) })
 }
}
