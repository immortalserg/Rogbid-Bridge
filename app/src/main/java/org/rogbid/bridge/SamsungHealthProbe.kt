package org.rogbid.bridge

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log

/** Read-only diagnostic for Samsung Health's exported provider. */
object SamsungHealthProbe {
    private const val TAG = "SamsungHealthProbe"
    private const val READ_PERMISSION =
        "com.samsung.android.wear.shealth.healthdataprovider.permission.READ"
    private val URI = Uri.parse(
        "content://com.samsung.android.wear.shealth.healthdataprovider"
    )

    fun run(context: Context) {
        val granted = context.checkSelfPermission(READ_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
        Log.i(TAG, "READ permission granted: $granted")
        if (!granted) return

        try {
            val result = context.contentResolver.call(URI, "heart_rate", null, null)
            if (result == null) {
                Log.w(TAG, "Provider returned null")
                return
            }
            for (key in result.keySet()) {
                Log.i(TAG, "Result[$key] = ${result.get(key)}")
            }
            Log.i(TAG, "Probe finished")
        } catch (e: Exception) {
            Log.e(TAG, "Provider call failed", e)
        }
    }
}
