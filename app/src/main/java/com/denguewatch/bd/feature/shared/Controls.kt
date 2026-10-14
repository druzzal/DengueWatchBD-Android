package com.denguewatch.bd.feature.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.ui.theme.cardSurface
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Hit
import com.denguewatch.bd.ui.theme.Radius
import com.denguewatch.bd.ui.theme.Shapes
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.icon
import com.denguewatch.bd.ui.theme.pressable
import com.denguewatch.bd.ui.theme.rememberPressSource
import com.denguewatch.bd.ui.theme.typo

/**
 * The one prominent button style, so "the main action" looks the same
 * everywhere and nothing else competes with it.
 */
@Composable
fun PrimaryActionButton(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val fill = tint ?: Dw.colors.accent
    val source = rememberPressSource()
    val shape = RoundedCornerShape(Radius.control + 2.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.pressable(source) else Modifier)
            .heightIn(min = Hit.minimum + 6.dp)
            .clip(shape)
            .background(if (enabled) fill else fill.copy(alpha = 0.35f))
            .clickable(
                enabled = enabled,
                interactionSource = source,
                indication = ripple(color = Color.White),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = Space.card, vertical = Space.row),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.tight))
        }
        Text(title, style = typo(Typo.HEADLINE), color = Color.White)
    }
}

/** Secondary action: same footprint, quieter treatment. */
@Composable
fun SecondaryActionButton(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val fill = tint ?: Dw.colors.accent
    val source = rememberPressSource()
    val shape = RoundedCornerShape(Radius.control + 2.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressable(source)
            .heightIn(min = Hit.minimum)
            .clip(shape)
            .background(fill.copy(alpha = 0.12f))
            .clickable(
                interactionSource = source,
                indication = ripple(color = fill),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = Space.card, vertical = Space.row),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fill, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.tight))
        }
        Text(title, style = typo(Typo.SUBHEADLINE, FontWeight.SemiBold), color = fill)
    }
}

/**
 * An advisory banner: icon, title, one line of explanation, one action.
 * Deliberately calm — no flashing, no full-bleed red.
 */
@Composable
fun AlertCard(
    risk: RiskLevel,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionTitle: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = Dw.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .background(colors.riskSoft(risk))
            .border(1.dp, colors.riskTint(risk).copy(alpha = 0.28f), Shapes.card)
            .padding(Space.card),
        horizontalArrangement = Arrangement.spacedBy(Space.row),
    ) {
        Icon(
            risk.icon,
            contentDescription = null,
            tint = colors.riskInk(risk),
            modifier = Modifier.size(24.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(Space.hair + 2.dp)) {
            Text(title, style = typo(Typo.HEADLINE))
            Text(message, style = typo(Typo.CALLOUT), color = colors.secondaryText)

            if (actionTitle != null && onAction != null) {
                Row(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clip(Shapes.chip)
                        .clickable(role = Role.Button, onClick = onAction)
                        .padding(vertical = 6.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        actionTitle,
                        style = typo(Typo.CAPTION, FontWeight.SemiBold),
                        color = colors.riskInk(risk),
                    )
                    Icon(
                        DwIcons.chevronRight,
                        contentDescription = null,
                        tint = colors.riskInk(risk),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

/**
 * Quiet inline note — disclosures and caveats that must be visible but must not
 * compete with the data.
 */
@Composable
fun InlineNote(
    detail: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = DwIcons.info,
    title: String? = null,
    /**
     * Colours the symbol and its ground for a note that carries weight — a
     * plan that has gone stale, a reading a home cuff cannot be trusted on.
     * The text stays in the reading colour either way, so the note is never
     * legible by colour alone.
     */
    tint: Color? = null,
) {
    val colors = Dw.colors
    val ink = tint ?: colors.mutedInk
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Shapes.control)
            .background(ink.copy(alpha = if (tint == null) 0.07f else 0.10f))
            .padding(Space.row),
        horizontalArrangement = Arrangement.spacedBy(Space.tight + 2.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(16.dp).padding(top = 1.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (title != null) {
                Text(title, style = typo(Typo.CAPTION, FontWeight.SemiBold))
            }
            Text(detail, style = typo(Typo.MICRO), color = colors.secondaryText)
        }
    }
}

/** A row in a list of places you can go for help. */
@Composable
fun FacilityRow(
    name: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    distanceText: String? = null,
    icon: ImageVector = DwIcons.hospital,
    onDirections: (() -> Unit)? = null,
    onCall: (() -> Unit)? = null,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Space.row),
        horizontalArrangement = Arrangement.spacedBy(Space.row),
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(name, style = typo(Typo.SUBHEADLINE, FontWeight.Medium))
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle,
                    style = typo(Typo.CAPTION),
                    color = colors.secondaryText,
                )
            }
            if (distanceText != null) {
                Text(distanceText, style = typo(Typo.MICRO), color = colors.accent)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Space.tight)) {
            if (onCall != null) {
                CircleActionButton(
                    icon = DwIcons.phone,
                    tint = colors.downIsGood,
                    contentDescription = loc.t("care.call"),
                    onClick = onCall,
                )
            }
            if (onDirections != null) {
                CircleActionButton(
                    icon = DwIcons.directions,
                    tint = colors.accent,
                    contentDescription = loc.t("care.directions"),
                    onClick = onDirections,
                )
            }
        }
    }
}

@Composable
fun CircleActionButton(
    icon: ImageVector,
    tint: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(Hit.minimum - 8.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/**
 * A segmented control.
 *
 * Material has no equivalent that fits: `SingleChoiceSegmentedButtonRow` draws
 * outlined pills with check marks, which is a heavier, more decorated thing
 * than the quiet range switch these charts want above them.
 */
@Composable
fun <T> SegmentedPicker(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    val colors = Dw.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Shapes.control)
            .background(colors.mutedInk.copy(alpha = 0.10f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.control - 3.dp))
                    .background(if (isSelected) colors.card else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(option) }
                    .defaultMinSize(minHeight = Hit.minimum - 12.dp)
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = typo(
                        Typo.CAPTION,
                        if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (isSelected) colors.primaryText else colors.secondaryText,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A tappable settings-style row with a title, detail and chevron. */
@Composable
fun DisclosureRow(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val colors = Dw.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = Hit.minimum)
            .padding(vertical = Space.tight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.row),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = typo(Typo.SUBHEADLINE, FontWeight.SemiBold))
            if (detail != null) {
                Text(detail, style = typo(Typo.CAPTION), color = colors.secondaryText)
            }
        }
        Icon(
            DwIcons.chevronRight,
            contentDescription = null,
            tint = colors.tertiaryText,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** A thin separator used between stacked full-width rows. */
@Composable
fun RowSpacer(height: androidx.compose.ui.unit.Dp = Space.row) {
    Spacer(Modifier.height(height))
}

/**
 * A round chip in the top bar, as iOS draws its toolbar controls.
 *
 * A plain icon button would sit flat on the plane; these read as small raised
 * surfaces, which is what lets the header carry controls without a bar of its
 * own behind them — the large title underneath then belongs to the content
 * rather than to a chrome bar.
 */
@Composable
fun CircleChipButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    label: String? = null,
) {
    val colors = Dw.colors
    Box(
        modifier
            .size(Hit.minimum)
            .cardSurface(shape = CircleShape)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        when {
            icon != null -> Icon(
                icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(22.dp),
            )
            label != null -> Text(
                label,
                style = typo(Typo.CAPTION, FontWeight.SemiBold),
                color = colors.accent,
            )
        }
    }
}

/**
 * The search field above the area list.
 *
 * A plain rounded field rather than Material's `SearchBar`: that one expands
 * into a full-screen results surface with its own back affordance, which is a
 * different interaction from the one iOS has here — where typing simply filters
 * the list underneath in place.
 */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = Dw.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(Shapes.chip)
            .background(colors.card)
            .border(1.dp, colors.hairline, Shapes.chip)
            .padding(horizontal = Space.card, vertical = Space.row),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.row),
    ) {
        Icon(
            DwIcons.search,
            contentDescription = null,
            tint = colors.secondaryText,
            modifier = Modifier.size(20.dp),
        )
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(placeholder, style = typo(Typo.BODY), color = colors.secondaryText)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = typo(Typo.BODY).copy(color = colors.primaryText),
                cursorBrush = SolidColor(colors.accent),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                DwIcons.close,
                contentDescription = null,
                tint = colors.secondaryText,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onQueryChange("") },
            )
        }
    }
}
