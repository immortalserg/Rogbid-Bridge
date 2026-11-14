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
     if (request.optString("type") == "heart_history") {
      val after = request.optLong("after_id", 0L).coerceAtLeast(0L)
      val helper = object : android.database.sqlite.SQLiteOpenHelper(this@BleGattService, "measurements.db", null, 1) {
       override fun onCreate(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS measurements (id INTEGER PRIMARY KEY AUTOINCREMENT, measured_at TEXT NOT NULL, sensor_type INTEGER NOT NULL, heart_rate INTEGER, spo2 INTEGER)")
       }
       override fun onUpgrade(db: android.database.sqlite.SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
      }
      try {
       val page = JSONObject().put("type", "heart_history").put("after_id", after)
       helper.readableDatabase.rawQuery(
        "SELECT id, measured_at, heart_rate FROM measurements WHERE sensor_type=21 AND id>? AND heart_rate IS NOT NULL ORDER BY id ASC LIMIT 1",
        arrayOf(after.toString())
       ).use { cursor ->
        if (cursor.moveToFirst()) {
         page.put("record", JSONObject().put("id", cursor.getLong(0))
          .put("measured_at", cursor.getString(1)).put("heart_rate", cursor.getInt(2)))
        } else page.put("done", true)
       }
       synchronized(this@BleGattService) { response = page.toString() }
       status = BluetoothGatt.GATT_SUCCESS
       Log.i(TAG, "GATT heart_history after_id=$after")
      } finally { helper.close() }
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
