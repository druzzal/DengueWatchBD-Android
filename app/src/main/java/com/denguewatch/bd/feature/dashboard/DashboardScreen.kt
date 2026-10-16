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
import androidx.compose.ui.platform.LocalDensity
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
import com.denguewatch.bd.model.Series
import com.denguewatch.bd.model.StatGrid
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
import androidx.compose.ui.graphics.Color
import com.denguewatch.bd.model.SurveillanceDigest
import com.denguewatch.bd.ui.theme.Card
import com.denguewatch.bd.feature.shared.SecondaryActionButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ripple
import androidx.compose.ui.text.style.TextAlign
import com.denguewatch.bd.feature.shared.HairlineDivider
import com.denguewatch.bd.feature.shared.RiskBadge
import com.denguewatch.bd.feature.shared.ShrinkToFitText
import com.denguewatch.bd.feature.shared.TrendIndicator
import com.denguewatch.bd.ui.theme.Hit
import com.denguewatch.bd.ui.theme.tabularFigures
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

/**
 * One ranked area.
 *
 * Two full-width lines rather than one row of five competing items. The badge
 * shares the name's line because their widths vary together — a long name is
 * not also a long badge — and the rate then has the whole row, so nothing is
 * ever cut short.
 */
@Composable
private fun TopAreaRow(
    rank: Int,
    area: Area,
    onClick: () -> Unit,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val source = rememberPressSource()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(source)
            .heightIn(min = Hit.minimum)
            .clickable(
                interactionSource = source,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = Space.tight),
        horizontalArrangement = Arrangement.spacedBy(Space.row),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The rank sits on the name's baseline, and the two text lines keep
        // their own column so the rate stays indented under the name.
        //
        // Centred against the whole two-line row the numeral floated in the gap
        // between the name and the rate, level with neither — measured, the "2"
        // sat 19px below the baseline of the name it ranks. Aligning the rank
        // to the Column's first baseline fixes that without hand-computing an
        // indent for the second line, which is how the rate ended up starting
        // left of the name on the first attempt.
        Text(
            loc.num(rank),
            style = typo(Typo.CAPTION).tabularFigures(),
            color = colors.secondaryText,
            textAlign = TextAlign.End,
            modifier = Modifier.width(RANK_WIDTH).alignByBaseline(),
        )

        Column(
            Modifier.weight(1f).alignByBaseline(),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.tight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // One weight, not two. A weighted spacer beside a weighted
                // label split the row in half and clipped "Dhaka (outside
                // city)" to "Dhaka (outside" — which reads as a name rather
                // than as a truncation, exactly what this project forbids.
                // The label takes the space the badge does not.
                ShrinkToFitText(
                    area.displayName(loc.language),
                    style = typo(Typo.SUBHEADLINE),
                    modifier = Modifier.weight(1f),
                    minScale = 0.7f,
                )
                RiskBadge(area.risk, compact = true)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.tight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // A fixed gutter, so the trend starts at the same place on
                // every row. Sized to the text, the rate carried the trend
                // along with it: "8.1 per 100,000" is a character shorter than
                // "41.3 per 100,000", so Dhaka (outside city) printed its
                // change 8dp left of the four rows above it and the column
                // read as crooked. Tabular figures on top, so the digits
                // themselves do not shuffle the ones that are the same length.
                ShrinkToFitText(
                    loc.t("top.rate", loc.decimal(area.incidencePer100k)),
                    style = typo(Typo.MICRO).tabularFigures(),
                    color = colors.secondaryText,
                    modifier = Modifier.width(TOP_RATE_WIDTH),
                )
                TrendIndicator(area.weeklyChange)
            }
        }

        Icon(
            DwIcons.chevronRight,
            contentDescription = null,
            tint = colors.mutedInk,
            modifier = Modifier.size(16.dp),
        )
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

/**
 * Icon and colour for a digest statement. They carry the same meaning as the
 * words, so the section still reads without colour vision and without reading
 * every sentence.
 */
/** The gutter the rank sits in, and the indent its row's second line needs. */
private val RANK_WIDTH = 24.dp

/**
 * The gutter the per-100,000 rate sits in.
 *
 * Wide enough for the longest form either language produces — English's
 * "41.3 per 100,000" runs about 87dp and is the demanding one, since Bengali's
 * "প্রতি লাখে ৪১.৩" is shorter — with room for a three-digit rate should an
 * area ever report one. Anything longer still shrinks rather than wraps.
 */
private val TOP_RATE_WIDTH = 96.dp

private val SurveillanceDigest.Kind.icon
    get() = when (this) {
        SurveillanceDigest.Kind.NATIONAL_RISE,
        SurveillanceDigest.Kind.STEEPEST_RISES,
        -> DwIcons.trendUp
        SurveillanceDigest.Kind.NATIONAL_FALL -> DwIcons.trendDown
        SurveillanceDigest.Kind.NATIONAL_STEADY -> DwIcons.trendFlat
        SurveillanceDigest.Kind.HOTSPOTS -> DwIcons.riskSevere
        SurveillanceDigest.Kind.NO_BREAKDOWN -> DwIcons.info
    }

@Composable
private fun SurveillanceDigest.Kind.tint(): Color = when (this) {
    SurveillanceDigest.Kind.NATIONAL_RISE,
    SurveillanceDigest.Kind.STEEPEST_RISES,
    -> Dw.colors.riskTint(RiskLevel.HIGH)
    SurveillanceDigest.Kind.NATIONAL_FALL -> Dw.colors.riskTint(RiskLevel.LOW)
    SurveillanceDigest.Kind.HOTSPOTS -> Dw.colors.riskTint(RiskLevel.SEVERE)
    SurveillanceDigest.Kind.NATIONAL_STEADY,
    SurveillanceDigest.Kind.NO_BREAKDOWN,
    -> Dw.colors.secondaryText
}
