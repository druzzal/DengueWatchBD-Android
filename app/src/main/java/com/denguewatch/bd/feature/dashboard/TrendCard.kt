package com.denguewatch.bd.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.feature.shared.BarAverageChart
import com.denguewatch.bd.feature.shared.BarPoint
import com.denguewatch.bd.feature.shared.ChartLegend
import com.denguewatch.bd.feature.shared.LegendItem
import com.denguewatch.bd.feature.shared.SegmentedPicker
import com.denguewatch.bd.feature.shared.TrendIndicator
import com.denguewatch.bd.feature.shared.axisTicks
import com.denguewatch.bd.feature.shared.pickLabels
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.model.DailyPoint
import com.denguewatch.bd.model.Series
import com.denguewatch.bd.ui.theme.Card
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.tabularFigures
import com.denguewatch.bd.ui.theme.typo

/**
 * The windows offered on the home trend chart. Short by design — the home
 * screen answers "what is happening now", and the long season view lives on the
 * area detail screen.
 */
enum class TrendRange(val days: Int) {
    WEEK(7),
    FORTNIGHT(14),
    MONTH(30);

    val labelKey: String get() = "trend.range.${name.lowercase()}"
}

/**
 * A compact epidemiological curve: daily reports as bars, a smoothed average
 * over the top, and a scrubbable read-out.
 *
 * One measure, one axis. Deaths have their own card rather than a second scale
 * here.
 */
@Composable
fun TrendCard(
    points: List<DailyPoint>,
    range: TrendRange,
    onRangeChange: (TrendRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    var selectedIndex by remember(range) { mutableStateOf<Int?>(null) }

    val series = remember(points, range) { points.takeLast(range.days) }

    // Smoothed over the whole history, so the left edge of a 7-day window is a
    // real average rather than a ramp from zero.
    val average = remember(points, range) {
        Series.movingAverage(points.map { it.cases }, window = 7).takeLast(series.size)
    }

    val windowTotal = remember(series) { series.sumOf { it.cases } }
    val windowChange = remember(points, range) {
        val previous = points.dropLast(range.days).takeLast(range.days).sumOf { it.cases }
        Series.change(previous, windowTotal)
    }

    val selected = selectedIndex?.let { series.getOrNull(it) }

    Card(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.row)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(Space.tight),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(loc.t("trend.title"), style = typo(Typo.HEADLINE))
                    Text(
                        loc.t("trend.subtitle"),
                        style = typo(Typo.CAPTION),
                        color = colors.secondaryText,
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    if (selected != null) {
                        Text(
                            loc.num(selected.cases),
                            style = typo(Typo.HEADLINE).tabularFigures(),
                        )
                        Text(
                            loc.dayMonth(selected.dateMillis),
                            style = typo(Typo.MICRO),
                            color = colors.secondaryText,
                        )
                    } else {
                        Text(
                            loc.num(windowTotal),
                            style = typo(Typo.HEADLINE).tabularFigures(),
                        )
                        Text(
                            loc.t("trend.windowTotal", loc.t(range.labelKey)),
                            style = typo(Typo.MICRO),
                            color = colors.secondaryText,
                            textAlign = TextAlign.End,
                        )
                        TrendIndicator(windowChange)
                    }
                }
            }

            SegmentedPicker(
                options = TrendRange.entries,
                selected = range,
                label = { loc.t(it.labelKey) },
                onSelect = {
                    selectedIndex = null
                    onRangeChange(it)
                },
            )

            BarAverageChart(
                bars = series.map { BarPoint(it.cases, loc.dayMonth(it.dateMillis)) },
                average = average,
                barColor = colors.casesMuted,
                lineColor = colors.cases,
                selectedIndex = selectedIndex,
                onSelect = { selectedIndex = it },
                yAxisTicks = axisTicks(
                    maxOf(
                        series.maxOfOrNull { it.cases }?.toDouble() ?: 1.0,
                        average.maxOrNull() ?: 1.0,
                    )
                ),
                formatTick = { loc.compact(it) },
                xAxisLabels = pickLabels(
                    series.map { loc.dayMonth(it.dateMillis) },
                    if (range == TrendRange.MONTH) 4 else 3,
                ),
            )

            ChartLegend(
                listOf(
                    LegendItem(loc.t("dash.legend.daily"), colors.casesMuted),
                    LegendItem(loc.t("dash.legend.average"), colors.cases, isLine = true),
                )
            )
        }
    }
}
