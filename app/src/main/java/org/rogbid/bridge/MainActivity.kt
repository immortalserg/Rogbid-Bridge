package org.rogbid.bridge

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.graphics.Color
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
 private var measureStatus: TextView? = null
 private var measureSymbol: TextView? = null
 private var measureUnit: TextView? = null
 private var measureAnimation: TextView? = null
 private var measureHeartButton: Button? = null
 private var measureOxygenButton: Button? = null
 private var activeMeasurementType = 0
 private var lastReadingType = 21
 private var lastReadingValue = "—"
 private var latestHeart: TextView? = null
 private var latestOxygen: TextView? = null
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
  button.background = GradientDrawable().apply {
   setColor(skin.button)
   cornerRadius = 18f * resources.displayMetrics.density
  }
 }
 private fun showReading(type: Int, value: String) {
  measureSymbol?.text = if (type == 21) "♥" else "O₂"
  measureUnit?.text = if (type == 21) "bpm" else "%"
  measureStatus?.text = value
  lastReadingType = type
  if (value != "…" && value != "—") lastReadingValue = value
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
  measureAnimation?.text = ""
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
    contentResolver.query(Uri.parse("content://com.wiite.provider/sleep"),
     null, null, null, "_id DESC")?.use { cursor ->
     if (cursor.moveToFirst()) {
      fun minutes(prefix: String): Int? {
       val h = cursor.getColumnIndex(prefix + "HH")
       val m = cursor.getColumnIndex(prefix + "MM")
       if (h < 0 || m < 0 || cursor.isNull(h) || cursor.isNull(m)) return null
       val hh = cursor.getString(h)?.toIntOrNull()
       val mm = cursor.getString(m)?.toIntOrNull()
       return if (hh != null && mm != null && hh >= 0 && mm in 0..59) hh * 60 + mm else null
      }
      minutes("sum")?.let { sleep = "${it / 60}h ${it % 60}m" }
     }
    }
   } catch (_: Exception) {}
   runOnUiThread {
    latestHeart?.text = heart
    latestOxygen?.text = oxygen
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
  showReading(type, if (type == 21) heart.toString() else oxygen.toString())
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
  val frames = arrayOf("● ○ ○", "○ ● ○", "○ ○ ●", "○ ● ○")
  measureAnimation?.text = frames[frame % frames.size]
  handler.postDelayed({ animateMeasurement(generation, frame + 1) }, 350)
 }
 private fun beginMeasurement(type: Int) {
  if (activeMeasurementType == type) {
   stopMeasurement()
   showReading(lastReadingType, lastReadingValue)
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
  measureHeartButton?.isEnabled = true
  measureOxygenButton?.isEnabled = false
  if (type == 31) {
   measureHeartButton?.isEnabled = false
   measureOxygenButton?.isEnabled = true
  }
  showReading(type, "…")
  animateMeasurement(generation)
  val listener = object : SensorEventListener {
   override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
   override fun onSensorChanged(event: SensorEvent) {
    if (generation != measurementGeneration || event.values.isEmpty()) return
    val heart = event.values[0].toInt()
    val oxygen = if (type == 31 && event.values.size > 1) event.values[1].toInt() else 0
    if (heart !in 35..220 || (type == 31 && oxygen !in 70..100)) {
     recent.clear()
     showReading(type, "…")
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
     showReading(type, if (type == 21) heart.toString() else oxygen.toString())
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
  stopService(android.content.Intent(this, SyncService::class.java))
  prefs.edit().putBoolean("auto", false).apply()
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
  val readingRow = LinearLayout(this).apply {
   orientation = LinearLayout.HORIZONTAL
   gravity = Gravity.CENTER
   minimumHeight = (92 * density).toInt()
  }
  val symbol = TextView(this).apply {
   text = "♥"; textSize = 30f; gravity = Gravity.CENTER
   setTextColor(skin.accent)
   setSingleLine(true)
  }
  val sensorStatus = TextView(this).apply {
   text = "—"; textSize = 52f; gravity = Gravity.CENTER
   typeface = Typeface.DEFAULT_BOLD
   setTextColor(skin.foreground)
   setSingleLine(true)
  }
  val unit = TextView(this).apply {
   text = "bpm"; textSize = 20f; gravity = Gravity.CENTER
   setTextColor(skin.accent)
   setSingleLine(true)
  }
  readingRow.addView(symbol, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
   rightMargin = (10 * density).toInt()
  })
  readingRow.addView(sensorStatus)
  readingRow.addView(unit, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
   leftMargin = (7 * density).toInt()
  })
  layout.addView(readingRow)
  val animation = TextView(this).apply {
   text = ""; textSize = 16f; gravity = Gravity.CENTER
   setTextColor(skin.accent)
   minimumHeight = (24 * density).toInt()
  }
  layout.addView(animation)
  measureAnimation = animation
  measureSymbol = symbol
  measureStatus = sensorStatus
  measureUnit = unit
  measureHeartButton = heartButton
  measureOxygenButton = oxygenButton
  fun statCell(icon: String, label: String, value: String): Pair<LinearLayout, TextView> {
   val cell = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    gravity = Gravity.CENTER
    setPadding((4 * density).toInt(), (7 * density).toInt(), (4 * density).toInt(), (7 * density).toInt())
   }
   val line = TextView(this).apply {
    text = icon + "  " + value
    textSize = 17f
    gravity = Gravity.CENTER
    setTextColor(skin.foreground)
    setSingleLine(true)
   }
   val caption = TextView(this).apply {
    text = label
    textSize = 13f
    gravity = Gravity.CENTER
    setTextColor(skin.accent)
    setSingleLine(true)
   }
   cell.addView(line)
   cell.addView(caption)
   return Pair(cell, line)
  }
  val summaryRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
  val summaryRow2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
  val heartCell = statCell("♥", "Pulse", "—")
  val oxygenCell = statCell("O₂", "Oxygen", "—")
  val stepsCell = statCell("♟", "Steps", "—")
  val sleepCell = statCell("☾", "Sleep", "—")
  for ((row, pair) in listOf(
   Pair(summaryRow1, heartCell), Pair(summaryRow1, oxygenCell),
   Pair(summaryRow2, stepsCell), Pair(summaryRow2, sleepCell)
  )) row.addView(pair.first, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
  layout.addView(summaryRow1)
  layout.addView(summaryRow2)
  latestHeart = heartCell.second
  latestOxygen = oxygenCell.second
  latestSteps = stepsCell.second
  latestSleep = sleepCell.second
  heartButton.setOnClickListener { beginMeasurement(21) }
  oxygenButton.setOnClickListener { beginMeasurement(31) }
  val button = Button(this).apply { text = "Send data" }
  val history = Button(this).apply { text = "Send history" }
  val settings = Button(this).apply { text = "Settings" }
  val status = TextView(this)
  val lower = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
  val lower2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
  for (control in arrayOf(button, history, settings)) styleButton(control, skin)
  lower.addView(button, LinearLayout.LayoutParams(0, (60 * density).toInt(), 1f).apply { rightMargin = (4 * density).toInt() })
  lower.addView(history, LinearLayout.LayoutParams(0, (60 * density).toInt(), 1f).apply { leftMargin = (4 * density).toInt() })
  lower2.addView(settings, LinearLayout.LayoutParams(0, (60 * density).toInt(), 1f))
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
 private fun showSettings() {
  val layout = page()
  layout.addView(TextView(this).apply { text = "Settings"; textSize = 22f; setTextColor(currentSkin().foreground) })
  fun field(label: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText =
   EditText(this).apply {
    hint = label
    setText(value)
    inputType = type
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
  val save = Button(this).apply { text = "Save settings" }
  val back = Button(this).apply { text = "Back" }
  val status = TextView(this)
  layout.addView(save)
  layout.addView(back)
  layout.addView(status)
  save.setOnClickListener {
   val p = port.text.toString().toIntOrNull()
   val d = device.text.toString().trim()
   if (host.text.toString().trim().isEmpty() || p == null || p !in 1..65535 ||
    !d.matches(Regex("[A-Za-z0-9_-]+"))) {
    status.text = "Invalid host, port or device ID"
   } else {
    prefs.edit().putString("host", host.text.toString().trim())
     .putString("port", p.toString()).putString("user", user.text.toString())
     .putString("password", pass.text.toString()).putString("device", d)
      .putString("skin", skinKeys[skinChoice.selectedItemPosition]).apply()
    showHome()
   }
  }
  back.setOnClickListener { showHome() }
  show(layout)
 }
 override fun onBackPressed() { showHome() }
 override fun onDestroy() { stopMeasurement(); super.onDestroy() }
}
