package org.watchbridge.wearos

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.PassiveListenerCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.IntervalDataPoint
import androidx.health.services.client.data.PassiveListenerConfig
import androidx.health.services.client.data.SampleDataPoint
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Wear Health Services adapter for Watch4+.
 *
 * Uses the proven DAILY aggregate types demonstrated by galaxy-health-bridge,
 * rather than assuming vendor Samsung Health ContentProviders are accessible.
 * Pulse is sampled using the Health Services MeasureClient while the user has
 * explicitly started collection. Sleep/SpO2 are NOT provided by these APIs.
 *
 * API approach: https://github.com/piyush140104/galaxy-health-bridge
 * That project's MIT licensed HealthReader was consulted; this adapter is
 * independently implemented for the Watch Bridge MQTT event contract.
 */
class WearHealthReader(
 private val context: Context,
 private val journal: HealthJournal,
 private val onMetric: (String, Double) -> Unit
) {
 private val health = HealthServices.getClient(context)
 private val passive = health.passiveMonitoringClient
 private val measure = health.measureClient
 private var lastHeartRecordMs = 0L

 private val passiveCallback = object : PassiveListenerCallback {
  override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
   try {
    dataPoints.getData(DataType.STEPS_DAILY).forEach { p: IntervalDataPoint<Long> ->
     updateDaily("steps", p.value.toDouble())
    }
    dataPoints.getData(DataType.CALORIES_DAILY).forEach { p: IntervalDataPoint<Double> ->
     updateDaily("kcal", p.value)
    }
    dataPoints.getData(DataType.DISTANCE_DAILY).forEach { p: IntervalDataPoint<Double> ->
     updateDaily("diatance", p.value)
    }
    dataPoints.getData(DataType.HEART_RATE_BPM).forEach { p: SampleDataPoint<Double> ->
     val whenMs = System.currentTimeMillis() - SystemClock.elapsedRealtime() + p.timeDurationFromBoot.toMillis()
     saveHeart(p.value, whenMs, true)
    }
   } catch (error: Exception) { Log.e(TAG, "Passive health event rejected", error) }
  }
 }

 private val measureCallback = object : MeasureCallback {
  override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
   Log.i(TAG, "HR availability: " + availability)
  }
  override fun onDataReceived(data: DataPointContainer) {
   try {
    data.getData(DataType.HEART_RATE_BPM).forEach { p: SampleDataPoint<Double> ->
     saveHeart(p.value, System.currentTimeMillis(), false)
    }
   } catch (error: Exception) { Log.e(TAG, "Measured heart event rejected", error) }
  }
 }

 fun start() {
  val types: Set<DeltaDataType<*, *>> = setOf(
   DataType.HEART_RATE_BPM,
   DataType.STEPS_DAILY,
   DataType.CALORIES_DAILY,
   DataType.DISTANCE_DAILY
  )
  try {
   passive.setPassiveListenerCallback(
    PassiveListenerConfig.builder().setDataTypes(types).build(),
    passiveCallback
   )
   measure.registerMeasureCallback(DataType.HEART_RATE_BPM, measureCallback)
  } catch (error: Exception) {
   Log.e(TAG, "Cannot subscribe to Health Services", error)
   WatchConfig.setStatus(context, "Health Services: " + error.javaClass.simpleName)
  }
 }

 fun stop() {
  try { passive.clearPassiveListenerCallbackAsync() } catch (_: Exception) {}
  try { measure.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, measureCallback) } catch (_: Exception) {}
 }

 private fun saveHeart(bpm: Double, whenMs: Long, passiveSample: Boolean) {
  if (!bpm.isFinite() || bpm < 25.0 || bpm > 250.0) return
  onMetric("heart_rate", bpm)
  // A passive sample carries historical time; MeasureClient adds a sample
  // at most once a minute to avoid overloading the MQTT/SQLite journal.
  if (!passiveSample && whenMs - lastHeartRecordMs < 60_000) return
  lastHeartRecordMs = whenMs
  val timestamp = Instant.ofEpochMilli(whenMs).toString()
  val id = "heart:" + whenMs
  val record = JSONObject().put("kind", "heart").put("id", id)
   .put("measured_at", timestamp).put("bpm", bpm.toInt())
  journal.save("heart", id, timestamp, record)
 }

 private fun updateDaily(metric: String, amount: Double) {
  if (!amount.isFinite() || amount < 0.0) return
  onMetric(metric, amount)
  val today = LocalDate.now(ZoneId.systemDefault())
  val timestamp = today.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime().toString()
  val id = "steps:" + today
  // Keep partial daily values in app preferences, then publish full day totals.
  val prefs = context.getSharedPreferences("watch_bridge_metrics", Context.MODE_PRIVATE)
  prefs.edit().putString(metric, amount.toString()).apply()
  val record = JSONObject().put("kind", "steps").put("id", id)
   .put("measured_at", timestamp).put("date", today.toString())
  for (key in arrayOf("steps", "kcal", "diatance")) {
   prefs.getString(key, null)?.toDoubleOrNull()?.let { record.put(key, it) }
  }
  journal.save("steps", id, timestamp, record)
 }

 companion object { private const val TAG = "WatchBridgeHealth" }
}
