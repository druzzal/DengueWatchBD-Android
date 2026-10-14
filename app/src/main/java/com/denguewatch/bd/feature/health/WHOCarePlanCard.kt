package com.denguewatch.bd.feature.health

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.feature.shared.CardSection
import com.denguewatch.bd.feature.shared.InlineNote
import com.denguewatch.bd.feature.shared.SecondaryActionButton
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.model.WHOCarePlan
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Radius
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.typo

/**
 * The WHO management group the reader's own record falls in, what WHO advises
 * for it, and — always — what produced it.
 *
 * The reasons are not decoration. A plan that says "go to hospital" without
 * saying why reads as an oracle, and an oracle is exactly what a health app
 * must not be. Every line here names a box the reader ticked or a number they
 * wrote down.
 */
@Composable
fun WHOCarePlanCard(
    plan: WHOCarePlan,
    /** When the check behind this plan was run. */
    checkedAtMillis: Long,
    /** False once the check is old enough that it describes a past day. */
    isCurrent: Boolean,
    /**
     * Lab measures well outside their range, named for the doctor rather than
     * folded into the group.
     */
    notableLabs: String?,
    onRecheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val risk = plan.group.risk

    CardSection(
        title = loc.t("who.plan.title"),
        subtitle = loc.t("who.plan.checkedAt", loc.dayAndTime(checkedAtMillis)),
        modifier = modifier,
    ) {
        if (!isCurrent) {
            // Shown rather than hidden — it is the reader's own record — but
            // never presented as advice for today.
            InlineNote(
                detail = loc.t("who.plan.stale"),
                icon = DwIcons.refresh,
                tint = colors.riskInk(RiskLevel.MODERATE),
            )
        }

        Header(plan, risk)
        Advice(plan)

        if (plan.reasons.isNotEmpty() && plan.reasons != listOf(WHOCarePlan.Reason.NONE_OF_THESE)) {
            Reasons(plan)
        }

        if (notableLabs != null) {
            InlineNote(
                detail = loc.t("care.plan.readings", notableLabs),
                icon = DwIcons.riskHigh,
                tint = colors.riskInk(RiskLevel.HIGH),
            )
            // Only where a lab value is on the card: this is the one place a
            // reader could reasonably assume their blood count moved the
            // group. It did not.
            InlineNote(detail = loc.t("care.plan.note"))
        }

        if (plan.measuredReasons.isNotEmpty()) {
            // A home cuff is not a clinician's hands, and the plan should say
            // so where it leans on one.
            InlineNote(
                detail = loc.t("who.plan.measuredNote"),
                icon = DwIcons.alerts,
                tint = colors.riskInk(RiskLevel.HIGH),
            )
        }

        SecondaryActionButton(
            title = loc.t("care.plan.recheck"),
            icon = DwIcons.check,
            onClick = onRecheck,
        )
        InlineNote(detail = loc.t("who.plan.source"))
    }
}

/**
 * The group, stated with WHO's own letter so a clinician here recognises it on
 * sight.
 */
@Composable
private fun Header(plan: WHOCarePlan, risk: RiskLevel) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val title = loc.t(plan.group.titleKey)
    val detail = loc.t(plan.group.detailKey)

    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = "$title. $detail" },
        horizontalArrangement = Arrangement.spacedBy(Space.row),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.riskSoft(risk)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                plan.group.letter,
                style = typo(Typo.TITLE, FontWeight.Bold),
                color = colors.riskInk(risk),
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                style = typo(Typo.SUBHEADLINE, FontWeight.SemiBold),
                color = colors.riskInk(risk),
            )
            Text(detail, style = typo(Typo.CAPTION), color = colors.secondaryText)
        }
    }
}

@Composable
private fun ColumnScope.Advice(plan: WHOCarePlan) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    Column(verticalArrangement = Arrangement.spacedBy(Space.tight)) {
        plan.group.adviceKeys.forEach { key ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.tight),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    DwIcons.tick,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(13.dp).padding(top = 2.dp),
                )
                Text(loc.t(key), style = typo(Typo.CALLOUT))
            }
        }
    }
}

@Composable
private fun ColumnScope.Reasons(plan: WHOCarePlan) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val lines = plan.reasons.map { loc.t(it.labelKey) }
    val heading = loc.t("who.plan.becauseTitle")

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.control))
            .background(colors.plane)
            .padding(Space.row)
            .semantics { contentDescription = "$heading: ${lines.joinToString(". ")}" },
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            heading.uppercase(),
            style = typo(Typo.MICRO, FontWeight.SemiBold),
            color = colors.secondaryText,
        )
        lines.forEach { line ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.tight),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    DwIcons.chevronRight,
                    contentDescription = null,
                    tint = colors.mutedInk,
                    modifier = Modifier.size(11.dp).padding(top = 2.dp),
                )
                Text(line, style = typo(Typo.CAPTION), color = colors.secondaryText)
            }
        }
    }
}
