package org.watchbridge.wearos

import android.content.Context
import android.util.Log
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.concurrent.Executors

/** MQTT transport for local measurements. Does not run as a background service. */
class WatchMqtt(private val context: Context, private val report: (String) -> Unit) {
 private val queue = Executors.newSingleThreadExecutor()
 private var client: MqttClient? = null
 private var currentUri = ""
 private val prefs get() = context.getSharedPreferences("watch_bridge", Context.MODE_PRIVATE)
 private val deviceId get() = prefs.getString("device_id", "wearos_unknown") ?: "wearos_unknown"

 fun publish(kind: String, payload: String) {
  if (!prefs.getBoolean("mqtt_enabled", false)) return
  val host = prefs.getString("mqtt_host", "")?.trim().orEmpty()
  val port = prefs.getString("mqtt_port", "8883")?.toIntOrNull() ?: return
  val tls = prefs.getBoolean("mqtt_tls", true)
  if (host.isEmpty() || port !in 1..65535) { report("MQTT: неверный адрес или порт"); return }
  queue.execute {
   var stage = "создание подключения"
   try {
    val uri = (if (tls) "ssl://" else "tcp://") + host + ":" + port
    if (uri != currentUri || client?.isConnected != true) {
     try { client?.disconnect() } catch (_: Exception) {}
     try { client?.close() } catch (_: Exception) {}
     val next = MqttClient(uri, "watchbridge_" + deviceId.takeLast(18), MemoryPersistence())
     val opts = MqttConnectOptions().apply {
      isCleanSession = true
      connectionTimeout = 10
      keepAliveInterval = 30
      userName = prefs.getString("mqtt_user", "").orEmpty().takeIf { it.isNotEmpty() }
      val password = prefs.getString("mqtt_password", "").orEmpty()
      if (password.isNotEmpty()) this.password = password.toCharArray()
     }
     stage = "соединение с брокером"
     next.connect(opts)
     client = next
     currentUri = uri
     report("MQTT: подключено")
    }
    val base = "rogbid/$deviceId"
    stage = "публикация $kind"
    client?.publish("$base/history/$kind", payload.toByteArray(Charsets.UTF_8), 1, false)
    report("MQTT: отправлено $kind")
   } catch (e: Exception) {
    Log.w("WatchBridgeMQTT", "Publish failed", e)
    val cause = generateSequence(e as Throwable?) { it.cause }.last()
    val details = (cause.message ?: e.message ?: "нет описания").take(180)
    report("MQTT [$stage]: ${e.javaClass.simpleName}: $details")
    Log.e("WatchBridgeMQTT", "Stage=$stage, host=$host, port=$port, tls=$tls", e)
   }
  }
 }
 fun close() {
  queue.execute {
   try { client?.disconnect() } catch (_: Exception) {}
   try { client?.close() } catch (_: Exception) {}
   client = null
  }
  queue.shutdown()
 }
}
