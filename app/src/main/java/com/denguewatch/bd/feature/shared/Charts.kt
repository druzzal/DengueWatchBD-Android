package com.denguewatch.bd.feature.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.typo
import androidx.compose.foundation.layout.size

/**
 * The charts are drawn directly rather than pulled from a library.
 *
 * Every plot in this app is one of three shapes — a sparkline, a bar-and-average
 * curve, and a paired bar — and each has to sit inside a colour system with
 * two hand-tuned schemes and a risk scale that must not be recoloured. A
 * charting dependency would be configured almost entirely out of its defaults
 * to reach the same result, and would still own the theming.
 */

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
