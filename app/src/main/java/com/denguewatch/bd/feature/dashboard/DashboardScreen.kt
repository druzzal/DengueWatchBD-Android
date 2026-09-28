package com.denguewatch.bd.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.AppState
import com.denguewatch.bd.Tab
import com.denguewatch.bd.data.DengueStore
import com.denguewatch.bd.data.FeedSync
import com.denguewatch.bd.feature.map.MapLibreRiskMap
import com.denguewatch.bd.feature.map.MapMetric
import com.denguewatch.bd.feature.shared.AlertCard
import com.denguewatch.bd.feature.shared.ChartSkeleton
import com.denguewatch.bd.feature.shared.ErrorStateView
import com.denguewatch.bd.feature.shared.RiskCardSkeleton
import com.denguewatch.bd.feature.shared.RiskScaleLegend
import com.denguewatch.bd.feature.shared.CircleChipButton
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
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Shapes
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.cardSurface
import com.denguewatch.bd.ui.theme.pressable
import com.denguewatch.bd.ui.theme.rememberPressSource
import com.denguewatch.bd.ui.theme.typo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
                    )
                }
            }

            is DengueStore.LoadState.Loaded -> loadedContent(
                state, loc, focusArea, focusRisk, focusAreaName, nationalIncidence,
                trendRange, { trendRange = it }, onOpenArea, onOpenRiskDetail, scope,
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
                change = focusArea?.weeklyChange ?: store.weeklyCaseChange,
                lastUpdatedMillis = store.lastUpdated,
                incidence = focusArea?.incidencePer100k ?: nationalIncidence,
                onClick = {
                    onOpenRiskDetail(
                        focusRisk,
                        focusAreaName,
                        focusArea?.incidencePer100k ?: nationalIncidence,
                        focusArea?.recentCases ?: store.nationalRecentCases,
                    )
                },
            )

            if (focusArea == null) {
                // Without an area the reading is national, which is much less
                // useful than a local one. Offer the fix rather than silently
                // showing a country-wide number.
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
                        if (state.sync.status is FeedSync.Status.Failed) {
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

    // MARK: Activity
    item {
        Column(
            Modifier.padding(horizontal = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.row),
        ) {
            SectionHeader(
                title = loc.t("activity.title"),
                subtitle = loc.t("activity.subtitle"),
            ) { SourceBadge(SourceKind.OFFICIAL, detail = "DGHS") }

            val colors = Dw.colors
            // IntrinsicSize.Min plus fillMaxHeight on each card, so both cards
            // in a row share the taller one's height. Without it a card whose
            // caption wraps to two lines — which Bengali does, where English
            // fits on one — leaves its neighbour ending short, and the row reads
            // as broken rather than as two cards.
            Row(
                Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Space.row),
            ) {
                StatCard(
                    label = loc.t("dash.stat.cases"),
                    value = loc.num(store.seasonCases),
                    caption = store.national.firstOrNull()?.let {
                        loc.t("activity.since", loc.dayMonth(it.dateMillis))
                    },
                    accent = colors.cases,
                    series = store.nationalRecent(30).map { it.cases.toDouble() },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                StatCard(
                    label = loc.t("activity.thisWeek"),
                    value = loc.num(store.last7Cases),
                    change = store.weeklyCaseChange,
                    caption = loc.t("dash.stat.vsLastWeek"),
                    accent = colors.cases,
                    series = store.nationalRecent(14).map { it.cases.toDouble() },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            Row(
                Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Space.row),
            ) {
                // The 24-hour count is what DGHS leads its daily release with —
                // the freshest figure on the screen.
                StatCard(
                    label = loc.t("dash.stat.last24"),
                    value = loc.num(store.cases24h),
                    caption = loc.t("dash.stat.last24Deaths", loc.num(store.deaths24h)),
                    accent = colors.admitted,
                    series = store.nationalRecent(14).map { it.cases.toDouble() },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                StatCard(
                    label = loc.t("activity.hotspots"),
                    value = loc.num(store.hotspots.size),
                    caption = loc.t("activity.hotspotsCaption"),
                    accent = colors.deaths,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
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

/** A still map with the areas marked, standing in for the full map. */
@Composable
fun MapPreviewCard(
    areas: List<Area>,
    userLocation: Pair<Double, Double>?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loc = LocalLocalization.current
    val source = rememberPressSource()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .pressable(source)
            .cardSurface()
            .clip(Shapes.card)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
    ) {
        // The same map the map tab draws, not a drawing of one. iOS puts a real
        // MapKit view here with interaction switched off, and a preview that
        // does not match the thing it previews is its own small lie.
        Box(Modifier.fillMaxWidth().height(190.dp)) {
            MapLibreRiskMap(
                areas = areas,
                metric = MapMetric.TOTAL,
                userLocation = userLocation,
                interactive = false,
                modifier = Modifier.matchParentSize(),
            )
            // A transparent lid over the map. Gestures are off, but the MapView
            // is still a view and can consume a tap before the card behind it
            // sees one, which would make the card dead exactly where it looks
            // most tappable.
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = source,
                        indication = null,
                        onClick = onClick,
                    )
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(Space.row),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.tight),
        ) {
            Box(Modifier.weight(1f)) { RiskScaleLegend() }
            Icon(
                DwIcons.chevronRight,
                contentDescription = loc.t("map.preview.a11y"),
                tint = Dw.colors.tertiaryText,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ChooseAreaHint(onClick: () -> Unit) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Shapes.control)
            .background(colors.accent.copy(alpha = 0.10f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(Space.row),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.tight),
    ) {
        Icon(
            DwIcons.myLocation,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(18.dp),
        )
        Text(
            loc.t("area.chooseHint"),
            style = typo(Typo.CAPTION),
            color = colors.accent,
            modifier = Modifier.weight(1f),
        )
        Icon(
            DwIcons.chevronRight,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** One line telling the reader how fresh the data is and why. */
@Composable
fun SyncStatusRow(sync: FeedSync, modifier: Modifier = Modifier) {
    val loc = LocalLocalization.current
    val colors = Dw.colors

    val text = when (val status = sync.status) {
        is FeedSync.Status.Syncing -> loc.t("sync.updating")
        is FeedSync.Status.Updated -> loc.t("sync.justUpdated")
        is FeedSync.Status.UpToDate -> loc.t("sync.lastChecked", loc.relative(status.at))
        is FeedSync.Status.Offline -> loc.t("sync.offline")
        is FeedSync.Status.Failed -> loc.t("sync.failed")
        is FeedSync.Status.BundledOnly, is FeedSync.Status.Idle -> loc.t("sync.bundled")
    }

    val icon = when (sync.status) {
        is FeedSync.Status.Syncing -> DwIcons.syncing
        is FeedSync.Status.Updated, is FeedSync.Status.UpToDate -> DwIcons.upToDate
        is FeedSync.Status.Offline -> DwIcons.offline
        is FeedSync.Status.Failed -> DwIcons.unreachable
        else -> DwIcons.bundled
    }

    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.hair + 2.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(13.dp))
        Text(text, style = typo(Typo.MICRO), color = colors.secondaryText)
    }
}

/** The language switch, shown on the home screen as the brief requires. */
@Composable
fun LanguageToggle(modifier: Modifier = Modifier) {
    val loc = LocalLocalization.current
    CircleChipButton(
        contentDescription = loc.t("lang.toggle.a11y"),
        onClick = { loc.toggle() },
        label = loc.language.shortLabel,
        modifier = modifier,
    )
}
