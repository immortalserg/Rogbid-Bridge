package org.watchbridge.wearos

import android.Manifest
import android.health.connect.HealthConnectManager
import android.health.connect.ReadRecordsRequestUsingFilters
import android.health.connect.ReadRecordsResponse
import android.health.connect.TimeInstantRangeFilter
import android.health.connect.HealthConnectException
import android.health.connect.datatypes.Record
import android.health.connect.datatypes.SleepSessionRecord
import android.health.connect.datatypes.HeartRateRecord
import android.health.connect.datatypes.StepsRecord
import android.health.connect.datatypes.OxygenSaturationRecord
import android.os.OutcomeReceiver
import java.time.Instant
import java.time.temporal.ChronoUnit
import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
 private var heartValue: TextView? = null
 private var stepsValue: TextView? = null
 private var screen = "home"
 private val prefs by lazy { getSharedPreferences("watch_bridge", MODE_PRIVATE) }
 private data class Skin(val bg: Int, val fg: Int, val accent: Int, val tile: Int)
 private fun skin(): Skin = when(prefs.getString("skin", "dark")) {
  "light" -> Skin(0xfff6f8fb.toInt(), 0xff192432.toInt(), 0xff1777c0.toInt(), 0xffe1ebf4.toInt())
  "forest" -> Skin(0xff0d2822.toInt(), Color.WHITE, 0xff74e1ab.toInt(), 0xff1c4b3f.toInt())
  "sunset" -> Skin(0xff31182c.toInt(), Color.WHITE, 0xffffaa6c.toInt(), 0xff67314c.toInt())
  "pink" -> Skin(0xffffe8f2.toInt(), 0xff532340.toInt(), 0xffb8316f.toInt(), 0xfffac0da.toInt())
  "lavender" -> Skin(0xfff4eeff.toInt(), 0xff382a57.toInt(), 0xff704ab5.toInt(), 0xffddcff6.toInt())
  "mint" -> Skin(0xffe8fbf3.toInt(), 0xff19483a.toInt(), 0xff168969.toInt(), 0xffbdebd9.toInt())
  "cream" -> Skin(0xfffff9e8.toInt(), 0xff4e3b23.toInt(), 0xffa46828.toInt(), 0xfff6e2bb.toInt())
  "sky" -> Skin(0xffe8f6ff.toInt(), 0xff1e3c5b.toInt(), 0xff2473b8.toInt(), 0xffc4e3f7.toInt())
  else -> Skin(0xff11151f.toInt(), Color.WHITE, 0xff69bbff.toInt(), 0xff2a3345.toInt())
 }
 private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
 private fun page(): LinearLayout = LinearLayout(this).apply {
  orientation = LinearLayout.VERTICAL
  gravity = Gravity.CENTER_HORIZONTAL
  setPadding(dp(18), dp(16), dp(18), dp(16))
  setBackgroundColor(skin().bg)
 }
 private fun showPage(root: LinearLayout) {
  setContentView(ScrollView(this).apply { setBackgroundColor(skin().bg); addView(root) })
 }
 private fun label(value: String, size: Float = 15f): TextView = TextView(this).apply {
  text = value; textSize = size; setTextColor(skin().fg)
  gravity = Gravity.CENTER; setPadding(dp(3), dp(6), dp(3), dp(6))
 }
 private fun button(title: String, action: () -> Unit): Button = Button(this).apply {
  text = title; isAllCaps = false; textSize = 11f; setTextColor(skin().fg)
  minHeight = dp(34); minimumHeight = dp(34); minWidth = 0; minimumWidth = 0
  setPadding(dp(8), dp(5), dp(8), dp(5))
  background = GradientDrawable().apply { setColor(skin().tile); cornerRadius = dp(12).toFloat() }
  setOnClickListener { action() }
 }
 private fun tile(title: String, initial: String, action: () -> Unit): Pair<LinearLayout, TextView> {
  val cell = LinearLayout(this).apply {
   orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
   setPadding(dp(3), dp(5), dp(3), dp(5))
   background = GradientDrawable().apply { setColor(skin().tile); cornerRadius = dp(14).toFloat() }
   setOnClickListener { action() }
  }
  cell.addView(label(title, 11f).apply { setTextColor(skin().accent) })
  val value = label(initial, 18f).apply { typeface = Typeface.DEFAULT_BOLD }
  cell.addView(value)
  return cell to value
 }
 private fun showHome() {
  screen = "home"; heartValue = null; stepsValue = null
  val root = page()
  root.addView(label("Watch Bridge", 20f).apply { typeface = Typeface.DEFAULT_BOLD })
  val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
  actions.addView(button("♥", { startSensors() }), LinearLayout.LayoutParams(0, dp(38), 1f).apply { setMargins(dp(5), dp(4), dp(5), dp(6)) })
  actions.addView(button("O₂", { showJournal("oxygen") }), LinearLayout.LayoutParams(0, dp(38), 1f).apply { setMargins(dp(5), dp(4), dp(5), dp(6)) })
  root.addView(actions)
  val first = LinearLayout(this)
  val second = LinearLayout(this)
  val heartTile = tile("♥ Пульс", heart?.toInt()?.toString() ?: "—") { showJournal("heart") }
  val oxygenTile = tile("O₂ Кислород", "—") { showJournal("oxygen") }
  val stepsTile = tile("♟ Шаги", steps?.toInt()?.toString() ?: "—") { showJournal("steps") }
  val sleepTile = tile("☾ Сон", "—") { showJournal("sleep") }
  heartValue = heartTile.second; stepsValue = stepsTile.second
  for ((row, cell) in listOf(first to heartTile.first, first to oxygenTile.first, second to stepsTile.first, second to sleepTile.first)) {
   row.addView(cell, LinearLayout.LayoutParams(0, dp(76), 1f).apply { setMargins(dp(5), dp(5), dp(5), dp(5)) })
  }
  root.addView(first); root.addView(second)
  root.addView(button("Настройки") { showSettings() }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38)).apply { setMargins(dp(5), dp(10), dp(5), dp(4)) })
  showPage(root)
 }
 private fun showJournal(kind: String) {
  screen = kind
  val root = page()
  val title = when(kind) { "heart" -> "История пульса"; "oxygen" -> "История кислорода"; "steps" -> "История шагов"; else -> "История сна" }
  root.addView(label(title, 19f))
  root.addView(label("Исторические записи пока не подключены. Данные не выдумываются.", 13f))
  if (kind == "heart") root.addView(label("Сейчас: ${heart?.toInt()?.toString() ?: "—"} bpm"))
  if (kind == "steps") root.addView(label("Счётчик с перезагрузки: ${steps?.toInt()?.toString() ?: "—"}"))
  root.addView(button("Назад") { showHome() })
  showPage(root)
 }
 private fun showSettings() {
  screen = "settings"
  val root = page()
  root.addView(label("Настройки", 19f))
  root.addView(label("Тема оформления", 14f))
  for (name in listOf("dark","light","forest","sunset","pink","lavender","mint","cream","sky")) {
   root.addView(button(name) { prefs.edit().putString("skin", name).apply(); showSettings() }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(36)).apply { setMargins(dp(5), dp(4), dp(5), dp(4)) })
  }
  root.addView(button("Диагностика датчиков") { showDiagnostics() })
  root.addView(label("MQTT: пока не подключён", 12f))
  root.addView(button("Назад") { showHome() })
  showPage(root)
 }
 private fun showDiagnostics() {
  screen = "diagnostics"
  val root = page()
  root.addView(label("Диагностика", 19f))
  output = label("", 12f).apply { gravity = Gravity.START }
  root.addView(output)
  root.addView(button("Разрешения") { requestMissingPermissions() })
  root.addView(button("Samsung provider") { checkProvider() })
  root.addView(button("Health Connect: 7 дней") { checkHealthConnect() })
  root.addView(button("Датчики") { startSensors() })
  root.addView(button("Назад") { showSettings() })
  showPage(root); refresh()
 }

 private var heart: Float? = null
 private var steps: Float? = null
 private var sensorInfo = "Waiting for permissions"
 private var providerInfo = "Not checked"
 private var healthConnectInfo = "Not checked"
 private val permissions = arrayOf(
  Manifest.permission.ACTIVITY_RECOGNITION,
  "android.permission.health.READ_HEART_RATE"
 )
 private val healthPermissions = arrayOf(
  "android.permission.health.READ_HEART_RATE",
  "android.permission.health.READ_SLEEP",
  "android.permission.health.READ_STEPS",
  "android.permission.health.READ_OXYGEN_SATURATION"
 )

 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val prefs = getSharedPreferences("watch_bridge", MODE_PRIVATE)
  if (prefs.getString("device_id", null) == null) {
   prefs.edit().putString("device_id", "wearos_" + UUID.randomUUID().toString().replace("-", "")).apply()
  }
  manager = getSystemService(SENSOR_SERVICE) as SensorManager
  showHome()
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
  if (requestCode == 101) checkHealthConnect()
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

 private fun checkHealthConnect() {
  if (Build.VERSION.SDK_INT < 34) {
   healthConnectInfo = "Health Connect requires API 34+"
   refresh()
   return
  }
  val missing = healthPermissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
  if (missing.isNotEmpty()) {
   healthConnectInfo = "Missing permissions: " + missing.joinToString(", ") { it.substringAfterLast('.') }
   refresh()
   requestPermissions(missing.toTypedArray(), 101)
   return
  }
  val hc = getSystemService(HealthConnectManager::class.java)
  if (hc == null) {
   healthConnectInfo = "HealthConnectManager unavailable"
   refresh()
   return
  }
  healthConnectInfo = "Reading last 7 days..."
  refresh()
  val from = Instant.now().minus(7, ChronoUnit.DAYS)
  val to = Instant.now()
  readHealthRecords(hc, SleepSessionRecord::class.java, from, to, "Sleep") { records ->
   records.take(3).joinToString(" | ") { r ->
    "${r.startTime}–${r.endTime} stages=${r.stages.size} origin=${r.metadata.dataOrigin.packageName}"
   }
  }
  readHealthRecords(hc, HeartRateRecord::class.java, from, to, "Heart rate") { records ->
   records.take(3).joinToString(" | ") { r ->
    "samples=${r.samples.size} origin=${r.metadata.dataOrigin.packageName}"
   }
  }
  readHealthRecords(hc, StepsRecord::class.java, from, to, "Steps") { records ->
   records.take(3).joinToString(" | ") { r ->
    "count=${r.count} origin=${r.metadata.dataOrigin.packageName}"
   }
  }
  readHealthRecords(hc, OxygenSaturationRecord::class.java, from, to, "SpO2") { records ->
   records.take(3).joinToString(" | ") { r ->
    "value=${r.percentage.value} origin=${r.metadata.dataOrigin.packageName}"
   }
  }
 }

 private fun <T : Record> readHealthRecords(
  hc: HealthConnectManager, clazz: Class<T>, from: Instant, to: Instant,
  title: String, describe: (List<T>) -> String
 ) {
  try {
   val request = ReadRecordsRequestUsingFilters.Builder(clazz)
    .setTimeRangeFilter(TimeInstantRangeFilter.Builder().setStartTime(from).setEndTime(to).build())
    .setPageSize(100)
    .build()
   hc.readRecords(request, mainExecutor, object : OutcomeReceiver<ReadRecordsResponse<T>, HealthConnectException> {
    override fun onResult(result: ReadRecordsResponse<T>) {
     val rows = result.records
     val preview = if (rows.isEmpty()) "No records" else describe(rows)
     healthConnectInfo += "\n$title: ${rows.size} records; $preview" +
      if (result.nextPageToken != -1L) " (more pages)" else ""
     refresh()
    }
    override fun onError(error: HealthConnectException) {
     healthConnectInfo += "\\n$title: ${error.javaClass.simpleName}: ${error.message}"
     refresh()
    }
   })
  } catch (e: Exception) {
   healthConnectInfo += "\\n$title: ${e.javaClass.simpleName}: ${e.message}"
   refresh()
  }
 }

 private fun checkProvider() {
  val authority = "com.samsung.android.wear.shealth.healthdataprovider"
  val info = packageManager.resolveContentProvider(authority, 0)
  if (info == null) {
   providerInfo = "Provider not visible / not found"
  } else {
   val permission = "com.samsung.android.wear.shealth.healthdataprovider.permission.READ"
   val granted = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
   val uri = Uri.parse("content://$authority")
   val types = listOf("heart_rate", "steps", "sleep", "spo2")
   val results = types.map { type ->
    try {
     val response = contentResolver.call(uri, type, null, Bundle())
     val summary = if (response == null) "null bundle" else {
      response.keySet().sorted().joinToString(", ") { key ->
       val value = response.get(key)
       "$key=${value?.toString()?.take(120) ?: "null"}"
      }.ifEmpty { "empty bundle" }
     }
     "$type: $summary"
    } catch (e: Exception) {
     "$type: ${e.javaClass.simpleName}: ${e.message?.take(160)}"
    }
   }
   providerInfo = "exported=${info.exported}, readPermission=${info.readPermission}, granted=$granted\n" +
    results.joinToString("\n")
  }
  refresh()
 }

 private fun refresh() {
  if (screen == "home") { heartValue?.text = heart?.toInt()?.toString() ?: "—"; stepsValue?.text = steps?.toInt()?.toString() ?: "—" }
  if (screen != "diagnostics" || !::output.isInitialized) return
  output.text = "Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}\n" +
     "Heart rate: ${heart?.toString() ?: "waiting"} bpm\n" +
     "Step counter: ${steps?.toInt()?.toString() ?: "waiting"} (since reboot)\n\n" +
     "Sensors:\n$sensorInfo\n\nSamsung provider:\n$providerInfo\n\n" +
     "Health Connect:\n$healthConnectInfo\n\nNo data sent to MQTT."
 }

 override fun onSensorChanged(event: SensorEvent) {
  if (event.values.isEmpty()) return
  when (event.sensor.type) {
   Sensor.TYPE_HEART_RATE -> {
    val bpm = event.values[0]
    if (bpm.isFinite() && bpm > 0f) heart = bpm
   }
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
