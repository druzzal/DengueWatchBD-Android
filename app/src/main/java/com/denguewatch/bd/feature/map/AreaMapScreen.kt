package com.denguewatch.bd.feature.map

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.denguewatch.bd.feature.shared.ChartSkeleton
import com.denguewatch.bd.feature.shared.EmptyStateView
import com.denguewatch.bd.feature.shared.ErrorStateView
import com.denguewatch.bd.feature.shared.HairlineDivider
import com.denguewatch.bd.feature.shared.RiskBadge
import com.denguewatch.bd.feature.shared.RiskScaleLegend
import com.denguewatch.bd.feature.shared.RowsSkeleton
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
import com.denguewatch.bd.ui.theme.readableColumn
import com.denguewatch.bd.ui.theme.typo
import com.denguewatch.bd.ui.theme.windowSizeDp
import com.denguewatch.bd.ui.theme.ShortHeightBreakpoint

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
    onRequestBackgroundLocation: () -> Unit,
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
                onRequestBackgroundLocation = onRequestBackgroundLocation,
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
    onRequestBackgroundLocation: () -> Unit,
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
    var fitCountryKey by remember { mutableIntStateOf(0) }
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

    val picker = @Composable { pickerModifier: Modifier ->
        SegmentedPicker(
            options = MapMetric.entries,
            selected = metric,
            label = { loc.t(it.labelKey) },
            onSelect = onMetricChange,
            modifier = pickerModifier,
        )
    }

        // clipToBounds is kept deliberately. A map SurfaceView that measures
        // itself larger than the slot Compose gives it paints straight over the
        // metric picker above and the legend below — which is exactly what
        // happened under osmdroid. MapLibre behaves, but the clip costs nothing
        // and the failure it prevents is silent and ugly.
    val map = @Composable { mapModifier: Modifier ->
        Box(
            mapModifier
                .clipToBounds()
                // The reader's position is a dot drawn inside the map's own
                // layers, which TalkBack cannot reach. iOS labels that marker
                // "Your location"; here the map says it, with the area.
                .semantics {
                    if (location.lastKnownLocation != null) {
                        contentDescription = listOfNotNull(
                            loc.t("map.youAreHere.a11y"),
                            currentArea?.let { loc.t("map.you.inArea", it.displayName(loc.language)) },
                        ).joinToString(", ")
                    }
                },
        ) {
            MapLibreRiskMap(
                areas = store.areas,
                metric = metric,
                highlightedCode = currentArea?.code,
                userLocation = location.lastKnownLocation,
                recenterKey = recenterKey,
                recenterZoom = recenterZoom,
                fitCountryKey = fitCountryKey,
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

    }

    // Under the map, not over it, so it never covers an area.
    val details = @Composable { detailsModifier: Modifier ->
        Column(
            detailsModifier,
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
                        // Back out to the whole country, as on iOS: after
                        // zooming to the reader there is otherwise no quick way
                        // to see the other nine areas again.
                        Icon(
                            DwIcons.map,
                            contentDescription = loc.t("map.you.wholeCountry"),
                            tint = colors.secondaryText,
                            modifier = Modifier
                                .size(Hit.minimum)
                                .clip(CircleShape)
                                .clickable(role = Role.Button) { fitCountryKey++ }
                                .padding(14.dp),
                        )
                        RiskBadge(currentArea.risk, compact = true)
                    }
                    HairlineDivider()
                }

                // A position the app has, but outside Bangladesh: no area to
                // name, and the reader should know why rather than see nothing.
                location.lastKnownLocation != null -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Space.tight),
                    ) {
                        Icon(DwIcons.noLocation, contentDescription = null, tint = colors.secondaryText,
                             modifier = Modifier.size(14.dp))
                        Text(loc.t("map.you.outside"), style = typo(Typo.MICRO), color = colors.secondaryText)
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

            // The entry-alert control, on the map itself — ported from iOS.
            //
            // It appears whenever there is a high-risk area to watch, not only
            // while the reader is standing in one. iOS made that change for a
            // reason worth keeping: the alert exists to warn you *before* you
            // enter a high-risk area, so gating it on already being in one hid
            // it from exactly the person it was for.
            if (store.hotspots.isNotEmpty()) {
                GeofencePrompt(
                    state = state,
                    onRequestBackgroundLocation = onRequestBackgroundLocation,
                )
                HairlineDivider()
            }

            Text(
                loc.t(metric.explanationKey),
                style = typo(Typo.MICRO),
                color = colors.secondaryText,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.row),
                verticalAlignment = Alignment.Bottom,
            ) {
                // What a circle's size stands for, at the sizes the map draws
                // — the same three samples iOS shows. Without a key the size
                // was a shape to guess at.
                val maxValue = maxOf(store.areas.maxOfOrNull { metric.value(it) } ?: 1.0, 1.0)
                listOf(0.08, 0.35, 1.0).forEach { fraction ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        val radius = 5.0 + 22.0 * kotlin.math.sqrt(fraction)
                        Box(
                            Modifier
                                .size((radius * 2).dp)
                                .clip(CircleShape)
                                .background(colors.mutedInk.copy(alpha = 0.30f)),
                        )
                        Text(
                            if (metric == MapMetric.TOTAL) loc.compact((maxValue * fraction).toInt())
                            else loc.decimal(maxValue * fraction, 0),
                            style = typo(Typo.MICRO),
                            color = colors.secondaryText,
                        )
                    }
                }
            }
            // Named, as iOS names it: the colours are the risk band, not the
            // number of cases the circle sizes show.
            Text(
                loc.t("map.legend.risk"),
                style = typo(Typo.MICRO, FontWeight.SemiBold),
                color = colors.secondaryText,
            )
            RiskScaleLegend()
            MapAsOfFooter(store)
        }
    }

    // A phone on its side has the width and none of the height: stacked, the
    // picker and the notes underneath left the map a strip about 100dp tall.
    // There the map takes the full height and everything else sits beside it,
    // scrolling if it has to. Portrait and tablets keep the stack.
    val window = windowSizeDp()
    val sideBySide = window.height < ShortHeightBreakpoint && window.width > window.height
    if (sideBySide) {
        Row(Modifier.fillMaxSize().padding(contentPadding)) {
            map(Modifier.weight(1f).fillMaxHeight())
            Column(
                Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = Space.tight),
                verticalArrangement = Arrangement.spacedBy(Space.tight),
            ) {
                picker(Modifier.padding(horizontal = Space.screen))
                details(Modifier.fillMaxWidth().padding(horizontal = Space.screen))
            }
        }
    } else {
        Column(
            Modifier.fillMaxSize().padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            picker(
                Modifier
                    .readableColumn()
                    .padding(horizontal = Space.screen, vertical = Space.tight)
            )
            map(Modifier.weight(1f).fillMaxWidth())
            details(
                Modifier
                    .readableColumn()
                    .fillMaxWidth()
                    .padding(horizontal = Space.screen)
                    .padding(bottom = Space.tight)
            )
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
