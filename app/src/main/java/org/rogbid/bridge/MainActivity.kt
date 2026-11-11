package org.rogbid.bridge

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject

class MainActivity : Activity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val prefs = getSharedPreferences("mqtt", MODE_PRIVATE)
  val layout = LinearLayout(this).apply {
   orientation = LinearLayout.VERTICAL
   setPadding(20, 20, 20, 20)
  }
  fun field(label: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText {
   return EditText(this).apply {
    hint = label
    setText(value)
    inputType = type
    setSingleLine(true)
    layout.addView(this)
   }
  }
  layout.addView(TextView(this).apply { text = "Rogbid Bridge MQTT"; textSize = 20f })
  val host = field("Broker IP / hostname", prefs.getString("host", "") ?: "")
  val port = field("Port: 1883 or 8883", prefs.getString("port", "1883") ?: "1883", InputType.TYPE_CLASS_NUMBER)
  val user = field("Username", prefs.getString("user", "") ?: "")
  val pass = field("MQTT password (visible for testing)", prefs.getString("password", "") ?: "")
  val device = field("Device ID", prefs.getString("device", "king") ?: "king")
  val button = Button(this).apply { text = "Send MQTT test" }
  val interval = field("Interval in minutes (1-60)", prefs.getInt("interval_minutes", 1).toString(), InputType.TYPE_CLASS_NUMBER)
  val startSync = Button(this).apply { text = "Start auto sync" }
  val stopSync = Button(this).apply { text = "Stop auto sync" }
  val refresh = Button(this).apply { text = "Show sync status" }
  val status = TextView(this).apply { textSize = 15f }
  layout.addView(button)
  layout.addView(startSync)
  layout.addView(stopSync)
  layout.addView(refresh)
  layout.addView(status)
  refresh.setOnClickListener {
   status.text = "Auto enabled: " + prefs.getBoolean("auto", false) +
    "\\nLast attempt: " + prefs.getString("last_attempt", "none") +
    "\\nLast result: " + prefs.getString("last_sync", "none")
  }
  startSync.setOnClickListener {
   val hostname = host.text.toString().trim()
   val number = port.text.toString().toIntOrNull()
   val id = device.text.toString().trim()
   val minutes = interval.text.toString().toIntOrNull()
   if (minutes == null || minutes !in 1..60) {
    status.text = "Interval must be 1 to 60 minutes"
    return@setOnClickListener
   }
   if (hostname.isBlank() || number == null || number !in 1..65535 ||
    !id.matches(Regex("[A-Za-z0-9_-]+"))) {
    status.text = "Check host, port and device ID"
   } else {
    val editor = prefs.edit().putString("host", hostname)
     .putString("port", number.toString()).putString("user", user.text.toString())
     .putString("device", id).putBoolean("auto", true)
     .putInt("interval_minutes", minutes)
     .putString("password", pass.text.toString())
    editor.apply()
    startForegroundService(Intent(this, SyncService::class.java))
    status.text = "Auto sync requested every " + minutes + " minute(s). Check status after 1-2 intervals."
   }
  }
  stopSync.setOnClickListener {
   stopService(Intent(this, SyncService::class.java))
   prefs.edit().putBoolean("auto", false).apply()
   status.text = "Auto sync stopped"
  }
  setContentView(ScrollView(this).apply { addView(layout) })
  try {
   val steps = Settings.System.getInt(contentResolver, "today_steps")
   val count = contentResolver.query(Uri.parse("content://com.watchHealth.Walk/query"), null, null, null, null)?.use { it.count }
   status.text = "Steps: " + steps + "\nHistory rows: " + count
  } catch (e: Exception) {
   status.text = "Diagnostics error: " + e.message
  }
  button.setOnClickListener {
   val hostname = host.text.toString().trim()
   val number = port.text.toString().toIntOrNull()
   val id = device.text.toString().trim()
   if (hostname.isEmpty() || number == null || number !in 1..65535 || !id.matches(Regex("[A-Za-z0-9_-]+"))) {
    status.text = "Check host, port and device ID"
    return@setOnClickListener
   }
   val login = user.text.toString()
   val password = pass.text.toString()
   prefs.edit().putString("host", hostname).putString("port", number.toString())
    .putString("user", login).putString("device", id)
    .putString("password", password).apply()
   button.isEnabled = false
   status.text = "Connecting..."
   Thread {
    try {
     val data = JSONObject()
     data.put("steps", Settings.System.getInt(contentResolver, "today_steps"))
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
}
