package org.watchbridge.wearos

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/** Rolling ECG signal preview. Values are raw Samsung tracker millivolts, not a diagnosis. */
class EcgWaveformView(context: Context) : View(context) {
 private val samples = ArrayDeque<Float>()
 private val grid = Paint(1).apply { color = Color.rgb(90, 65, 75); alpha = 95; strokeWidth = 1f }
 private val line = Paint(1).apply { color = Color.rgb(93, 225, 147); strokeWidth = 2.5f; style = Paint.Style.STROKE }
 private val trace = Path()
 fun append(value: Float) {
  if (!value.isFinite()) return
  samples.addLast(value.coerceIn(-5f, 5f))
  while (samples.size > 500) samples.removeFirst()
  invalidate()
 }
 fun clear() { samples.clear(); invalidate() }
 override fun onDraw(canvas: Canvas) {
  super.onDraw(canvas)
  val w = width.toFloat(); val h = height.toFloat()
  canvas.drawColor(Color.rgb(24, 28, 39))
  for (i in 1..9) canvas.drawLine(w * i / 10f, 0f, w * i / 10f, h, grid)
  for (i in 1..5) canvas.drawLine(0f, h * i / 6f, w, h * i / 6f, grid)
  if (samples.size < 2) return
  val points = samples.toList()
  val center = h / 2f
  val scale = h / 6f
  trace.reset()
  points.forEachIndexed { index, value ->
   val x = index.toFloat() / 499f * w
   val y = (center - value * scale).coerceIn(0f, h)
   if (index == 0) trace.moveTo(x, y) else trace.lineTo(x, y)
  }
  canvas.drawPath(trace, line)
 }
}
