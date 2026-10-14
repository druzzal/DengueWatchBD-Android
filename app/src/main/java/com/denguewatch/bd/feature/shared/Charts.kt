package com.denguewatch.bd.feature.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.rememberTextMeasurer
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
import kotlin.math.ceil
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.model.HealthTrend
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.tabularFigures

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
data class BarPoint(
    val value: Int,
    val label: String,
    /**
     * This bar's own colour, where height and hue say different things.
     *
     * The weekly area chart draws height as that week's cases and colour as the
     * band that week sat in, so the curve can be read either way round. Null
     * keeps the chart's single [barColor], which is what every daily chart wants.
     */
    val color: Color? = null,
)

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
    /**
     * The axis ticks, highest first, as values rather than text.
     *
     * The chart needs the numbers, not just their labels. Given only strings it
     * scaled the plot to the tallest bar while the axis was labelled to the next
     * round number above it, so a 2,600 bar filled a plot whose axis said 3,000
     * and every bar read high. The axis is the promise; the drawing has to keep
     * it, and it can only do that if it knows what the top line means.
     */
    yAxisTicks: List<Int> = emptyList(),
    /** How a tick is written for the reader — Bengali digits, k and লাখ. */
    formatTick: (Int) -> String = { it.toString() },
    xAxisLabels: List<Pair<Int, String>> = emptyList(),
) {
    val colors = Dw.colors
    val yAxisLabels = remember(yAxisTicks, formatTick) { yAxisTicks.map(formatTick) }
    val yAxisTop = yAxisTicks.firstOrNull()?.toDouble()
    val fill = barColor ?: colors.casesMuted
    val stroke = lineColor ?: colors.cases
    val labelStyle = typo(Typo.MICRO)

    if (bars.isEmpty()) return

    // Zero-anchored, because a bar's length is read as its magnitude. The top of
    // the scale takes the taller of the two series so the average line cannot
    // leave the plot on a week the mean sits above every single day.
    val maxValue = maxOf(
        yAxisTop ?: 0.0,
        bars.maxOf { it.value }.toDouble(),
        average.maxOrNull() ?: 0.0,
        1.0,
    )

    // What the y-axis labels occupy, measured from the widest of them.
    //
    // Two things depend on knowing it. The numbers are right-aligned inside it,
    // so "0" and "625" end where "2.5k" ends instead of trailing off towards the
    // plot; and the date labels underneath start after it, so "23 Sep" sits
    // beneath the first bar rather than beneath the axis numbers.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val gutter = remember(yAxisLabels, labelStyle, density) {
        if (yAxisLabels.isEmpty()) {
            0.dp
        } else {
            with(density) {
                yAxisLabels.maxOf { measurer.measure(it, labelStyle).size.width }.toDp()
            }
        }
    }
    val axisGap = 6.dp
    val plotInset = if (yAxisLabels.isEmpty()) 0.dp else gutter + axisGap

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth().height(height)) {
            if (yAxisLabels.isNotEmpty()) {
                // Each label is centred on the rule it names.
                //
                // The column used to lay these out with SpaceBetween and no
                // height of its own, so there was no spare space to distribute
                // and all of them stacked at the top of a plot they were meant
                // to span — on the deaths chart the three numbers sat inside the
                // top third while the data ran the full height beneath them.
                //
                // A zero-height anchor is the same trick the health chart uses:
                // the label is centred on something with no height, so whatever
                // the text measures, its middle lands on the rule.
                Box(Modifier.width(gutter).height(height)) {
                    val lines = (yAxisLabels.size - 1).coerceAtLeast(1)
                    yAxisLabels.forEachIndexed { index, label ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(0.dp)
                                .offset(y = height * index / lines.toFloat()),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Text(
                                label,
                                style = labelStyle,
                                color = colors.mutedInk,
                                modifier = Modifier.wrapContentHeight(unbounded = true),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(axisGap))
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

                    // Grid: one quiet rule per tick, drawn under everything, so
                    // every line is a number the reader can see named beside it.
                    // Four evenly spaced rules regardless of the labels meant the
                    // lines and the numbers were separate claims about the same
                    // chart, and they did not agree.
                    val rules = if (yAxisLabels.size >= 2) yAxisLabels.size else 4
                    repeat(rules) { step ->
                        val y = size.height * step / (rules - 1).coerceAtLeast(1).toFloat()
                        drawLine(
                            colors.grid,
                            Offset(0f, y.coerceAtMost(size.height - 0.5f)),
                            Offset(size.width, y.coerceAtMost(size.height - 0.5f)),
                            strokeWidth = 1f,
                        )
                    }

                    val dimmed = selectedIndex != null
                    bars.forEachIndexed { index, bar ->
                        val barHeight = (bar.value / maxValue * size.height).toFloat()
                        val x = index * slot + (slot - barWidth) / 2
                        drawRoundRect(
                            // A bar's own colour where it has one — the weekly
                            // area chart bands each week — otherwise the chart's.
                            color = (bar.color ?: fill).let {
                                if (dimmed && index != selectedIndex) it.copy(alpha = 0.45f) else it
                            },
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
                // Inset to where the plot starts, so the first date sits under
                // the first bar. Without it the row spans the whole card and
                // the dates drift left of the data they label.
                Modifier.fillMaxWidth().padding(start = plotInset),
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
 *
 * [unrecorded] is cases the source counted without recording a sex. They are
 * drawn beyond the right-hand bar in neutral ink rather than left out, so the
 * row draws the same number of people it prints beside itself. They are not
 * split between the two sides: which side they belong on is exactly what the
 * source does not say.
 *
 * [max] is the count that fills one half, from the spine to the edge — the
 * same for every row, so equal counts draw equal lengths on either side.
 */
@Composable
fun PairedBar(
    left: Int,
    right: Int,
    max: Int,
    leftColor: Color,
    rightColor: Color,
    modifier: Modifier = Modifier,
    unrecorded: Int = 0,
    unrecordedColor: Color = Color.Unspecified,
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
        if (unrecorded > 0 && unrecordedColor != Color.Unspecified) {
            val width = (unrecorded.toFloat() / safeMax * half).coerceAtLeast(2f)
            drawRoundRect(
                unrecordedColor,
                // A hairline clear of the female bar, so the two read as two
                // segments rather than one bar in two shades.
                topLeft = Offset(half + gap + rightWidth + 1.dp.toPx(), 0f),
                size = Size(width, size.height),
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

// MARK: - A reader's own readings over time

/**
 * A line on the chart, and which way a reading crosses it to matter.
 *
 * Temperature counts when it climbs above the line; a platelet count counts
 * when it falls below one. The chart draws both the same way and only needs
 * telling which side is the worrying one — marking the top of a platelet range
 * would highlight exactly the readings nobody is worried about.
 */
private val PLOT_HEIGHT = 132.dp

data class TrendThreshold(
    val value: Double,
    val concern: Concern,
    /** Printed beside the line, so the mark is never only a colour. */
    val word: String = "",
) {
    enum class Concern { ABOVE, BELOW }

    fun isCrossedBy(reading: Double): Boolean = when (concern) {
        Concern.ABOVE -> reading > value
        Concern.BELOW -> reading < value
    }
}

/**
 * A line through the reader's own readings, with the direction stated.
 *
 * Reusable across measures so temperature and platelets are drawn the same way
 * — a reader comparing the two should not have to relearn the chart.
 */
@Composable
fun HealthTrendChart(
    title: String,
    points: List<Pair<Long, Double>>,
    trend: HealthTrend,
    tint: Color,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    /** The edge worth marking, when the measure has one. */
    threshold: TrendThreshold? = null,
    xAxisLabels: List<String> = emptyList(),
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    if (points.isEmpty()) return

    val values = points.map { it.second }

    // The vertical range the line is drawn in.
    //
    // Left to a tidy-numbers axis this rounds outward, which for a platelet
    // count meant a scale starting at zero: half the plot empty and a fall from
    // 186 to 88 flattened into a gentle slope. The shape of that fall is the
    // reason the chart is there, so the domain is the readings and the
    // threshold and little else — padded enough that neither sits on an edge.
    var low = values.min()
    var high = values.max()
    if (threshold != null) {
        low = minOf(low, threshold.value)
        high = maxOf(high, threshold.value)
    }
    // A flat line still needs a band to sit in.
    val pad = maxOf((high - low) * 0.15, 0.5)
    val domainLow = maxOf(0.0, low - pad)
    val domainHigh = high + pad

    val thresholdLabel = threshold?.let {
        if (it.word.isEmpty()) format(it.value) else "${format(it.value)} ${it.word}"
    }

    // VoiceOver gets the direction and the endpoints, which is what the line
    // conveys — reading out every point would be worse than useless.
    val summary = "$title. ${loc.t(trend.labelKey)}. " +
        "${format(values.first())} to ${format(values.last())}."

    Column(verticalArrangement = Arrangement.spacedBy(Space.tight)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                trend.icon,
                contentDescription = null,
                tint = colors.secondaryText,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.size(4.dp))
            Text(
                loc.t(trend.labelKey),
                style = typo(Typo.MICRO, FontWeight.SemiBold),
                color = colors.secondaryText,
            )
        }

        Row(Modifier.fillMaxWidth().height(PLOT_HEIGHT)) {
            // Each label is centred on the gridline it names.
            //
            // Not `SpaceBetween`: that aligns the first label's top edge and
            // the last one's bottom edge to the plot, leaving both 24px off the
            // lines they label — the chart said 39.7 beside a line that was not
            // 39.7. Correcting it with an offset needs the label's measured
            // height, which is not the same as its style's line height, so the
            // arithmetic was wrong in the other direction.
            //
            // A zero-height anchor sidesteps the question: the label is
            // centred on an anchor that has no height of its own, so whatever
            // the text measures, its middle lands on the gridline.
            val labelStyle = typo(Typo.MICRO).tabularFigures()
            Box(Modifier.width(42.dp).height(PLOT_HEIGHT)) {
                listOf(domainHigh, (domainHigh + domainLow) / 2, domainLow)
                    .forEachIndexed { index, value ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(0.dp)
                                .offset(y = PLOT_HEIGHT * index / 2f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.wrapContentHeight(unbounded = true)) {
                                ShrinkToFitText(
                                    format(value),
                                    style = labelStyle,
                                    color = colors.mutedInk,
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }
                        }
                    }
            }
            Spacer(Modifier.size(Space.tight))

            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics { contentDescription = summary },
            ) {
                val plotHeight = maxHeight
                Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
                    val span = (domainHigh - domainLow).takeIf { it > 0 } ?: 1.0
                    fun yOf(value: Double) =
                        (size.height - ((value - domainLow) / span * size.height)).toFloat()

                    repeat(3) { index ->
                        val y = size.height * index / 2f
                        drawLine(colors.grid, Offset(0f, y), Offset(size.width, y), 1f)
                    }

                    if (threshold != null) {
                        val y = yOf(threshold.value)
                        drawLine(
                            colors.riskTint(RiskLevel.HIGH),
                            Offset(0f, y),
                            Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(3.dp.toPx(), 4.dp.toPx()),
                            ),
                        )
                    }

                    val step =
                        if (values.size == 1) 0f else size.width / (values.size - 1)
                    val plotted = values.mapIndexed { index, value ->
                        Offset(index * step, yOf(value))
                    }
                    drawPath(
                        monotonePath(plotted),
                        tint,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                    plotted.forEachIndexed { index, offset ->
                        // The most recent reading when it has crossed the
                        // threshold — the one point a reader is actually
                        // asking about.
                        val isLatestAndOut = index == plotted.lastIndex &&
                            threshold?.isCrossedBy(values[index]) == true
                        drawCircle(
                            if (isLatestAndOut) colors.riskTint(RiskLevel.SEVERE) else tint,
                            radius = if (isLatestAndOut) 5.dp.toPx() else 3.5f.dp.toPx(),
                            center = offset,
                        )
                    }
                }

                if (threshold != null && thresholdLabel != null) {
                    // The threshold in words as well as a line — colour is
                    // never the only signal here.
                    //
                    // It is placed against its own line rather than against the
                    // edge of the plot: aligned to the top, "150 Low" sat up at
                    // the 201 gridline and read as a label for the top of the
                    // scale. It sits on the safe side of the line, so it never
                    // lands on the readings that crossed it.
                    val span = (domainHigh - domainLow).takeIf { it > 0 } ?: 1.0
                    val fromTop = ((domainHigh - threshold.value) / span).toFloat()
                    val labelHeight = 14.dp
                    val y = plotHeight * fromTop +
                        if (threshold.concern == TrendThreshold.Concern.ABOVE) {
                            -labelHeight
                        } else {
                            1.dp
                        }
                    Text(
                        thresholdLabel,
                        style = typo(Typo.MICRO),
                        color = colors.riskInk(RiskLevel.HIGH),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(y = y.coerceIn(0.dp, plotHeight - labelHeight))
                            .background(colors.card.copy(alpha = 0.9f))
                            .padding(horizontal = 3.dp),
                    )
                }
            }
        }

        if (xAxisLabels.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 42.dp + Space.tight),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                xAxisLabels.forEach {
                    ShrinkToFitText(it, style = typo(Typo.MICRO), color = colors.mutedInk)
                }
            }
        }

        Text(
            loc.t("trend.readings", loc.num(points.size)),
            style = typo(Typo.MICRO),
            color = colors.mutedInk,
        )

        // The row above the plot names a direction — "Falling" — which is the
        // closest this app comes to saying something about a reader's numbers.
        // It is a fact about the series and not a finding, and the difference
        // is not obvious to someone watching their own platelet count drop. So
        // the claim carries its limit with it.
        if (trend != HealthTrend.NOT_ENOUGH_DATA) {
            Text(
                loc.t("trend.note"),
                style = typo(Typo.MICRO),
                color = colors.mutedInk,
            )
        }
    }
}

private val HealthTrend.icon
    get() = when (this) {
        HealthTrend.RISING -> DwIcons.trendUp
        HealthTrend.FALLING -> DwIcons.trendDown
        else -> DwIcons.trendFlat
    }
