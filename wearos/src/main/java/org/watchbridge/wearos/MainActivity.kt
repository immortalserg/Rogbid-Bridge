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
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.util.Log
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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.ProgressBar
import android.widget.EditText
import android.text.InputType
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
  root.addView(label("Watch Bridge", 13f).apply { typeface = Typeface.DEFAULT_BOLD })
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
  root.addView(label("Настройки", 17f))
  root.addView(button("Настройки приложения") { showAppSettings() })
  root.addView(button("Параметры тела") { showBodySettings() })
  root.addView(button("Диагностика датчиков") { showDiagnostics() })
  root.addView(button("Назад") { showHome() })
  showPage(root)
 }
 private fun showAppSettings() {
  screen = "app_settings"
  val root = page()
  root.addView(label("Настройки приложения", 16f))
  root.addView(label("Тема оформления", 13f))
  val themeKeys = listOf("dark", "light", "forest", "sunset", "pink", "lavender", "mint", "cream", "sky")
  val themeNames = themeKeys.map { themeTitle(it) }
  val selector = Spinner(this).apply {
   val palette = skin()
   adapter = object : ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_spinner_item, themeNames) {
    override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
     return (super.getView(position, convertView, parent) as TextView).apply {
      setTextColor(palette.fg)
      setBackgroundColor(palette.tile)
      textSize = 13f
      setPadding(dp(10), dp(8), dp(8), dp(8))
     }
    }
    override fun getDropDownView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
     return (super.getDropDownView(position, convertView, parent) as TextView).apply {
      setTextColor(palette.fg)
      setBackgroundColor(palette.tile)
      textSize = 13f
      setPadding(dp(10), dp(10), dp(8), dp(10))
     }
    }
   }
   setPopupBackgroundDrawable(GradientDrawable().apply { setColor(palette.tile); cornerRadius = dp(10).toFloat() })
   background = GradientDrawable().apply { setColor(palette.tile); cornerRadius = dp(10).toFloat() }
   setSelection(themeKeys.indexOf(prefs.getString("skin", "dark")).coerceAtLeast(0))
   onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
    override fun onNothingSelected(parent: AdapterView<*>?) {}
    override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
     val selected = themeKeys[position]
     if (prefs.getString("skin", "dark") != selected) {
      prefs.edit().putString("skin", selected).apply()
      showAppSettings()
     }
    }
   }
  }
  root.addView(selector, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)).apply {
   setMargins(dp(5), dp(4), dp(5), dp(8))
  })
  root.addView(label("MQTT: пока не подключён", 12f))
  root.addView(button("Назад") { showSettings() })
  showPage(root)
 }
 private fun showBodySettings() {
  screen = "body_settings"
  val root = page()
  root.addView(label("Параметры тела", 18f))
  root.addView(label("Для расчёта состава тела (BIA)", 12f))
  fun field(title: String, key: String, decimal: Boolean): EditText {
   root.addView(label(title, 12f))
   val input = EditText(this).apply {
    setText(prefs.getString(key, "") ?: "")
    textSize = 16f
    setTextColor(skin().fg)
    setHintTextColor(skin().accent)
    gravity = Gravity.CENTER
    inputType = if (decimal) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                else InputType.TYPE_CLASS_NUMBER
    setSingleLine(true)
    background = GradientDrawable().apply { setColor(skin().tile); cornerRadius = dp(10).toFloat() }
    setPadding(dp(8), dp(7), dp(8), dp(7))
   }
   root.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(43)))
   return input
  }
  val height = field("Рост (см)", "body_height", true)
  val weight = field("Вес (кг)", "body_weight", true)
  val age = field("Возраст (лет)", "body_age", false)
  root.addView(label("Пол для алгоритма BIA", 12f))
  val gender = Spinner(this).apply {
   adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Мужской", "Женский"))
   setSelection(if (prefs.getInt("body_gender", 1) == 0) 1 else 0)
  }
  root.addView(gender)
  val notice = label("", 12f)
  root.addView(notice)
  root.addView(button("Сохранить") {
   val h = height.text.toString().replace(',', '.').toFloatOrNull()
   val w = weight.text.toString().replace(',', '.').toFloatOrNull()
   val a = age.text.toString().toIntOrNull()
   if (h == null || h !in 100f..250f || w == null || w !in 20f..300f || a == null || a !in 18..120) {
    notice.text = "Проверь данные: рост 100–250 см, вес 20–300 кг, возраст 18–120"
   } else {
    prefs.edit()
     .putString("body_height", h.toString())
     .putString("body_weight", w.toString())
     .putString("body_age", a.toString())
     .putInt("body_gender", if (gender.selectedItemPosition == 0) 1 else 0)
     .apply()
    notice.text = "Сохранено"
   }
  })
  root.addView(button("Назад") { showSettings() })
  showPage(root)
 }
 private fun themeTitle(name: String): String = when (name) {
  "dark" -> "Тёмная"
  "light" -> "Светлая"
  "forest" -> "Лесная"
  "sunset" -> "Закат"
  "pink" -> "Розовая"
  "lavender" -> "Лавандовая"
  "mint" -> "Мятная"
  "cream" -> "Кремовая"
  "sky" -> "Небесная"
  else -> name
 }
 private var progressBar: ProgressBar? = null
 private var progressLabel: TextView? = null
 private var measurementStatus: TextView? = null
 private var measurementResult: TextView? = null
 private var measurementProgress = 0
 private var measurementMessage = "Ожидание"
 private var measurementPreview = ""
 private fun showDiagnostics() {
  screen = "diagnostics"
  val root = page()
  root.addView(label("Измерения", 19f))
  root.addView(button("♥ Пульс") { showMeasurement("HEART_RATE") })
  root.addView(button("O₂ Кислород") { showMeasurement("SPO2") })
  root.addView(button("ЭКГ") { showMeasurement("ECG") })
  root.addView(button("Состав тела · BIA") { showMeasurement("BIA") })
  root.addView(button("История состава тела") { showBiaHistory() })
  root.addView(button("Системная диагностика") { showSystemDiagnostics() })
  root.addView(button("Назад") { showSettings() })
  showPage(root)
 }
 private fun showMeasurement(tracker: String) {
  screen = "measurement"
  val root = page()
  val title = when (tracker) {
   "BIA" -> "Состав тела"
   "ECG" -> "ЭКГ"
   "SPO2" -> "Кислород"
   else -> "Пульс"
  }
  root.addView(label(title, 19f))
  if (tracker == "BIA") root.addView(label(if (bodyProfile() == null) "Сначала укажи параметры тела в настройках" else "Профиль BIA настроен", 11f))
  progressLabel = label("0%", 24f).apply { typeface = Typeface.DEFAULT_BOLD }
  progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
   max = 100
   progress = 0
   progressTintList = android.content.res.ColorStateList.valueOf(skin().accent)
  }
  measurementStatus = label("Нажми «Начать»", 13f)
  measurementResult = label("", 12f)
  root.addView(progressLabel)
  root.addView(progressBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(12)).apply {
   setMargins(dp(10), dp(5), dp(10), dp(8))
  })
  root.addView(measurementStatus)
  root.addView(measurementResult)
  root.addView(button("Начать") {
   measurementProgress = 0
   measurementMessage = "Ожидание данных датчика…"
   measurementPreview = ""
   if (tracker == "SPO2") startSpO2Measurement() else startSamsungTrackerTest(tracker)
   updateMeasurementUi()
  })
  if (tracker == "BIA") root.addView(button("История результатов") { stopSamsungTrackerTest(); showBiaHistory() })
  root.addView(button("Остановить") {
   stopSamsungTrackerTest()
   measurementMessage = "Измерение остановлено"
   updateMeasurementUi()
  })
  root.addView(button("Назад") {
   stopSamsungTrackerTest()
   progressBar = null
   progressLabel = null
   measurementStatus = null
   measurementResult = null
   showDiagnostics()
  })
  showPage(root)
  updateMeasurementUi()
 }
 private fun biaField(message: String, name: String): Float? =
  Regex("(?:^|\\\\s)" + Regex.escape(name) + "=(-?[0-9]+(?:\\\\.[0-9]+)?)")
   .find(message)?.groupValues?.get(1)?.toFloatOrNull()
 private fun formatBia(value: Float?, unit: String): String =
  if (value != null && value.isFinite()) String.format(Locale.getDefault(), "%.1f %s", value, unit) else "—"
 private fun biaSummary(message: String): String {
  val fields = listOf(
   Triple("Жир", "fatRatio", "%"),
   Triple("Жировая масса", "fatMass", "кг"),
   Triple("Вода", "bodyWater", "кг"),
   Triple("Скелетные мышцы", "muscleMass", "кг"),
   Triple("Доля мышц", "muscleRatio", "%"),
   Triple("Безжировая масса", "fatFreeMass", "кг"),
   Triple("Базовый обмен", "bmr", "ккал/сут"),
   Triple("Импеданс", "impedanceMagnitude", "Ом")
  )
  return fields.joinToString("\\n") { (title, key, unit) ->
   "$title: ${formatBia(biaField(message, key), unit)}"
  }
 }
 private fun saveBiaResult(message: String) {
  val entry = JSONObject()
  entry.put("timestamp", System.currentTimeMillis())
  for (key in listOf("fatRatio", "fatMass", "bodyWater", "muscleRatio", "muscleMass",
                     "bmr", "fatFreeRatio", "fatFreeMass", "impedanceMagnitude", "impedanceDegree")) {
   biaField(message, key)?.let { entry.put(key, it.toDouble()) }
  }
  val history = try { JSONArray(prefs.getString("bia_history", "[]")) } catch (_: Exception) { JSONArray() }
  val updated = JSONArray()
  updated.put(entry)
  for (i in 0 until minOf(history.length(), 49)) updated.put(history.get(i))
  prefs.edit().putString("bia_history", updated.toString()).apply()
 }
 private fun showBiaHistory() {
  screen = "bia_history"
  val root = page()
  root.addView(label("История BIA", 18f))
  val history = try { JSONArray(prefs.getString("bia_history", "[]")) } catch (_: Exception) { JSONArray() }
  if (history.length() == 0) root.addView(label("Завершённых измерений пока нет", 12f))
  for (i in 0 until history.length()) {
   val entry = history.optJSONObject(i) ?: continue
   val date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(entry.optLong("timestamp")))
   val fat = entry.optDouble("fatRatio", Double.NaN)
   val fatText = if (fat.isFinite()) String.format(Locale.getDefault(), "%.1f%%", fat) else "—"
   root.addView(button("$date · $fatText") {
    val details = page()
    details.addView(label(date, 16f))
    val lines = listOf(
     Triple("Жир", "fatRatio", "%"), Triple("Жировая масса", "fatMass", "кг"),
     Triple("Вода", "bodyWater", "кг"), Triple("Мышцы", "muscleMass", "кг"),
     Triple("Безжировая масса", "fatFreeMass", "кг"),
     Triple("Базовый обмен", "bmr", "ккал/сут"),
     Triple("Импеданс", "impedanceMagnitude", "Ом")
    ).joinToString("\\n") { (title, key, unit) ->
     "$title: ${formatBia(if (entry.has(key)) entry.optDouble(key).toFloat() else null, unit)}"
    }
    details.addView(label(lines, 13f))
    details.addView(button("Назад") { showBiaHistory() })
    screen = "bia_details"
    showPage(details)
   })
  }
  root.addView(button("Назад") { showDiagnostics() })
  showPage(root)
 }
 private fun updateMeasurementUi() {
  if (screen != "measurement") return
  progressBar?.progress = measurementProgress
  progressLabel?.text = if (activeTrackerName == "BIA" || measurementProgress > 0) "$measurementProgress%" else "—"
  measurementStatus?.text = measurementMessage
  measurementResult?.text = measurementPreview
 }
 private fun startSpO2Measurement() {
  stopSamsungTrackerTest()
  activeTrackerName = "SPO2"
  measurementMessage = "Измерение кислорода…"
  spo2Probe = SamsungTrackerProbe(this) { message ->
   runOnUiThread {
    val result = publishSpO2FromProbe(message)
    if (result != null) {
     spo2Result = result
     measurementMessage = "Измерение завершено"
     measurementPreview = result
     measurementProgress = 100
    } else if (message.contains("failed", true) || message.contains("error", true) || message.contains("timeout", true)) {
     measurementMessage = "Ошибка: " + message.take(90)
    }
    updateMeasurementUi()
   }
  }
  spo2Probe?.start()
 }
 private fun showSystemDiagnostics() {
  screen = "system_diagnostics"
  val root = page()
  root.addView(label("Система", 18f))
  output = label("", 11f).apply { gravity = Gravity.START }
  root.addView(button("Разрешения") { requestMissingPermissions() })
  root.addView(button("Samsung provider") { checkProvider() })
  root.addView(button("Samsung services") { checkSamsungServices() })
  root.addView(button("BLE: запустить") { startBle() })
  root.addView(button("BLE: остановить") { bleServer?.stop(); bleServer = null; bleInfo = "BLE stopped"; refresh() })
  root.addView(button("Health Connect: 7 дней") { checkHealthConnect() })
  root.addView(button("Разрешения Health Connect") { requestHealthPermissions() })
  root.addView(button("Датчики Android") { startSensors() })
  root.addView(button("Назад") { showDiagnostics() })
  root.addView(output)
  showPage(root)
  refresh()
 }
 private fun biaMessage(code: Int): String = when(code) {
  0 -> "Измерение… удерживай пальцы"
  2 -> "Проверка датчика (NV_NG)"
  4 -> "Часы не касаются запястья"
  7, 8, 9 -> "Коснись обеих боковых кнопок"
  10 -> "Поправь ремешок: слабый контакт"
  11 -> "Слишком сухие пальцы"
  17 -> "Нестабильный контакт"
  else -> "Ошибка BIA: $code"
 }

 private var heart: Float? = null
 private var steps: Float? = null
 private var sensorInfo = "Waiting for permissions"
 private var providerInfo = "Not checked"
 private var samsungServicesInfo = "Not checked"
 private val samsungConnections = mutableListOf<ServiceConnection>()
 private var healthConnectInfo = "Not checked"
 private var spo2Probe: SamsungTrackerProbe? = null
 private var spo2Result = "—"
 private var samsungTrackerTestResult = "Не запускался"
 private var activeTrackerName: String? = null
 private var trackerTestGeneration = 0
 private var trackerSampleCount = 0
 private val trackerSamplePreviews = mutableListOf<String>()
 private var trackerLastUiUpdate = 0L
 private var bleServer: WatchBleServer? = null
 private var bleInfo = "BLE not started"
 private val permissions = arrayOf(
  Manifest.permission.ACTIVITY_RECOGNITION,
  "android.permission.health.READ_HEART_RATE"
 )
 private var healthPermissionRequestPending = false
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

 private fun startBle() {
  if (Build.VERSION.SDK_INT >= 31) {
   val needed = listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
    .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
   if (needed.isNotEmpty()) {
    bleInfo = "Grant Bluetooth permissions and tap BLE GATT again"
    requestPermissions(needed.toTypedArray(), 102)
    refresh()
    return
   }
  }
  if (bleServer == null) bleServer = WatchBleServer(this) { msg ->
   runOnUiThread { bleInfo = msg; refresh() }
  }
  bleServer?.start()
 }
 private fun stopSamsungTrackerTest() {
  trackerTestGeneration++
  val previous = activeTrackerName
  activeTrackerName = null
  spo2Probe?.stop()
  spo2Probe = null
  if (previous != null) {
   samsungTrackerTestResult = "$previous: остановлено, событий $trackerSampleCount" +
    if (trackerSamplePreviews.isNotEmpty()) "\nПервые образцы:\n" + trackerSamplePreviews.joinToString("\n") else ""
  }
  refresh()
 }
 private fun bodyProfile(): SamsungTrackerProbe.BodyProfile? {
  val h = prefs.getString("body_height", null)?.toFloatOrNull() ?: return null
  val w = prefs.getString("body_weight", null)?.toFloatOrNull() ?: return null
  val a = prefs.getString("body_age", null)?.toIntOrNull() ?: return null
  if (h !in 100f..250f || w !in 20f..300f || a !in 18..120) return null
  return SamsungTrackerProbe.BodyProfile(h, w, prefs.getInt("body_gender", 1), a)
 }
 private fun startSamsungTrackerTest(tracker: String) {
  stopSamsungTrackerTest()
  activeTrackerName = tracker
  trackerSampleCount = 0
  trackerSamplePreviews.clear()
  trackerLastUiUpdate = 0L
  measurementProgress = 0
  measurementMessage = "Ожидание данных датчика…"
  measurementPreview = ""
  updateMeasurementUi()
  val generation = trackerTestGeneration
  val probe = SamsungTrackerProbe(this, tracker, if (tracker == "BIA") bodyProfile() else null) { message ->
   if (generation != trackerTestGeneration || activeTrackerName != tracker) return@SamsungTrackerProbe
   if (tracker == "BIA" && message.startsWith("BIA samples=")) {
    val status = Regex("status=(-?\\d+)").find(message)?.groupValues?.get(1)?.toIntOrNull()
    val progress = Regex("progress=([0-9.]+)").find(message)?.groupValues?.get(1)?.toFloatOrNull()
    runOnUiThread {
     if (generation != trackerTestGeneration) return@runOnUiThread
     if (status != null) measurementMessage = biaMessage(status)
     if (status == 0 && progress != null) {
      measurementProgress = (progress * 100f).toInt().coerceIn(0, 100)
      if (measurementProgress >= 100) {
       measurementMessage = "Измерение завершено"
       measurementPreview = biaSummary(message)
       saveBiaResult(message)
      } else {
       measurementPreview = "Жир: " + formatBia(biaField(message, "fatRatio"), "%")
      }
     }
     updateMeasurementUi()
    }
   } else if (message.startsWith("$tracker samples=")) {
    trackerSampleCount++
    val now = android.os.SystemClock.elapsedRealtime()
    if (now - trackerLastUiUpdate >= 1000L) {
     trackerLastUiUpdate = now
     runOnUiThread {
      if (generation == trackerTestGeneration) {
       measurementMessage = "Получение данных…"
       measurementPreview = "Событий: $trackerSampleCount"
       updateMeasurementUi()
      }
     }
    }
   } else if (message.contains("failed", true) || message.contains("error", true) ||
              message.contains("timeout", true) || message.contains("bindService returned false")) {
    runOnUiThread {
     if (generation == trackerTestGeneration) {
      measurementMessage = "Ошибка: " + message.take(100)
      updateMeasurementUi()
     }
    }
   }
  }
  spo2Probe = probe
  probe.start()
 }
 private fun publishSpO2FromProbe(message: String): String? {
  val match = Regex("SpO2 status=(-?\\d+) value=(\\d+) pulse=(\\d+) accuracy=(-?\\d+) timestamp=(\\d+)").find(message) ?: return null
  val (status, oxygen, pulse, _, timestamp) = match.destructured
  val spo2 = oxygen.toIntOrNull() ?: return null
  // Only status 2 with a real, non-zero oxygen reading is a valid result.
  if (status != "2" || spo2 !in 1..100) return null
  val bpm = pulse.toIntOrNull() ?: 0
  bleServer?.publish(spo2, bpm, timestamp.toLong())
  return "SpO₂: $spo2%  |  Пульс: $bpm"
 }
 private fun requestMissingPermissions() {
  val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
  if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 100)
  else startSensors()
 }

 override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
  super.onRequestPermissionsResult(requestCode, permissions, grantResults)
  if (requestCode == 100) startSensors()
  if (requestCode == 101) {
   healthPermissionRequestPending = false
   val denied = healthPermissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
   healthConnectInfo = if (denied.isEmpty()) "Permissions granted. Tap Health Connect again to read." else
    "Permission denied: " + denied.joinToString(", ") { it.substringAfterLast('.') }
   refresh()
  }
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

 private fun requestHealthPermissions() {
  if (Build.VERSION.SDK_INT < 34 || healthPermissionRequestPending) return
  val missing = healthPermissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
  if (missing.isEmpty()) {
   healthConnectInfo = "All Health Connect permissions granted."
   refresh()
   return
  }
  healthPermissionRequestPending = true
  healthConnectInfo = "Requesting: " + missing.joinToString(", ") { it.substringAfterLast('.') }
  refresh()
  try { requestPermissions(missing.toTypedArray(), 101) }
  catch (e: Exception) {
   healthPermissionRequestPending = false
   healthConnectInfo = "Permission request failed: ${e.javaClass.simpleName}: ${e.message}"
   refresh()
  }
 }

 private fun checkHealthConnect() {
  if (Build.VERSION.SDK_INT < 34) {
   healthConnectInfo = "Health Connect requires API 34+"
   refresh()
   return
  }
  val missing = healthPermissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
  if (missing.isNotEmpty()) {
   healthConnectInfo = "Missing permissions: " + missing.joinToString(", ") { it.substringAfterLast('.') } +
    "\nUse the separate permission button to request access."
   refresh()
   return
  }
  val hcByClass = try { getSystemService(HealthConnectManager::class.java) } catch (e: Exception) {
   Log.e("WatchBridgeHC", "HealthConnectManager class lookup failed", e)
   null
  }
  val hcByName = try { getSystemService(Context.HEALTHCONNECT_SERVICE) } catch (e: Exception) {
   Log.e("WatchBridgeHC", "Health Connect name lookup failed", e)
   null
  }
  Log.i("WatchBridgeHC", "API=${Build.VERSION.SDK_INT}, class=${hcByClass?.javaClass?.name ?: "null"}, name=${hcByName?.javaClass?.name ?: "null"}")
  val hc = hcByClass ?: (hcByName as? HealthConnectManager)
  if (hc == null) {
   healthConnectInfo = "Health Connect service unavailable\n" +
    "API: ${Build.VERSION.SDK_INT}\\n" +
    "By class: ${hcByClass?.javaClass?.name ?: "null"}\\n" +
    "By name: ${hcByName?.javaClass?.name ?: "null"}"
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
     try {
      val rows = result.records
      val preview = if (rows.isEmpty()) "No records" else describe(rows)
      healthConnectInfo += "\n$title: ${rows.size} records; $preview" +
       if (result.nextPageToken != -1L) " (more pages)" else ""
     } catch (e: Exception) {
      healthConnectInfo += "\n$title: result error: ${e.javaClass.simpleName}: ${e.message}"
     }
     refresh()
    }
    override fun onError(error: HealthConnectException) {
     healthConnectInfo += "\n$title: ${error.javaClass.simpleName}: ${error.message}"
     refresh()
    }
   })
  } catch (e: Exception) {
   healthConnectInfo += "\n$title: ${e.javaClass.simpleName}: ${e.message}"
   refresh()
  }
 }

 private fun checkSamsungServices() {
  samsungServicesInfo = "Binding to Samsung services..."
  refresh()
  val targets = listOf(
   "HSP" to ".data.hsp.HealthPlatformService",
   "Tracker" to ".tracking.requesthandler.WearTrackerService"
  )
  for ((label, className) in targets) {
   val fullClass = "com.samsung.android.service.health" + className
   val connection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
     val descriptor = try { binder.interfaceDescriptor } catch (e: Exception) {
      "error: ${e.javaClass.simpleName}: ${e.message}"
     }
     val msg = "$label connected: $descriptor"
     Log.i("WatchBridgeSamsung", msg)
     samsungServicesInfo += "\n" + msg
     refresh()
     if (label == "Tracker" && descriptor == "com.samsung.android.service.health.tracking.IClientEventListener") {
      Thread {
       val request = Parcel.obtain()
       val response = Parcel.obtain()
       val result = try {
        request.writeInterfaceToken(descriptor)
        val handled = binder.transact(5, request, response, 0)
        if (!handled) "transaction 5 not handled"
        else {
         response.readException()
         val types = response.createStringArrayList()
         "Supported trackers: " + (types?.joinToString(", ") ?: "null")
        }
       } catch (e: Exception) {
        "Tracker query failed: ${e.javaClass.simpleName}: ${e.message}"
       } finally {
        response.recycle()
        request.recycle()
       }
       Log.i("WatchBridgeSamsung", result)
       runOnUiThread {
        samsungServicesInfo += "\n" + result
        refresh()
       }
      }.start()
     }
    }
    override fun onServiceDisconnected(name: ComponentName) {
     Log.i("WatchBridgeSamsung", "$label disconnected")
     samsungServicesInfo += "\n$label disconnected"
     refresh()
    }
    override fun onNullBinding(name: ComponentName) {
     Log.i("WatchBridgeSamsung", "$label null binding")
     samsungServicesInfo += "\n$label null binding"
     refresh()
    }
    override fun onBindingDied(name: ComponentName) {
     Log.i("WatchBridgeSamsung", "$label binding died")
     samsungServicesInfo += "\n$label binding died"
     refresh()
    }
   }
   try {
    val intent = Intent().setComponent(ComponentName("com.samsung.android.service.health", fullClass))
    val bound = bindService(intent, connection, Context.BIND_AUTO_CREATE)
    Log.i("WatchBridgeSamsung", "$label bindService=$bound")
    samsungServicesInfo += "\n$label bindService=$bound"
    if (bound) samsungConnections.add(connection)
   } catch (e: Exception) {
    val msg = "$label ${e.javaClass.simpleName}: ${e.message}"
    Log.e("WatchBridgeSamsung", msg, e)
    samsungServicesInfo += "\n" + msg
   }
  }
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
  if (screen != "system_diagnostics" || !::output.isInitialized) return
  output.text = "Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}\n" +
     "Heart rate: ${heart?.toString() ?: "waiting"} bpm\n" +
     "Step counter: ${steps?.toInt()?.toString() ?: "waiting"} (since reboot)\n\n" +
     "Sensors:\n$sensorInfo\n\nSamsung provider:\n$providerInfo\n\n" +
     "Health Connect:\n$healthConnectInfo\n\nBLE:\n$bleInfo\n\nSpO₂:\n$spo2Result\n\nSamsung tracker test:\n$samsungTrackerTestResult\n\nSamsung services:\n$samsungServicesInfo\n\nNo data sent to MQTT."
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
 override fun onDestroy() {
  spo2Probe?.stop()
  bleServer?.stop()
  for (connection in samsungConnections) try { unbindService(connection) } catch (_: Exception) {}
  samsungConnections.clear()
  super.onDestroy()
 }
 override fun onPause() {
  super.onPause()
  manager.unregisterListener(this)
 }
 override fun onResume() {
  super.onResume()
  if (::manager.isInitialized && ::output.isInitialized) startSensors()
 }
}
