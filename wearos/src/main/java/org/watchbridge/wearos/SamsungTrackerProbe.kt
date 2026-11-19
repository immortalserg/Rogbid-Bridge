package org.watchbridge.wearos

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import com.samsung.android.service.health.tracking.data.ServiceDataPoint

/** Experimental, read-only Samsung Health Platform sensor probe. */
class SamsungTrackerProbe(
 private val context: Context,
 private val trackerType: String = "SPO2",
 private val report: (String) -> Unit
) {
 private val serviceDescriptor = "com.samsung.android.service.health.tracking.IClientEventListener"
 private val callbackDescriptor = "com.samsung.android.service.health.tracking.IServiceEventListener"
 private val appId = context.packageName
 @Volatile private var remote: IBinder? = null
 @Volatile private var active = false
 private var sampleCount = 0L
 private var lastSampleReportMs = 0L
 private var connection: ServiceConnection? = null
 private val callback = object : Binder() {
  init { attachInterface(null, callbackDescriptor) }
  override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
   if (code == INTERFACE_TRANSACTION) {
    reply?.writeString(callbackDescriptor)
    return true
   }
   if (code !in 1..3) return super.onTransact(code, data, reply, flags)
   data.enforceInterface(callbackDescriptor)
   try {
    when (code) {
     1 -> {
      val points = data.createTypedArrayList(ServiceDataPoint.CREATOR).orEmpty()
      points.forEach { point ->
       if (!active) return@forEach
       if (trackerType != "SPO2") {
        val v = point.serviceValue
        sampleCount++
        val message = when (trackerType) {
         "ECG" -> if (v.size >= 6) {
          "ECG samples=$sampleCount t=${point.timeStamp} " +
           "ecgMv=${v[0].floatValue} ppgGreen=${v[1].integerValue} " +
           "leadOff=${v[2].integerValue} sequence=${v[3].integerValue} " +
           "maxThresholdMv=${v[4].floatValue} minThresholdMv=${v[5].floatValue}"
         } else "ECG unexpected field count=${v.size}"
         "BIA" -> if (v.size >= 12) {
          "BIA samples=$sampleCount t=${point.timeStamp} " +
           "status=${v[0].integerValue} fatRatio=${v[1].floatValue} " +
           "fatMass=${v[2].floatValue} bodyWater=${v[3].floatValue} " +
           "muscleRatio=${v[4].floatValue} muscleMass=${v[5].floatValue} " +
           "bmr=${v[6].floatValue} fatFreeRatio=${v[7].floatValue} " +
           "fatFreeMass=${v[8].floatValue} progress=${v[9].floatValue} " +
           "impedanceMagnitude=${v[10].floatValue} impedanceDegree=${v[11].floatValue}"
         } else "BIA unexpected field count=${v.size}"
         else -> {
          val preview = v.take(12).mapIndexed { index, value ->
           "$index=${value.integerValue}"
          }.joinToString(", ")
          "$trackerType samples=$sampleCount t=${point.timeStamp} n=${v.size} [$preview]"
         }
        }
        // Avoid flooding the watch UI with high-rate ECG callbacks.
        val now = android.os.SystemClock.elapsedRealtime()
        if (sampleCount <= 3 || now - lastSampleReportMs >= 1000L) {
         lastSampleReportMs = now
         emit(message)
        }
        return@forEach
       }
       val v = point.serviceValue
       if (v.size >= 4) {
        val status = v[0].integerValue
        val spo2 = v[1].integerValue
        val pulse = v[2].integerValue
        val accuracy = v[3].integerValue
        emit("$trackerType status=$status value=$spo2 pulse=$pulse accuracy=$accuracy timestamp=${point.timeStamp}")
        if (trackerType == "SPO2" && (status == 2 || status == 3 || status == -6)) Thread { stop() }.start()
       } else emit("SpO2 point: unexpected values count=${v.size}")
      }
     }
     2 -> {
      emit("Samsung tracker error code=${data.readInt()}")
      Thread { stop() }.start()
     }
     3 -> emit("Samsung tracker flush completed")
    }
    reply?.writeNoException()
   } catch (e: Exception) {
    Log.e("WatchBridgeSamsung", "Callback decoding failed", e)
    reply?.writeException(e)
   }
   return true
  }
 }
 private fun emit(message: String) {
  Log.i("WatchBridgeSamsung", message)
  report(message)
 }
 private fun transact(binder: IBinder, code: Int, writer: (Parcel) -> Unit) {
  val request = Parcel.obtain()
  val response = Parcel.obtain()
  try {
   request.writeInterfaceToken(serviceDescriptor)
   writer(request)
   check(binder.transact(code, request, response, 0)) { "Binder transaction $code not handled" }
   response.readException()
  } finally {
   response.recycle()
   request.recycle()
  }
 }
 @Synchronized fun start() {
  if (active) { emit("SpO2 probe already running"); return }
  active = true
  sampleCount = 0L
  lastSampleReportMs = 0L
  val conn = object : ServiceConnection {
   override fun onServiceConnected(name: ComponentName, binder: IBinder) {
    if (!active) return
    remote = binder
    Thread {
     try {
      if (!active) return@Thread
      emit("$trackerType: creating tracker")
      transact(binder, 1) {
       it.writeString(appId)
       it.writeString(trackerType)
       it.writeInt(1) // non-null TrackerUserProfile
       it.writeFloat(-1f) // height
       it.writeFloat(-1f) // weight
       it.writeInt(-1) // gender
       it.writeInt(-1) // age
       it.writeString(null) // exerciseType
      }
      if (!active) return@Thread
      emit("$trackerType: registering listener")
      transact(binder, 2) {
       it.writeString(appId)
       it.writeString(trackerType)
       it.writeStrongBinder(callback)
      }
      if (!active) { stop(); return@Thread }
      emit("$trackerType: waiting for measurements (60 seconds maximum)")
     } catch (e: Exception) {
      emit("SpO2 start failed: ${e.javaClass.simpleName}: ${e.message}")
      stop()
     }
    }.start()
   }
   override fun onServiceDisconnected(name: ComponentName) {
    remote = null
    emit("SpO2: Samsung service disconnected")
   }
  }
  connection = conn
  try {
   val intent = Intent().setComponent(ComponentName(
    "com.samsung.android.service.health",
    "com.samsung.android.service.health.tracking.requesthandler.WearTrackerService"
   ))
   if (!context.bindService(intent, conn, Context.BIND_AUTO_CREATE)) {
    emit("SpO2: bindService returned false")
    stop()
    return
   }
   Thread {
    Thread.sleep(60_000)
    if (active) {
     emit("SpO2: 60-second timeout")
     stop()
    }
   }.start()
  } catch (e: Exception) {
   emit("SpO2 binding failed: ${e.javaClass.simpleName}: ${e.message}")
   stop()
  }
 }
 @Synchronized fun stop() {
  if (!active) return
  active = false
  remote?.let { binder ->
   try {
    transact(binder, 3) {
     it.writeString(appId)
     it.writeString(trackerType)
    }
    emit("SpO2: listener removed")
   } catch (e: Exception) { emit("SpO2 cleanup: ${e.javaClass.simpleName}: ${e.message}") }
  }
  remote = null
  connection?.let { try { context.unbindService(it) } catch (_: Exception) {} }
  connection = null
 }
}
