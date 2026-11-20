package org.rogbid.bridge

/**
 * Diagnostic-only parser of the vendor sleep broadcast payload.
 * The device daemon emits a byte array consisting of a header byte followed
 * by (hour, minute, stage) triples. Do not archive results until verified
 * against a known native-app sleep session.
 */
internal object VendorSleepDecoder {
 data class Point(val minuteOfDay: Int, val type: Int)
 data class Result(
  val code: Int,
  val points: List<Point>,
  val firstSleep: Int?,
  val lastWake: Int?,
  val durationMinutes: Int?,
  val stageMinutes: Map<Int, Int>
 )

 fun decode(data: ByteArray): Result? {
  if (data.size < 4) return null
  val points = ArrayList<Point>()
  var offset = 1
  while (offset + 2 < data.size) {
   val hour = data[offset].toInt() and 0xff
   val minute = data[offset + 1].toInt() and 0xff
   val type = data[offset + 2].toInt() and 0xff
   if (hour !in 0..23 || minute !in 0..59 || type !in 0..3) break
   points.add(Point(hour * 60 + minute, if (type == 0) 3 else type))
   offset += 3
  }
  if (points.isEmpty()) return null
  // Native implementation truncates trailing padding and finds the last
  // non-wake segment before the final wake marker.
  val finalWakeIndex = points.indexOfLast { it.type == 3 }
  val lastSleepIndex = if (finalWakeIndex >= 0)
   (0 until finalWakeIndex).lastOrNull { points[it].type != 3 } else null
  val firstSleepIndex = points.indexOfFirst { it.type != 3 }.takeIf { it >= 0 }
  val first = firstSleepIndex?.let { points[it].minuteOfDay }
  val wake = if (lastSleepIndex != null && finalWakeIndex > lastSleepIndex)
   points[finalWakeIndex].minuteOfDay else null
  fun elapsed(from: Int, to: Int): Int = (to - from + 1440) % 1440
  val totals = mutableMapOf<Int, Int>()
  for (i in 0 until points.lastIndex) {
   val delta = elapsed(points[i].minuteOfDay, points[i + 1].minuteOfDay)
   if (delta in 1..720) totals[points[i].type] = (totals[points[i].type] ?: 0) + delta
  }
  val duration = if (first != null && wake != null) elapsed(first, wake) else null
  return Result(data[0].toInt() and 0xff, points, first, wake, duration, totals)
 }
}
