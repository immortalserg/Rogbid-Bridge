package org.watchbridge.wearos

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.UUID

/** Read-only, on-device diagnostics. No MQTT publishing or historical data access yet. */
class MainActivity : Activity(), SensorEventListener {
 private lateinit var manager: SensorManager
 private lateinit var output: TextView
 private var heart: Float? = null
 private var steps: Float? = null
 private var sensorInfo = "Waiting for permissions"
 private var providerInfo = "Not checked"
 private val permissions = arrayOf(
  Manifest.permission.ACTIVITY_RECOGNITION,
  "android.permission.health.READ_HEART_RATE"
 )

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val prefs = getSharedPreferences("watch_bridge", MODE_PRIVATE)
  if (prefs.getString("device_id", null) == null) {
   prefs.edit().putString("device_id", "wearos_" + UUID.randomUUID().toString().replace("-", "")).apply()
  }
  manager = getSystemService(SENSOR_SERVICE) as SensorManager
  val root = LinearLayout(this).apply {
   orientation = LinearLayout.VERTICAL
   gravity = Gravity.CENTER
   setPadding(20, 20, 20, 20)
  }
  root.addView(TextView(this).apply {
   text = "Watch Bridge diagnostics"
   textSize = 18f
   gravity = Gravity.CENTER
  })
  output = TextView(this).apply {
   textSize = 12f
   setPadding(0, 14, 0, 14)
  }
  root.addView(output)
  root.addView(Button(this).apply {
   text = "Grant permissions"
   setOnClickListener { requestMissingPermissions() }
  })
  root.addView(Button(this).apply {
   text = "Check Samsung provider"
   setOnClickListener { checkProvider() }
  })
  root.addView(Button(this).apply {
   text = "Refresh sensors"
   setOnClickListener { startSensors() }
  })
  setContentView(ScrollView(this).apply { addView(root) })
  requestMissingPermissions()
  refresh()
 }

 private fun requestMissingPermissions() {
  val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
  if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 100)
  else startSensors()
 }

 override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
  super.onRequestPermissionsResult(requestCode, permissions, grantResults)
  if (requestCode == 100) startSensors()
 }

 private fun startSensors() {
  manager.unregisterListener(this)
  val details = mutableListOf<String>()
  val sensors = listOf(Sensor.TYPE_HEART_RATE, Sensor.TYPE_STEP_COUNTER)
  for (type in sensors) {
   val permission = if (type == Sensor.TYPE_HEART_RATE) "android.permission.health.READ_HEART_RATE"
     else Manifest.permission.ACTIVITY_RECOGNITION
   if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
    details.add("$type: permission denied")
    continue
   }
   val sensor = manager.getDefaultSensor(type)
   if (sensor == null) {
    details.add("$type: not found")
   } else {
    val registered = try { manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL) }
      catch (e: SecurityException) { false }
    details.add("${sensor.name}: ${if (registered) "listening" else "registration failed"}")
   }
  }
  sensorInfo = details.joinToString("\n")
  refresh()
 }

 private fun checkProvider() {
  val authority = "com.samsung.android.wear.shealth.healthdataprovider"
  val info = packageManager.resolveContentProvider(authority, 0)
  if (info == null) {
   providerInfo = "Provider not visible / not found"
  } else {
   val permission = "com.samsung.android.wear.shealth.healthdataprovider.permission.READ"
   val granted = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
   // Querying the root URI is exploratory only; the provider may require documented paths.
   val queryResult = try {
    contentResolver.query(Uri.parse("content://$authority/"), null, null, null, null)?.use {
     "root query OK, rows=${it.count}"
    } ?: "root query returned null"
   } catch (e: Exception) {
    "root query: ${e.javaClass.simpleName}: ${e.message?.take(100)}"
   }
   providerInfo = "exported=${info.exported}, readPermission=${info.readPermission}, granted=$granted\n$queryResult"
  }
  refresh()
 }

 private fun refresh() {
  output.text = "Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}\n" +
   "Heart rate: ${heart?.toString() ?: "waiting"} bpm\n" +
   "Step counter: ${steps?.toInt()?.toString() ?: "waiting"} (since reboot)\n\n" +
   "Sensors:\n$sensorInfo\n\nSamsung provider:\n$providerInfo\n\n" +
   "No data sent to MQTT."
 }

 override fun onSensorChanged(event: SensorEvent) {
  if (event.values.isEmpty()) return
  when (event.sensor.type) {
   Sensor.TYPE_HEART_RATE -> heart = event.values[0]
   Sensor.TYPE_STEP_COUNTER -> steps = event.values[0]
  }
  refresh()
 }

 override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
 override fun onPause() {
  super.onPause()
  manager.unregisterListener(this)
 }
 override fun onResume() {
  super.onResume()
  if (::manager.isInitialized && ::output.isInitialized) startSensors()
 }
}
