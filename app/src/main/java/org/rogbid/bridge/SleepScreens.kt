package org.rogbid.bridge

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
 private fun duration(minutes: Int): String = "${minutes / 60} ч ${minutes % 60} мин"

 fun latest() {
  val all = records()
  if (all.isEmpty()) {
   val root = page("Сон")
   root.addView(label("Пока нет сохранённых ночей. После завершения следующего цикла данные появятся здесь."))
   root.addView(button("История сна") { history() })
   root.addView(button("Назад") { onHome() })
  } else details(all.first(), false)
 }

 fun history() {
  val root = page("История сна")
  root.addView(label("Дата | Всего | Глубокий | Лёгкий | Пробуждения", 12f))
  val all = records()
  if (all.isEmpty()) root.addView(label("Сохранённых записей пока нет"))
  for (record in all) {
   val date = record.optString("sleep_date")
   val total = duration(record.optInt("duration_minutes"))
   val deep = duration(record.optInt("deep_minutes"))
   val light = duration(record.optInt("light_minutes"))
   val awakenings = wakeCount(record.optString("stages_json"))
   root.addView(label("$date  •  $total\nГлубокий: $deep  •  Лёгкий: $light\nПробуждения: $awakenings", 14f))
   root.addView(button("График сна — $date") { details(record, true) })
  }
  root.addView(button("Назад") { latest() })
 }

 private fun wakeCount(raw: String): String {
  // The vendor's known types are deep=1, light=2 and session end=3.
  // Type 3 is NOT evidence of an awakening; do not invent counts.
  return "нет данных"
 }

 private fun details(record: JSONObject, fromHistory: Boolean) {
  val root = page("Сон — " + record.optString("sleep_date"))
  root.addView(label(
   "Всего: " + duration(record.optInt("duration_minutes")) +
   "\nГлубокий: " + duration(record.optInt("deep_minutes")) +
   "\nЛёгкий: " + duration(record.optInt("light_minutes")) +
   "\nПробуждения: нет данных" +
   "\n" + record.optString("start_time") + " — " + record.optString("end_time")
  ))
  root.addView(label("График фаз сна", 17f))
  val stages = try { JSONArray(record.optString("stages_json")) } catch (_: Exception) { JSONArray() }
  root.addView(SleepGraph(activity, stages, foreground, accent).apply {
   layoutParams = LinearLayout.LayoutParams(-1, dp(180))
  })
  root.addView(label("Глубокий — синий  •  Лёгкий — бирюзовый", 12f))
  root.addView(button("Посмотреть историю") { history() })
  root.addView(button("Назад") { if (fromHistory) history() else onHome() })
 }

 private class SleepGraph(
  context: Activity, private val stages: JSONArray,
  private val textColor: Int, private val accent: Int
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
   val top = 18f
   val bottom = height - 32f
   paint.color = textColor
   paint.textSize = 12f * resources.displayMetrics.scaledDensity
   if (stages.length() < 2) {
    canvas.drawText("Нет данных для графика", left, height / 2f, paint)
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
     1 -> Color.rgb(45, 95, 210)
     2 -> Color.rgb(50, 190, 185)
     else -> Color.GRAY
    }
    val x1 = left + (right - left) * (t - start).toFloat() / (end - start)
    val x2 = left + (right - left) * (next - start).toFloat() / (end - start)
    val y1 = if (kind == 1) top + (bottom - top) * 0.48f else top
    canvas.drawRect(x1, y1, x2.coerceAtMost(right), bottom, paint)
   }
   paint.color = textColor
   canvas.drawText(stages.optJSONObject(0)?.optString("time") ?: "", left, height - 7f, paint)
   val finish = stages.optJSONObject(stages.length() - 1)?.optString("time") ?: ""
   canvas.drawText(finish, (right - paint.measureText(finish)).coerceAtLeast(left), height - 7f, paint)
  }
 }
}
