package com.denguewatch.bd.feature.triage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.AppState
import com.denguewatch.bd.feature.shared.CardSection
import com.denguewatch.bd.feature.shared.PrimaryActionButton
import com.denguewatch.bd.feature.shared.SecondaryActionButton
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Shapes
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.typo

/**
 * What the check came to, and what to do about it.
 *
 * The signs the reader actually ticked are drawn again here, worst group first,
 * so the picture they see is the one that drove the advice.
 */
@Composable
fun TriageResultScreen(
    state: AppState,
    outcome: TriageOutcome,
    selected: Set<String>,
    feverDaysAgo: Int?,
    onFindCare: () -> Unit,
    onCall: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors

    val accent = when (outcome) {
        TriageOutcome.SELF_CARE -> colors.downIsGood
        TriageOutcome.TEST_ADVISED -> colors.riskTint(RiskLevel.MODERATE)
        TriageOutcome.SEE_DOCTOR_TODAY -> colors.riskTint(RiskLevel.HIGH)
        TriageOutcome.EMERGENCY -> colors.riskTint(RiskLevel.SEVERE)
    }
    val outcomeIcon = when (outcome) {
        TriageOutcome.SELF_CARE -> DwIcons.upToDate
        TriageOutcome.TEST_ADVISED -> DwIcons.hospital
        TriageOutcome.SEE_DOCTOR_TODAY -> DwIcons.care
        TriageOutcome.EMERGENCY -> DwIcons.riskSevere
    }

    // Worst group first.
    val reportedSigns = remember(selected) {
        TriageEngine.symptoms
            .filter { it.id in selected }
            .sortedBy {
                when (it.group) {
                    Symptom.Group.SEVERE -> 0
                    Symptom.Group.WARNING -> 1
                    Symptom.Group.CORE -> 2
                }
            }
    }


    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.plane),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Space.stack),
    ) {
        // The verdict
        item {
            Column(
                Modifier
                    .padding(horizontal = Space.screen)
                    .fillMaxWidth()
                    .clip(Shapes.card)
                    .background(colors.card)
                    .border(1.5.dp, accent.copy(alpha = 0.5f), Shapes.card)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(Space.row),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.row),
                ) {
                    Icon(
                        outcomeIcon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(28.dp),
                    )
                    Text(loc.t(outcome.headlineKey), style = typo(Typo.TITLE))
                }
                Text(
                    loc.t(outcome.summaryKey),
                    style = typo(Typo.CALLOUT),
                    color = colors.secondaryText,
                )
                TriageEngine.phaseKey(feverDaysAgo)?.let { key ->
                    Text(
                        loc.t(key, loc.num((feverDaysAgo ?: 0) + 1)),
                        style = typo(Typo.CAPTION),
                        color = colors.secondaryText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(11.dp))
                            .background(accent.copy(alpha = 0.10f))
                            .padding(11.dp),
                    )
                }
            }
        }

        // The signs that drove it
        if (reportedSigns.isNotEmpty()) {
            item {
                CardSection(
                    title = loc.t("check.title"),
                    modifier = Modifier.padding(horizontal = Space.screen),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        reportedSigns.forEach { symptom ->
                            Column(
                                Modifier.width(84.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                SymptomIllustration(symptom.id, symptom.group, size = 66.dp)
                                Text(
                                    loc.t(symptom.titleKey),
                                    style = typo(Typo.MICRO),
                                    color = colors.secondaryText,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }

        // What to do now
        item {
            CardSection(
                title = loc.t("result.whatToDo"),
                modifier = Modifier.padding(horizontal = Space.screen),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.row)) {
                    outcome.actionKeys.forEachIndexed { index, key ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(11.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(accent.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    loc.num(index + 1),
                                    style = typo(Typo.CAPTION, FontWeight.Bold),
                                    color = accent,
                                )
                            }
                            Text(loc.t(key), style = typo(Typo.SUBHEADLINE))
                        }
                    }
                }
            }
        }

        // The two urgent outcomes get the actions that match them.
        if (outcome == TriageOutcome.EMERGENCY || outcome == TriageOutcome.SEE_DOCTOR_TODAY) {
            item {
                Column(
                    Modifier.padding(horizontal = Space.screen),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PrimaryActionButton(
                        title = loc.t("result.call999"),
                        icon = DwIcons.phone,
                        tint = colors.riskTint(RiskLevel.SEVERE),
                        onClick = { onCall("999") },
                    )
                    SecondaryActionButton(
                        title = loc.t("result.findHospital"),
                        icon = DwIcons.hospital,
                        onClick = onFindCare,
                    )
                }
            }
        }

        item {
            Text(
                loc.t("result.disclaimer"),
                style = typo(Typo.MICRO),
                color = colors.secondaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Space.card + 8.dp),
            )
        }

        item { Spacer(Modifier.height(Space.section)) }
    }
}
