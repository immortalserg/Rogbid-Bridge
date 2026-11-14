package org.rogbid.bridge

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.graphics.Color
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject

class MainActivity : Activity() {
 private val sensorManager by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }
 private val handler = Handler(Looper.getMainLooper())
 private var activeListener: SensorEventListener? = null
 private var pulseLabel: TextView? = null
 private var oxygenLabel: TextView? = null
 private var pulseValue: TextView? = null
 private var oxygenValue: TextView? = null
 private var measurementValid = false
 private var lastPulse = "—"
 private var lastOxygen = "—"
 private var measureHeartButton: Button? = null
 private var measureOxygenButton: Button? = null
 private var activeMeasurementType = 0
 private var lastReadingType = 21
 private var lastReadingValue = "—"

 private var latestSteps: TextView? = null
 private var latestSleep: TextView? = null
 private var measurementGeneration = 0
 private var pendingSensorType = 0
 private val measurementDb by lazy {
  object : android.database.sqlite.SQLiteOpenHelper(this, "measurements.db", null, 1) {
   override fun onCreate(db: android.database.sqlite.SQLiteDatabase) {
    db.execSQL("CREATE TABLE measurements (id INTEGER PRIMARY KEY AUTOINCREMENT, measured_at TEXT NOT NULL, sensor_type INTEGER NOT NULL, heart_rate INTEGER, spo2 INTEGER)")
   }
   override fun onUpgrade(db: android.database.sqlite.SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
  }
 }
 private data class Skin(val background: Int, val foreground: Int, val accent: Int, val button: Int)
 private fun currentSkin(): Skin = when (prefs.getString("skin", "dark")) {
  "light" -> Skin(Color.rgb(246, 248, 251), Color.rgb(25, 36, 50), Color.rgb(23, 119, 192), Color.rgb(225, 235, 244))
  "forest" -> Skin(Color.rgb(13, 40, 34), Color.WHITE, Color.rgb(116, 225, 171), Color.rgb(28, 75, 63))
  "sunset" -> Skin(Color.rgb(49, 24, 44), Color.WHITE, Color.rgb(255, 170, 108), Color.rgb(103, 49, 76))
  else -> Skin(Color.rgb(17, 21, 31), Color.WHITE, Color.rgb(105, 187, 255), Color.rgb(42, 51, 69))
 }
 private fun styleButton(button: Button, skin: Skin) {
  button.setTextColor(skin.foreground)
  button.isAllCaps = false
  val radius = 18f * resources.displayMetrics.density
  val shape = GradientDrawable().apply {
   setColor(skin.button)
   cornerRadius = radius
  }
  val mask = GradientDrawable().apply {
   setColor(Color.WHITE)
   cornerRadius = radius
  }
  button.background = RippleDrawable(
   ColorStateList.valueOf(skin.accent and 0x00FFFFFF or 0x66000000),
   shape, mask
  )
 }
 private fun showReading(type: Int, value: String) {
  val target = if (type == 21) pulseValue else oxygenValue
  target?.text = value
  target?.setTextColor(if (activeMeasurementType == type && measurementValid) Color.rgb(63, 210, 110) else currentSkin().foreground)
 }
 private fun restoreReading(type: Int) {
  showReading(type, if (type == 21) lastPulse else lastOxygen)
 }
 private fun notifyMessage(message: String) {
  Toast.makeText(this, message, Toast.LENGTH_LONG).show()
 }
 private fun stopMeasurement() {
  measurementGeneration++
  activeListener?.let { sensorManager.unregisterListener(it) }
  activeListener = null
  measureHeartButton?.isEnabled = true
  measureOxygenButton?.isEnabled = true
  activeMeasurementType = 0
  pulseLabel?.alpha = 1f
  oxygenLabel?.alpha = 1f
  pulseValue?.setTextColor(currentSkin().foreground)
  oxygenValue?.setTextColor(currentSkin().foreground)
 }
 private fun refreshLatest() {
  Thread {
   var heart = Settings.System.getInt(contentResolver, "heart_rate", -1).takeIf { it in 35..220 }?.let { "$it bpm" } ?: "—"
   var oxygen = Settings.System.getInt(contentResolver, "OXYGEN", -1).takeIf { it in 70..100 }?.let { "$it %" } ?: "—"
   try {
    measurementDb.readableDatabase.rawQuery(
     "SELECT sensor_type, heart_rate, spo2 FROM measurements ORDER BY id DESC LIMIT 30", null
    ).use { cursor ->
     var hasHeart = false
     var hasOxygen = false
     while (cursor.moveToNext() && (!hasHeart || !hasOxygen)) {
      val type = cursor.getInt(0)
      if (!hasHeart && type == 21 && !cursor.isNull(1)) {
       heart = cursor.getInt(1).toString() + " bpm"
       hasHeart = true
      }
      if (!hasOxygen && type == 31 && !cursor.isNull(2)) {
       oxygen = cursor.getInt(2).toString() + " %"
       hasOxygen = true
      }
     }
    }
   } catch (_: Exception) {}
   val steps = try {
    val n = Settings.System.getInt(contentResolver, "today_steps", -1)
    if (n >= 0) "$n" else "—"
   } catch (_: Exception) { "—" }
   var sleep = "—"
   try {
    // Archived sleep takes precedence over the resettable vendor provider.
    val repo = SleepRepository(this)
    val saved = try { repo.latest() } finally { repo.close() }
    if (saved != null) {
     val mins = saved.optInt("duration_minutes")
     if (mins > 0) sleep = "${mins / 60}h ${mins % 60}m"
    } else {
     SleepCollector(this).read()?.let {
      sleep = "${it.duration / 60}h ${it.duration % 60}m"
     }
    }
   } catch (e: Exception) {
    android.util.Log.w("RogbidSleep", "Cannot load last sleep", e)
   }
   runOnUiThread {
    lastPulse = heart
    lastOxygen = oxygen
    if (activeMeasurementType != 21) pulseValue?.text = heart
    if (activeMeasurementType != 31) oxygenValue?.text = oxygen
    latestSteps?.text = steps
    latestSleep?.text = sleep
   }
  }.start()
 }
 private fun publishMeasurement(type: Int, heart: Int, oxygen: Int, time: String) {
  val host = prefs.getString("host", "")?.trim().orEmpty()
  val port = prefs.getString("port", "1883")?.toIntOrNull()
  val id = prefs.getString("device", "king")?.trim().orEmpty()
  if (host.isEmpty() || port == null || port !in 1..65535 ||
   !id.matches(Regex("[A-Za-z0-9_-]+"))) throw IllegalStateException("Configure MQTT in Settings")
  val topic = "rogbid/$id/measurements/" + (if (type == 21) "heart_rate" else "spo2")
  val client = MqttClient((if (port == 8883) "ssl" else "tcp") + "://$host:$port",
   "rogbid-measure-$id-" + System.currentTimeMillis(), MemoryPersistence())
  try {
   val options = MqttConnectOptions().apply {
    isCleanSession = true
    connectionTimeout = 10
    prefs.getString("user", "")?.takeIf { it.isNotBlank() }?.let { userName = it }
    prefs.getString("password", "")?.takeIf { it.isNotEmpty() }?.let { password = it.toCharArray() }
   }
   client.connect(options)
   val device = JSONObject().apply {
    put("identifiers", org.json.JSONArray().put("rogbid_$id"))
    put("name", "Rogbid $id")
    put("manufacturer", "Rogbid")
    put("model", "Z10")
   }
   fun discovery(key: String, label: String, unit: String) {
    val config = JSONObject().apply {
     put("name", label)
     put("unique_id", "rogbid_" + id + "_" + key)
     put("state_topic", "rogbid/$id/measurements/" + key)
     put("value_template", "{{ value_json." + key + " }}")
     put("unit_of_measurement", unit)
     put("state_class", "measurement")
     put("device", device)
    }
    client.publish("homeassistant/sensor/rogbid_$id/$key/config",
     MqttMessage(config.toString().toByteArray(Charsets.UTF_8)).apply { qos = 1; isRetained = true })
   }
   discovery("heart_rate", "Heart Rate", "bpm")
   discovery("spo2", "SpO2", "%")
   val data = JSONObject().put("measured_at", time).put("sensor_type", type)
   if (type == 21) data.put("heart_rate", heart) else data.put("spo2", oxygen)
   client.publish(topic, MqttMessage(data.toString().toByteArray(Charsets.UTF_8)).apply {
    qos = 1
    isRetained = true
   })
  } finally {
   if (client.isConnected) client.disconnect()
   client.close()
  }
 }
 private fun completeMeasurement(type: Int, heart: Int, oxygen: Int) {
  stopMeasurement()
  val time = java.time.Instant.now().toString()
  if (type == 21) lastPulse = "$heart bpm" else lastOxygen = "$oxygen %"
  showReading(type, if (type == 21) lastPulse else lastOxygen)
  pulseValue?.setTextColor(currentSkin().foreground)
  oxygenValue?.setTextColor(currentSkin().foreground)
  Thread {
   var saved = false
   try {
    val values = android.content.ContentValues().apply {
     put("measured_at", time)
     put("sensor_type", type)
     put("heart_rate", heart)
     if (type == 31) put("spo2", oxygen)
    }
    measurementDb.writableDatabase.insertOrThrow("measurements", null, values)
    saved = true
    runOnUiThread { refreshLatest() }

    publishMeasurement(type, heart, oxygen, time)

   } catch (e: Exception) {
    runOnUiThread { notifyMessage((if (saved) "MQTT error: " else "Save error: ") + e.message) }
   }
  }.start()
 }
 private fun animateMeasurement(generation: Int, frame: Int = 0) {
  if (generation != measurementGeneration) return
  val label = if (activeMeasurementType == 21) pulseLabel else oxygenLabel
  label?.alpha = if (frame % 2 == 0) 1f else 0.25f
  handler.postDelayed({ animateMeasurement(generation, frame + 1) }, 450)
 }
 private fun beginMeasurement(type: Int) {
  if (activeMeasurementType == type) {
   stopMeasurement()
   restoreReading(type)
   return
  }
  stopMeasurement()
  if (android.os.Build.VERSION.SDK_INT >= 23 &&
   checkSelfPermission(Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
   pendingSensorType = type
   requestPermissions(arrayOf(Manifest.permission.BODY_SENSORS), 1201)
   return
  }
  val sensor = sensorManager.getDefaultSensor(type)
  if (sensor == null) {
   notifyMessage("Sensor type $type is unavailable")
   return
  }
  val generation = measurementGeneration
  val startedAt = android.os.SystemClock.elapsedRealtime()
  val recent = java.util.ArrayDeque<Pair<Int, Int>>()
  activeMeasurementType = type
  measurementValid = false
  measureHeartButton?.isEnabled = true
  measureOxygenButton?.isEnabled = false
  if (type == 31) {
   measureHeartButton?.isEnabled = false
   measureOxygenButton?.isEnabled = true
  }
  showReading(type, if (type == 21) lastPulse else lastOxygen)
  animateMeasurement(generation)
  val listener = object : SensorEventListener {
   override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
   override fun onSensorChanged(event: SensorEvent) {
    if (generation != measurementGeneration || event.values.isEmpty()) return
    val heart = event.values[0].toInt()
    val oxygen = if (type == 31 && event.values.size > 1) event.values[1].toInt() else 0
    if (heart !in 35..220 || (type == 31 && oxygen !in 70..100)) {
     recent.clear()
     if (!measurementValid) restoreReading(type)
     return
    }
    recent.addLast(Pair(heart, oxygen))
    if (recent.size > 3) recent.removeFirst()
    val values = recent.toList()
    val stable = values.size == 3 &&
     values.maxOf { it.first } - values.minOf { it.first } <= 5 &&
     (type == 21 || values.maxOf { it.second } - values.minOf { it.second } <= 2)
    if (stable && android.os.SystemClock.elapsedRealtime() - startedAt >= 10000) {
     completeMeasurement(type, values.map { it.first }.average().toInt(),
      if (type == 31) values.map { it.second }.average().toInt() else 0)
    } else {
     showReading(type, if (type == 21) "$heart bpm" else "$oxygen %")
    }
   }
  }
  activeListener = listener
  try {
   if (!sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
    stopMeasurement()
    notifyMessage("Unable to register sensor $type")
    return
   }
  } catch (e: SecurityException) {
   stopMeasurement()
   notifyMessage("Sensor permission denied: " + e.message)
   return
  }
  handler.postDelayed({
   if (generation == measurementGeneration) {
    stopMeasurement()
    notifyMessage("Measurement timed out")
   }
  }, 45000)
 }
 override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
  super.onRequestPermissionsResult(requestCode, permissions, grantResults)
  if (requestCode == 1201) {
   if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
    beginMeasurement(pendingSensorType)
   } else notifyMessage("Body sensors permission is required")
  }
 }
 private val prefs by lazy { getSharedPreferences("mqtt", MODE_PRIVATE) }
 private fun page(): LinearLayout = LinearLayout(this).apply {
  orientation = LinearLayout.VERTICAL
  setPadding(16, 16, 16, 16)
  setBackgroundColor(currentSkin().background)
 }
 private fun show(view: LinearLayout) {
  stopMeasurement()
  setContentView(ScrollView(this).apply {
   setBackgroundColor(currentSkin().background)
   addView(view)
  })
 }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  if (prefs.getBoolean("auto", false)) {
   startForegroundService(Intent(this, SyncService::class.java))
  }
  showHome()
 }
 private fun showHome() {
  val skin = currentSkin()
  val layout = page()
  val density = resources.displayMetrics.density
  val measurementRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
  val heartButton = Button(this).apply { text = "♥"; textSize = 34f; gravity = Gravity.CENTER }
  val oxygenButton = Button(this).apply { text = "O₂"; textSize = 32f; gravity = Gravity.CENTER }
  styleButton(heartButton, skin)
  styleButton(oxygenButton, skin)
  val side = ((resources.displayMetrics.widthPixels - 40 * density) / 4).toInt()
  measurementRow.addView(heartButton, LinearLayout.LayoutParams(0, side, 1f).apply { rightMargin = (4 * density).toInt() })
  measurementRow.addView(oxygenButton, LinearLayout.LayoutParams(0, side, 1f).apply { leftMargin = (4 * density).toInt() })
  layout.addView(measurementRow)
  fun tile(icon: String, label: String): Pair<LinearLayout, Pair<TextView, TextView>> {
   val cell = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.CENTER
    setPadding((4 * density).toInt(), (7 * density).toInt(), (4 * density).toInt(), (7 * density).toInt())
    val tileShape = GradientDrawable().apply {
     setColor(skin.button)
     cornerRadius = 16 * density
    }
    val tileMask = GradientDrawable().apply {
     setColor(Color.WHITE)
     cornerRadius = 16 * density
    }
    background = RippleDrawable(
     ColorStateList.valueOf(skin.accent and 0x00FFFFFF or 0x66000000),
     tileShape, tileMask
    )
    isClickable = true
    isFocusable = true
   }
   val caption = TextView(this).apply {
    text = "$icon  $label"; textSize = 15f; gravity = Gravity.CENTER
    setTextColor(skin.accent)
    setSingleLine(true)
   }
   val value = TextView(this).apply {
    text = "—"; textSize = 25f; gravity = Gravity.CENTER
    typeface = Typeface.DEFAULT_BOLD
    setTextColor(skin.foreground)
    setSingleLine(true)
   }
   cell.addView(caption)
   cell.addView(value)
   return Pair(cell, Pair(caption, value))
  }
  val summaryRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
  val summaryRow2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
  val heartCell = tile("♥", "Pulse")
  val oxygenCell = tile("O₂", "Oxygen")
  val stepsCell = tile("♟", "Steps")
  val sleepCell = tile("☾", "Sleep")
  for ((row, pair) in listOf(
   Pair(summaryRow1, heartCell), Pair(summaryRow1, oxygenCell),
   Pair(summaryRow2, stepsCell), Pair(summaryRow2, sleepCell)
  )) row.addView(pair.first, LinearLayout.LayoutParams(0, (88 * density).toInt(), 1f).apply {
   setMargins((3 * density).toInt(), (3 * density).toInt(), (3 * density).toInt(), (3 * density).toInt())
  })
  layout.addView(summaryRow1)
  layout.addView(summaryRow2)
  pulseLabel = heartCell.second.first
  oxygenLabel = oxygenCell.second.first
  pulseValue = heartCell.second.second
  oxygenValue = oxygenCell.second.second
  latestSteps = stepsCell.second.second
  latestSleep = sleepCell.second.second
  heartCell.first.setOnClickListener { showJournal("heart") }
  oxygenCell.first.setOnClickListener { showJournal("oxygen") }
  stepsCell.first.setOnClickListener { showJournal("steps") }
  sleepCell.first.setOnClickListener { showSleepLatest() }
  measureHeartButton = heartButton
  measureOxygenButton = oxygenButton
  heartButton.setOnClickListener { beginMeasurement(21) }
  oxygenButton.setOnClickListener { beginMeasurement(31) }
  val button = Button(this).apply { text = "Send data" }
  val history = Button(this).apply { text = "Send history" }
  val settings = Button(this).apply { text = "Settings" }
  val status = TextView(this)
  val lower = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
  val lower2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
  for (control in arrayOf(button, history, settings)) styleButton(control, skin)
  val buttonGap = (8 * density).toInt()
  lower.addView(button, LinearLayout.LayoutParams(0, (52 * density).toInt(), 1f).apply { rightMargin = buttonGap / 2 })
  lower.addView(history, LinearLayout.LayoutParams(0, (52 * density).toInt(), 1f).apply { leftMargin = buttonGap / 2 })
  lower2.addView(settings, LinearLayout.LayoutParams(0, (52 * density).toInt(), 1f))
  lower2.setPadding(0, buttonGap, 0, 0)
  layout.addView(lower)
  layout.addView(lower2)
  show(layout)
  refreshLatest()
  settings.setOnClickListener { showSettings() }
  history.setOnClickListener {
   val hostname = prefs.getString("host", "")?.trim().orEmpty()
   val number = prefs.getString("port", "1883")?.toIntOrNull()
   val id = prefs.getString("device", "king")?.trim().orEmpty()
   if (hostname.isEmpty() || number == null || number !in 1..65535 ||
    !id.matches(Regex("[A-Za-z0-9_-]+"))) {
    notifyMessage("Configure MQTT in Settings")
    return@setOnClickListener
   }
   history.isEnabled = false
   button.isEnabled = false
   
   Thread {
    try {
     val records = linkedMapOf<String, JSONObject>()
     val uri = Uri.parse("content://com.watchHealth.Walk/query")
     val cursor = contentResolver.query(uri,
      arrayOf("date", "steps", "diatance", "kcal", "targetsteps"), null, null, "date ASC")
      ?: throw IllegalStateException("History provider returned null")
     cursor.use {
      val dateIndex = it.getColumnIndex("date")
      if (dateIndex < 0) throw IllegalStateException("Missing date column")
      while (it.moveToNext()) {
       if (it.isNull(dateIndex)) continue
       val date = it.getString(dateIndex)?.trim().orEmpty()
       // Date is also used as a topic segment: reject unexpected provider values.
       if (!date.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) continue
       val data = JSONObject().put("date", date)
       for (name in arrayOf("steps", "diatance", "kcal", "targetsteps")) {
        val index = it.getColumnIndex(name)
        if (index >= 0 && !it.isNull(index)) data.put(name, it.getLong(index))
       }
       records[date] = data
      }
     }
     if (records.isEmpty()) throw IllegalStateException("No dated history records found")
     val login = prefs.getString("user", "").orEmpty()
     val password = prefs.getString("password", "").orEmpty()
     val scheme = if (number == 8883) "ssl" else "tcp"
     val client = MqttClient(scheme + "://" + hostname + ":" + number,
      "rogbid-history-" + id + "-" + System.currentTimeMillis(), MemoryPersistence())
     var published = 0
     try {
      val options = MqttConnectOptions().apply {
       isCleanSession = true
       connectionTimeout = 10
       if (login.isNotBlank()) userName = login
       if (password.isNotEmpty()) this.password = password.toCharArray()
      }
      client.connect(options)
      for ((date, data) in records) {
       client.publish("rogbid/" + id + "/history/" + date,
        MqttMessage(data.toString().toByteArray(Charsets.UTF_8)).apply {
         qos = 1
         isRetained = true
        })
       published++
      }
      runOnUiThread { notifyMessage("History published: " + published + " day(s)") }
     } finally {
      if (client.isConnected) client.disconnect()
      client.close()
     }
    } catch (e: Exception) {
     runOnUiThread { notifyMessage("History error: " + e.message) }
    } finally {
     runOnUiThread {
      history.isEnabled = true
      button.isEnabled = true
     }
    }
   }.start()
  }
  button.setOnClickListener {
   val hostname = prefs.getString("host", "")?.trim().orEmpty()
   val number = prefs.getString("port", "1883")?.toIntOrNull()
   val id = prefs.getString("device", "king")?.trim().orEmpty()
   if (hostname.isEmpty() || number == null || number !in 1..65535 ||
    !id.matches(Regex("[A-Za-z0-9_-]+"))) {
    notifyMessage("Configure MQTT in Settings")
    return@setOnClickListener
   }
   val login = prefs.getString("user", "").orEmpty()
   val password = prefs.getString("password", "").orEmpty()
   button.isEnabled = false
   
   Thread {
    try {
     val data = JSONObject()
     data.put("last_update", java.time.Instant.now().toString())
     data.put("steps", Settings.System.getInt(contentResolver, "today_steps"))
     // Vendor firmware exposes the most recent measurements in Settings.System.
     val heartRate = Settings.System.getInt(contentResolver, "heart_rate", -1)
     if (heartRate in 1..250) data.put("heart_rate", heartRate)
     val oxygen = Settings.System.getInt(contentResolver, "OXYGEN", -1)
     if (oxygen in 1..100) data.put("spo2", oxygen)
     contentResolver.query(Uri.parse("content://com.watchHealth.Walk/query"),
      arrayOf("date", "steps", "diatance", "kcal", "targetsteps"), null, null, "date DESC")?.use { cursor ->
      if (cursor.moveToFirst()) {
       for (name in arrayOf("date", "steps", "diatance", "kcal", "targetsteps")) {
        val i = cursor.getColumnIndex(name)
        if (i >= 0 && !cursor.isNull(i)) {
         if (name == "date") data.put(name, cursor.getString(i))
         else data.put(name, cursor.getLong(i))
        }
       }
      }
     }
     // Read the latest sleep record from the vendor ContentProvider.
     // Keep raw timestamps and phase details until their formats are confirmed.
     var sleepError: String? = null
     try {
      contentResolver.query(Uri.parse("content://com.wiite.provider/sleep"),
       null, null, null, "_id DESC")?.use { cursor ->
       if (cursor.moveToFirst()) {
        fun raw(name: String): String? {
         val index = cursor.getColumnIndex(name)
         return if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
        }
        fun minutes(prefix: String): Int? {
         val hh = raw(prefix + "HH")?.toIntOrNull()
         val mm = raw(prefix + "MM")?.toIntOrNull()
         return if (hh != null && mm != null && hh >= 0 && mm in 0..59) hh * 60 + mm else null
        }
        val sleep = JSONObject()
        for (name in arrayOf("_id", "sleeptime", "wakeuptime", "dataList", "code",
         "sumHH", "sumMM", "fastHH", "fastMM", "lightHH", "lightMM", "deepHH", "deepMM")) {
         raw(name)?.let { sleep.put(name, it) }
        }
        data.put("sleep_raw", sleep)
        minutes("sum")?.let { data.put("sleep_duration", it) }
        minutes("deep")?.let { data.put("sleep_deep", it) }
        minutes("light")?.let { data.put("sleep_light", it) }
        minutes("fast")?.let { data.put("sleep_fast", it) }
        raw("sleeptime")?.let { data.put("sleep_start", it) }
        raw("wakeuptime")?.let { data.put("sleep_end", it) }
       }
      } ?: run { sleepError = "Sleep provider returned null" }
     } catch (e: Exception) {
      sleepError = e.javaClass.simpleName + ": " + e.message
     }
     sleepError?.let { data.put("sleep_error", it) }
     val scheme = if (number == 8883) "ssl" else "tcp"
     val client = MqttClient(scheme + "://" + hostname + ":" + number,
      "rogbid-" + id + "-" + System.currentTimeMillis(), MemoryPersistence())
     try {
      val options = MqttConnectOptions().apply {
       isCleanSession = true
       connectionTimeout = 10
       if (login.isNotBlank()) userName = login
       if (password.isNotEmpty()) this.password = password.toCharArray()
      }
      client.connect(options)
      val topic = "rogbid/" + id + "/state"
      val device = JSONObject().apply {
       put("identifiers", org.json.JSONArray().put("rogbid_" + id))
       put("name", "Rogbid " + id)
       put("manufacturer", "Rogbid")
       put("model", "Z10")
      }
      fun discovery(key: String, label: String, unit: String?, deviceClass: String?, stateClass: String?) {
       val config = JSONObject().apply {
        put("name", label)
        put("unique_id", "rogbid_" + id + "_" + key)
        put("state_topic", topic)
        put("value_template", "{{ value_json." + key + " }}")
        put("availability_topic", topic)
        put("availability_template", "{{ 'online' if value_json." + key + " is defined else 'offline' }}")
        put("payload_available", "online")
        put("payload_not_available", "offline")
        put("device", device)
        if (unit != null) put("unit_of_measurement", unit)
        if (deviceClass != null) put("device_class", deviceClass)
        if (stateClass != null) put("state_class", stateClass)
       }
       client.publish("homeassistant/sensor/rogbid_" + id + "/" + key + "/config",
        MqttMessage(config.toString().toByteArray(Charsets.UTF_8)).apply {
         qos = 1
         isRetained = true
        })
      }
      discovery("steps", "Steps", "steps", null, "measurement")
      // Direct measurements manage heart rate and SpO2 MQTT discovery.
      discovery("last_update", "Last Update", null, "timestamp", null)
      discovery("sleep_duration", "Sleep Duration", "min", "duration", "measurement")
      discovery("sleep_deep", "Deep Sleep", "min", "duration", "measurement")
      discovery("sleep_light", "Light Sleep", "min", "duration", "measurement")
      discovery("sleep_fast", "Fast Sleep", "min", "duration", "measurement")
      discovery("sleep_start", "Sleep Start", null, null, null)
      discovery("sleep_end", "Wake Up", null, null, null)
      client.publish(topic, MqttMessage(data.toString().toByteArray(Charsets.UTF_8)).apply {
       qos = 1
       isRetained = true
      })
      runOnUiThread { status.text = "Published " + topic + "\n" + data.toString() }
     } finally {
      if (client.isConnected) client.disconnect()
      client.close()
     }
    } catch (e: Exception) {
     runOnUiThread { notifyMessage("MQTT error: " + e.message) }
    } finally {
     runOnUiThread { button.isEnabled = true }
    }
   }.start()
  }
 }
 private fun sleepScreens() = SleepScreens(
  this, currentSkin().background, currentSkin().foreground,
  currentSkin().accent, currentSkin().button
 ) { showHome() }

 private fun showSleepLatest() {
  stopMeasurement()
  try {
   sleepScreens().latest()
  } catch (e: Exception) {
   android.util.Log.e("RogbidSleep", "Sleep screen failed", e)
   notifyMessage("Sleep screen error: " + (e.message ?: e.javaClass.simpleName))
   showHome()
  }
 }

 private fun showJournal(kind: String) {
  val skin = currentSkin()
  val layout = page()
  val title = when (kind) {
   "heart" -> "♥ Pulse history"
   "oxygen" -> "O₂ Oxygen history"
   "steps" -> "♟ Steps history"
   else -> "☾ Sleep history"
  }
  layout.addView(TextView(this).apply {
   text = title; textSize = 22f; setTextColor(skin.foreground)
  })
  if (kind == "sleep") {
   sleepScreens().history()
   return
  }
  if (kind == "sleep-diagnostics") {
   val diagnostic = Button(this).apply { text = "Diagnose sleep source" }
   styleButton(diagnostic, skin)
   layout.addView(diagnostic)
   diagnostic.setOnClickListener { showSleepDiagnostics() }
  }
  val entries = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
  val back = Button(this).apply { text = "Back" }
  styleButton(back, skin)
  layout.addView(back)
  layout.addView(entries)
  back.setOnClickListener { showHome() }
  show(layout)
  Thread {
   val records = mutableListOf<String>()
   try {
    if (kind == "heart" || kind == "oxygen") {
     val type = if (kind == "heart") 21 else 31
     measurementDb.readableDatabase.rawQuery(
      "SELECT measured_at, heart_rate, spo2 FROM measurements WHERE sensor_type = ? ORDER BY id DESC LIMIT 100",
      arrayOf(type.toString())
     ).use { cursor ->
      while (cursor.moveToNext()) {
       val time = cursor.getString(0).replace("T", " ").take(19)
       val reading = if (type == 21) cursor.getInt(1).toString() + " bpm"
        else cursor.getInt(2).toString() + " %"
       records.add("$time    $reading")
      }
     }
    } else if (kind == "steps") {
      contentResolver.query(Uri.parse("content://com.watchHealth.Walk/query"),
       arrayOf("date", "steps", "kcal", "diatance"), null, null, "date DESC")?.use { cursor ->
       fun value(name: String): String {
        val index = cursor.getColumnIndex(name)
        return if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else "—"
       }
       while (cursor.moveToNext() && records.size < 100) {
        records.add("${value("date")} — ${value("steps")} шагов — ${value("kcal")} ккал — ${value("diatance")} расстояние")
       }
      }
     } else {
     contentResolver.query(Uri.parse("content://com.wiite.provider/sleep"),
      null, null, null, "_id DESC")?.use { cursor ->
      fun value(name: String): String? {
       val i = cursor.getColumnIndex(name)
       return if (i >= 0 && !cursor.isNull(i)) cursor.getString(i) else null
      }
      while (cursor.moveToNext() && records.size < 100) {
       val hh = value("sumHH")?.toIntOrNull()
       val mm = value("sumMM")?.toIntOrNull()
       val duration = if (hh != null && mm != null) "${hh}h ${mm}m" else "—"
       records.add((value("sleeptime") ?: value("_id") ?: "Sleep") + "    " + duration)
      }
     }
    }
   } catch (e: Exception) {
    records.add("History unavailable: " + e.message)
   }
   runOnUiThread {
    if (records.isEmpty()) records.add("No history available")
    for (record in records) entries.addView(TextView(this).apply {
     text = record; textSize = 16f
     setTextColor(skin.foreground)
     setPadding(0, 10, 0, 10)
    })
   }
  }.start()
 }
 private fun showSleepDiagnostics() {
  val skin = currentSkin()
  val layout = page()
  layout.addView(TextView(this).apply {
   text = "Sleep source diagnostics"; textSize = 21f; setTextColor(skin.foreground)
  })
  val hint = TextView(this).apply {
   text = "Capture before and after opening the stock Sleep app. Compare IDs, times and all fields."
   textSize = 14f; setTextColor(skin.foreground)
  }
  layout.addView(hint)
  val output = TextView(this).apply {
   text = "Tap Read source"; textSize = 13f; setTextColor(skin.foreground)
   setTextIsSelectable(true)
  }
  val read = Button(this).apply { text = "Read source" }
  val copy = Button(this).apply { text = "Copy snapshot" }
  val back = Button(this).apply { text = "Back" }
  for (button in arrayOf(read, copy, back)) {
   styleButton(button, skin)
   layout.addView(button)
  }
  layout.addView(output)
  var snapshot = ""
  read.setOnClickListener {
   read.isEnabled = false
   output.text = "Reading..."
   Thread {
    val result = StringBuilder()
    result.append("Captured: ").append(java.time.ZonedDateTime.now()).append("\n")
    val uri = Uri.parse("content://com.wiite.provider/sleep")
    for (order in arrayOf("_id DESC", null)) {
     result.append("\nQuery order: ").append(order ?: "provider default").append("\n")
     try {
      contentResolver.query(uri, null, null, null, order)?.use { cursor ->
       result.append("Columns: ").append(cursor.columnNames.joinToString()).append("\n")
       result.append("Rows: ").append(cursor.count).append("\n")
       var count = 0
       while (cursor.moveToNext() && count < 20) {
        count++
        result.append("ROW ").append(count).append(":\n")
        for (i in 0 until cursor.columnCount) {
         val name = cursor.getColumnName(i)
         val value = if (cursor.isNull(i)) "NULL" else cursor.getString(i) ?: "NULL"
         result.append("  ").append(name).append(" = ")
          .append(if (name == "dataList") value else value.take(300)).append("\n")
        }
       }
       if (cursor.count > count) result.append("... additional rows omitted\n")
      } ?: result.append("Provider returned NULL cursor\n")
     } catch (e: Exception) {
      result.append("ERROR: ").append(e.javaClass.simpleName).append(": ")
       .append(e.message).append("\n")
     }
    }
    snapshot = result.toString()
    android.util.Log.d("RogbidSleep", "SNAPSHOT BEGIN")
    snapshot.lineSequence().forEach { line -> android.util.Log.d("RogbidSleep", line) }
    android.util.Log.d("RogbidSleep", "SNAPSHOT END")
    runOnUiThread {
     output.text = snapshot
     read.isEnabled = true
    }
   }.start()
  }
  copy.setOnClickListener {
   if (snapshot.isEmpty()) notifyMessage("Read source first")
   else {
    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Rogbid sleep snapshot", snapshot))
    notifyMessage("Snapshot copied")
   }
  }
  back.setOnClickListener { showJournal("sleep") }
  show(layout)
 }
 private fun showSettings() {
  val layout = page()
  layout.addView(TextView(this).apply { text = "Settings"; textSize = 22f; setTextColor(currentSkin().foreground) })
  fun field(label: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText =
   EditText(this).apply {
    hint = label
    setText(value)
    inputType = type
    setTextColor(currentSkin().foreground)
    setHintTextColor((currentSkin().foreground and 0x00FFFFFF) or 0x99000000.toInt())
    setSingleLine(true)
    layout.addView(this)
   }
  val host = field("Broker IP / hostname", prefs.getString("host", "").orEmpty())
  val port = field("Port", prefs.getString("port", "1883").orEmpty(), InputType.TYPE_CLASS_NUMBER)
  val user = field("Username", prefs.getString("user", "").orEmpty())
  val pass = field("Password (visible during testing)", prefs.getString("password", "").orEmpty())
  val device = field("Device ID", prefs.getString("device", "king").orEmpty())
  val skinNames = arrayOf("Dark", "Light", "Forest", "Sunset")
  val skinKeys = arrayOf("dark", "light", "forest", "sunset")
  layout.addView(TextView(this).apply {
   text = "Appearance"; textSize = 18f; setTextColor(currentSkin().foreground)
  })
  val skinChoice = Spinner(this)
  skinChoice.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, skinNames)
  skinChoice.setSelection(skinKeys.indexOf(prefs.getString("skin", "dark")).coerceAtLeast(0))
  layout.addView(skinChoice)
  val autoSync = CheckBox(this).apply {
   text = "Automatic background MQTT and sleep sync"
   isChecked = prefs.getBoolean("auto", false)
   setTextColor(currentSkin().foreground)
  }
  layout.addView(autoSync)
  val interval = field("Sync interval (minutes, 1-60)",
   prefs.getInt("interval_minutes", 1).toString(), InputType.TYPE_CLASS_NUMBER)
  val save = Button(this).apply { text = "Save settings" }
  val back = Button(this).apply { text = "Back" }
  val status = TextView(this).apply { setTextColor(currentSkin().foreground) }
  layout.addView(save)
  layout.addView(back)
  layout.addView(status)
  save.setOnClickListener {
   val p = port.text.toString().toIntOrNull()
   val intervalMinutes = interval.text.toString().toIntOrNull()
   val d = device.text.toString().trim()
   if (host.text.toString().trim().isEmpty() || p == null || p !in 1..65535 ||
    intervalMinutes == null || intervalMinutes !in 1..60 ||
    !d.matches(Regex("[A-Za-z0-9_-]+"))) {
    status.text = "Invalid host, port or device ID"
   } else {
    prefs.edit().putString("host", host.text.toString().trim())
     .putString("port", p.toString()).putString("user", user.text.toString())
     .putString("password", pass.text.toString()).putString("device", d)
      .putString("skin", skinKeys[skinChoice.selectedItemPosition])
      .putInt("interval_minutes", intervalMinutes)
      .putBoolean("auto", autoSync.isChecked).apply()
    if (autoSync.isChecked) {
     startForegroundService(Intent(this, SyncService::class.java))
    } else {
     stopService(Intent(this, SyncService::class.java))
    }
    showHome()
   }
  }
  back.setOnClickListener { showHome() }
  show(layout)
 }
 override fun onBackPressed() { showHome() }
 override fun onDestroy() { stopMeasurement(); super.onDestroy() }
}