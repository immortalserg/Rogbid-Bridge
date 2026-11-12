package org.rogbid.bridge

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject

class MainActivity : Activity() {
 private val prefs by lazy { getSharedPreferences("mqtt", MODE_PRIVATE) }
 private fun page(): LinearLayout = LinearLayout(this).apply {
  orientation = LinearLayout.VERTICAL
  setPadding(24, 24, 24, 24)
 }
 private fun show(view: LinearLayout) {
  setContentView(ScrollView(this).apply { addView(view) })
 }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  // Retire the experimental background worker on upgrade.
  stopService(android.content.Intent(this, SyncService::class.java))
  prefs.edit().putBoolean("auto", false).apply()
  showHome()
 }
 private fun showHome() {
  val layout = page()
  layout.addView(TextView(this).apply { text = "Rogbid Bridge"; textSize = 24f })
  val button = Button(this).apply { text = "Send data to MQTT" }
  val history = Button(this).apply { text = "Send history to MQTT" }
  val settings = Button(this).apply { text = "Settings" }
  val status = TextView(this).apply { textSize = 15f }
  layout.addView(button)
  layout.addView(history)
  layout.addView(settings)
  layout.addView(status)
  show(layout)
  settings.setOnClickListener { showSettings() }
  history.setOnClickListener {
   val hostname = prefs.getString("host", "")?.trim().orEmpty()
   val number = prefs.getString("port", "1883")?.toIntOrNull()
   val id = prefs.getString("device", "king")?.trim().orEmpty()
   if (hostname.isEmpty() || number == null || number !in 1..65535 ||
    !id.matches(Regex("[A-Za-z0-9_-]+"))) {
    status.text = "Open Settings and configure MQTT connection"
    return@setOnClickListener
   }
   history.isEnabled = false
   button.isEnabled = false
   status.text = "Reading step history..."
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
      runOnUiThread { status.text = "History published: " + published + " day(s)" }
     } finally {
      if (client.isConnected) client.disconnect()
      client.close()
     }
    } catch (e: Exception) {
     runOnUiThread { status.text = "History error: " + e.javaClass.simpleName + ": " + e.message }
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
    status.text = "Open Settings and configure MQTT connection"
    return@setOnClickListener
   }
   val login = prefs.getString("user", "").orEmpty()
   val password = prefs.getString("password", "").orEmpty()
   button.isEnabled = false
   status.text = "Connecting..."
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
      discovery("heart_rate", "Heart Rate", "bpm", null, "measurement")
      discovery("spo2", "SpO2", "%", null, "measurement")
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
     runOnUiThread { status.text = "MQTT error: " + e.javaClass.simpleName + ": " + e.message }
    } finally {
     runOnUiThread { button.isEnabled = true }
    }
   }.start()
  }
 }
 private fun showSettings() {
  val layout = page()
  layout.addView(TextView(this).apply { text = "MQTT Settings"; textSize = 22f })
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
     .putString("password", pass.text.toString()).putString("device", d).apply()
    showHome()
   }
  }
  back.setOnClickListener { showHome() }
  show(layout)
 }
 override fun onBackPressed() { showHome() }
}
