package com.denguewatch.bd.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.AppState
import com.denguewatch.bd.feature.shared.BarAverageChart
import com.denguewatch.bd.feature.shared.BarPoint
import com.denguewatch.bd.feature.shared.CardSection
import com.denguewatch.bd.feature.shared.ChartLegend
import com.denguewatch.bd.feature.shared.HairlineDivider
import com.denguewatch.bd.feature.shared.InlineNote
import com.denguewatch.bd.feature.shared.LegendItem
import com.denguewatch.bd.feature.shared.RiskBadge
import com.denguewatch.bd.feature.shared.SecondaryActionButton
import com.denguewatch.bd.feature.shared.StatCard
import com.denguewatch.bd.feature.shared.axisTicks
import com.denguewatch.bd.feature.shared.pickLabels
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.i18n.displayName
import com.denguewatch.bd.i18n.guidanceKey
import com.denguewatch.bd.i18n.labelKey
import com.denguewatch.bd.model.Area
import com.denguewatch.bd.model.Series
import com.denguewatch.bd.ui.theme.Card
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.typo

/**
 * Everything the feed carries about one reporting area.
 *
 * DGHS publishes area figures by epidemiological week, not by day, so this
 * chart is weekly where the national one is daily.
 */
@Composable
fun AreaDetailScreen(
    area: Area,
    state: AppState,
    onOpenArea: (Area) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val store = state.store
    val preferences = state.preferences
    val isHome = preferences.homeAreaCode == area.code

    /** Three-week trailing mean, to steady the week-to-week reporting jitter. */
    val average = remember(area) { Series.movingAverage(area.weeklyCases, window = 3) }

    val shareOfNational = if (store.seasonCases > 0) {
        area.seasonCases.toDouble() / store.seasonCases * 100
    } else 0.0

    val rank = store.areasByCases.indexOfFirst { it.code == area.code }
        .takeIf { it >= 0 }?.plus(1)

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.plane),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Space.stack),
    ) {
        item {
            Card(Modifier.padding(horizontal = Space.card)) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.row)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                loc.t("area.division", area.division.displayName(loc.language)),
                                style = typo(Typo.SUBHEADLINE, FontWeight.SemiBold),
                            )
                            Text(
                                loc.t(
                                    "area.population",
                                    loc.compact(area.populationThousands * 1000),
                                ),
                                style = typo(Typo.CAPTION),
                                color = colors.secondaryText,
                            )
                        }
                        RiskBadge(area.risk)
                    }

                    Text(
                        loc.t(area.risk.guidanceKey),
                        style = typo(Typo.CAPTION),
                        color = colors.secondaryText,
                    )

                    SecondaryActionButton(
                        title = loc.t(if (isHome) "area.isHome" else "area.setHome"),
                        icon = if (isHome) DwIcons.check2 else DwIcons.myLocation,
                        tint = if (isHome) colors.downIsGood else colors.accent,
                        onClick = {
                            preferences.homeAreaCode = if (isHome) null else area.code
                        },
                    )
                }
            }
        }

        item {
            Column(
                Modifier.padding(horizontal = Space.card),
                verticalArrangement = Arrangement.spacedBy(Space.row),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.row)) {
                    StatCard(
                        label = loc.t("area.stat.season"),
                        value = loc.compact(area.seasonCases),
                        change = area.weeklyChange,
                        caption = loc.t("dash.stat.vsLastWeek"),
                        accent = colors.cases,
                        series = area.recentWeeks(12).map { it.toDouble() },
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = loc.t("area.stat.last7"),
                        value = loc.num(area.lastWeekCases),
                        caption = rank?.let { loc.t("area.rank", loc.num(it)) },
                        accent = colors.cases,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.row)) {
                    StatCard(
                        label = loc.t("area.stat.rate"),
                        value = loc.decimal(area.incidencePer100k),
                        unit = loc.t("common.per100k"),
                        caption = loc.t("area.riskBand", loc.t(area.risk.labelKey)),
                        accent = colors.deaths,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = loc.t("area.stat.deaths"),
                        value = loc.num(area.seasonDeaths),
                        caption = loc.t("area.share", loc.decimal(shareOfNational)),
                        accent = colors.deaths,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            CardSection(
                title = loc.t("area.chart.title", area.displayName(loc.language)),
                subtitle = loc.t("area.chart.subtitle"),
                modifier = Modifier.padding(horizontal = Space.card),
            ) {
                ChartLegend(
                    listOf(
                        LegendItem(loc.t("dash.legend.daily"), colors.casesMuted),
                        LegendItem(loc.t("dash.legend.average"), colors.cases, isLine = true),
                    )
                )
                BarAverageChart(
                    bars = area.weeklyCases.mapIndexed { index, cases ->
                        BarPoint(cases, store.epiWeeks.getOrNull(index) ?: "")
                    },
                    average = average,
                    barColor = colors.casesMuted,
                    lineColor = colors.cases,
                    height = 170.dp,
                    yAxisLabels = axisTicks(
                        maxOf(
                            area.weeklyCases.maxOrNull()?.toDouble() ?: 1.0,
                            average.maxOrNull() ?: 1.0,
                        )
                    ).map { loc.compact(it) },
                    xAxisLabels = pickLabels(store.epiWeeks, 4),
                )
            }
        }

        item {
            val peers = store.areas.filter { it.division == area.division }
            val peak = maxOf(peers.maxOfOrNull { it.seasonCases } ?: 1, 1)
            CardSection(
                title = loc.t("area.peers.title", area.division.displayName(loc.language)),
                subtitle = loc.t("area.peers.subtitle"),
                modifier = Modifier.padding(horizontal = Space.card),
            ) {
                Column {
                    peers.forEachIndexed { index, peer ->
                        AreaRow(
                            area = peer,
                            peak = peak,
                            onClick = if (peer.code == area.code) null else {
                                { onOpenArea(peer) }
                            },
                        )
                        if (index < peers.size - 1) HairlineDivider()
                    }
                }
            }
        }

        if (area.weeklyIsApportioned) {
            item {
                InlineNote(
                    detail = loc.t("area.apportioned.note"),
                    modifier = Modifier.padding(horizontal = Space.card),
                )
            }
        }

        item { Spacer(Modifier.height(Space.section)) }
    }
}
