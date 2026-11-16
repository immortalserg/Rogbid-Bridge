package org.rogbid.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/** Restore local sleep collection and BLE transport after the watch boots. */
class BootReceiver : BroadcastReceiver() {
 override fun onReceive(context: Context, intent: Intent) {
  Log.i("RogbidBoot", "Receiver invoked: action=${intent.action}")
  if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
   Log.i("RogbidBoot", "Ignoring unsupported broadcast action")
   return
  }
  try {
   val service = Intent(context, SyncService::class.java)
   Log.i("RogbidBoot", "Requesting SyncService start; sdk=${Build.VERSION.SDK_INT}")
   val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    context.startForegroundService(service)
   } else {
    context.startService(service)
   }
   Log.i("RogbidBoot", "SyncService start requested: component=$started")
  } catch (e: Exception) {
   Log.e("RogbidBoot", "Cannot restart SyncService after boot", e)
  }
 }
}
