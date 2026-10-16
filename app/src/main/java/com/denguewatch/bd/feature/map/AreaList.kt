package com.denguewatch.bd.feature.map

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.AppState
import com.denguewatch.bd.data.DengueStore
import com.denguewatch.bd.feature.shared.GroupedSection
import com.denguewatch.bd.feature.shared.HairlineDivider
import com.denguewatch.bd.feature.shared.RiskBadge
import com.denguewatch.bd.feature.shared.SearchField
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.i18n.displayName
import com.denguewatch.bd.i18n.divisionLabel
import com.denguewatch.bd.i18n.labelKey
import com.denguewatch.bd.model.Area
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.tabularFigures
import com.denguewatch.bd.ui.theme.typo
import androidx.compose.ui.text.style.TextAlign
import com.denguewatch.bd.feature.shared.ShrinkToFitText

@Composable
internal fun AreaList(
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
                modifier = Modifier.padding(horizontal = Space.screen),
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
                modifier = Modifier.padding(horizontal = Space.screen),
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
                modifier = Modifier.padding(horizontal = Space.screen),
                footnote = loc.t("map.list.footer"),
            ) {
                if (areas.isEmpty()) {
                    Text(
                        loc.t("map.state.emptyTitle"),
                        style = typo(Typo.CAPTION),
                        color = colors.secondaryText,
                    )
                } else {
                    BoxWithConstraints {
                        val nameMetrics = rememberAreaNameMetrics(
                            areas.map { it.displayName(loc.language) },
                            areas.map { loc.num(it.seasonCases) },
                            maxWidth,
                        )
                        Column {
                            areas.forEachIndexed { index, area ->
                                AreaRow(
                                    area = area,
                                    peak = peak,
                                    onClick = { onOpenArea(area) },
                                    nameScale = nameMetrics.scale,
                                    nameSlotWidth = nameMetrics.slotWidth,
                                    countWidth = nameMetrics.countWidth,
                                )
                                if (index < areas.size - 1) HairlineDivider()
                            }
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
internal fun DivisionRow(summary: DengueStore.DivisionSummary) {
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

/** The column the season count sits in, so the figures line up down a list. */
/**
 * Breathing room either side of the measured count, so it never sits flush.
 *
 * Kept small on purpose. Measured on a 360dp handset, the name needed 12dp
 * more to reach full size and this gutter was holding 10 of them — and the
 * name is the row's subject while the count has a column of its own either
 * way.
 */
internal val ROW_COUNT_PADDING = 4.dp

/** Wide enough for the longest band name in either language. */
/**
 * The gutter the risk badge sits in.
 *
 * 84dp left "Moderate" about 2dp short once the icon and the chip's padding
 * were taken out, so the longest band name in English wrapped inside its own
 * chip. Widened to clear it at full size; the area name beside it already
 * wraps by design, which is the right place for the extra line to fall.
 */
internal val ROW_BADGE_WIDTH = 80.dp

/**
 * How far a whole list may be shrunk to keep one type size.
 *
 * Past this the list holds its size and the over-long name shrinks alone: a
 * single odd row reads better than every row made small for it.
 *
 * 0.80 rather than 0.85 because the floor has to clear the lists that should
 * stay uniform. "Rest of Dhaka division" needs 0.82 to fit "Dhaka (outside
 * city)"; a floor of 0.85 stopped that card following its longest name and put
 * it back to two sizes — the very thing the shared scale exists to prevent.
 * Set just under it, so that card goes uniform and the map's full list, which
 * wants 0.74, still only falls to 0.80 with its one outlier shrinking alone.
 */
private const val SHARED_NAME_SCALE_FLOOR = 0.80f

/** The chevron's slot, reserved on every row whether or not one is drawn. */
internal val ROW_CHEVRON_WIDTH = 16.dp

/**
 * One type size for every name in a list.
 *
 * Shrink-to-fit sizes each row on its own, which keeps every name whole and on
 * one line but lets a list end up in two different sizes — "Dhaka (outside
 * city)" at 82% beside "Dhaka North City" at full size reads as a fault rather
 * than as a fit. So the scale is decided once, from the longest name in the
 * list, and every row is drawn at it: uniform, and never smaller than it has
 * to be.
 *
 * Measured against the width the name actually gets, which is the row less the
 * three fixed gutters and the gaps between them, with a little taken off so a
 * rounding difference between measuring and drawing cannot clip the longest
 * name by a pixel.
 *
 * Floored, because one outlier should not shrink a whole list. The map's list
 * of every reporting area carries one name far longer than the rest, and
 * sizing all 64 rows to it dropped each to 73% — legible rows made small by a
 * name most of them sit nowhere near, and visibly smaller than the division
 * list directly above. Below the floor the list stops following the longest
 * name and that one row shrinks on its own instead.
 */
@Composable
fun rememberAreaNameMetrics(
    names: List<String>,
    counts: List<String>,
    rowWidth: Dp,
    minScale: Float = SHARED_NAME_SCALE_FLOOR,
): AreaNameMetrics {
    val nameStyle = typo(Typo.SUBHEADLINE)
    val countStyle = typo(Typo.SUBHEADLINE).tabularFigures()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(names, counts, rowWidth, nameStyle, density) {
        // softWrap = false is what measures on one line; a maxLines cap on top
        // would be redundant, and NoTruncationTest reads any such cap in a
        // screen file as text being clipped.
        fun widest(items: List<String>, style: TextStyle) = items.maxOfOrNull {
            measurer.measure(AnnotatedString(it), style, softWrap = false).size.width.toFloat()
        } ?: 0f

        // The count column is measured rather than fixed at a guess. 72dp was
        // set wide enough for any total and then kept, which quietly spent
        // 17dp the name needed — and the name is the row's subject. Measuring
        // it also follows the reader: Bengali digits and a larger font size
        // both change what "17,791" costs.
        val countPx = widest(counts, countStyle)
        val countWidth = with(density) { countPx.toDp() } + ROW_COUNT_PADDING

        // The slot the row will actually give the name, handed back so the row
        // can be laid out to exactly this width. Deriving the width here and
        // letting the row take weight(1f) instead left the two free to disagree
        // — they did, by about 8dp, so the scale came out too generous and the
        // longest name still had to shrink on its own, which is the mixed-size
        // list this is meant to prevent.
        val slot = (rowWidth - countWidth - ROW_BADGE_WIDTH - ROW_CHEVRON_WIDTH -
            Space.tight * 3).coerceAtLeast(0.dp)
        val available = with(density) { slot.toPx() } * 0.98f
        val scale = if (available <= 0f) {
            1f
        } else {
            val w = widest(names, nameStyle)
            if (w <= available) 1f else (available / w).coerceAtLeast(minScale)
        }
        AreaNameMetrics(slot, countWidth, scale)
    }
}

/** What a row gives the name and the count, and the one size every name is drawn at. */
data class AreaNameMetrics(val slotWidth: Dp, val countWidth: Dp, val scale: Float)

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
    nameScale: Float = 1f,
    nameSlotWidth: Dp? = null,
    countWidth: Dp = 72.dp,
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
            // badge stay right-aligned down the column. The name is never
            // capped to a line and never ellipsised — it is the row's whole
            // subject, and a clipped "Dhaka (outside" reads as a place rather
            // than as a truncation. It used to wrap instead, which kept the
            // name whole but put most rows on two lines and left the list
            // looking ragged. Shrinking is the third option and the one this
            // project already uses elsewhere: one line, every character still
            // there, and only the rows that need it step down a little.
            Row(
                // Exactly the width the shared scale was measured against, so
                // the name that set that scale fits at it and no row has to
                // shrink further. Falls back to a weighted slot where no list
                // has worked the width out.
                nameSlotWidth?.let { Modifier.width(it) } ?: Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.tight),
            ) {
                ShrinkToFitText(
                    name,
                    style = typo(Typo.SUBHEADLINE).let {
                        it.copy(fontSize = it.fontSize * nameScale)
                    },
                    modifier = Modifier.weight(1f, fill = false),
                    // Normally the list has already chosen a size that fits
                    // every name, and nothing here shrinks. This floor is for
                    // the one case that remains: a name so long the shared
                    // scale hit its own floor rather than follow it, which
                    // leaves that row — and only that row — to shrink.
                    minScale = 0.8f,
                )
                // Only where it adds something. Most areas carry their own
                // division inside their name, and repeating it there costs the
                // name the width it needed — see divisionLabel.
                divisionLabel(name, area.division.displayName(loc.language))?.let { division ->
                    Text(
                        division,
                        style = typo(Typo.MICRO),
                        color = colors.secondaryText,
                    )
                }
            }
            // The count gets a column of its own rather than taking its
            // intrinsic width, so the figures line up down the list instead of
            // each being pushed by the badge beside it — "16,331", "10,015" and
            // "10,509" ended at three different x positions.
            ShrinkToFitText(
                loc.num(area.seasonCases),
                style = typo(Typo.SUBHEADLINE)
                    .tabularFigures()
                    .copy(textAlign = TextAlign.End),
                modifier = Modifier.width(countWidth),
                minScale = 0.7f,
            )
            // The chevron's space is reserved even where there is no chevron.
            // The current area is not tappable, and without this its badge ran
            // into the gutter the other rows leave, so one badge in the list
            // sat further right than the rest.
            // The badge gets a column too. Right-aligning it alone was not
            // enough: a wide band ("Moderate", "মাঝারি") pushed the count
            // column left of a narrow one ("High", "বেশি"), so the figures
            // still stepped in and out down the list even though the badges
            // lined up.
            Box(Modifier.width(ROW_BADGE_WIDTH), contentAlignment = Alignment.CenterEnd) {
                RiskBadge(area.risk, compact = true)
            }
            Box(Modifier.size(16.dp)) {
                if (onClick != null) {
                    Icon(
                        DwIcons.chevronRight,
                        contentDescription = null,
                        tint = colors.tertiaryText,
                        modifier = Modifier.size(16.dp),
                    )
                }
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
