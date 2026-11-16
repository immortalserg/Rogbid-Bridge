package org.rogbid.bridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.util.Log

/** Restore synchronization after boot, without accessing credential-encrypted data before unlock. */
class BootReceiver : BroadcastReceiver() {
 override fun onReceive(context: Context, intent: Intent) {
  val action = intent.action
  Log.i("RogbidBoot", "Receiver invoked: action=$action")
  if (action != Intent.ACTION_BOOT_COMPLETED &&
      action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
      action != Intent.ACTION_USER_UNLOCKED) {
   Log.i("RogbidBoot", "Ignoring unsupported broadcast action")
   return
  }
  val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !userManager.isUserUnlocked) {
   // SyncService reads preferences and SQLite databases in credential-encrypted storage.
   Log.i("RogbidBoot", "User still locked; deferring service start until unlock")
   return
  }
  try {
   val service = Intent(context, SyncService::class.java)
   Log.i("RogbidBoot", "Requesting SyncService start; action=$action sdk=${Build.VERSION.SDK_INT}")
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
