package com.denguewatch.bd.feature.shared

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.i18n.labelKey
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Shapes
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.icon
import com.denguewatch.bd.ui.theme.typo

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
