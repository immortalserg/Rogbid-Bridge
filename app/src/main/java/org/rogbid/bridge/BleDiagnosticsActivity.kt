package org.rogbid.bridge

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothGattServerCallback
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** On-device capability probe. Does not advertise or expose health information. */
class BleDiagnosticsActivity : Activity() {
 private lateinit var output: TextView
 private val permissionRequest = 4102

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val column = LinearLayout(this).apply {
   orientation = LinearLayout.VERTICAL
   setPadding(20, 20, 20, 20)
  }
  column.addView(TextView(this).apply {
   text = "BLE diagnostics / Диагностика BLE"
   textSize = 20f
  })
  output = TextView(this).apply { textSize = 15f }
  val run = Button(this).apply { text = "Check BLE / Проверить BLE" }
  val back = Button(this).apply { text = "Back / Назад" }
  column.addView(run)
  column.addView(output)
  column.addView(back)
  setContentView(ScrollView(this).apply { addView(column) })
  run.setOnClickListener { checkCapabilities() }
  back.setOnClickListener { finish() }
  checkCapabilities()
 }

 private fun checkCapabilities() {
  if (Build.VERSION.SDK_INT >= 31 &&
   checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
   output.text = "Bluetooth permission required / Требуется разрешение Bluetooth"
   requestPermissions(
    arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE),
    permissionRequest
   )
   return
  }
  val result = StringBuilder()
  result.append("Android API: ").append(Build.VERSION.SDK_INT).append('\n')
  result.append("BLE feature: ").append(
   packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
  ).append('\n')
  val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
  val adapter = manager.adapter
  if (adapter == null) {
   output.text = result.append("Bluetooth adapter: unavailable").toString()
   return
  }
  result.append("Bluetooth enabled: ").append(adapter.isEnabled).append('\n')
  if (!adapter.isEnabled) {
   result.append("Enable Bluetooth and retry / Включите Bluetooth и повторите")
   output.text = result.toString()
   return
  }
  try {
   result.append("Multiple advertising supported: ")
    .append(adapter.isMultipleAdvertisementSupported).append('\n')
   result.append("BLE advertiser available: ")
    .append(adapter.bluetoothLeAdvertiser != null).append('\n')
  } catch (e: SecurityException) {
   result.append("Advertising check permission denied: ").append(e.message).append('\n')
  } catch (e: Exception) {
   result.append("Advertising check failed: ").append(e.javaClass.simpleName).append('\n')
  }
  if (Build.VERSION.SDK_INT >= 31 &&
   checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
   output.text = result.append("GATT: missing BLUETOOTH_CONNECT permission").toString()
   return
  }
  try {
   val server = manager.openGattServer(this, object : BluetoothGattServerCallback() {})
   result.append("Open GATT server: ").append(if (server == null) "FAILED" else "OK").append('\n')
   server?.close()
   result.append("Diagnostic GATT server closed; no service was advertised.")
  } catch (e: Exception) {
   result.append("Open GATT server failed: ").append(e.javaClass.simpleName)
    .append(": ").append(e.message)
  }
  output.text = result.toString()
 }

 override fun onRequestPermissionsResult(
  requestCode: Int, permissions: Array<out String>, grantResults: IntArray
 ) {
  super.onRequestPermissionsResult(requestCode, permissions, grantResults)
  if (requestCode == permissionRequest) {
   if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
    checkCapabilities()
   } else {
    output.text = "Bluetooth permission denied / Нет разрешения Bluetooth"
   }
  }
 }
