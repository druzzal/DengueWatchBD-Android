package com.denguewatch.bd.feature.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
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
import com.denguewatch.bd.ui.theme.tabularFigures
import com.denguewatch.bd.ui.theme.typo
import kotlin.math.abs

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
