package com.denguewatch.bd.feature.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.i18n.labelKey
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.ui.theme.Card
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Shapes
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.icon
import com.denguewatch.bd.ui.theme.tabularFigures
import com.denguewatch.bd.ui.theme.typo
import kotlin.math.abs

// MARK: - Structure

/**
 * A section heading with optional subtitle and trailing control.
 *
 * Sections are separated by whitespace and a heading, not by nesting everything
 * in another card.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {},
) {
    // Side by side while both fit; otherwise the accessory drops below rather
    // than squeezing the title onto two lines — what iOS does with
    // ViewThatFits. "Today in Bangladesh" beside the DGHS badge is exactly wide
    // enough to need it: side by side it broke as "Today in / Bangladesh".
    //
    // The gap allowed beside the title is the hairline, not the usual tight
    // spacing: on a 384dp phone the pair missed by four pixels at 8dp and
    // stacked, where the iPhone it was drawn for keeps them on one line.
    Layout(
        modifier = modifier.fillMaxWidth().semantics { heading() },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = typo(Typo.SECTION_TITLE))
                if (subtitle != null) {
                    Text(subtitle, style = typo(Typo.CAPTION), color = Dw.colors.secondaryText)
                }
            }
            Box { trailing() }
        },
    ) { (titleBlock, accessory), constraints ->
        val width = constraints.maxWidth
        val gap = Space.tight.roundToPx()
        val besideGap = Space.hair.roundToPx()
        val accessoryPlaceable = accessory.measure(constraints.copy(minWidth = 0, minHeight = 0))
        // A header with no accessory must not gain a gap where one would have
        // gone; most headers in the app are title-only.
        val hasAccessory = accessoryPlaceable.width > 0
        val besideWidth = if (hasAccessory) accessoryPlaceable.width + besideGap else 0
        val fitsBeside = titleBlock.maxIntrinsicWidth(constraints.maxHeight) + besideWidth <= width

        if (fitsBeside || !hasAccessory) {
            val titlePlaceable = titleBlock.measure(
                Constraints(maxWidth = (width - besideWidth).coerceAtLeast(0))
            )
            layout(width, maxOf(titlePlaceable.height, accessoryPlaceable.height)) {
                titlePlaceable.place(0, 0)
                if (hasAccessory) accessoryPlaceable.place(width - accessoryPlaceable.width, 0)
            }
        } else {
            val titlePlaceable = titleBlock.measure(Constraints(maxWidth = width))
            layout(width, titlePlaceable.height + gap + accessoryPlaceable.height) {
                titlePlaceable.place(0, 0)
                accessoryPlaceable.place(0, titlePlaceable.height + gap)
            }
        }
    }
}

/** A card that leads with a title and, optionally, a subtitle and accessory. */
/**
 * A card with its heading set outside it, in grey — iOS's grouped-list shape.
 *
 * [CardSection] puts the heading inside the card in ink, which reads as a title
 * belonging to the content. A grouped header instead labels the group from
 * outside it, so the card holds only the rows. The About screen and the map's
 * area list are built this way because that is how the iOS screens they mirror
 * are built; the rest of the app still uses [CardSection].
 */
@Composable
fun GroupedSection(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    footnote: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.tight)) {
        Row(
            Modifier.padding(start = Space.card, end = Space.card),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.tight),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Dw.colors.secondaryText,
                    modifier = Modifier.size(17.dp),
                )
            }
            Text(title, style = typo(Typo.SUBHEADLINE), color = Dw.colors.secondaryText)
        }
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(Space.row), content = content)
        }
        if (footnote != null) {
            Text(
                footnote,
                style = typo(Typo.MICRO),
                color = Dw.colors.secondaryText,
                modifier = Modifier.padding(horizontal = Space.card),
            )
        }
    }
}

/**
 * The same group of rows with no heading above it.
 *
 * iOS forms use headerless sections freely — rows that follow on from the
 * section before them, where a heading would only repeat the screen's own
 * title. Separate from [GroupedSection] rather than a nullable title on it, so
 * a caller cannot accidentally pass an empty string and leave a gap where a
 * heading used to be.
 */
@Composable
fun PlainSection(
    modifier: Modifier = Modifier,
    footnote: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.tight)) {
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(Space.row), content = content)
        }
        if (footnote != null) {
            Text(
                footnote,
                style = typo(Typo.MICRO),
                color = Dw.colors.secondaryText,
                modifier = Modifier.padding(horizontal = Space.card),
            )
        }
    }
}

@Composable
fun CardSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accessory: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.row)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(Space.tight),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = typo(Typo.HEADLINE))
                    if (subtitle != null) {
                        Text(subtitle, style = typo(Typo.CAPTION), color = Dw.colors.secondaryText)
                    }
                }
                accessory()
            }
            content()
        }
    }
}

/** A screen heading drawn inside the content column, for wide layouts. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = typo(Typo.HERO),
        modifier = modifier.fillMaxWidth().semantics { heading() },
    )
}

/** The hairline rule used between rows inside a card. */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(0.5.dp).background(Dw.colors.hairline))
}

// MARK: - Risk

/** Risk as colour + icon + word. Never colour alone. */
@Composable
fun RiskBadge(
    risk: RiskLevel,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val loc = LocalLocalization.current
    val label = loc.t(risk.labelKey)
    Row(
        modifier = modifier
            .clip(Shapes.chip)
            .background(Dw.colors.riskSoft(risk))
            .border(0.5.dp, Dw.colors.riskTint(risk).copy(alpha = 0.25f), Shapes.chip)
            .padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 4.dp else 6.dp,
            )
            .clearAndSetSemantics { contentDescription = loc.t("risk.a11y", label) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.hair),
    ) {
        Icon(
            risk.icon,
            contentDescription = null,
            tint = Dw.colors.riskInk(risk),
            modifier = Modifier.size(if (compact) 12.dp else 14.dp),
        )
        // A band name is never broken across lines. Inside the fixed badge
        // gutter of a ranked row, "Moderate" was 2dp wider than the space left
        // after the icon and padding, so it wrapped to "Moderat" / "e" — a word
        // split mid-syllable, which reads as a rendering fault rather than as a
        // risk level. Shrinking is the project's answer to a tight fit; where
        // the badge sizes to its content, as it does on the home card, there is
        // room at full size and nothing shrinks.
        ShrinkToFitText(
            label,
            style = typo(if (compact) Typo.MICRO else Typo.CAPTION),
            color = Dw.colors.riskInk(risk),
            minScale = 0.8f,
        )
    }
}

/**
 * Four segments, filled to the current band — a second, non-colour channel for
 * how severe things are.
 *
 * Filled segments step up in strength toward the current band, so the meter
 * reads as a scale rather than a solid bar. At Severe every segment is lit, and
 * a single flat colour left it looking like one long block with no sense of how
 * far up the scale it sat. One hue, light to dark — the band's own colour still
 * carries the meaning.
 */
@Composable
fun RiskMeter(risk: RiskLevel, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 6.dp) {
    fun opacity(level: RiskLevel): Float {
        if (level.ordinal > risk.ordinal) return 0f
        val distance = risk.ordinal - level.ordinal
        return maxOf(0.4f, 1f - distance * 0.2f)
    }

    Row(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        RiskLevel.entries.forEach { level ->
            val fill by animateFloatAsState(opacity(level), label = "riskSegment")
            Box(
                Modifier
                    .weight(1f)
                    .height(height)
                    .clip(CircleShape)
                    .background(Dw.colors.hairline)
                    .background(Dw.colors.riskTint(risk).copy(alpha = fill))
            )
        }
    }
}

/**
 * Marks where a number came from. Official surveillance and educational
 * guidance must never look like the same class of information.
 */
enum class SourceKind { OFFICIAL, GUIDANCE }

@Composable
fun SourceBadge(kind: SourceKind, modifier: Modifier = Modifier, detail: String? = null) {
    val loc = LocalLocalization.current
    val label = loc.t(if (kind == SourceKind.OFFICIAL) "source.official" else "source.guidance")
    Row(
        modifier = modifier
            .clip(Shapes.chip)
            .background(Dw.colors.mutedInk.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.hair),
    ) {
        Icon(
            if (kind == SourceKind.OFFICIAL) DwIcons.official else DwIcons.guidance,
            contentDescription = null,
            tint = Dw.colors.secondaryText,
            modifier = Modifier.size(11.dp),
        )
        Text(label, style = typo(Typo.MICRO), color = Dw.colors.secondaryText)
        if (detail != null) {
            Text("· $detail", style = typo(Typo.MICRO), color = Dw.colors.secondaryText)
        }
    }
}

// MARK: - Numbers

/**
 * Week-on-week movement. Rising case counts are bad news, so the arrow and
 * wording say so rather than leaving colour to imply it.
 */
@Composable
fun TrendIndicator(
    change: Double?,
    modifier: Modifier = Modifier,
    inverted: Boolean = false,
) {
    val loc = LocalLocalization.current
    val isRise = (change ?: 0.0) >= 0
    val tint = when {
        change == null || abs(change) < 0.005 -> Dw.colors.secondaryText
        if (inverted) !isRise else isRise -> Dw.colors.upIsBad
        else -> Dw.colors.downIsGood
    }
    val text = loc.percentChange(change)

    Row(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = loc.t("dash.delta.a11y", text)
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            if (isRise) DwIcons.trendUp else DwIcons.trendDown,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Text(text, style = typo(Typo.CAPTION).tabularFigures(), color = tint)
    }
}

/** One metric: a large figure, a quiet label, an optional trend and sparkline. */
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    change: Double? = null,
    caption: String? = null,
    accent: Color? = null,
    series: List<Double> = emptyList(),
) {
    val tint = accent ?: Dw.colors.cases
    Card(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.tight)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                ShrinkToFitText(
                    value,
                    style = typo(Typo.STAT_VALUE).tabularFigures(),
                    modifier = Modifier.weight(1f, fill = false),
                    minScale = 0.5f,
                )
                if (unit != null) {
                    Text(unit, style = typo(Typo.STAT_UNIT), color = Dw.colors.secondaryText)
                }
            }

            Text(
                label,
                style = typo(Typo.CAPTION),
                color = Dw.colors.secondaryText,
            )

            if (series.isNotEmpty()) {
                Sparkline(
                    values = series,
                    color = tint,
                    modifier = Modifier.fillMaxWidth().height(26.dp),
                )
            }

            if (change != null || caption != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.hair + 2.dp),
                ) {
                    if (change != null) TrendIndicator(change)
                    if (caption != null) {
                        // One line, shrunk to fit — the same treatment iOS gives
                        // it, so the card keeps the single-line rhythm of the
                        // design rather than growing a second line in one
                        // language and not the other.
                        //
                        // The floor has to clear the longest caption in either
                        // language on the *narrowest* card that uses one, which
                        // is the half-width pair on the area screen. 0.7 was set
                        // for "গত সপ্তাহের তুলনায়"; the weekly caption is now
                        // "আগের ৭ দিনের তুলনায়", which is longer, and at 0.7 it
                        // clipped to "আগের ৭ দিনের" — a phrase that reads as
                        // finished rather than as cut.
                        ShrinkToFitText(
                            caption,
                            style = typo(Typo.MICRO),
                            color = Dw.colors.secondaryText,
                            modifier = Modifier.weight(1f),
                            minScale = 0.55f,
                        )
                    }
                }
            }
        }
    }
}

/** A legend is present whenever a plot carries two or more series. */
data class LegendItem(val label: String, val color: Color, val isLine: Boolean = false)

@Composable
fun ChartLegend(items: List<LegendItem>, modifier: Modifier = Modifier) {
    // Flows onto a second line rather than squeezing.
    //
    // A plain Row divided the width between however many keys there were, and
    // the weekly area chart needs five — four bands and the average. On a 360dp
    // handset the last one was given so little room that "3-week average" broke
    // mid-word into "3-week / averag / e". A legend key is a label, not a
    // paragraph: it either fits on its line or takes the next one.
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.row),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (item.isLine) {
                    Box(
                        Modifier.width(14.dp).height(2.5.dp)
                            .clip(CircleShape).background(item.color)
                    )
                } else {
                    Box(
                        Modifier.size(9.dp)
                            .clip(RoundedCornerShape(2.dp)).background(item.color)
                    )
                }
                Text(item.label, style = typo(Typo.MICRO), color = Dw.colors.secondaryText)
            }
        }
    }
}

/**
 * Ordinal scale legend for the risk bands.
 *
 * Band names differ a lot in width between English and Bengali, and at larger
 * font sizes four of them will not fit on one line. Rather than truncating a
 * band name — which would leave "Modera…" standing for a risk level — the
 * legend wraps onto a second row.
 */
@Composable
fun RiskScaleLegend(modifier: Modifier = Modifier) {
    val loc = LocalLocalization.current
    FlowRowCompat(
        modifier = modifier.fillMaxWidth(),
        horizontalSpacing = Space.tight,
        verticalSpacing = Space.hair + 2.dp,
    ) {
        RiskLevel.entries.forEach { level ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.hair),
            ) {
                Box(
                    Modifier.width(11.dp).height(8.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Dw.colors.riskTint(level))
                )
                Text(
                    loc.t(level.labelKey),
                    style = typo(Typo.MICRO),
                    color = Dw.colors.secondaryText,
                )
            }
        }
    }
}

/**
 * A minimal flow row.
 *
 * Compose's own `FlowRow` is still experimental; this does the one thing the
 * legends need — wrap onto the next line when the row is full — without opting
 * the whole app into an unstable API.
 */
@Composable
fun FlowRowCompat(
    modifier: Modifier = Modifier,
    horizontalSpacing: androidx.compose.ui.unit.Dp = Space.tight,
    verticalSpacing: androidx.compose.ui.unit.Dp = Space.tight,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val hGap = horizontalSpacing.roundToPx()
        val vGap = verticalSpacing.roundToPx()
        val maxWidth = constraints.maxWidth

        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }

        val rows = mutableListOf<MutableList<androidx.compose.ui.layout.Placeable>>()
        var current = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentWidth = 0
        placeables.forEach { placeable ->
            val extra = if (current.isEmpty()) 0 else hGap
            if (current.isNotEmpty() && currentWidth + extra + placeable.width > maxWidth) {
                rows.add(current)
                current = mutableListOf()
                currentWidth = 0
            }
            currentWidth += (if (current.isEmpty()) 0 else hGap) + placeable.width
            current.add(placeable)
        }
        if (current.isNotEmpty()) rows.add(current)

        val height = rows.sumOf { row -> row.maxOf { it.height } } +
            vGap * (rows.size - 1).coerceAtLeast(0)

        layout(maxWidth, height) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                row.forEach { placeable ->
                    placeable.placeRelative(x, y)
                    x += placeable.width + hGap
                }
                y += row.maxOf { it.height } + vGap
            }
        }
    }
}

/** A label and its figure on one row. */
@Composable
fun LabeledFigure(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The label takes what it needs and the value takes the rest. It used to
        // be the other way round, and a long value then squeezed the label to a
        // sliver: "Dataset" came down the card one letter per line. A value that
        // outgrows its side now wraps on its own side instead.
        Text(
            label,
            style = typo(Typo.CALLOUT),
            color = Dw.colors.secondaryText,
        )
        Spacer(Modifier.width(Space.tight))
        Text(
            value,
            style = typo(Typo.CALLOUT, weight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                .tabularFigures(),
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * A label with its value beneath it, for values too long to sit alongside.
 *
 * iOS's `LabeledContent` collapses to this shape on its own when the value will
 * not fit; Compose has no equivalent, so the few rows that need it say so.
 */
@Composable
fun LabeledBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = typo(Typo.CALLOUT))
        Text(value, style = typo(Typo.CALLOUT), color = Dw.colors.secondaryText)
    }
}

/**
 * Text that shrinks to fit rather than truncating.
 *
 * The equivalent of iOS's `minimumScaleFactor`, and it is not cosmetic here.
 * Bengali sets wider than Latin at the same point size, so the two places that
 * carry a single decisive value both overflowed in Bengali: the risk band
 * "মাঝারি" rendered as "মাঝা…", and 42,590 lost its last digit to the card
 * edge. A truncated risk band and a case count wrong by a digit are the two
 * worst things this app could show, so they shrink instead.
 *
 * Built on Compose's own auto-sizing rather than a hand-rolled measure loop:
 * the first attempt here watched `hasVisualOverflow` from `onTextLayout` and
 * stepped the size down, which never fired — with `softWrap = false` the text
 * lays out at full intrinsic width and reports no overflow, then gets clipped
 * by the parent instead. Losing the ellipsis made it worse than before, since
 * a clipped "মাঝা" reads as a word rather than as a truncation.
 *
 * [minScale] is a floor, not a target: text only shrinks when it must.
 */
@Composable
fun ShrinkToFitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    minScale: Float = 0.6f,
) {
    val ink = when {
        color != Color.Unspecified -> color
        style.color != Color.Unspecified -> style.color
        else -> Dw.colors.primaryText
    }
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = ink),
        maxLines = 1,
        // softWrap stays on. With it off the text lays out at its full intrinsic
        // width on one line and never reports overflow, so auto-sizing never
        // engages and the parent clips instead — which is exactly how "মাঝারি"
        // came out as "মাঝা".
        autoSize = TextAutoSize.StepBased(
            minFontSize = style.fontSize * minScale,
            maxFontSize = style.fontSize,
            stepSize = 0.5.sp,
        ),
    )
}

/**
 * A compact figure used three-across inside a sheet.
 *
 * The value carries the colour, not the tile: the rate tile takes the area's own
 * risk tint so the number a reader is asked to judge is the one wearing the
 * band, while counts stay in the series blue that means "cases" everywhere else
 * in the app.
 */
@Composable
fun MiniStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    val tint = accent ?: Dw.colors.cases
    Card(modifier, shape = Shapes.control, padding = Space.row) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.hair)) {
            ShrinkToFitText(
                value,
                style = typo(Typo.HEADLINE).tabularFigures(),
                color = tint,
                minScale = 0.6f,
            )
            Text(
                label,
                style = typo(Typo.MICRO),
                color = Dw.colors.secondaryText,
            )
        }
    }
}
