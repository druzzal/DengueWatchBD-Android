package com.denguewatch.bd.feature.shared

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.size

/*
 * Shared by every chart: the curve through a series, where its points fall,
 * and the round numbers and labels its axes carry. No drawing of its own.
 */

// MARK: - Curve helpers

/**
 * A monotone cubic through the points.
 *
 * Plain cubic smoothing overshoots on epidemic curves — a sharp climb followed
 * by a plateau makes the spline bulge above every observation, which on a case
 * chart reads as a peak that was never reported. Fritsch–Carlson keeps the
 * interpolant inside the data it is drawn from.
 */
internal fun monotonePath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 1) return path
    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }

    val n = points.size
    val slopes = DoubleArray(n - 1)
    val dx = DoubleArray(n - 1)
    for (i in 0 until n - 1) {
        dx[i] = (points[i + 1].x - points[i].x).toDouble()
        slopes[i] = if (dx[i] == 0.0) 0.0 else (points[i + 1].y - points[i].y) / dx[i]
    }

    val tangents = DoubleArray(n)
    tangents[0] = slopes[0]
    tangents[n - 1] = slopes[n - 2]
    for (i in 1 until n - 1) {
        tangents[i] = if (slopes[i - 1] * slopes[i] <= 0) 0.0 else (slopes[i - 1] + slopes[i]) / 2
    }
    // Clamp so no segment can overshoot its own endpoints.
    for (i in 0 until n - 1) {
        if (slopes[i] == 0.0) {
            tangents[i] = 0.0
            tangents[i + 1] = 0.0
        } else {
            val a = tangents[i] / slopes[i]
            val b = tangents[i + 1] / slopes[i]
            val h = Math.hypot(a, b)
            if (h > 3.0) {
                val scale = 3.0 / h
                tangents[i] = scale * a * slopes[i]
                tangents[i + 1] = scale * b * slopes[i]
            }
        }
    }

    for (i in 0 until n - 1) {
        val third = dx[i] / 3.0
        path.cubicTo(
            (points[i].x + third).toFloat(),
            (points[i].y + tangents[i] * third).toFloat(),
            (points[i + 1].x - third).toFloat(),
            (points[i + 1].y - tangents[i + 1] * third).toFloat(),
            points[i + 1].x,
            points[i + 1].y,
        )
    }
    return path
}

internal fun DrawScope.plotPoints(
    values: List<Double>,
    min: Double,
    max: Double,
    width: Float = size.width,
    height: Float = size.height,
    top: Float = 0f,
): List<Offset> {
    if (values.isEmpty()) return emptyList()
    val span = (max - min).takeIf { it > 0 } ?: 1.0
    val step = if (values.size == 1) 0f else width / (values.size - 1)
    return values.mapIndexed { index, value ->
        val y = top + height - ((value - min) / span * height).toFloat()
        Offset(index * step, y)
    }
}

/** Rounds a domain up to a readable axis maximum (100, 250, 500, 1000, …). */
fun niceCeiling(value: Double): Double {
    if (value <= 0) return 1.0
    val magnitude = Math.pow(10.0, Math.floor(Math.log10(value)))
    val normalised = value / magnitude
    val step = when {
        normalised <= 1 -> 1.0
        normalised <= 2 -> 2.0
        normalised <= 2.5 -> 2.5
        normalised <= 5 -> 5.0
        else -> 10.0
    }
    return step * magnitude
}

/**
 * A readable interval between ticks: 1, 2, 5 or 10 times a power of ten.
 *
 * 2.5 is on the list only where it still lands on whole numbers. On the deaths
 * chart, peaking at ten, a step of 2.5 put ticks at 7.5 and 2.5, which rounded
 * to 8 and 3 and gave the axis "10 · 8 · 5 · 3 · 0" — the fractional step was
 * invisible but its rounding was not. These axes count people, so below a
 * magnitude of ten the step stays whole.
 */
private fun niceStep(value: Double): Double {
    if (value <= 0) return 1.0
    val magnitude = Math.pow(10.0, Math.floor(Math.log10(value)))
    val normalised = value / magnitude
    val factor = when {
        normalised <= 1 -> 1.0
        normalised <= 2 -> 2.0
        normalised <= 2.5 && magnitude >= 10 -> 2.5
        normalised <= 5 -> 5.0
        else -> 10.0
    }
    return factor * magnitude
}

/**
 * Tick values from zero past [max], inclusive, highest first.
 *
 * The *step* is the round number, not the ceiling. Rounding the ceiling and then
 * cutting it into [count] equal parts is what produced "2.5k · 1.9k · 1.3k ·
 * 625 · 0" on the Trend card: 2,500 is a fine axis maximum but a quarter of it
 * is 625, and a reader cannot place a bar against that. Choosing the step first
 * — 1, 2, 2.5, 5 or 10 times a power of ten — and counting up in it gives
 * "3.0k · 2.0k · 1.0k · 0", which is what iOS draws, because Swift Charts picks
 * its ticks the same way.
 *
 * The step never falls below one. These axes count people, and a half-step
 * across a range of two rounds to the same integer twice — an axis reading
 * "2 2 1 1 0" looks broken rather than precise.
 */
fun axisTicks(max: Double, count: Int = 4): List<Int> {
    if (max <= 0 || count < 1) return listOf(0)
    val step = maxOf(1.0, niceStep(max / count))
    val steps = ceil(max / step).toInt().coerceAtLeast(1)
    return (steps downTo 0).map { index -> (index * step).roundToInt() }
}

/**
 * Every [stride]-th epidemiological week, chosen by its own number.
 *
 * A season runs to 52 categories, so every label would collide. Showing every
 * fourth keeps roughly monthly gridposts, which is what a reader scans for —
 * and anchoring on the week number rather than the position keeps the interval
 * regular. Picking ten evenly spaced indices instead gave W01, W05, W10, W14:
 * steps of four and five alternating, so the gaps between gridposts were not
 * the same length and the axis could not be read as months. Matches iOS.
 */
fun weekLabels(weeks: List<String>, stride: Int = 4): List<Pair<Int, String>> =
    weeks.mapIndexedNotNull { index, week ->
        week.removePrefix("W").toIntOrNull()
            ?.takeIf { stride > 0 && it % stride == 0 }
            ?.let { index to week }
    }

/** Picks [desired] roughly evenly spaced label positions from a series. */
fun <T> pickLabels(items: List<T>, desired: Int): List<Pair<Int, T>> {
    if (items.isEmpty()) return emptyList()
    if (items.size <= desired) return items.mapIndexed { index, item -> index to item }
    val step = (items.size - 1).toDouble() / (desired - 1).coerceAtLeast(1)
    return (0 until desired).map { position ->
        val index = (position * step).roundToInt().coerceIn(0, items.size - 1)
        index to items[index]
    }.distinctBy { it.first }
}

/** True when two doubles are close enough to treat as the same value. */
fun nearlyEqual(a: Double, b: Double, tolerance: Double = 1e-6) = abs(a - b) < tolerance
