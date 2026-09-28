package com.denguewatch.bd.feature.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.typo
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The charts are drawn directly rather than pulled from a library.
 *
 * Every plot in this app is one of three shapes — a sparkline, a bar-and-average
 * curve, and a paired bar — and each has to sit inside a colour system with
 * two hand-tuned schemes and a risk scale that must not be recoloured. A
 * charting dependency would be configured almost entirely out of its defaults
 * to reach the same result, and would still own the theming.
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
private fun monotonePath(points: List<Offset>): Path {
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

private fun DrawScope.plotPoints(
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

// MARK: - Sparkline

/** Trend shape only — no axes, no labels, one hue. */
@Composable
fun Sparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val tint = color ?: Dw.colors.cases
    if (values.isEmpty()) return

    Canvas(modifier.clearAndSetSemantics { }) {
        // The domain deliberately excludes zero: on a series that never drops
        // near it, anchoring to zero flattens the whole shape into a straight
        // line and the sparkline stops saying anything.
        val min = values.min()
        val max = values.max()
        val inset = 2.dp.toPx()
        val points = plotPoints(
            values, min, max,
            width = size.width,
            height = size.height - inset * 2,
            top = inset,
        )

        val line = monotonePath(points)
        val area = Path().apply {
            addPath(line)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }

        drawPath(
            area,
            Brush.verticalGradient(
                listOf(tint.copy(alpha = 0.22f), tint.copy(alpha = 0.01f)),
            ),
        )
        drawPath(
            line,
            tint,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

// MARK: - Bars with an average line

/** One bar in [BarAverageChart], plus whatever the caller needs to label it. */
data class BarPoint(val value: Int, val label: String)

/**
 * The daily epidemiological curve: reported counts as bars, a smoothed average
 * over the top, and a scrubbable read-out.
 *
 * One measure, one axis. Deaths and other series get their own card rather than
 * a second scale here.
 */
@Composable
fun BarAverageChart(
    bars: List<BarPoint>,
    average: List<Double>,
    modifier: Modifier = Modifier,
    barColor: Color? = null,
    lineColor: Color? = null,
    height: Dp = 168.dp,
    selectedIndex: Int? = null,
    onSelect: (Int?) -> Unit = {},
    yAxisLabels: List<String> = emptyList(),
    xAxisLabels: List<Pair<Int, String>> = emptyList(),
) {
    val colors = Dw.colors
    val fill = barColor ?: colors.casesMuted
    val stroke = lineColor ?: colors.cases
    val labelStyle = typo(Typo.MICRO)

    if (bars.isEmpty()) return

    // Zero-anchored, because a bar's length is read as its magnitude. The top of
    // the scale takes the taller of the two series so the average line cannot
    // leave the plot on a week the mean sits above every single day.
    val maxValue = maxOf(
        bars.maxOf { it.value }.toDouble(),
        average.maxOrNull() ?: 0.0,
        1.0,
    )

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().height(height)) {
            if (yAxisLabels.isNotEmpty()) {
                Column(
                    Modifier.padding(end = 6.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    yAxisLabels.forEach {
                        Text(it, style = labelStyle, color = colors.mutedInk)
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(height)
                        .pointerInput(bars.size) {
                            // Scrubbing and tapping resolve to the same thing:
                            // whichever bar the finger is over.
                            fun indexAt(x: Float): Int =
                                ((x / size.width) * bars.size).toInt().coerceIn(0, bars.size - 1)

                            detectHorizontalDragGestures(
                                onDragStart = { onSelect(indexAt(it.x)) },
                                onDragEnd = { onSelect(null) },
                                onDragCancel = { onSelect(null) },
                            ) { change, _ -> onSelect(indexAt(change.position.x)) }
                        }
                        .pointerInput(bars.size) {
                            detectTapGestures { offset ->
                                val index = ((offset.x / size.width) * bars.size)
                                    .toInt().coerceIn(0, bars.size - 1)
                                onSelect(if (index == selectedIndex) null else index)
                            }
                        }
                        .clearAndSetSemantics { },
                ) {
                    val slot = size.width / bars.size
                    // A hairline gap at wide spacing, none at narrow: below about
                    // 3px a gap eats more of the bar than it separates.
                    val barWidth = (slot - if (slot > 8f) 2.dp.toPx() else 1f).coerceAtLeast(1f)
                    val corner = minOf(3.dp.toPx(), barWidth / 2)

                    // Grid: four quiet rules, drawn under everything.
                    repeat(4) { step ->
                        val y = size.height * step / 4f
                        drawLine(
                            colors.grid,
                            Offset(0f, y),
                            Offset(size.width, y),
                            strokeWidth = 1f,
                        )
                    }

                    val dimmed = selectedIndex != null
                    bars.forEachIndexed { index, bar ->
                        val barHeight = (bar.value / maxValue * size.height).toFloat()
                        val x = index * slot + (slot - barWidth) / 2
                        drawRoundRect(
                            color = if (dimmed && index != selectedIndex) {
                                fill.copy(alpha = 0.45f)
                            } else fill,
                            topLeft = Offset(x, size.height - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
                        )
                    }

                    if (average.isNotEmpty()) {
                        val points = average.mapIndexed { index, value ->
                            Offset(
                                index * slot + slot / 2,
                                size.height - (value / maxValue * size.height).toFloat(),
                            )
                        }
                        drawPath(
                            monotonePath(points),
                            stroke,
                            style = Stroke(
                                width = 2.4.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                    }

                    selectedIndex?.let { index ->
                        val x = index * slot + slot / 2
                        drawLine(
                            colors.axis,
                            Offset(x, 0f),
                            Offset(x, size.height),
                            strokeWidth = 1.dp.toPx(),
                        )
                        val y = size.height -
                            (bars[index].value / maxValue * size.height).toFloat()
                        drawCircle(stroke, radius = 5.dp.toPx(), center = Offset(x, y))
                        drawCircle(
                            colors.card,
                            radius = 5.dp.toPx(),
                            center = Offset(x, y),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
            }
        }

        if (xAxisLabels.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                xAxisLabels.forEach { (_, label) ->
                    Text(label, style = labelStyle, color = colors.mutedInk)
                }
            }
        }
    }
}

// MARK: - Horizontal paired bars

/**
 * A back-to-back bar for one age band.
 *
 * Male and female are a nominal pair, not a scale, so they take two series
 * colours rather than two steps of one hue — and each side is labelled, because
 * the split is the entire point of the chart.
 */
@Composable
fun PairedBar(
    left: Int,
    right: Int,
    max: Int,
    leftColor: Color,
    rightColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val safeMax = maxOf(max, 1)
        val gap = 2.dp.toPx()
        val half = (size.width - gap) / 2
        val corner = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())

        val leftWidth = (left.toFloat() / safeMax * half).coerceAtLeast(if (left > 0) 2f else 0f)
        val rightWidth = (right.toFloat() / safeMax * half).coerceAtLeast(if (right > 0) 2f else 0f)

        if (leftWidth > 0) {
            drawRoundRect(
                leftColor,
                topLeft = Offset(half - leftWidth, 0f),
                size = Size(leftWidth, size.height),
                cornerRadius = corner,
            )
        }
        if (rightWidth > 0) {
            drawRoundRect(
                rightColor,
                topLeft = Offset(half + gap, 0f),
                size = Size(rightWidth, size.height),
                cornerRadius = corner,
            )
        }
    }
}

// MARK: - Season comparison

/**
 * One bar per season. The current year is marked rather than recoloured — a
 * second hue here would imply the current season is a different kind of thing
 * from the ones before it.
 */
@Composable
fun YearBars(
    values: List<Pair<Int, Int>>,
    currentYear: Int,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
) {
    val colors = Dw.colors
    if (values.isEmpty()) return
    val max = maxOf(values.maxOf { it.second }, 1)

    Canvas(modifier.fillMaxWidth().height(height).clearAndSetSemantics { }) {
        val slot = size.width / values.size
        val barWidth = (slot - 6.dp.toPx()).coerceAtLeast(3f)
        val corner = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())

        values.forEachIndexed { index, (year, cases) ->
            val barHeight = (cases.toFloat() / max * size.height).coerceAtLeast(2f)
            val x = index * slot + (slot - barWidth) / 2
            val isCurrent = year == currentYear
            drawRoundRect(
                color = if (isCurrent) colors.cases else colors.casesMuted,
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = corner,
            )
        }
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
 * Evenly spaced tick values from zero to [max], inclusive, highest first.
 *
 * The tick count drops when the ceiling is too small to fill it: early in a
 * season the deaths chart peaks at one or two, and five ticks across that range
 * round to the same integer repeatedly — an axis reading "1 1 1 0 0" looks
 * broken rather than precise.
 */
fun axisTicks(max: Double, count: Int = 4): List<Int> {
    val ceiling = niceCeiling(max)
    val steps = minOf(count, ceiling.roundToInt().coerceAtLeast(1))
    return (steps downTo 0).map { step -> (ceiling * step / steps).roundToInt() }
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
