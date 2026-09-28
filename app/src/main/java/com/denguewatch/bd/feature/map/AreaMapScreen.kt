package com.denguewatch.bd.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.AppState
import com.denguewatch.bd.data.DengueStore
import com.denguewatch.bd.feature.shared.CardSection
import com.denguewatch.bd.feature.shared.ChartSkeleton
import com.denguewatch.bd.feature.shared.EmptyStateView
import com.denguewatch.bd.feature.shared.ErrorStateView
import com.denguewatch.bd.feature.shared.GroupedSection
import com.denguewatch.bd.feature.shared.HairlineDivider
import com.denguewatch.bd.feature.shared.RiskBadge
import com.denguewatch.bd.feature.shared.RiskScaleLegend
import com.denguewatch.bd.feature.shared.RowsSkeleton
import com.denguewatch.bd.feature.shared.SearchField
import com.denguewatch.bd.feature.shared.SegmentedPicker
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.i18n.displayName
import com.denguewatch.bd.i18n.labelKey
import com.denguewatch.bd.model.Area
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Hit
import com.denguewatch.bd.ui.theme.Shapes
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.tabularFigures
import com.denguewatch.bd.ui.theme.readableColumn
import com.denguewatch.bd.ui.theme.typo

/**
 * Where it is spreading: the national map, or the same ten areas as a ranked
 * list. Both views answer the same question; the list is the one that works
 * with a screen reader and at large text sizes.
 */
@Composable
fun AreaMapScreen(
    state: AppState,
    showsList: Boolean,
    onOpenArea: (Area) -> Unit,
    onRequestLocation: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val store = state.store

    var metric by remember { mutableStateOf(MapMetric.TOTAL) }
    var selected by remember { mutableStateOf<Area?>(null) }

    Box(modifier.fillMaxSize().background(colors.plane)) {
        when {
            store.state is DengueStore.LoadState.Idle ||
                store.state is DengueStore.LoadState.Loading -> {
                Column(
                    Modifier
                        .padding(contentPadding)
                        .padding(horizontal = Space.screen, vertical = Space.card),
                    verticalArrangement = Arrangement.spacedBy(Space.stack),
                ) {
                    ChartSkeleton(height = 260.dp)
                    RowsSkeleton(count = 3)
                }
            }

            store.state is DengueStore.LoadState.Failed -> {
                ErrorStateView(
                    title = loc.t("dash.error.title"),
                    message = loc.t("dash.error.message"),
                    modifier = Modifier.padding(contentPadding),
                )
            }

            store.areas.isEmpty() -> {
                EmptyStateView(
                    icon = DwIcons.map,
                    title = loc.t("map.state.emptyTitle"),
                    message = loc.t("map.state.emptyMessage"),
                    modifier = Modifier.padding(contentPadding),
                )
            }

            // The list is reading matter and takes the capped column; the map
            // below it deliberately does not, because a map is the one thing
            // here that genuinely improves with every pixel of width.
            showsList -> AreaList(
                state = state,
                onOpenArea = onOpenArea,
                contentPadding = contentPadding,
                modifier = Modifier.readableColumn().align(Alignment.TopCenter),
            )

            else -> MapView(
                state = state,
                metric = metric,
                onMetricChange = { metric = it },
                selected = selected,
                onSelect = { selected = it },
                onOpenArea = onOpenArea,
                onRequestLocation = onRequestLocation,
                contentPadding = contentPadding,
            )
        }
    }
}

@Composable
private fun MapView(
    state: AppState,
    metric: MapMetric,
    onMetricChange: (MapMetric) -> Unit,
    selected: Area?,
    onSelect: (Area?) -> Unit,
    onOpenArea: (Area) -> Unit,
    onRequestLocation: () -> Unit,
    contentPadding: PaddingValues,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val store = state.store
    val location = state.location

    val currentArea = location.lastKnownLocation
        ?.let { (lat, lon) -> store.nearestArea(lat, lon) }

    var recenterKey by remember { mutableIntStateOf(0) }
    var recenterZoom by remember { mutableStateOf<Double?>(null) }
    var hasAutoCentred by remember { mutableStateOf(false) }

    // Follow the device only while the map is on screen. Tracking is stopped on
    // the way out rather than left to the process, so leaving the tab stops the
    // GPS draw immediately.
    DisposableEffect(location.isAuthorized) {
        if (location.isAuthorized) location.startTracking()
        onDispose { location.stopTracking() }
    }

    // Move to the reader once, when the first fix lands. Following every update
    // would drag the map back each time a new position arrived, so a reader
    // could never pan away to look at another division.
    LaunchedEffect(location.lastKnownLocation) {
        if (!hasAutoCentred && location.lastKnownLocation != null) {
            hasAutoCentred = true
            recenterZoom = null   // pan to the reader, keep the national scale
            recenterKey++
        }
    }

    Column(
        Modifier.fillMaxSize().padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SegmentedPicker(
            options = MapMetric.entries,
            selected = metric,
            label = { loc.t(it.labelKey) },
            onSelect = onMetricChange,
            modifier = Modifier
                .readableColumn()
                .padding(horizontal = Space.card, vertical = Space.tight),
        )

        // clipToBounds is kept deliberately. A map SurfaceView that measures
        // itself larger than the slot Compose gives it paints straight over the
        // metric picker above and the legend below — which is exactly what
        // happened under osmdroid. MapLibre behaves, but the clip costs nothing
        // and the failure it prevents is silent and ugly.
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            MapLibreRiskMap(
                areas = store.areas,
                metric = metric,
                highlightedCode = currentArea?.code,
                userLocation = location.lastKnownLocation,
                recenterKey = recenterKey,
                recenterZoom = recenterZoom,
                onSelect = onSelect,
                modifier = Modifier.fillMaxSize(),
            )

            // Credit for the whole tile chain, not just the data: OSM supplies
            // the map data, OpenMapTiles the schema the vector tiles are cut to,
            // and both licences require visible attribution wherever the tiles
            // are shown. Placed over the map so it travels with them.
            Text(
                "© OpenStreetMap · OpenMapTiles",
                style = typo(Typo.MICRO),
                color = colors.secondaryText,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(Space.tight)
                    .background(colors.card.copy(alpha = 0.82f), Shapes.chip)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )

            MyLocationButton(
                isAuthorized = location.isAuthorized,
                isLocating = location.isRequesting,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Space.row),
                onClick = {
                    if (location.isAuthorized) {
                        location.startTracking()
                        recenterZoom = 9.0   // the button is where "closer" belongs
                        recenterKey++
                    } else {
                        onRequestLocation()
                    }
                },
            )
        }

        // Under the map, not over it, so it never covers an area.
        Column(
            Modifier
                .readableColumn()
                .fillMaxWidth()
                .padding(horizontal = Space.card)
                .padding(bottom = Space.tight),
            verticalArrangement = Arrangement.spacedBy(Space.tight),
        ) {
            when {
                currentArea != null -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.tight),
                    ) {
                        Icon(
                            DwIcons.myLocation,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            loc.t("map.you.inArea", currentArea.displayName(loc.language)),
                            style = typo(Typo.CAPTION, FontWeight.Medium),
                            color = colors.accent,
                            modifier = Modifier.weight(1f),
                        )
                        RiskBadge(currentArea.risk, compact = true)
                    }
                    HairlineDivider()
                }

                // Told apart on purpose. "Location is off" and "we are still
                // looking" are different situations, and a reader who has the
                // system switch turned off would otherwise watch a spinner that
                // was never going to resolve.
                location.isAuthorized && !location.isLocationEnabled -> {
                    Text(
                        loc.t("map.you.denied"),
                        style = typo(Typo.MICRO),
                        color = colors.secondaryText,
                    )
                    HairlineDivider()
                }

                location.isAuthorized && location.isRequesting -> {
                    Text(
                        loc.t("map.you.locating"),
                        style = typo(Typo.MICRO),
                        color = colors.secondaryText,
                    )
                    HairlineDivider()
                }
            }

            Text(
                loc.t(metric.explanationKey),
                style = typo(Typo.MICRO),
                color = colors.secondaryText,
            )
            RiskScaleLegend()
            MapAsOfFooter(store)
        }
    }

    if (selected != null) {
        AreaRiskSheet(
            area = selected,
            state = state,
            onDismiss = { onSelect(null) },
            onOpenArea = {
                onSelect(null)
                onOpenArea(it)
            },
        )
    }
}

/**
 * Recentres the map on the reader, or asks for permission when there is none.
 *
 * One control for both states rather than a button that appears only once
 * permission is granted: the reader who has not been asked yet still needs
 * somewhere to say yes.
 */
@Composable
private fun MyLocationButton(
    isAuthorized: Boolean,
    isLocating: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val label = loc.t("map.you.enable")

    Box(
        modifier
            .size(Hit.minimum)
            .clip(Shapes.chip)
            .background(colors.card)
            .border(1.dp, colors.hairline, Shapes.chip)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (isLocating) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = colors.accent,
            )
        } else {
            Icon(
                DwIcons.myLocation,
                contentDescription = null,
                tint = if (isAuthorized) colors.accent else colors.secondaryText,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** When the map's figures are from, stated on the map itself. */
@Composable
fun MapAsOfFooter(store: DengueStore, modifier: Modifier = Modifier) {
    val loc = LocalLocalization.current
    val updated = store.lastUpdated ?: return
    Text(
        loc.t("map.asOf", loc.fullDate(updated)),
        style = typo(Typo.MICRO),
        color = Dw.colors.mutedInk,
        modifier = modifier,
    )
}

@Composable
private fun AreaList(
    state: AppState,
    onOpenArea: (Area) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val store = state.store
    var query by rememberSaveable { mutableStateOf("") }

    val peak = remember(store.areas) { store.peakAreaCases }
    val divisions = remember(store.areas) { store.divisionSummaries }

    // Matched against both languages' names at once, so a reader typing Bengali
    // finds an area whose English name is what the feed carries, and the other
    // way round.
    val areas = remember(store.areas, query, loc.language) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            store.areasByCases
        } else {
            store.areasByCases.filter { area ->
                area.name.contains(needle, ignoreCase = true) ||
                    area.displayName(loc.language).contains(needle, ignoreCase = true) ||
                    area.division.label.contains(needle, ignoreCase = true) ||
                    area.division.displayName(loc.language).contains(needle, ignoreCase = true)
            }
        }
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Space.stack),
    ) {
        item {
            SearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = loc.t("map.search"),
                modifier = Modifier.padding(horizontal = Space.card),
            )
        }

        // The eight divisions, folded up from the ten reporting areas. Shown
        // first because it is the shape of the country a reader already has in
        // their head; the reporting areas below split Dhaka three ways, which
        // is the feed's shape rather than theirs.
        //
        // Kept on screen while a search is running, as iOS does: there the
        // search field filters the areas section alone, so the roll-up stays
        // put as a stable frame of reference rather than the list emptying out
        // around the one row that matched.
        item {
            GroupedSection(
                title = loc.t("map.list.divisions"),
                modifier = Modifier.padding(horizontal = Space.card),
            ) {
                Column {
                    divisions.forEachIndexed { index, summary ->
                        DivisionRow(summary)
                        if (index < divisions.size - 1) HairlineDivider()
                    }
                }
            }
        }

        item {
            GroupedSection(
                title = loc.t("map.list.areas"),
                modifier = Modifier.padding(horizontal = Space.card),
                footnote = loc.t("map.list.footer"),
            ) {
                if (areas.isEmpty()) {
                    Text(
                        loc.t("map.state.emptyTitle"),
                        style = typo(Typo.CAPTION),
                        color = colors.secondaryText,
                    )
                } else {
                    Column {
                        areas.forEachIndexed { index, area ->
                            AreaRow(
                                area = area,
                                peak = peak,
                                onClick = { onOpenArea(area) },
                            )
                            if (index < areas.size - 1) HairlineDivider()
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(Space.section)) }
    }
}

/**
 * One division: the name, its season total, and its band.
 *
 * No bar and no chevron, unlike the area rows below. A division is a roll-up
 * rather than a place the app can open, and giving it the same affordances
 * would promise a page that does not exist.
 */
@Composable
private fun DivisionRow(summary: DengueStore.DivisionSummary) {
    val loc = LocalLocalization.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Space.row),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            summary.division.displayName(loc.language),
            style = typo(Typo.SUBHEADLINE),
            modifier = Modifier.weight(1f),
        )
        Text(
            loc.compact(summary.cases),
            style = typo(Typo.SUBHEADLINE).tabularFigures(),
        )
        RiskBadge(summary.risk, compact = true)
    }
}

/**
 * One area in a ranked list: the name, its division, the season total, the
 * band, and a bar for comparison.
 */
@Composable
fun AreaRow(
    area: Area,
    peak: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val name = area.displayName(loc.language)
    val summary = loc.t(
        "map.bubble.a11y",
        name,
        loc.num(area.seasonCases),
        loc.t(area.risk.labelKey),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else Modifier
            )
            .padding(vertical = Space.row)
            .semantics { contentDescription = summary },
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.tight),
        ) {
            // Name and division share one weighted slot, so the count and the
            // badge stay right-aligned down the column. Neither text is capped
            // to a line: an area name is the row's whole subject, and both
            // ellipsis and a bare `maxLines` clip lose part of it. Wrapping is
            // the one option that always shows the name in full, so a long
            // Bengali name takes a second line rather than a "…".
            Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.tight),
            ) {
                Text(name, style = typo(Typo.SUBHEADLINE))
                // Only where it adds something. Nine of the ten areas are named
                // after their own division, so printing "Khulna  Khulna" is
                // noise; the three Dhaka rows are the ones a reader needs it on.
                val division = area.division.displayName(loc.language)
                if (!name.equals(division, ignoreCase = true)) {
                    Text(
                        division,
                        style = typo(Typo.MICRO),
                        color = colors.secondaryText,
                    )
                }
            }
            Text(
                loc.num(area.seasonCases),
                style = typo(Typo.SUBHEADLINE).tabularFigures(),
            )
            RiskBadge(area.risk, compact = true)
            if (onClick != null) {
                Icon(
                    DwIcons.chevronRight,
                    contentDescription = null,
                    tint = colors.tertiaryText,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // Tinted by band, which is a deliberate departure from iOS — that build
        // draws every bar in the series blue so length reads purely as the
        // season total. Colouring by band ties the row's two signals together
        // and makes the list scannable by risk at a glance; the cost is that
        // length and hue now say different things, so a long green bar means a
        // populous low-risk area rather than a bad one. The legend under the
        // list states the bands, and the badge beside each row names them.
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.grid)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(
                        (area.seasonCases.toFloat() / maxOf(peak, 1)).coerceIn(0.01f, 1f)
                    )
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.riskTint(area.risk))
            )
        }
    }
}
