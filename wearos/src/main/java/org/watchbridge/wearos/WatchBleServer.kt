package org.watchbridge.wearos

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/** Experimental local-only GATT bridge; no measurement is generated here. */
class WatchBleServer(private val context: Context, private val report: (String) -> Unit) {
 companion object {
  val SERVICE_UUID: UUID = UUID.fromString("f3e9b8c0-4471-4a42-a14b-6f2f7a91c001")
  val DATA_UUID: UUID = UUID.fromString("f3e9b8c0-4471-4a42-a14b-6f2f7a91c002")
  private val CCC_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
 }
 private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
 private var server: BluetoothGattServer? = null
 private var dataCharacteristic: BluetoothGattCharacteristic? = null
 private var advertiser: android.bluetooth.le.BluetoothLeAdvertiser? = null
 private val subscribers = mutableSetOf<String>()
 private var latest: ByteArray = byteArrayOf()
 private val callback = object : BluetoothGattServerCallback() {
  override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
   if (newState == BluetoothProfile.STATE_DISCONNECTED) synchronized(subscribers) { subscribers.remove(device.address) }
  }
  override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
   if (characteristic.uuid != DATA_UUID) {
    server?.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null)
    return
   }
   val bytes = latest
   if (offset > bytes.size) server?.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
   else server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, bytes.copyOfRange(offset, bytes.size))
  }
  override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
   val enabled = synchronized(subscribers) { device.address in subscribers }
   val bytes = if (enabled) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
   if (offset > bytes.size) server?.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
   else server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, bytes.copyOfRange(offset, bytes.size))
  }
  override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
   val valid = descriptor.uuid == CCC_UUID && !preparedWrite && offset == 0 &&
    (value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) || value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE))
   if (valid) synchronized(subscribers) {
    if (value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) subscribers.add(device.address)
    else subscribers.remove(device.address)
   }
   if (responseNeeded) server?.sendResponse(device, requestId, if (valid) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null)
  }
 }
 private val advertiseCallback = object : AdvertiseCallback() {
  override fun onStartSuccess(settingsInEffect: AdvertiseSettings) { report("BLE advertising active") }
  override fun onStartFailure(errorCode: Int) { report("BLE advertising failed: $errorCode") }
 }
 fun start() {
  if (server != null) return
  if (Build.VERSION.SDK_INT >= 31 &&
   (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
    context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED)) {
   report("BLE permissions missing"); return
  }
  try {
   val adapter = manager.adapter ?: run { report("Bluetooth adapter unavailable"); return }
   if (!adapter.isEnabled || !adapter.isMultipleAdvertisementSupported) {
    report("Bluetooth disabled or BLE advertising unsupported"); return
   }
   val adv = adapter.bluetoothLeAdvertiser ?: run { report("BLE advertiser unavailable"); return }
   val gatt = manager.openGattServer(context, callback) ?: run { report("GATT server unavailable"); return }
   val characteristic = BluetoothGattCharacteristic(DATA_UUID,
    BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
    BluetoothGattCharacteristic.PERMISSION_READ)
   characteristic.addDescriptor(BluetoothGattDescriptor(CCC_UUID,
    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))
   val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
   service.addCharacteristic(characteristic)
   server = gatt
   dataCharacteristic = characteristic
   if (!gatt.addService(service)) { report("GATT addService failed"); stop(); return }
   advertiser = adv
   val settings = AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM).setConnectable(true).build()
   val data = AdvertiseData.Builder().addServiceUuid(ParcelUuid(SERVICE_UUID))
    .setIncludeDeviceName(false).build()
   adv.startAdvertising(settings, data, advertiseCallback)
   report("GATT service created")
  } catch (e: Exception) {
   Log.e("WatchBridgeBLE", "BLE start failed", e)
   report("BLE start failed: ${e.javaClass.simpleName}: ${e.message}")
   stop()
  }
 }
 /** Packet v1: version u8, SpO2 u8, pulse u16 LE, timestamp i64 LE. */
 fun publish(spo2: Int, pulse: Int, timestamp: Long) {
  if (spo2 !in 1..100 || pulse !in 1..300) return
  val bytes = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
   .put(1.toByte()).put(spo2.toByte()).putShort(pulse.toShort()).putLong(timestamp).array()
  latest = bytes
  val gatt = server ?: return
  val characteristic = dataCharacteristic ?: return
  val clients = manager.getConnectedDevices(BluetoothProfile.GATT)
  val targets = synchronized(subscribers) { subscribers.toSet() }
  for (device in clients) if (device.address in targets) {
   try {
    if (Build.VERSION.SDK_INT >= 33) gatt.notifyCharacteristicChanged(device, characteristic, false, bytes)
    else {
     @Suppress("DEPRECATION")
     characteristic.value = bytes
     @Suppress("DEPRECATION")
     gatt.notifyCharacteristicChanged(device, characteristic, false)
    }
   } catch (e: Exception) { Log.w("WatchBridgeBLE", "Notify failed", e) }
  }
  report("BLE updated SpO2=$spo2 pulse=$pulse")
 }
 fun stop() {
  try { advertiser?.stopAdvertising(advertiseCallback) } catch (_: Exception) {}
  advertiser = null
  try { server?.close() } catch (_: Exception) {}
  server = null
  dataCharacteristic = null
  synchronized(subscribers) { subscribers.clear() }
 }
}