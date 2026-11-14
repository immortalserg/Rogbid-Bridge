package org.watchbridge.wearos

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Wear OS controls. Unsupported health features are visible placeholders. */
class MainActivity : Activity() {
 private val permissionRequest = 1234
 private lateinit var broker: EditText
 private lateinit var username: EditText
 private lateinit var password: EditText
 private lateinit var status: TextView

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  WatchConfig.deviceId(this)
  render()
 }

 private fun render() {
  val root = LinearLayout(this).apply {
   orientation = LinearLayout.VERTICAL
   gravity = Gravity.CENTER_HORIZONTAL
   setPadding(20, 20, 20, 20)
  }
  fun label(value: String, size: Float = 14f): TextView =
   TextView(this).apply {
    text = value
    textSize = size
    gravity = Gravity.CENTER
    setPadding(0, 8, 0, 8)
    root.addView(this)
   }
  fun field(hintText: String, saved: String, secret: Boolean = false): EditText =
   EditText(this).apply {
    hint = hintText
    setSingleLine(true)
    setText(saved)
    textSize = 13f
    inputType = InputType.TYPE_CLASS_TEXT or
     (if (secret) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_VARIATION_URI)
    root.addView(this)
   }
  fun button(value: String, onClick: () -> Unit) {
   root.addView(Button(this).apply {
    text = value
    textSize = 13f
    setOnClickListener { onClick() }
   })
  }
  label("Watch Bridge", 19f)
  label("Wear OS / " + Build.MODEL, 12f)
  label("ID: " + WatchConfig.deviceId(this).takeLast(10), 11f)
  label("Pulse · Steps · Calories · Distance", 12f)
  label("Sleep: not implemented", 11f)
  label("SpO₂: not implemented", 11f)
  label("Floors: not implemented", 11f)
  broker = field("ssl://broker:8883", WatchConfig.broker(this))
  username = field("MQTT user", WatchConfig.username(this))
  password = field("MQTT password", WatchConfig.password(this), true)
  button("Save MQTT settings") { save() }
  button("Start health sync") {
   if (save()) requestOrStart()
  }
  button("Stop health sync") {
   stopService(Intent(this, HealthSyncService::class.java))
   WatchConfig.setStatus(this, "Monitoring stopped")
   refreshStatus()
  }
  button("Refresh status") { refreshStatus() }
  status = label(WatchConfig.status(this), 11f)
  setContentView(ScrollView(this).apply { addView(root) })
 }

 private fun save(): Boolean =
  try {
   WatchConfig.save(this, broker.text.toString(), username.text.toString(), password.text.toString())
   Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
   true
  } catch (e: IllegalArgumentException) {
   Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
   false
  }

 private fun missingPermissions(): Array<String> {
  val permissions = mutableListOf(
   Manifest.permission.BODY_SENSORS,
   Manifest.permission.ACTIVITY_RECOGNITION
  )
  if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
  return permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()
 }

 private fun requestOrStart() {
  val missing = missingPermissions()
  if (missing.isNotEmpty()) {
   requestPermissions(missing, permissionRequest)
   return
  }
  startForegroundService(Intent(this, HealthSyncService::class.java))
  WatchConfig.setStatus(this, "Starting health sync")
  refreshStatus()
 }

 override fun onRequestPermissionsResult(
  requestCode: Int, permissions: Array<out String>, grantResults: IntArray
 ) {
  super.onRequestPermissionsResult(requestCode, permissions, grantResults)
  if (requestCode == permissionRequest) {
   if (missingPermissions().isEmpty()) requestOrStart()
   else Toast.makeText(this, "Grant health permissions to start", Toast.LENGTH_LONG).show()
  }
 }

 private fun refreshStatus() { status.text = WatchConfig.status(this) }
}
