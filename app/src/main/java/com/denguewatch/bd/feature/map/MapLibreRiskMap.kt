package com.denguewatch.bd.feature.map

import android.graphics.PointF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.denguewatch.bd.model.Area
import com.denguewatch.bd.model.Geography
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwColors
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlin.math.sqrt

/**
 * The risk map, on MapLibre.
 *
 * The marks are the same as the iOS build's: one proportional circle per
 * reporting area, coloured by band, largest painted first so a small
 * high-incidence area is never buried under a big city. What changed underneath
 * is the renderer — MapLibre draws the circles as a styled layer on the GPU
 * rather than as a canvas overlay redrawn on every frame, so they stay pinned
 * through pan, zoom and rotation without the app doing per-frame work.
 *
 * Geography is division level and stops there: eight divisions plus the two
 * Dhaka city corporations, which is exactly what the iOS app plots and exactly
 * as fine as DGHS publishes. There is no district or upazila layer because
 * there is no district or upazila data to colour it with.
 *
 * Tiles are raster OSM, described by a style built in [rasterStyle] rather than
 * fetched from a style server. That keeps the app free of an API key and of any
 * third-party style host, and OSM's usage policy is met the same way as before:
 * a specific user agent, set in [MapLibre.getInstance], and visible credit drawn
 * over the map by the screen.
 */

private const val SOURCE_TILES = "osm-raster"
private const val LAYER_TILES = "osm-raster-layer"
private const val SOURCE_AREAS = "dengue-areas"
private const val LAYER_AREAS = "dengue-areas-circles"
private const val LAYER_AREAS_RING = "dengue-areas-ring"
private const val SOURCE_USER = "user-location"
private const val LAYER_USER_HALO = "user-location-halo"
private const val LAYER_USER_DOT = "user-location-dot"

private const val PROP_CODE = "code"
private const val PROP_RADIUS = "radius"
private const val PROP_COLOR = "color"
private const val PROP_HIGHLIGHTED = "highlighted"

/** Bangladesh, framed from the same outline the domain uses for containment. */
private val countryBounds: LatLngBounds by lazy {
    val lats = Geography.outline.map { it.first }
    val lons = Geography.outline.map { it.second }
    LatLngBounds.Builder()
        .include(LatLng(lats.max(), lons.max()))
        .include(LatLng(lats.min(), lons.min()))
        .build()
}

/**
 * The basemap style, and the script its labels are drawn in.
 *
 * These were raster OSM tiles, which bake their labels into the image — and OSM
 * renders each country's local `name`, so Bangladesh came out in Bengali no
 * matter what language the app was set to. There is no keyless raster source
 * that renders English here: Wikimedia's `osm-intl` still returns Bengali for
 * Bangladesh, and CARTO's English basemaps now watermark every tile with
 * "API KEY REQUIRED".
 *
 * OpenFreeMap serves OpenMapTiles-schema vector tiles with no key and no quota.
 * Vector tiles carry the label text as data rather than pixels, so the style can
 * be told which name field to draw.
 */
private fun styleUrl(dark: Boolean): String =
    if (dark) "https://tiles.openfreemap.org/styles/dark"
    else "https://tiles.openfreemap.org/styles/positron"

/**
 * Draw every basemap label in Latin script, in both languages.
 *
 * This deliberately does not follow the app's language switch, and it is the
 * one screen in the app that does not. Asking for `name:bn` produced visibly
 * broken Bengali: "সিলেট" came out as "সলিটে", "মিজোরাম" as "মজিোরাম" — the
 * matras present but sitting after their consonant instead of before it.
 *
 * It is not a missing font. The style's fontstack carries all 92 Bengali
 * codepoints, matras and virama included. What no fontstack can carry is a
 * conjunct such as ক্ষ, because a conjunct is not a codepoint — shaping builds
 * it. MapLibre draws one glyph per codepoint in memory order and does no
 * complex-script shaping for Indic: it neither moves a pre-base matra in front
 * of its consonant nor joins consonants across the virama. (It ships an RTL
 * plugin for Arabic and Hebrew; there is no Indic equivalent.) So "স ি ল ে ট"
 * is drawn in exactly that order, which reads as সলিটে.
 *
 * Latin is therefore the only script this renderer can be trusted with here.
 * A Bengali reader sees Latin place names on the basemap and Bengali
 * everywhere else, which is legible; broken Bengali is not. Note this affects
 * only the basemap's own labels — every name the app itself draws, including
 * the area list and the tapped-bubble sheet, stays in the chosen language.
 *
 * `coalesce` rather than a bare `get`: not every feature carries `name:en`, and
 * a small place missing it would otherwise draw an empty label.
 */
private fun applyLatinLabels(style: Style) {
    val field = Expression.coalesce(
        Expression.get("name:en"),
        Expression.get("name:latin"),
        Expression.get("name"),
    )
    style.layers.filterIsInstance<SymbolLayer>().forEach { layer ->
        runCatching { layer.setProperties(PropertyFactory.textField(field)) }
    }
}

/** The ten areas as GeoJSON, carrying everything the style needs to draw them. */
private fun areaFeatures(
    areas: List<Area>,
    metric: MapMetric,
    colors: DwColors,
    highlightedCode: String?,
    /**
     * Non-null draws every area at this radius instead of sizing it by value.
     *
     * The dashboard card is 190dp and has to show the whole country, so a
     * size-encoded circle for Dhaka covers half of Bangladesh and the encoding
     * stops being readable anyway. iOS draws a plain 14pt dot there for the
     * same reason: at that size the colour is the only channel that survives.
     */
    fixedRadius: Float?,
): FeatureCollection {
    val maxValue = maxOf(areas.maxOfOrNull { metric.value(it) } ?: 1.0, 1.0)

    // Largest first in the collection, so smaller circles are drawn last and
    // land on top — the same ordering the iOS build paints in.
    val features = areas.sortedByDescending { metric.value(it) }.map { area ->
        // Area-proportional: a circle twice as wide means four times the value.
        val radius = fixedRadius
            ?: (5f + 22f * sqrt((metric.value(area) / maxValue).coerceAtLeast(0.0)).toFloat())
        Feature.fromGeometry(Point.fromLngLat(area.longitude, area.latitude)).apply {
            addStringProperty(PROP_CODE, area.code)
            addNumberProperty(PROP_RADIUS, radius)
            addStringProperty(PROP_COLOR, colors.riskTint(area.risk).toHexString())
            addBooleanProperty(PROP_HIGHLIGHTED, area.code == highlightedCode)
        }
    }
    return FeatureCollection.fromFeatures(features)
}

private fun androidx.compose.ui.graphics.Color.toHexString(): String {
    val argb = toArgb()
    return String.format("#%06X", 0xFFFFFF and argb)
}

@Composable
fun MapLibreRiskMap(
    areas: List<Area>,
    metric: MapMetric,
    modifier: Modifier = Modifier,
    highlightedCode: String? = null,
    userLocation: Pair<Double, Double>? = null,
    recenterKey: Int = 0,
    recenterZoom: Double? = null,
    /**
     * False turns the map into a picture: no panning, no zooming, no taps.
     *
     * The dashboard preview needs that. It is a button that opens the map tab,
     * and a map that swallowed the drag would leave the reader stranded on a
     * pane they cannot get out of, on a card that looks tappable.
     */
    interactive: Boolean = true,
    onSelect: (Area) -> Unit = {},
) {
    val context = LocalContext.current
    val colors = Dw.colors
    // The full map can afford a wide margin around the country; the dashboard
    // card is 190dp tall and the same margin there eats the fit.
    val fitPadding = if (interactive) 48 else 16
    // 7f is half of the 14pt dot iOS draws in its preview card.
    val markerRadius: Float? = if (interactive) null else 7f
    val density = LocalDensity.current.density
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentAreas by rememberUpdatedState(areas)

    // Once per process, and it must happen here rather than in a LaunchedEffect:
    // MapLibre has to be initialised before the MapView on the next line is
    // constructed, and an effect would run after composition. Remembering the
    // returned instance rather than discarding it keeps this a value, which is
    // what remember is for.
    remember(context) { MapLibre.getInstance(context.applicationContext) }

    val mapView = remember { MapView(context) }
    val mapRef = remember { arrayOfNulls<MapLibreMap>(1) }
    val styleRef = remember { arrayOfNulls<Style>(1) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
        }
    }

    AndroidView(
        factory = { view ->
            mapView.apply {
                onCreate(null)
                getMapAsync { map ->
                    mapRef[0] = map
                    map.uiSettings.apply {
                        // The library's own chrome is switched off. A compass and
                        // an attribution "i" button in the corner would be the
                        // MapLibre demo's furniture, not this app's — the credit
                        // is drawn by the screen instead, in the app's own type.
                        isAttributionEnabled = false
                        isLogoEnabled = false
                        isCompassEnabled = false
                        isRotateGesturesEnabled = false
                        isTiltGesturesEnabled = false
                        if (!interactive) {
                            setAllGesturesEnabled(false)
                        }
                    }
                    // 5.5 is a floor for the interactive map: it stops a reader
                    // pinching out until Bangladesh is a speck in a world view.
                    // The still preview has to be allowed below it — the card is
                    // 190dp tall, fitting the country there needs about z4.3,
                    // and clamping to 5.5 is what cropped the north off it.
                    map.setMinZoomPreference(if (interactive) 5.5 else 3.0)
                    map.setMaxZoomPreference(12.0)

                    map.setStyle(styleUrl(colors.dark)) { style ->
                        styleRef[0] = style
                        applyLatinLabels(style)
                        installLayers(style, colors)
                        pushAreas(style, currentAreas, metric, colors, highlightedCode, markerRadius)
                        pushUser(style, userLocation)
                        // Posted, not immediate. `newLatLngBounds` derives the
                        // zoom from the view's size at the moment it is called,
                        // and the style finishes loading before the dashboard
                        // card has been squeezed to its 190dp height — so an
                        // immediate call fitted the country to the full-screen
                        // height and cropped the north off the card. Posting
                        // runs it after the layout pass, at the real size.
                        mapView.post {
                            map.moveCamera(
                                CameraUpdateFactory.newLatLngBounds(countryBounds, fitPadding)
                            )
                        }
                    }

                    // The fit above is computed from whatever size the view has
                    // when the style finishes loading, which on the dashboard
                    // card is not yet its final one — the country came out
                    // cropped, missing Rangpur off the top. Refitting on each
                    // real layout change fixes that. Only for the still map:
                    // on the interactive one this would drag the reader back
                    // to the whole country every time the view resized.
                    if (!interactive) {
                        mapView.addOnLayoutChangeListener {
                            _, left, top, right, bottom,
                            oldLeft, oldTop, oldRight, oldBottom,
                            ->
                            val changed = (right - left) != (oldRight - oldLeft) ||
                                (bottom - top) != (oldBottom - oldTop)
                            if (changed && right > left && bottom > top) {
                                map.moveCamera(
                                    CameraUpdateFactory.newLatLngBounds(countryBounds, fitPadding)
                                )
                            }
                        }
                    }

                    if (interactive) {
                        map.addOnMapClickListener { point ->
                            val screen = map.projection.toScreenLocation(point)
                            hitTest(map, screen, density)?.let { code ->
                                currentAreas.firstOrNull { it.code == code }?.let(currentOnSelect)
                                true
                            } ?: false
                        }
                    }
                }
            }
        },
        modifier = modifier,
        update = {
            val style = styleRef[0] ?: return@AndroidView
            pushAreas(style, areas, metric, colors, highlightedCode, markerRadius)
            pushUser(style, userLocation)
        },
    )

    // The basemap is a different style file per theme, so a theme change means
    // reloading it — and the layers and data have to be reinstalled afterwards,
    // because a style load discards everything added to the previous one.
    LaunchedEffect(colors.dark) {
        val map = mapRef[0] ?: return@LaunchedEffect
        if (styleRef[0] == null) return@LaunchedEffect
        map.setStyle(styleUrl(colors.dark)) { style ->
            styleRef[0] = style
            applyLatinLabels(style)
            installLayers(style, colors)
            pushAreas(style, currentAreas, metric, colors, highlightedCode, markerRadius)
            pushUser(style, userLocation)
        }
    }

    // Deliberately keyed on the button, not on the fix: following every update
    // would drag the map back each time a new position arrived, so a reader
    // could never pan away to look at another division.
    LaunchedEffect(recenterKey) {
        if (recenterKey <= 0) return@LaunchedEffect
        val map = mapRef[0] ?: return@LaunchedEffect
        val (lat, lon) = userLocation ?: return@LaunchedEffect
        val target = LatLng(lat, lon)
        val update = if (recenterZoom == null) {
            // Pan only. The first fix centres the reader without zooming in, so
            // all ten areas stay on screen — this tab is called "where it is
            // spreading", and a jump to street level would hide seven of them.
            CameraUpdateFactory.newLatLng(target)
        } else {
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(target).zoom(recenterZoom).build()
            )
        }
        map.animateCamera(update, 700)
    }
}

/** Sources and layers, added once when the style first loads. */
private fun installLayers(style: Style, colors: DwColors) {
    style.addSource(GeoJsonSource(SOURCE_AREAS, FeatureCollection.fromFeatures(emptyList())))
    style.addSource(GeoJsonSource(SOURCE_USER, FeatureCollection.fromFeatures(emptyList())))

    // The reader's own position, under the area circles: it is context, not the
    // subject, and it must never hide a band.
    style.addLayer(
        CircleLayer(LAYER_USER_HALO, SOURCE_USER).withProperties(
            PropertyFactory.circleRadius(14f),
            PropertyFactory.circleColor(colors.accent.toArgb()),
            PropertyFactory.circleOpacity(0.18f),
        )
    )
    style.addLayer(
        CircleLayer(LAYER_USER_DOT, SOURCE_USER).withProperties(
            PropertyFactory.circleRadius(5f),
            PropertyFactory.circleColor(colors.accent.toArgb()),
            PropertyFactory.circleStrokeWidth(2f),
            PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
        )
    )

    // The bands. Radius and colour come from the feature, so a data change is a
    // source update rather than a restyle.
    style.addLayer(
        CircleLayer(LAYER_AREAS, SOURCE_AREAS).withProperties(
            PropertyFactory.circleRadius(Expression.get(PROP_RADIUS)),
            PropertyFactory.circleColor(Expression.get(PROP_COLOR)),
            PropertyFactory.circleOpacity(0.75f),
            PropertyFactory.circleStrokeWidth(2f),
            PropertyFactory.circleStrokeColor(colors.card.toArgb()),
        )
    )

    // The dashed ring marking the reader's own area is a second, wider circle
    // rather than a stroke, because a stroke cannot be dashed here.
    style.addLayer(
        CircleLayer(LAYER_AREAS_RING, SOURCE_AREAS).withProperties(
            // Outside the bubble, not on its edge. Drawn at the same radius it
            // reads as a heavy outline; iOS sets it clear of the circle so it
            // reads as a separate "you are here" marker.
            PropertyFactory.circleRadius(
                Expression.sum(Expression.get(PROP_RADIUS), Expression.literal(5f))
            ),
            PropertyFactory.circleOpacity(0f),
            PropertyFactory.circleStrokeWidth(1.5f),
            PropertyFactory.circleStrokeColor(colors.primaryText.toArgb()),
            PropertyFactory.circleStrokeOpacity(
                Expression.switchCase(
                    Expression.get(PROP_HIGHLIGHTED), Expression.literal(0.9f),
                    Expression.literal(0f),
                )
            ),
        )
    )
}

private fun pushAreas(
    style: Style,
    areas: List<Area>,
    metric: MapMetric,
    colors: DwColors,
    highlightedCode: String?,
    fixedRadius: Float?,
) {
    val source = style.getSourceAs<GeoJsonSource>(SOURCE_AREAS) ?: return
    source.setGeoJson(areaFeatures(areas, metric, colors, highlightedCode, fixedRadius))
}

private fun pushUser(style: Style, userLocation: Pair<Double, Double>?) {
    val source = style.getSourceAs<GeoJsonSource>(SOURCE_USER) ?: return
    val features = userLocation?.let { (lat, lon) ->
        listOf(Feature.fromGeometry(Point.fromLngLat(lon, lat)))
    } ?: emptyList()
    source.setGeoJson(FeatureCollection.fromFeatures(features))
}

/**
 * Which area a tap landed on.
 *
 * Queried against a padded box rather than the exact point: the mark stays
 * proportional to the data, but the tap target never drops below the platform
 * minimum — a low-case area would otherwise be a 10px target, effectively
 * unreachable.
 */
private fun hitTest(map: MapLibreMap, screen: PointF, density: Float): String? {
    val half = 24f * density
    val box = android.graphics.RectF(
        screen.x - half, screen.y - half, screen.x + half, screen.y + half,
    )
    val hits = map.queryRenderedFeatures(box, LAYER_AREAS)
    if (hits.isEmpty()) return null

    // Smaller circles sit on top of larger ones, so the smallest match is the
    // one the finger meant.
    return hits.minByOrNull { it.getNumberProperty(PROP_RADIUS)?.toFloat() ?: Float.MAX_VALUE }
        ?.getStringProperty(PROP_CODE)
}
