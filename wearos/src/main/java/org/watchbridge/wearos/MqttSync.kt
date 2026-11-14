package org.watchbridge.wearos

import android.content.Context
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.time.LocalTime

/** Runs on a background HandlerThread. Does not use BLE or a companion phone. */
class MqttSync(private val context: Context, private val journal: HealthJournal) {
 fun publish(state: JSONObject) {
  val broker = WatchConfig.broker(context)
  if (broker.isBlank()) {
   WatchConfig.setStatus(context, "Configure MQTT broker first")
   return
  }
  val id = WatchConfig.deviceId(context)
  val client = MqttClient(broker, "watchbridge-" + id, MemoryPersistence())
  try {
   val options = MqttConnectOptions().apply {
    isCleanSession = true
    connectionTimeout = 8
    keepAliveInterval = 30
    val username = WatchConfig.username(context)
    if (username.isNotBlank()) userName = username
    val secret = WatchConfig.password(context)
    if (secret.isNotEmpty()) password = secret.toCharArray()
   }
   client.connect(options)
   val base = "rogbid/" + id
   val stateMessage = MqttMessage(state.toString().toByteArray(Charsets.UTF_8)).apply {
    qos = 1
    isRetained = true
   }
   client.publish(base + "/state", stateMessage)
   var count = 0
   for (event in journal.unsent()) {
    val message = MqttMessage(event.payload.toByteArray(Charsets.UTF_8)).apply {
     qos = 1
     isRetained = false
    }
    client.publish(base + "/history/" + event.kind, message)
    journal.markSent(event)
    count++
   }
   WatchConfig.setStatus(context, "Synced " + LocalTime.now() + " (" + count + " records)")
  } catch (error: Exception) {
   WatchConfig.setStatus(context, "MQTT error: " + error.javaClass.simpleName + ": " + (error.message ?: ""))
  } finally {
   try { if (client.isConnected) client.disconnect() } catch (_: Exception) {}
   try { client.close() } catch (_: Exception) {}
  }
 }
}
