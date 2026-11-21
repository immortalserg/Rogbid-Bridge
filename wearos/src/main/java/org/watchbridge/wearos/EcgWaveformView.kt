package org.watchbridge.wearos

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.abs
import kotlin.math.max

/** Rolling 3-second ECG preview, using raw samples and an adaptive display scale. */
class EcgWaveformView(context: Context) : View(context) {
 private val samples = ArrayDeque<Float>()
 private val capacity = 1500 // about 3 seconds at ~500 Hz
 private val grid = Paint(1).apply { color = Color.rgb(90, 65, 75); alpha = 95; strokeWidth = 1f }
 private val line = Paint(1).apply { color = Color.rgb(93, 225, 147); strokeWidth = 2.2f; style = Paint.Style.STROKE }
 private val trace = Path()
 private var baseline = 0f
 private var smooth = 0f
 private var initialized = false
 private var filterEnabled = true
 fun setFilterEnabled(enabled: Boolean) { filterEnabled = enabled; clear() }
 fun setRecording(values: List<Float>) { clear(); appendBatch(values) }
 fun appendBatch(values: List<Float>) {
  values.forEach { value ->
   if (value.isFinite()) {
    if (!initialized) { baseline = value; smooth = 0f; initialized = true }
    // Approximate 500 Hz: 0.5 Hz high-pass, 40 Hz low-pass; display only.
    baseline += 0.00626f * (value - baseline)
    smooth += 0.335f * ((value - baseline) - smooth)
    samples.addLast(if (filterEnabled) smooth else value)
    while (samples.size > capacity) samples.removeFirst()
   }
  }
  invalidate()
 }
 fun clear() { samples.clear(); initialized = false; baseline = 0f; smooth = 0f; invalidate() }
 override fun onDraw(canvas: Canvas) {
  super.onDraw(canvas)
  val w = width.toFloat()
  val h = height.toFloat()
  canvas.drawColor(Color.rgb(24, 28, 39))
  for (i in 1..9) canvas.drawLine(w * i / 10f, 0f, w * i / 10f, h, grid)
  for (i in 1..5) canvas.drawLine(0f, h * i / 6f, w, h * i / 6f, grid)
  if (samples.size < 2 || w <= 0f || h <= 0f) return
  val points = samples.toList()
  // Median-centered, robust amplitude scale: avoids one bad sample flattening the trace.
  val sorted = points.sorted()
  val median = sorted[sorted.size / 2]
  val deviations = points.map { abs(it - median) }.sorted()
  val typical = deviations[(deviations.size * 95 / 100).coerceIn(0, deviations.lastIndex)]
  val amplitude = max(typical * 1.5f, 0.05f)
  val scale = h * 0.43f / amplitude
  val center = h / 2f
  trace.reset()
  points.forEachIndexed { index, value ->
   val x = index.toFloat() / (capacity - 1).toFloat() * w
   val y = (center - (value - median) * scale).coerceIn(1f, h - 1f)
   if (index == 0) trace.moveTo(x, y) else trace.lineTo(x, y)
  }
  canvas.drawPath(trace, line)
 }
}
