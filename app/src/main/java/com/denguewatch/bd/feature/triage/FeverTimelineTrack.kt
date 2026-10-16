package com.denguewatch.bd.feature.triage

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.feature.shared.FlowRowCompat
import com.denguewatch.bd.feature.shared.InlineNote
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.tabularFigures
import com.denguewatch.bd.ui.theme.typo

/**
 * Where someone is in the course of a dengue fever, drawn as a track of ten
 * days. Ported from iOS's `FeverTimelineView`, which the checker and its
 * result have always shown there.
 *
 * The point of showing it at all is one clinical fact that surprises people:
 * the dangerous window is when the fever *falls*, not when it is highest. A
 * reader who feels better on day five and stops paying attention is exactly
 * the reader this is for.
 *
 * Phase boundaries come from [TriageEngine.FeverPhase], so this cannot drift
 * from the sentence shown beside it.
 *
 * @param feverDay 1-based day of fever, or null when no start date was given.
 */
@Composable
fun FeverTimelineTrack(feverDay: Int?, modifier: Modifier = Modifier) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val phase = feverDay?.let { TriageEngine.FeverPhase.of(it - 1) }

    // One sentence for TalkBack, rather than ten unlabelled bars.
    val spoken = if (feverDay == null || phase == null) {
        loc.t("phase.a11y.unknown")
    } else {
        loc.t("phase.a11y.day", loc.num(feverDay), loc.t(phase.nameKey))
    }

    Column(
        modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(Space.row),
    ) {
        Row(
            Modifier.fillMaxWidth().height(40.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            (1..10).forEach { day ->
                val dayPhase = TriageEngine.FeverPhase.of(day - 1)
                val isCurrent = day == feverDay
                // The current day is marked by height and weight as well as
                // colour, so it survives colour blindness and greyscale.
                val barHeight by animateDpAsState(if (isCurrent) 22.dp else 12.dp, label = "day $day")
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(barHeight)
                            .clip(RoundedCornerShape(3.dp))
                            .background(tint(dayPhase).copy(alpha = if (isCurrent) 1f else 0.28f)),
                    )
                    Text(
                        loc.num(day),
                        style = typo(Typo.MICRO, if (isCurrent) FontWeight.Bold else FontWeight.Normal)
                            .tabularFigures(),
                        color = if (isCurrent) colors.primaryText else colors.secondaryText,
                    )
                }
            }
        }

        FlowRowCompat(horizontalSpacing = Space.tight, verticalSpacing = 6.dp) {
            TriageEngine.FeverPhase.entries.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(tint(item)))
                    Text(
                        loc.t(item.nameKey),
                        style = typo(Typo.MICRO, if (item == phase) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (item == phase) colors.primaryText else colors.secondaryText,
                    )
                }
            }
        }

        if (phase == TriageEngine.FeverPhase.CRITICAL) {
            // Only raised while it applies, so it stays a warning rather than
            // becoming background text people stop reading.
            InlineNote(
                detail = loc.t("phase.critical.note"),
                icon = DwIcons.riskHigh,
                tint = colors.riskInk(RiskLevel.SEVERE),
            )
        }
    }
}

@Composable
private fun tint(phase: TriageEngine.FeverPhase): Color {
    val colors = Dw.colors
    return when (phase) {
        TriageEngine.FeverPhase.FEBRILE -> colors.riskTint(RiskLevel.MODERATE)
        TriageEngine.FeverPhase.CRITICAL -> colors.riskTint(RiskLevel.SEVERE)
        TriageEngine.FeverPhase.RECOVERY -> colors.riskTint(RiskLevel.LOW)
    }
}
