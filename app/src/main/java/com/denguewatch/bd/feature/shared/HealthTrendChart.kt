package com.denguewatch.bd.feature.shared

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.typo
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

// MARK: - A reader's own readings over time

private val PLOT_HEIGHT = 132.dp

/**
 * A line on the chart, and which way a reading crosses it to matter.
 *
 * Temperature counts when it climbs above the line; a platelet count counts
 * when it falls below one. The chart draws both the same way and only needs
 * telling which side is the worrying one — marking the top of a platelet range
 * would highlight exactly the readings nobody is worried about.
 */
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
