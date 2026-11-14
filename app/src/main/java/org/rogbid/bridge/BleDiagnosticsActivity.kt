package org.rogbid.bridge

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothGattServerCallback
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.util.TypedValue

/** On-device capability probe. Does not advertise or expose health information. */
class BleDiagnosticsActivity : Activity() {
 private lateinit var output: TextView
 private val permissionRequest = 4102

 private data class Skin(val background: Int, val foreground: Int, val accent: Int, val button: Int)
 private fun skin(): Skin = when (getSharedPreferences("mqtt", MODE_PRIVATE).getString("skin", "dark")) {
  "light" -> Skin(Color.rgb(246,248,251), Color.rgb(25,36,50), Color.rgb(23,119,192), Color.rgb(225,235,244))
  "forest" -> Skin(Color.rgb(13,40,34), Color.WHITE, Color.rgb(116,225,171), Color.rgb(28,75,63))
  "sunset" -> Skin(Color.rgb(49,24,44), Color.WHITE, Color.rgb(255,170,108), Color.rgb(103,49,76))
  "pink" -> Skin(Color.rgb(255,232,242), Color.rgb(83,35,64), Color.rgb(184,49,111), Color.rgb(250,192,218))
  "lavender" -> Skin(Color.rgb(244,238,255), Color.rgb(56,42,87), Color.rgb(112,74,181), Color.rgb(221,207,246))
  "mint" -> Skin(Color.rgb(232,251,243), Color.rgb(25,72,58), Color.rgb(22,137,105), Color.rgb(189,235,217))
  "cream" -> Skin(Color.rgb(255,249,232), Color.rgb(78,59,35), Color.rgb(164,104,40), Color.rgb(246,226,187))
  "sky" -> Skin(Color.rgb(232,246,255), Color.rgb(30,60,91), Color.rgb(36,115,184), Color.rgb(196,227,247))
  else -> Skin(Color.rgb(17,21,31), Color.WHITE, Color.rgb(105,187,255), Color.rgb(42,51,69))
 }
 private fun button(label: String, colors: Skin): Button = Button(this).apply {
  text = label
  isAllCaps = false
  setTextColor(colors.foreground)
  setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
  setAutoSizeTextTypeUniformWithConfiguration(13, 18, 1, TypedValue.COMPLEX_UNIT_SP)
  val radius = 18f * resources.displayMetrics.density
  val shape = GradientDrawable().apply { setColor(colors.button); cornerRadius = radius }
  val mask = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = radius }
  background = RippleDrawable(ColorStateList.valueOf(colors.accent and 0x00FFFFFF or 0x66000000),
   shape, mask)
  layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
   (52 * resources.displayMetrics.density).toInt()).apply {
   bottomMargin = (8 * resources.displayMetrics.density).toInt()
  }
 }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val colors = skin()
  val column = LinearLayout(this).apply {
   orientation = LinearLayout.VERTICAL
   setPadding(16, 16, 16, 16)
   setBackgroundColor(colors.background)
  }
  column.addView(TextView(this).apply {
   text = "BLE diagnostics / Диагностика BLE"
   textSize = 22f
   setTextColor(colors.foreground)
  })
  output = TextView(this).apply {
   textSize = 16f
   setTextColor(colors.foreground)
   setPadding(4, 12, 4, 12)
  }
  val run = button("Check BLE / Проверить BLE", colors)
  val start = button("Start BLE GATT / Включить BLE", colors)
  val stop = button("Stop BLE GATT / Выключить BLE", colors)
  val back = button("Back / Назад", colors)
  column.addView(run)
  column.addView(start)
  column.addView(stop)
  column.addView(output)
  column.addView(back)
  setContentView(ScrollView(this).apply {
   setBackgroundColor(colors.background)
   addView(column)
  })
  run.setOnClickListener { checkCapabilities() }
  start.setOnClickListener {
   startService(Intent(this, BleGattService::class.java))
   output.text = "BLE start requested. Check logcat tag WatchBridgeBLE for advertising result."
  }
  stop.setOnClickListener {
   stopService(Intent(this, BleGattService::class.java))
   output.text = "BLE service stop requested / BLE остановлен"
  }
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
}
