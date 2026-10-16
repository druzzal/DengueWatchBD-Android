package com.denguewatch.bd.feature.dashboard

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.AppState
import com.denguewatch.bd.Tab
import com.denguewatch.bd.data.DengueStore
import com.denguewatch.bd.feature.location.LocationPermissionCard
import com.denguewatch.bd.data.FeedSync
import com.denguewatch.bd.feature.shared.AlertCard
import com.denguewatch.bd.feature.shared.ChartSkeleton
import com.denguewatch.bd.feature.shared.ErrorStateView
import com.denguewatch.bd.feature.shared.RiskCardSkeleton
import com.denguewatch.bd.feature.shared.ScreenTitle
import com.denguewatch.bd.feature.shared.SectionHeader
import com.denguewatch.bd.feature.shared.SkeletonBlock
import com.denguewatch.bd.feature.shared.SourceBadge
import com.denguewatch.bd.feature.shared.SourceKind
import com.denguewatch.bd.feature.shared.StatCard
import com.denguewatch.bd.feature.shared.StatGridSkeleton
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.i18n.Localization
import com.denguewatch.bd.i18n.displayName
import com.denguewatch.bd.model.Area
import com.denguewatch.bd.model.Series
import com.denguewatch.bd.model.StatGrid
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Shapes
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.typo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.denguewatch.bd.model.SurveillanceDigest
import com.denguewatch.bd.ui.theme.Card
import com.denguewatch.bd.feature.shared.SecondaryActionButton
import com.denguewatch.bd.feature.shared.HairlineDivider
import com.denguewatch.bd.ui.theme.windowSizeDp
import com.denguewatch.bd.ui.theme.Layout

/**
 * The home screen, ordered by how urgently a reader needs each thing: current
 * risk, then local activity, then the trend, then where it is spreading, then
 * where the figures came from.
 */
@Composable
fun DashboardScreen(
    state: AppState,
    onOpenArea: (Area) -> Unit,
    onOpenRiskDetail: (RiskLevel, String, Double, Int) -> Unit,
    modifier: Modifier = Modifier,
    onRequestLocation: () -> Unit = {},
    locationDenied: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val store = state.store
    val scope = rememberCoroutineScope()

    var trendRange by remember { mutableStateOf(TrendRange.FORTNIGHT) }

    val focusArea = state.focusArea
    val focusRisk = focusArea?.risk ?: store.nationalRisk
    val focusAreaName = focusArea?.displayName(loc.language) ?: loc.t("area.nationwide")
    val nationalIncidence = store.nationalIncidencePer100k

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.plane),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Space.section),
    ) {
        run {
            item { ScreenTitle(loc.t("dash.title"), Modifier.padding(horizontal = Space.screen)) }
        }

        when (val loadState = store.state) {
            is DengueStore.LoadState.Idle, is DengueStore.LoadState.Loading -> {
                item {
                    Column(
                        Modifier.padding(horizontal = Space.screen),
                        verticalArrangement = Arrangement.spacedBy(Space.section),
                    ) {
                        RiskCardSkeleton()
                        Column(verticalArrangement = Arrangement.spacedBy(Space.row)) {
                            SkeletonBlock(width = 140.dp, height = 15.dp)
                            StatGridSkeleton()
                        }
                        ChartSkeleton()
                    }
                }
            }

            is DengueStore.LoadState.Failed -> {
                if (store.areas.isEmpty()) {
                    item {
                        ErrorStateView(
                            title = loc.t("dash.error.title"),
                            message = loc.t("dash.error.message"),
                            onRetry = { scope.launch { store.reload() } },
                            modifier = Modifier.padding(horizontal = Space.screen),
                        )
                    }
                } else {
                    // Keep showing what we have. On a surveillance app a refresh
                    // failure is no reason to blank the screen — yesterday's
                    // figures still answer the question.
                    item {
                        AlertCard(
                            risk = RiskLevel.MODERATE,
                            title = loc.t("dash.error.title"),
                            message = loc.t("dash.stale.message"),
                            actionTitle = loc.t("common.tryAgain"),
                            onAction = { scope.launch { store.reload() } },
                            modifier = Modifier.padding(horizontal = Space.screen),
                        )
                    }
                    loadedContent(
                        state, loc, focusArea, focusRisk, focusAreaName, nationalIncidence,
                        trendRange, { trendRange = it }, onOpenArea, onOpenRiskDetail, scope,
                        onRequestLocation, locationDenied,
                    )
                }
            }

            is DengueStore.LoadState.Loaded -> loadedContent(
                state, loc, focusArea, focusRisk, focusAreaName, nationalIncidence,
                trendRange, { trendRange = it }, onOpenArea, onOpenRiskDetail, scope,
                onRequestLocation, locationDenied,
            )
        }

        item { Spacer(Modifier.height(Space.section)) }
    }
}

private fun LazyListScope.loadedContent(
    state: AppState,
    loc: Localization,
    focusArea: Area?,
    focusRisk: RiskLevel,
    focusAreaName: String,
    nationalIncidence: Double,
    trendRange: TrendRange,
    onTrendRangeChange: (TrendRange) -> Unit,
    onOpenArea: (Area) -> Unit,
    onOpenRiskDetail: (RiskLevel, String, Double, Int) -> Unit,
    scope: CoroutineScope,
    onRequestLocation: () -> Unit,
    locationDenied: Boolean,
) {
    val store = state.store

    // MARK: Risk
    item {
        Column(
            Modifier.padding(horizontal = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.row),
        ) {
            DengueRiskCard(
                risk = focusRisk,
                areaName = focusAreaName,
                // An area with no prior week has no trend; it must not borrow
                // the national one under its own name.
                change = focusArea.let { if (it != null) it.weeklyChange else store.weeklyCaseChange },
                lastUpdatedMillis = store.lastUpdated,
                incidence = focusArea?.incidencePer100k ?: nationalIncidence,
                isCurrentLocation = state.focusIsCurrentLocation,
                coverage = focusArea?.takeIf { state.focusIsCurrentLocation }?.let {
                    loc.t("risk.card.covers", loc.compact(it.populationThousands * 1_000))
                },
                onClick = {
                    onOpenRiskDetail(
                        focusRisk,
                        focusAreaName,
                        focusArea?.incidencePer100k ?: nationalIncidence,
                        focusArea?.recentCases ?: store.nationalRecentCases,
                    )
                },
            )

            if (focusArea == null && !state.location.isAuthorized) {
                // The reading is national, which is much less useful than a
                // local one. Say what location is for before Android asks, and
                // always leave the manual route open — as iOS does.
                LocationPermissionCard(
                    denied = locationDenied,
                    onAllow = onRequestLocation,
                    onChooseManually = { state.show(Tab.MAP) },
                )
            } else if (focusArea == null) {
                // Allowed, but no fix yet or the reader is outside Bangladesh.
                // The picker is the only route left.
                ChooseAreaHint(onClick = { state.show(Tab.MAP) })
            }

            if (store.isStale) {
                val days = store.dataAgeInDays
                if (days != null) {
                    val outdated = store.freshness == DengueStore.DataFreshness.OUTDATED
                    // The age of the data, plus — when the last check actually
                    // failed — the fact that it did. "Days old" alone would imply
                    // DGHS simply published nothing, which is a different
                    // situation from the app being unable to ask.
                    val message = buildString {
                        append(
                            loc.t(
                                if (outdated) "outdated.message" else "stale.message",
                                loc.num(days),
                            )
                        )
                        // Offline too: with no connection the check cannot be
                        // made at all, which is the same news for the reader.
                        if (state.sync.status is FeedSync.Status.Failed ||
                            state.sync.status is FeedSync.Status.Offline
                        ) {
                            append(" ")
                            append(loc.t("stale.checkFailed"))
                        }
                    }
                    // Outdated reads louder than stale.
                    AlertCard(
                        risk = if (outdated) RiskLevel.HIGH else RiskLevel.MODERATE,
                        title = loc.t(if (outdated) "outdated.title" else "stale.title"),
                        message = message,
                        actionTitle = loc.t("common.tryAgain"),
                        onAction = { scope.launch { state.refreshNow() } },
                    )
                }
            }

            // Only one advisory at a time, and staleness wins: a "cases are
            // rising" claim derived from figures the app has just called out as
            // days old would be asserting a trend it cannot stand behind.
            val change = store.weeklyCaseChange
            if (!store.isStale && change != null && change >= 0.15) {
                AlertCard(
                    risk = RiskLevel.MODERATE,
                    title = loc.t("alert.rising.title"),
                    message = loc.t("alert.rising.message", loc.percentChange(change)),
                    actionTitle = loc.t("alert.viewMap"),
                    onAction = { state.show(Tab.MAP) },
                )
            }
        }
    }

    // MARK: Today in Bangladesh
    //
    // The national snapshot, led by the freshest figure DGHS publishes rather
    // than by the season total, as on iOS. Every tile can be absent, and one
    // with nothing behind it is left out rather than shown as zero — see
    // NationalSnapshot for why that distinction is not pedantry.
    item {
        Column(
            Modifier.padding(horizontal = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.row),
        ) {
            // "Today" only when the figures are actually from today. Ten-day-old
            // numbers under a "Today" heading would be the app misreporting DGHS.
            SectionHeader(
                title = loc.t(if (store.isStale) "activity.title.stale" else "activity.title"),
                subtitle = store.lastUpdated?.let { loc.t("activity.reported", loc.reportedDate(it)) },
            ) { SourceBadge(SourceKind.OFFICIAL, detail = "DGHS") }

            val colors = Dw.colors
            val snapshot = store.snapshot
            val since = snapshot.seasonStartMillis?.let { loc.t("activity.since", loc.dayMonth(it)) }
            val tiles = buildList<@Composable (Modifier) -> Unit> {
                snapshot.last24Cases?.let { cases ->
                    add { tileModifier ->
                        StatCard(
                            label = loc.t("dash.stat.last24"),
                            value = loc.num(cases),
                            // Omitted rather than "0 deaths" when the figure
                            // never arrived: that reads as good news.
                            caption = snapshot.last24Deaths
                                ?.let { loc.t("dash.stat.last24Deaths", loc.num(it)) },
                            accent = colors.admitted,
                            series = store.nationalRecent(14).map { it.cases.toDouble() },
                            modifier = tileModifier,
                        )
                    }
                }
                snapshot.weekCases?.let { week ->
                    add { tileModifier ->
                        StatCard(
                            label = loc.t("activity.thisWeek"),
                            value = loc.num(week),
                            change = snapshot.weeklyChange,
                            caption = snapshot.weeklyChange?.let { loc.t("dash.stat.vsLastWeek") },
                            accent = colors.cases,
                            series = store.nationalRecent(14).map { it.cases.toDouble() },
                            modifier = tileModifier,
                        )
                    }
                }
                snapshot.seasonCases?.let { season ->
                    add { tileModifier ->
                        StatCard(
                            label = loc.t("dash.stat.cases"),
                            value = loc.num(season),
                            caption = since,
                            accent = colors.cases,
                            series = store.nationalRecent(30).map { it.cases.toDouble() },
                            modifier = tileModifier,
                        )
                    }
                }
                snapshot.seasonDeaths?.let { deaths ->
                    // Beside the season case count, which is the figure it
                    // belongs next to.
                    add { tileModifier ->
                        StatCard(
                            label = loc.t("dash.stat.seasonDeaths"),
                            value = loc.num(deaths),
                            caption = since,
                            accent = colors.deaths,
                            // The seven-day average over the last month. Raw
                            // daily deaths are single digits and would be noise
                            // at this size, not a trend.
                            series = Series.movingAverage(store.national.map { it.deaths }, 7)
                                .takeLast(30),
                            modifier = tileModifier,
                        )
                    }
                }
                snapshot.highRiskAreas?.let { hotspots ->
                    add { tileModifier ->
                        StatCard(
                            label = loc.t("activity.hotspots"),
                            value = loc.num(hotspots),
                            // The feed's reporting areas, not the country's 64
                            // districts. The app has no district breakdown, so
                            // it cannot use a district denominator.
                            caption = loc.t("activity.hotspotsCaption", loc.num(snapshot.reportingAreas)),
                            accent = colors.deaths,
                            modifier = tileModifier,
                        )
                    }
                }
            }

            // One column once text is large enough that two would crush the
            // figures; four across on a tablet, where two leaves each card
            // mostly empty.
            val columns = if (LocalDensity.current.fontScale >= 1.5f) {
                1
            } else {
                StatGrid.columns(tiles.size, wide = windowSizeDp().width >= Layout.regularWidthBreakpoint)
            }
            tiles.chunked(columns).forEach { row ->
                // IntrinsicSize.Min plus fillMaxHeight on each card, so cards in
                // a row share the tallest one's height. Without it a card whose
                // caption wraps to two lines — which Bengali does, where English
                // fits on one — leaves its neighbour ending short, and the row
                // reads as broken rather than as a set.
                Row(
                    Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(Space.row),
                ) {
                    row.forEach { tile -> tile(Modifier.weight(1f).fillMaxHeight()) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }

    // MARK: What changed
    //
    // Statements about what moved since the last report. The section disappears
    // entirely when the digest has nothing it can support — an empty card
    // saying "no changes" would be a claim of its own.
    val digest = SurveillanceDigest.items(
        nationalChange = store.weeklyCaseChange,
        hotspotCount = store.hotspots.size,
        hasAreaBreakdown = store.areas.isNotEmpty(),
        risingAreas = store.areasByRisk
            .mapNotNull { area ->
                area.weeklyChange?.takeIf { it > 0 }?.let { area.displayName(loc.language) to it }
            }
            .sortedByDescending { it.second },
        formatNumber = loc::num,
    )
    if (digest.isNotEmpty()) {
        item {
            Column(
                Modifier.padding(horizontal = Space.screen),
                verticalArrangement = Arrangement.spacedBy(Space.row),
            ) {
                SectionHeader(
                    title = loc.t("digest.title"),
                    subtitle = loc.t("digest.subtitle"),
                )
                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.tight)) {
                        digest.forEach { statement ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Space.tight),
                                verticalAlignment = Alignment.Top,
                            ) {
                                // Icon and colour carry the same meaning as the
                                // words, so the section still reads without
                                // colour vision and without reading every
                                // sentence.
                                Icon(
                                    statement.kind.icon,
                                    contentDescription = null,
                                    tint = statement.kind.tint(),
                                    modifier = Modifier.size(16.dp).padding(top = 2.dp),
                                )
                                Text(
                                    loc.t(statement.key, *statement.arguments.toTypedArray()),
                                    style = typo(Typo.CALLOUT),
                                )
                            }
                        }
                        SecondaryActionButton(
                            title = loc.t("digest.action"),
                            icon = DwIcons.map,
                            onClick = { state.show(Tab.MAP) },
                            modifier = Modifier.padding(top = Space.hair),
                        )
                    }
                }
            }
        }
    }

    // MARK: Top areas
    //
    // The areas carrying the most dengue per head, ranked.
    //
    // Ordered by `areasByRisk`, which sorts on the 14-day rate rather than raw
    // case counts. Ranking by counts would put the biggest cities on top every
    // week regardless of how bad things actually are there — which is the
    // question this list exists to answer.
    val ranked = store.areasByRisk.take(5)
    if (ranked.isNotEmpty()) {
        item {
            Column(
                Modifier.padding(horizontal = Space.screen),
                verticalArrangement = Arrangement.spacedBy(Space.row),
            ) {
                SectionHeader(title = loc.t("top.title"), subtitle = loc.t("top.subtitle"))
                Card {
                    Column {
                        ranked.forEachIndexed { index, area ->
                            TopAreaRow(
                                rank = index + 1,
                                area = area,
                                onClick = { onOpenArea(area) },
                            )
                            if (area.code != ranked.last().code) {
                                HairlineDivider(Modifier.padding(start = 44.dp))
                            }
                        }
                        SecondaryActionButton(
                            title = loc.t("top.viewMap"),
                            icon = DwIcons.map,
                            onClick = { state.show(Tab.MAP) },
                            modifier = Modifier.padding(top = Space.tight),
                        )
                    }
                }
            }
        }
    }

    // MARK: Trend
    item {
        Column(
            Modifier.padding(horizontal = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.row),
        ) {
            SectionHeader(loc.t("trend.section"))
            TrendCard(
                points = store.national,
                range = trendRange,
                onRangeChange = onTrendRangeChange,
            )
            DeathsCard(store.national)
            if (store.ageBandsCases.isNotEmpty()) {
                WhoIsAffectedCard(store.ageBandsCases, store.sexSplitCases)
            }
            if (store.history.isNotEmpty()) {
                SeasonComparisonCard(
                    history = store.history,
                    currentYear = store.meta?.year ?: store.history.lastOrNull()?.year ?: 0,
                )
            }
        }
    }

    // MARK: Map
    item {
        Column(
            Modifier.padding(horizontal = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.row),
        ) {
            SectionHeader(loc.t("map.section")) {
                Text(
                    loc.t("common.viewAll"),
                    style = typo(Typo.CAPTION),
                    color = Dw.colors.accent,
                    modifier = Modifier
                        .clip(Shapes.chip)
                        .clickable(role = Role.Button) { state.show(Tab.MAP) }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            MapPreviewCard(
                areas = store.areasByRisk,
                userLocation = state.location.lastKnownLocation,
                onClick = { state.show(Tab.MAP) },
            )
        }
    }

    // MARK: Provenance
    item {
        Row(Modifier.padding(horizontal = Space.screen)) {
            SyncStatusRow(state.sync)
        }
    }
}
