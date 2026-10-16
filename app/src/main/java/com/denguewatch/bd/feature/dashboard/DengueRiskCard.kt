package com.denguewatch.bd.feature.dashboard

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.i18n.guidanceKey
import com.denguewatch.bd.i18n.labelKey
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Motion
import com.denguewatch.bd.ui.theme.Shapes
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.icon
import com.denguewatch.bd.ui.theme.pressable
import com.denguewatch.bd.ui.theme.rememberPressSource
import com.denguewatch.bd.ui.theme.typo
import com.denguewatch.bd.feature.shared.ShrinkToFitText
import com.denguewatch.bd.feature.shared.RiskMeter

/**
 * The one thing the screen exists to answer: what is the dengue risk where I
 * am, and is it getting worse?
 *
 * The card is tinted by band rather than flooded with it — a severe reading
 * should be unmistakable without turning the phone into a red rectangle.
 */
@Composable
fun DengueRiskCard(
    risk: RiskLevel,
    areaName: String,
    change: Double?,
    lastUpdatedMillis: Long?,
    modifier: Modifier = Modifier,
    incidence: Double? = null,
    /** The area was found from the phone's position rather than chosen. */
    isCurrentLocation: Boolean = false,
    /**
     * How many people the figure covers, said when the area came from the
     * phone: a located reading is an average over a whole division, not the
     * reader's neighbourhood.
     */
    coverage: String? = null,
    reduceMotion: Boolean = false,
    onClick: () -> Unit,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val ink = colors.riskInk(risk)
    val tint = colors.riskTint(risk)
    val source = rememberPressSource()

    val trendKey = when {
        change == null -> "risk.trend.steady"
        change >= 0.05 -> "risk.trend.rising"
        change <= -0.05 -> "risk.trend.falling"
        else -> "risk.trend.steady"
    }
    val trendIcon = when {
        change == null -> DwIcons.trendFlat
        change >= 0.05 -> DwIcons.trendUp
        change <= -0.05 -> DwIcons.trendDown
        else -> DwIcons.trendFlat
    }

    val summary = buildList {
        add(loc.t("risk.card.title"))
        add(loc.t(risk.labelKey))
        add(if (isCurrentLocation) loc.t("risk.card.hereNow", areaName) else areaName)
        if (coverage != null) add(coverage)
        if (incidence != null) add(loc.t("risk.a11y.rate", loc.decimal(incidence)))
        add(loc.t(trendKey))
        add(loc.t(risk.guidanceKey))
        if (lastUpdatedMillis != null) {
            add(loc.t("risk.card.updated", loc.reportAge(lastUpdatedMillis)))
        }
    }.joinToString(", ")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .pressable(source)
            .clip(Shapes.hero)
            .background(colors.card)
            // A flat wash made the hero card the dullest thing on the screen.
            // The gradient is shallow on purpose: it gives the card a light
            // source without shifting the band's colour enough to be mistaken
            // for a second one.
            .background(
                Brush.linearGradient(
                    listOf(colors.riskSoft(risk), colors.riskSoft(risk).copy(alpha = 0.063f)),
                )
            )
            .border(1.dp, tint.copy(alpha = 0.35f), Shapes.hero)
            .clickable(
                interactionSource = source,
                indication = ripple(color = tint),
                onClick = onClick,
            )
            .padding(Space.card + 4.dp)
            .clearAndSetSemantics {
                contentDescription = summary
                role = Role.Button
                onClick(label = loc.t("risk.card.hint"), action = null)
            },
        verticalArrangement = Arrangement.spacedBy(Space.row),
    ) {
        // Header
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LivePulse(risk = risk, reduceMotion = reduceMotion)
            Spacer(Modifier.width(Space.tight))
            Text(
                loc.t("risk.card.title").uppercase(),
                style = typo(Typo.MICRO, FontWeight.SemiBold).copy(letterSpacing = 0.09.em),
                color = colors.secondaryText,
                modifier = Modifier.weight(1f),
            )
            Icon(
                DwIcons.chevronRight,
                contentDescription = null,
                tint = colors.tertiaryText,
                modifier = Modifier.size(18.dp),
            )
        }

        // The risk word
        Column(verticalArrangement = Arrangement.spacedBy(Space.hair)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.tight + 2.dp),
            ) {
                Text(
                    loc.t(risk.labelKey),
                    style = typo(Typo.HERO),
                    color = ink,
                )
                Icon(risk.icon, contentDescription = null, tint = ink, modifier = Modifier.size(24.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isCurrentLocation) {
                    Icon(
                        DwIcons.myLocation,
                        contentDescription = null,
                        tint = colors.secondaryText,
                        modifier = Modifier.size(13.dp),
                    )
                }
                Text(
                    areaName,
                    style = typo(Typo.SUBHEADLINE),
                    color = colors.secondaryText,
                )
            }
            if (coverage != null) {
                Text(
                    coverage,
                    style = typo(Typo.MICRO),
                    color = colors.mutedInk,
                )
            }
        }

        RiskMeter(risk = risk, height = 7.dp)

        Text(
            loc.t(risk.guidanceKey),
            style = typo(Typo.CALLOUT),
            color = colors.primaryText.copy(alpha = 0.85f),
        )

        // Footer: the trend, then when the figures are from.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.tight),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(trendIcon, contentDescription = null, tint = ink, modifier = Modifier.size(15.dp))
                Text(loc.t(trendKey), style = typo(Typo.CAPTION, FontWeight.SemiBold), color = ink)
            }
            if (lastUpdatedMillis != null) {
                Text("·", style = typo(Typo.CAPTION), color = colors.tertiaryText)
                Text(
                    loc.t("risk.card.updated", loc.reportAge(lastUpdatedMillis)),
                    style = typo(Typo.MICRO),
                    color = colors.secondaryText,
                )
            }
        }
    }
}

/** A slow live pulse, not a blinking alarm. */
@Composable
private fun LivePulse(risk: RiskLevel, reduceMotion: Boolean) {
    val colors = Dw.colors
    val tint = colors.riskTint(risk)

    val transition = rememberInfiniteTransition(label = "pulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(Motion.PULSE_MILLIS),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseProgress",
    )
    val phase = if (reduceMotion) 0f else progress

    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(14.dp)
                .scale(0.9f + phase * 0.6f)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.35f * (1f - phase)))
        )
        Box(Modifier.size(7.dp).clip(CircleShape).background(tint))
    }
}
