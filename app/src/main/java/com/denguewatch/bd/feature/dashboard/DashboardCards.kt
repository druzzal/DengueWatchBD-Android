package com.denguewatch.bd.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.data.FeedSync
import com.denguewatch.bd.feature.map.MapLibreRiskMap
import com.denguewatch.bd.feature.map.MapMetric
import com.denguewatch.bd.feature.shared.RiskScaleLegend
import com.denguewatch.bd.feature.shared.CircleChipButton
import com.denguewatch.bd.i18n.LocalLocalization
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
import androidx.compose.ui.graphics.Color
import com.denguewatch.bd.model.SurveillanceDigest
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ripple
import androidx.compose.ui.text.style.TextAlign
import com.denguewatch.bd.feature.shared.RiskBadge
import com.denguewatch.bd.feature.shared.ShrinkToFitText
import com.denguewatch.bd.feature.shared.TrendIndicator
import com.denguewatch.bd.ui.theme.Hit
import com.denguewatch.bd.ui.theme.tabularFigures

/**
 * One ranked area.
 *
 * Two full-width lines rather than one row of five competing items. The badge
 * shares the name's line because their widths vary together — a long name is
 * not also a long badge — and the rate then has the whole row, so nothing is
 * ever cut short.
 */
@Composable
internal fun TopAreaRow(
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
internal fun ChooseAreaHint(onClick: () -> Unit) {
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
internal val RANK_WIDTH = 24.dp

/**
 * The gutter the per-100,000 rate sits in.
 *
 * Wide enough for the longest form either language produces — English's
 * "41.3 per 100,000" runs about 87dp and is the demanding one, since Bengali's
 * "প্রতি লাখে ৪১.৩" is shorter — with room for a three-digit rate should an
 * area ever report one. Anything longer still shrinks rather than wraps.
 */
internal val TOP_RATE_WIDTH = 96.dp

internal val SurveillanceDigest.Kind.icon
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
internal fun SurveillanceDigest.Kind.tint(): Color = when (this) {
    SurveillanceDigest.Kind.NATIONAL_RISE,
    SurveillanceDigest.Kind.STEEPEST_RISES,
    -> Dw.colors.riskTint(RiskLevel.HIGH)
    SurveillanceDigest.Kind.NATIONAL_FALL -> Dw.colors.riskTint(RiskLevel.LOW)
    SurveillanceDigest.Kind.HOTSPOTS -> Dw.colors.riskTint(RiskLevel.SEVERE)
    SurveillanceDigest.Kind.NATIONAL_STEADY,
    SurveillanceDigest.Kind.NO_BREAKDOWN,
    -> Dw.colors.secondaryText
}
