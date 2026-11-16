package org.rogbid.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/** Restore local sleep collection and BLE transport after the watch boots. */
class BootReceiver : BroadcastReceiver() {
 override fun onReceive(context: Context, intent: Intent) {
  if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
  try {
   val service = Intent(context, SyncService::class.java)
   if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    context.startForegroundService(service)
   } else {
    context.startService(service)
   }
  } catch (e: Exception) {
   Log.w("RogbidBridge", "Cannot restart sync service after boot", e)
  }
 }
}
