package com.denguewatch.bd.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.feature.shared.BarAverageChart
import com.denguewatch.bd.feature.shared.BarPoint
import com.denguewatch.bd.feature.shared.CardSection
import com.denguewatch.bd.feature.shared.ShrinkToFitText
import com.denguewatch.bd.feature.shared.ChartLegend
import com.denguewatch.bd.feature.shared.LegendItem
import com.denguewatch.bd.feature.shared.PairedBar
import com.denguewatch.bd.feature.shared.axisTicks
import com.denguewatch.bd.feature.shared.pickLabels
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.model.AgeBand
import com.denguewatch.bd.model.AgeBands
import com.denguewatch.bd.model.DailyPoint
import com.denguewatch.bd.model.Series
import com.denguewatch.bd.model.SexSplit
import com.denguewatch.bd.model.YearSummary
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.tabularFigures
import com.denguewatch.bd.ui.theme.typo

/**
 * Deaths get their own card and their own axis. Plotting them against cases on
 * a second y-scale would invent a relationship the data does not show.
 */
@Composable
fun DeathsCard(points: List<DailyPoint>, modifier: Modifier = Modifier) {
    val loc = LocalLocalization.current
    val colors = Dw.colors

    val average = remember(points) {
        Series.movingAverage(points.map { it.deaths }, window = 7)
    }

    CardSection(
        title = loc.t("dash.deaths.title"),
        subtitle = loc.t("dash.deaths.subtitle"),
        modifier = modifier,
    ) {
        ChartLegend(
            listOf(
                LegendItem(loc.t("dash.legend.daily"), colors.deaths.copy(alpha = 0.35f)),
                LegendItem(loc.t("dash.legend.average"), colors.deaths, isLine = true),
            )
        )

        BarAverageChart(
            bars = points.map { BarPoint(it.deaths, loc.dayMonth(it.dateMillis)) },
            average = average,
            barColor = colors.deaths.copy(alpha = 0.35f),
            lineColor = colors.deaths,
            height = 120.dp,
            yAxisTicks = axisTicks(
                maxOf(
                    points.maxOfOrNull { it.deaths }?.toDouble() ?: 1.0,
                    average.maxOrNull() ?: 1.0,
                )
            ),
            formatTick = { loc.compact(it) },
            xAxisLabels = pickLabels(points.map { loc.dayMonth(it.dateMillis) }, 3),
        )
    }
}

/**
 * Season-on-season totals. Years are an ordered category, so one hue is enough:
 * a second colour would read as a second series.
 */
@Composable
fun SeasonComparisonCard(
    history: List<YearSummary>,
    currentYear: Int,
    modifier: Modifier = Modifier,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val maxCases = remember(history) { maxOf(history.maxOfOrNull { it.cases } ?: 1, 1) }

    CardSection(
        title = loc.t("dash.history.title"),
        subtitle = loc.t("dash.history.subtitle"),
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            history.reversed().forEach { year ->
                val isCurrent = year.year == currentYear
                // Years are written without a thousands separator — "2026", not
                // "2,026".
                val yearLabel = loc.num(year.year).replace(",", "")
                val summary = loc.t(
                    "dash.history.a11y",
                    yearLabel,
                    loc.num(year.cases),
                    year.deaths?.let { loc.num(it) } ?: loc.t("common.unknown"),
                )

                Row(
                    Modifier.fillMaxWidth().semantics { contentDescription = summary },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        yearLabel,
                        style = typo(
                            Typo.CAPTION,
                            if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        ).tabularFigures(),
                        color = if (isCurrent) colors.primaryText else colors.secondaryText,
                        modifier = Modifier.width(42.dp),
                    )

                    Box(Modifier.weight(1f).height(18.dp), contentAlignment = Alignment.CenterStart) {
                        ProportionalBar(
                            fraction = year.cases.toFloat() / maxCases,
                            color = colors.cases,
                        )
                    }

                    // Wide enough for a Bengali compact figure, and shrinking
                    // rather than wrapping if one still overflows.
                    //
                    // "1.4k" fits 56dp; "১.৪ হাজার" does not, and wrapped to a
                    // second line — which left the year label and its bar
                    // centred against a two-line value while every other row
                    // was one line, so that row sat visibly lower than the rest.
                    ShrinkToFitText(
                        loc.compact(year.cases),
                        style = typo(Typo.CAPTION, FontWeight.Medium)
                            .tabularFigures()
                            .copy(textAlign = TextAlign.End),
                        modifier = Modifier.width(78.dp),
                        minScale = 0.6f,
                    )

                    Text(
                        year.deaths?.let { loc.compact(it) } ?: "—",
                        style = typo(Typo.CAPTION).tabularFigures(),
                        color = colors.deaths,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(46.dp),
                    )
                }
            }
        }

        Text(
            loc.t("dash.history.footnote"),
            style = typo(Typo.MICRO),
            color = colors.secondaryText,
        )
    }
}

/**
 * Who is catching dengue this season — age bands, split by sex.
 *
 * Drawn as a paired bar per band rather than a population pyramid. A pyramid
 * reads well at a glance but needs a signed axis, and at phone width the
 * negative half is routinely misread as a decline.
 */
@Composable
fun WhoIsAffectedCard(
    bands: List<AgeBand>,
    split: SexSplit?,
    modifier: Modifier = Modifier,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val peak = remember(bands) { AgeBands.sidePeak(bands) }

    CardSection(
        title = loc.t("who.title"),
        subtitle = loc.t("who.subtitle"),
        modifier = modifier,
    ) {
        if (split != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.tight),
            ) {
                SexShare(
                    label = loc.t("who.male"),
                    value = split.male,
                    share = split.maleShare,
                    color = colors.cases,
                    modifier = Modifier.weight(1f),
                )
                // Flush right, mirroring the male figure over the left half,
                // so each heading sits over the side of the pyramid it counts.
                SexShare(
                    label = loc.t("who.female"),
                    value = split.female,
                    share = 1 - split.maleShare,
                    color = colors.deaths,
                    modifier = Modifier.weight(1f),
                    alignEnd = true,
                )
            }
        }

        // The third entry appears only when the source actually carries cases
        // of unrecorded sex, so a clean table is not given a legend key for
        // something it does not contain.
        ChartLegend(
            listOf(
                LegendItem(loc.t("who.male"), colors.cases),
                LegendItem(loc.t("who.female"), colors.deaths),
            ) + if (bands.any { it.unrecordedSex > 0 }) {
                listOf(LegendItem(loc.t("who.unrecorded"), colors.secondaryText))
            } else {
                emptyList()
            }
        )

        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            bands.forEach { band ->
                // The feed's Western numerals, put into the reader's digits.
                val bandLabel = AgeBands.displayLabel(band.label) { loc.num(it) }
                val summary = loc.t(
                    "who.a11y", bandLabel, loc.num(band.male), loc.num(band.female),
                ) + if (band.unrecordedSex > 0) {
                    " " + loc.t("who.a11y.unrecorded", loc.num(band.unrecordedSex))
                } else {
                    ""
                }
                Row(
                    Modifier.fillMaxWidth().semantics { contentDescription = summary },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.tight),
                ) {
                    Text(
                        bandLabel,
                        style = typo(Typo.MICRO).tabularFigures(),
                        color = colors.secondaryText,
                        modifier = Modifier.width(BAND_SIDE_WIDTH),
                    )

                    PairedBar(
                        left = band.male,
                        right = band.female,
                        max = peak,
                        leftColor = colors.cases,
                        rightColor = colors.deaths,
                        modifier = Modifier.weight(1f).height(13.dp),
                        unrecorded = band.unrecordedSex,
                        unrecordedColor = colors.secondaryText,
                    )

                    // One line, in both languages.
                    //
                    // 46dp fits "4.6k" and nothing like "৪.৬ হাজার", so every
                    // row of the pyramid wrapped in Bangla — the figure sat on
                    // two lines beside a one-line bar, which pushed each row
                    // apart and broke the comb of the chart. Wide enough for
                    // the Bengali form, and shrinking rather than wrapping if
                    // one still overflows.
                    ShrinkToFitText(
                        loc.compact(band.total),
                        style = typo(Typo.MICRO)
                            .tabularFigures()
                            .copy(textAlign = TextAlign.End),
                        modifier = Modifier.width(BAND_SIDE_WIDTH),
                        minScale = 0.6f,
                    )
                }
            }
        }
    }
}

/**
 * The gutter on each side of the age-band bars.
 *
 * Both sides get the same width, and that is the whole point. PairedBar draws
 * from a centre spine, male growing left and female right, so the chart reads
 * as a pyramid only while that spine is the middle of the card. The label gutter
 * was 44dp against the total's 74dp, which pushed the spine 15dp to the left of
 * centre: every row leaned, and the eye reads a lean as more men than women
 * before it reads a single bar.
 *
 * Sized for the total, which is the demanding side — "৪.৬ হাজার" needs the room
 * that "4.6k" does not. The label is flush left in its gutter and the total
 * flush right, so neither shows the extra width; only the spine moves.
 */
private val BAND_SIDE_WIDTH = 74.dp

@Composable
private fun SexShare(
    label: String,
    value: Int,
    share: Double,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Text(label, style = typo(Typo.MICRO), color = colors.secondaryText)
        Text(
            loc.t("who.share", loc.decimal(share * 100, places = 0)),
            style = typo(Typo.STAT_VALUE).tabularFigures(),
            color = color,
        )
        Text(loc.compact(value), style = typo(Typo.MICRO), color = colors.secondaryText)
    }
}

/**
 * A bar filling a fraction of whatever width it is given.
 *
 * Measured rather than weighted: a `weight` on a zero-length bar collapses the
 * row, and a year with almost no cases still needs to leave a visible mark
 * rather than disappearing.
 */
@Composable
private fun ProportionalBar(
    fraction: Float,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 16.dp,
) {
    Layout(
        content = {
            Box(
                Modifier
                    .height(height)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        },
        modifier = modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val width = (constraints.maxWidth * fraction).toInt().coerceAtLeast(3)
        val placeable = measurables.first().measure(
            constraints.copy(minWidth = width, maxWidth = width)
        )
        layout(constraints.maxWidth, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }
}
