package org.rogbid.bridge

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

/** Screens for locally archived sleep sessions, independent of the vendor's resettable row. */
class SleepScreens(
 private val activity: Activity,
 private val background: Int,
 private val foreground: Int,
 private val accent: Int,
 private val buttonColor: Int,
 private val onHome: () -> Unit
) {
 private val density = activity.resources.displayMetrics.density
 private companion object {
  val DEEP_COLOR = Color.rgb(45, 95, 210)
  val LIGHT_COLOR = Color.rgb(50, 190, 185)
  val AWAKE_COLOR = Color.GRAY
 }
 private fun dp(value: Int): Int = (value * density).toInt()
 private fun page(title: String): LinearLayout {
  val root = LinearLayout(activity).apply {
   orientation = LinearLayout.VERTICAL
   setPadding(dp(12), dp(12), dp(12), dp(12))
   setBackgroundColor(this@SleepScreens.background)
  }
  root.addView(TextView(activity).apply {
   text = title
   textSize = 21f
   setTextColor(this@SleepScreens.foreground)
   setPadding(0, 0, 0, dp(10))
  })
  activity.setContentView(ScrollView(activity).apply {
   setBackgroundColor(this@SleepScreens.background)
   addView(root)
  })
  return root
 }
 private fun button(title: String, action: () -> Unit): Button = Button(activity).apply {
  text = title
  isAllCaps = false
  setTextColor(this@SleepScreens.foreground)
  background = GradientDrawable().apply {
   setColor(buttonColor)
   cornerRadius = dp(12).toFloat()
  }
  setOnClickListener { action() }
 }
 private fun addSpacedButton(root: LinearLayout, title: String, action: () -> Unit) {
  root.addView(button(title, action), LinearLayout.LayoutParams(-1, -2).apply {
   topMargin = dp(8)
  })
 }
 private fun label(value: String, size: Float = 15f): TextView = TextView(activity).apply {
  text = value
  textSize = size
  setTextColor(this@SleepScreens.foreground)
  setPadding(dp(4), dp(6), dp(4), dp(6))
 }
 private fun records(): List<JSONObject> {
  val repo = SleepRepository(activity)
  return try { repo.history() } finally { repo.close() }
 }
 private fun duration(minutes: Int): String = "${minutes / 60} ${L10n.text("hours")} ${minutes % 60} ${L10n.text("minutes")}"

 fun latest() {
  val all = records()
  if (all.isEmpty()) {
   val root = page(L10n.text("sleep"))
   root.addView(label(L10n.text("no_sleep")))
   addSpacedButton(root, L10n.text("back")) { onHome() }
   addSpacedButton(root, L10n.text("sleep_history")) { history() }
  } else details(all.first(), false)
 }

 fun history() {
  val root = page(L10n.text("sleep_history"))
  addSpacedButton(root, L10n.text("back")) { latest() }
  val all = records()
  if (all.isEmpty()) root.addView(label(L10n.text("no_records")))
  for (record in all) {
   val date = record.optString("sleep_date")
   val total = duration(record.optInt("duration_minutes"))
   addSpacedButton(root, "$date — $total") { details(record, true) }
  }
 }

 private fun wakeCount(raw: String): String {
  // The vendor's known types are deep=1, light=2 and session end=3.
  // Type 3 is NOT evidence of an awakening; do not invent counts.
  return "—"
 }

 private fun metricRow(root: LinearLayout, title: String, value: String, swatch: Int? = null) {
  val row = LinearLayout(activity).apply {
   orientation = LinearLayout.HORIZONTAL
   gravity = Gravity.CENTER_VERTICAL
  }
  row.addView(label("$title: $value", 17f).apply {
   setTypeface(null, Typeface.BOLD)
  })
  if (swatch != null) {
   row.addView(View(activity).apply {
    background = GradientDrawable().apply {
     setColor(swatch)
     cornerRadius = dp(2).toFloat()
    }
    layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply {
     leftMargin = dp(8)
    }
   })
  }
  root.addView(row)
 }

 private fun details(record: JSONObject, fromHistory: Boolean) {
  val interval = record.optString("start_time") + "–" + record.optString("end_time")
  val root = page(L10n.text("sleep") + " — " + record.optString("sleep_date") + " — " + interval)
  metricRow(root, L10n.text("total"), duration(record.optInt("duration_minutes")))
  metricRow(root, L10n.text("deep"), duration(record.optInt("deep_minutes")), DEEP_COLOR)
  metricRow(root, L10n.text("light"), duration(record.optInt("light_minutes")), LIGHT_COLOR)
  metricRow(root, L10n.text("awakenings"), "—", AWAKE_COLOR)
  val stages = try { JSONArray(record.optString("stages_json")) } catch (_: Exception) { JSONArray() }
  root.addView(SleepGraph(activity, stages, foreground).apply {
   layoutParams = LinearLayout.LayoutParams(-1, dp(90))
  })
  addSpacedButton(root, L10n.text("view_history")) { history() }
  addSpacedButton(root, L10n.text("back")) { if (fromHistory) history() else onHome() }
 }

 private class SleepGraph(
  context: Activity, private val stages: JSONArray,
  private val textColor: Int
 ) : View(context) {
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private fun minute(clock: String): Int? {
   val pieces = clock.split(":")
   if (pieces.size != 2) return null
   val h = pieces[0].toIntOrNull() ?: return null
   val m = pieces[1].toIntOrNull() ?: return null
   if (h !in 0..23 || m !in 0..59) return null
   return h * 60 + m
  }
  override fun onDraw(canvas: Canvas) {
   super.onDraw(canvas)
   val left = 12f
   val right = width - 12f
   val top = 2f
   val bottom = height - 23f
   paint.color = textColor
   paint.textSize = 12f * resources.displayMetrics.scaledDensity
   if (stages.length() < 2) {
    canvas.drawText(L10n.text("no_graph"), left, height / 2f, paint)
    return
   }
   val timeline = mutableListOf<Pair<Int, Int>>()
   var previous = -1
   var dayOffset = 0
   for (i in 0 until stages.length()) {
    val stage = stages.optJSONObject(i) ?: continue
    val time = minute(stage.optString("time")) ?: continue
    if (previous >= 0 && time < previous) dayOffset += 1440
    previous = time
    timeline.add(Pair(time + dayOffset, stage.optInt("type", -1)))
   }
   if (timeline.size < 2) return
   val start = timeline.first().first
   val end = timeline.last().first
   if (end <= start) return
   for (i in 0 until timeline.size - 1) {
    val (t, kind) = timeline[i]
    val next = timeline[i + 1].first
    if (next <= t) continue
    paint.color = when (kind) {
     1 -> DEEP_COLOR
     2 -> LIGHT_COLOR
     else -> AWAKE_COLOR
    }
    val x1 = left + (right - left) * (t - start).toFloat() / (end - start)
    val x2 = left + (right - left) * (next - start).toFloat() / (end - start)
    canvas.drawRect(x1, top + (bottom - top) * 0.48f, x2.coerceAtMost(right), bottom, paint)
   }
   paint.color = textColor
   canvas.drawText(stages.optJSONObject(0)?.optString("time") ?: "", left, height - 7f, paint)
   val finish = stages.optJSONObject(stages.length() - 1)?.optString("time") ?: ""
   canvas.drawText(finish, (right - paint.measureText(finish)).coerceAtLeast(left), height - 7f, paint)
  }
 }
}
