package com.denguewatch.bd.feature.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.ui.theme.Card
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.icon
import com.denguewatch.bd.ui.theme.typo

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
