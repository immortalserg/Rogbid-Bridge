package org.rogbid.bridge

import android.app.Service
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Intent
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import java.util.UUID
import org.json.JSONObject
import android.net.Uri
import java.time.LocalDate
import java.time.ZoneId
import java.security.MessageDigest

/**
 * BLE transport: discoverable GATT endpoint with device identity handshake.
 * Supports identity and bounded, cursor-based heart history reads.
 */
class BleGattService : Service() {
 companion object {
  const val SERVICE_ID = "d312a001-7a7b-4e72-9e5b-a21b3471a001"
  const val REQUEST_ID = "d312a002-7a7b-4e72-9e5b-a21b3471a001"
  const val STREAM_ID = "d312a003-7a7b-4e72-9e5b-a21b3471a001"
  private const val TAG = "WatchBridgeBLE"
 }
 private var server: BluetoothGattServer? = null
 private var advertising = false
 private var callback: AdvertiseCallback? = null
 private var response = "{\"type\":\"error\",\"error\":\"no_request\"}"

 private val gattCallback = object : BluetoothGattServerCallback() {
  override fun onCharacteristicReadRequest(
   device: BluetoothDevice, requestId: Int, offset: Int,
   characteristic: BluetoothGattCharacteristic
  ) {
   val value = if (characteristic.uuid.toString() == STREAM_ID)
    synchronized(this@BleGattService) { response.toByteArray(Charsets.UTF_8) }
   else ByteArray(0)
   val status = if (offset <= value.size) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_INVALID_OFFSET
   server?.sendResponse(device, requestId, status, offset,
    if (status == BluetoothGatt.GATT_SUCCESS) value.copyOfRange(offset, value.size) else null)
  }
  override fun onCharacteristicWriteRequest(
   device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
   preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray
  ) {
   var status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED
   if (characteristic.uuid.toString() == REQUEST_ID && !preparedWrite && offset == 0 && value.size <= 256) {
    try {
     val request = JSONObject(String(value, Charsets.UTF_8))
     val type = request.optString("type")
     if (type == "heart_history" || type == "oxygen_history") {
      val after = request.optLong("after_id", 0L).coerceAtLeast(0L)
      val heart = type == "heart_history"
      val helper = object : android.database.sqlite.SQLiteOpenHelper(this@BleGattService, "measurements.db", null, 1) {
       override fun onCreate(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS measurements (id INTEGER PRIMARY KEY AUTOINCREMENT, measured_at TEXT NOT NULL, sensor_type INTEGER NOT NULL, heart_rate INTEGER, spo2 INTEGER)")
       }
       override fun onUpgrade(db: android.database.sqlite.SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
      }
      try {
       val page = JSONObject().put("type", type).put("after_id", after)
       val column = if (heart) "heart_rate" else "spo2"
       val sensor = if (heart) 21 else 31
       helper.readableDatabase.rawQuery(
        "SELECT id, measured_at, $column FROM measurements WHERE sensor_type=$sensor AND id>? AND $column IS NOT NULL ORDER BY id ASC LIMIT 1",
        arrayOf(after.toString())
       ).use { cursor ->
        if (cursor.moveToFirst()) {
         page.put("record", JSONObject().put("id", cursor.getLong(0))
          .put("measured_at", cursor.getString(1)).put(column, cursor.getInt(2)))
        } else page.put("done", true)
       }
       synchronized(this@BleGattService) { response = page.toString() }
       status = BluetoothGatt.GATT_SUCCESS
       Log.i(TAG, "GATT $type after_id=$after")
      } finally { helper.close() }
     } else if (type == "steps_history") {
      val after = request.optString("after_date", "")
      val page = JSONObject().put("type", type)
      var found = false
      contentResolver.query(
       Uri.parse("content://com.watchHealth.Walk/query"),
       arrayOf("date", "steps", "kcal", "diatance"), null, null, "date ASC"
      )?.use { cursor ->
       val dateCol = cursor.getColumnIndex("date")
       if (dateCol >= 0) {
        while (cursor.moveToNext()) {
         val date = cursor.getString(dateCol)?.take(10) ?: continue
         val day = try { LocalDate.parse(date) } catch (_: Exception) { continue }
         if (day.toString() <= after) continue
         if (getSharedPreferences("hidden_steps", MODE_PRIVATE).getBoolean(day.toString(), false)) continue
         val record = JSONObject().put("date", day.toString())
          .put("measured_at", day.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime().toString())
         for (column in arrayOf("steps", "kcal", "diatance")) {
          val index = cursor.getColumnIndex(column)
          if (index >= 0 && !cursor.isNull(index)) record.put(column, cursor.getDouble(index))
         }
         page.put("record", record)
         found = true
         break
        }
       }
      }
      if (!found) page.put("done", true)
      synchronized(this@BleGattService) { response = page.toString() }
      status = BluetoothGatt.GATT_SUCCESS
      Log.i(TAG, "GATT steps_history after_date=$after")
     } else if (type == "sleep_history") {
      val after = request.optLong("after_id", 0L).coerceAtLeast(0L)
      val page = JSONObject().put("type", type)
      val repo = SleepRepository(this@BleGattService)
      try {
       repo.readableDatabase.rawQuery(
        "SELECT id, sleep_date, start_time, end_time, duration_minutes, deep_minutes, light_minutes, fast_minutes, score, stages_json FROM sleep_history WHERE id>? ORDER BY id ASC LIMIT 1",
        arrayOf(after.toString())
       ).use { cursor ->
        if (cursor.moveToFirst()) {
         val date = cursor.getString(1)
         val start = cursor.getString(2)
         val endTime = cursor.getString(3)
         val stages = cursor.getString(9)
         val key = "$date|$start|$endTime|$stages"
         val digest = MessageDigest.getInstance("SHA-256")
          .digest(key.toByteArray(Charsets.UTF_8))
          .joinToString("") { "%02x".format(it.toInt() and 0xff) }
         val wakeDate = LocalDate.parse(date)
         val record = JSONObject().put("id", cursor.getLong(0))
          .put("sleep_date", date).put("start_time", start).put("end_time", endTime)
          .put("duration_minutes", cursor.getInt(4)).put("deep_minutes", cursor.getInt(5))
          .put("light_minutes", cursor.getInt(6)).put("fast_minutes", cursor.getInt(7))
          .put("stages_json", stages).put("digest", digest)
          .put("measured_at", wakeDate.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime().toString())
         if (!cursor.isNull(8)) record.put("score", cursor.getInt(8))
         page.put("record", record)
        } else page.put("done", true)
       }
       // Return fixed-size base64 chunks so the JSON reply fits small BLE MTUs.
       val encoded = page.toString().toByteArray(Charsets.UTF_8)
       val chunkOffset = request.optInt("offset", 0)
       if (chunkOffset < 0 || chunkOffset > encoded.size || encoded.size > 262144)
        throw IllegalArgumentException("Invalid sleep chunk offset or size")
       val next = minOf(chunkOffset + 64, encoded.size)
       val chunk = android.util.Base64.encodeToString(
        encoded.copyOfRange(chunkOffset, next), android.util.Base64.NO_WRAP)
       val envelope = JSONObject().put("type", "sleep_history_chunk")
        .put("offset", chunkOffset).put("total", encoded.size).put("data", chunk)
       synchronized(this@BleGattService) { response = envelope.toString() }
       status = BluetoothGatt.GATT_SUCCESS
       Log.i(TAG, "GATT sleep_history after_id=$after offset=$chunkOffset total=${encoded.size}")
      } finally { repo.close() }
     } else if (request.optString("type") == "device_info") {
      val id = WatchIdentity.id(this@BleGattService).trim()
      if (id.isNotEmpty()) {
       synchronized(this@BleGattService) {
        response = JSONObject().put("type", "device_info")
         .put("device_id", id).put("protocol", 1).toString()
       }
       status = BluetoothGatt.GATT_SUCCESS
       Log.i(TAG, "GATT device_info requested")
      }
     }
    } catch (e: Exception) { Log.w(TAG, "Invalid GATT request", e) }
   }
   if (responseNeeded) server?.sendResponse(device, requestId, status, offset, null)
  }
 }

 override fun onCreate() {
  super.onCreate()
  try {
   val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
   val adapter = manager.adapter ?: return
   if (!adapter.isEnabled) return
   val advertiser = adapter.bluetoothLeAdvertiser ?: return
   val gatt = manager.openGattServer(this, gattCallback) ?: return
   server = gatt
   val service = BluetoothGattService(UUID.fromString(SERVICE_ID), BluetoothGattService.SERVICE_TYPE_PRIMARY)
   val stream = BluetoothGattCharacteristic(
    UUID.fromString(STREAM_ID), BluetoothGattCharacteristic.PROPERTY_READ,
    BluetoothGattCharacteristic.PERMISSION_READ
   )
   val request = BluetoothGattCharacteristic(
    UUID.fromString(REQUEST_ID), BluetoothGattCharacteristic.PROPERTY_WRITE,
    BluetoothGattCharacteristic.PERMISSION_WRITE
   )
   service.addCharacteristic(stream)
   service.addCharacteristic(request)
   if (!gatt.addService(service)) {
    Log.e(TAG, "Cannot register GATT service")
    stopSelf()
    return
   }
   val cb = object : AdvertiseCallback() {
    override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
     advertising = true
     Log.i(TAG, "BLE advertising started")
    }
    override fun onStartFailure(errorCode: Int) {
     Log.e(TAG, "BLE advertising failed: $errorCode")
     stopSelf()
    }
   }
   callback = cb
   advertiser.startAdvertising(
    AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
     .setConnectable(true).setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM).build(),
    AdvertiseData.Builder().addServiceUuid(ParcelUuid(UUID.fromString(SERVICE_ID)))
     .setIncludeDeviceName(false).build(),
    cb
   )
  } catch (e: Exception) {
   Log.e(TAG, "BLE startup failed", e)
   stopSelf()
  }
 }

 override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
 override fun onBind(intent: Intent?): IBinder? = null
 override fun onDestroy() {
  try {
   val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
   callback?.let { manager.adapter?.bluetoothLeAdvertiser?.stopAdvertising(it) }
  } catch (e: Exception) { Log.w(TAG, "Stop advertising failed", e) }
  server?.close()
  server = null
  super.onDestroy()
 }
}
