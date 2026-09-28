package com.denguewatch.bd.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.AppState
import com.denguewatch.bd.feature.shared.CardSection
import com.denguewatch.bd.feature.shared.HairlineDivider
import com.denguewatch.bd.feature.shared.LabeledFigure
import com.denguewatch.bd.feature.shared.MiniStat
import com.denguewatch.bd.feature.shared.PrimaryActionButton
import com.denguewatch.bd.feature.shared.RiskBadge
import com.denguewatch.bd.feature.shared.RiskMeter
import com.denguewatch.bd.feature.shared.SecondaryActionButton
import com.denguewatch.bd.feature.shared.TrendIndicator
import com.denguewatch.bd.feature.shared.SourceBadge
import com.denguewatch.bd.feature.shared.SourceKind
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.i18n.displayName
import com.denguewatch.bd.i18n.guidanceKey
import com.denguewatch.bd.i18n.labelKey
import com.denguewatch.bd.model.Area
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.ui.theme.Card
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Shapes
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.typo

/**
 * Tapping a bubble opens the area's headline figures, without leaving the map.
 *
 * Deliberately short: the numbers a reader wants at a glance, the two things
 * they might want to do, and a way through to the full page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreaRiskSheet(
    area: Area,
    state: AppState,
    onDismiss: () -> Unit,
    onOpenArea: (Area) -> Unit,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val preferences = state.preferences
    val isHome = preferences.homeAreaCode == area.code
    val lastUpdated = state.store.lastUpdated

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = colors.plane,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen)
                .padding(bottom = Space.section),
            verticalArrangement = Arrangement.spacedBy(Space.stack),
        ) {
            // iOS puts a Done button in the sheet's navigation bar. Material
            // sheets dismiss by drag, and the handle above says so, but the
            // explicit control is part of the design and costs one row.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(
                        loc.t("common.done"),
                        style = typo(Typo.HEADLINE),
                        color = colors.accent,
                    )
                }
            }

            // Header: where, how bad, and which way it is moving.
            Column(verticalArrangement = Arrangement.spacedBy(Space.tight)) {
                Text(area.displayName(loc.language), style = typo(Typo.TITLE))
                Text(
                    loc.t("area.division", area.division.displayName(loc.language)),
                    style = typo(Typo.SUBHEADLINE),
                    color = colors.secondaryText,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.tight),
                    modifier = Modifier.padding(top = Space.hair),
                ) {
                    RiskBadge(area.risk)
                    TrendIndicator(area.weeklyChange)
                }
                RiskMeter(area.risk, modifier = Modifier.padding(top = Space.hair))
            }

            // Three across: the week, the rate that drives the band, the season.
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Space.row),
            ) {
                MiniStat(
                    value = loc.num(area.lastWeekCases),
                    label = loc.t("map.area.recentCases"),
                    accent = colors.cases,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                MiniStat(
                    value = loc.decimal(area.incidencePer100k),
                    label = loc.t("map.area.rate"),
                    accent = colors.riskTint(area.risk),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                MiniStat(
                    value = loc.compact(area.seasonCases),
                    label = loc.t("map.area.seasonCases"),
                    accent = colors.cases,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }

            // Guidance, and where the figures came from. A plain card, not a
            // risk-tinted one: the band is already stated twice above, and a
            // third coloured surface makes the sheet louder than the reading.
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(Space.row)) {
                    Text(loc.t(area.risk.guidanceKey), style = typo(Typo.CALLOUT))
                    HairlineDivider()
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SourceBadge(SourceKind.OFFICIAL, detail = "DGHS")
                        Spacer(Modifier.weight(1f))
                        if (lastUpdated != null) {
                            Text(
                                loc.t("risk.card.updated", loc.relative(lastUpdated)),
                                style = typo(Typo.MICRO),
                                color = colors.secondaryText,
                            )
                        }
                    }
                }
            }

            // Primary first, as on iOS: reading the full page is the likelier
            // next step than adopting the area.
            Column(verticalArrangement = Arrangement.spacedBy(Space.row)) {
                PrimaryActionButton(
                    title = loc.t("map.area.viewDetail"),
                    icon = DwIcons.home,   // Insights: the line-chart glyph, as iOS uses here
                    onClick = { onOpenArea(area) },
                )
                SecondaryActionButton(
                    title = loc.t(if (isHome) "area.isHome" else "map.area.setHome"),
                    icon = if (isHome) DwIcons.check2 else DwIcons.myLocation,
                    tint = if (isHome) colors.downIsGood else colors.accent,
                    onClick = { preferences.homeAreaCode = if (isHome) null else area.code },
                )
            }
        }
    }
}

/**
 * Tapping the risk card explains how the band was derived — the thresholds, the
 * window, and what the reading means for the reader.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiskDetailSheet(
    risk: RiskLevel,
    areaName: String,
    incidence: Double,
    last14Cases: Int,
    lastUpdatedMillis: Long?,
    onDismiss: () -> Unit,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.plane,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen)
                .padding(bottom = Space.section),
            verticalArrangement = Arrangement.spacedBy(Space.stack),
        ) {
            Text(areaName, style = typo(Typo.TITLE))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(Shapes.card)
                    .background(colors.riskSoft(risk))
                    .padding(Space.card),
                verticalArrangement = Arrangement.spacedBy(Space.row),
            ) {
                RiskBadge(risk)
                Text(loc.t(risk.guidanceKey), style = typo(Typo.BODY))
            }

            CardSection(loc.t("risk.detail.how")) {
                LabeledFigure(loc.t("risk.detail.window"), loc.num(last14Cases))
                HairlineDivider()
                LabeledFigure(loc.t("common.per100k"), loc.decimal(incidence))
                HairlineDivider()
                LabeledFigure(
                    loc.t("about.lastUpdated"),
                    lastUpdatedMillis?.let { loc.fullDate(it) } ?: "—",
                )
                Text(
                    loc.t("map.list.footer"),
                    style = typo(Typo.MICRO),
                    color = colors.secondaryText,
                )
            }

            CardSection(loc.t("risk.detail.bands")) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.row)) {
                    RiskLevel.entries.forEach { level ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.row),
                        ) {
                            RiskBadge(level, compact = true)
                            Text(
                                loc.t("risk.detail.band.${level.ordinal}"),
                                style = typo(Typo.CAPTION),
                                color = colors.secondaryText,
                                modifier = Modifier.weight(1f),
                            )
                            if (level == risk) {
                                Icon(
                                    DwIcons.check2,
                                    contentDescription = null,
                                    tint = colors.riskInk(risk),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            SourceBadge(SourceKind.OFFICIAL, detail = "DGHS")
        }
    }
}
