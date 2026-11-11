package org.rogbid.bridge

import android.app.Activity
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val output = TextView(this).apply {
            textSize = 16f
            setPadding(24, 24, 24, 24)
        }
        setContentView(ScrollView(this).apply { addView(output) })

        val report = StringBuilder("Rogbid Bridge — diagnostics\n\n")
        try {
            val steps = Settings.System.getInt(contentResolver, "today_steps")
            report.append("System today_steps: ").append(steps).append("\n")
        } catch (e: Exception) {
            report.append("System today_steps ERROR: ").append(e.javaClass.simpleName)
                .append(": ").append(e.message).append("\n")
        }

        val uri = Uri.parse("content://com.watchHealth.Walk/query")
        try {
            contentResolver.query(
                uri,
                arrayOf("date", "steps", "diatance", "kcal", "targetsteps"),
                null,
                null,
                "date DESC"
            )?.use { cursor ->
                report.append("History rows: ").append(cursor.count).append("\n")
                if (cursor.moveToFirst()) {
                    for (i in 0 until cursor.columnCount) {
                        report.append(cursor.getColumnName(i)).append(": ")
                            .append(cursor.getString(i)).append("\n")
                    }
                }
            } ?: report.append("History provider returned null\n")
        } catch (e: Exception) {
            report.append("History ERROR: ").append(e.javaClass.simpleName)
                .append(": ").append(e.message).append("\n")
        }
        output.text = report.toString()
    }
}
