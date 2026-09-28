package com.denguewatch.bd.feature.triage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.AppState
import com.denguewatch.bd.feature.shared.HairlineDivider
import com.denguewatch.bd.feature.shared.InlineNote
import com.denguewatch.bd.feature.shared.PrimaryActionButton
import com.denguewatch.bd.feature.shared.ScreenTitle
import com.denguewatch.bd.feature.shared.SecondaryActionButton
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Hit
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.typo
import com.denguewatch.bd.feature.shared.GroupedSection
import com.denguewatch.bd.ui.theme.Card

/**
 * The symptom check.
 *
 * Grouped the way WHO groups them — the usual picture of dengue, then the
 * warning signs, then the emergency signs — because which group a sign falls in
 * is what decides the advice, and a reader who understands that grouping can
 * act on it without re-running the check.
 */
@Composable
fun SymptomCheckerScreen(
    state: AppState,
    selected: Set<String>,
    onToggleSymptom: (String, Boolean) -> Unit,
    context: TriageEngine.Context,
    onContextChange: (TriageEngine.Context) -> Unit,
    feverDaysAgo: Int?,
    onFeverDaysChange: (Int?) -> Unit,
    onClearAll: () -> Unit,
    onSeeResult: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.plane),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Space.stack),
    ) {
        run {
            item { ScreenTitle(loc.t("check.title"), Modifier.padding(horizontal = Space.card)) }
        }

        item {
            Card(Modifier.padding(horizontal = Space.card)) {
                Text(
                    loc.t("check.intro"),
                    style = typo(Typo.CALLOUT),
                    color = colors.secondaryText,
                )
            }
        }

        Symptom.Group.entries.forEach { group ->
            item {
                GroupedSection(
                    title = loc.t(group.titleKey),
                    footnote = loc.t(group.footnoteKey),
                    modifier = Modifier.padding(horizontal = Space.card),
                ) {
                    Column {
                        val rows = TriageEngine.symptoms(group)
                        rows.forEachIndexed { index, symptom ->
                            SymptomRow(
                                symptom = symptom,
                                isOn = symptom.id in selected,
                                onChange = { onToggleSymptom(symptom.id, it) },
                            )
                            if (index < rows.size - 1) HairlineDivider()
                        }
                    }
                }
            }
        }

        item {
            GroupedSection(
                title = loc.t("check.fever.section"),
                modifier = Modifier.padding(horizontal = Space.card),
            ) {
                ToggleRow(
                    title = loc.t("check.fever.knowDate"),
                    checked = feverDaysAgo != null,
                    onCheckedChange = { onFeverDaysChange(if (it) 0 else null) },
                )

                if (feverDaysAgo != null) {
                    HairlineDivider()
                    Text(
                        loc.t("check.fever.started"),
                        style = typo(Typo.CAPTION),
                        color = colors.secondaryText,
                    )
                    // A stepper rather than a date picker: the question the
                    // triage actually asks is "which day of fever is this",
                    // and counting days is how someone ill answers it.
                    DayStepper(
                        days = feverDaysAgo,
                        onChange = onFeverDaysChange,
                    )
                    TriageEngine.phaseKey(feverDaysAgo)?.let { key ->
                        Text(
                            loc.t(key, loc.num(feverDaysAgo + 1)),
                            style = typo(Typo.CAPTION),
                            color = colors.secondaryText,
                        )
                    }
                }
            }
        }

        item {
            GroupedSection(
                title = loc.t("check.risk.section"),
                footnote = loc.t("check.risk.footer"),
                modifier = Modifier.padding(horizontal = Space.card),
            ) {
                ToggleRow(loc.t("check.risk.pregnant"), context.isPregnant) {
                    onContextChange(context.copy(isPregnant = it))
                }
                HairlineDivider()
                ToggleRow(loc.t("check.risk.age"), context.isUnderFiveOrOverSixty) {
                    onContextChange(context.copy(isUnderFiveOrOverSixty = it))
                }
                HairlineDivider()
                ToggleRow(loc.t("check.risk.chronic"), context.hasChronicCondition) {
                    onContextChange(context.copy(hasChronicCondition = it))
                }
                HairlineDivider()
                ToggleRow(loc.t("check.risk.previous"), context.hadDengueBefore) {
                    onContextChange(context.copy(hadDengueBefore = it))
                }
            }
        }

        item {
            Card(Modifier.padding(horizontal = Space.card)) {
                PrimaryActionButton(
                    title = loc.t("check.seeResult"),
                    enabled = selected.isNotEmpty(),
                    onClick = onSeeResult,
                )
                if (selected.isNotEmpty()) {
                    SecondaryActionButton(
                        title = loc.t("check.clearAll"),
                        tint = colors.riskTint(com.denguewatch.bd.model.RiskLevel.SEVERE),
                        onClick = onClearAll,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(Space.section)) }
    }
}

/**
 * A symptom row carries its own illustration — the drawing does the work of
 * recognition before the words are read, which matters when someone is ill or
 * reading in their second language.
 */
@Composable
private fun SymptomRow(
    symptom: Symptom,
    isOn: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox) { onChange(!isOn) }
            .padding(vertical = Space.tight)
            .semantics { selected = isOn },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.row),
    ) {
        Icon(
            if (isOn) DwIcons.upToDate else DwIcons.circleOutline,
            contentDescription = null,
            tint = if (isOn) colors.accent else colors.tertiaryText,
            modifier = Modifier.size(24.dp),
        )

        SymptomIllustration(symptom.id, symptom.group, size = 46.dp)

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(loc.t(symptom.titleKey), style = typo(Typo.SUBHEADLINE))
            Text(
                loc.t(symptom.detailKey),
                style = typo(Typo.CAPTION),
                color = colors.secondaryText,
            )
        }
    }
}

@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = Dw.colors
    Row(
        modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = Space.tight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.row),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                style = typo(Typo.SUBHEADLINE),
                color = if (enabled) colors.primaryText else colors.tertiaryText,
            )
            if (detail != null) {
                Text(detail, style = typo(Typo.CAPTION), color = colors.secondaryText)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.accent,
                checkedThumbColor = androidx.compose.ui.graphics.Color.White,
            ),
        )
    }
}

/** Counts days of fever up and down, bounded to the window the advice covers. */
@Composable
private fun DayStepper(days: Int, onChange: (Int) -> Unit) {
    val loc = LocalLocalization.current
    val colors = Dw.colors

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.row),
    ) {
        StepperButton(label = "−", enabled = days > 0) { onChange((days - 1).coerceAtLeast(0)) }
        // The date itself, so the reader can check the stepper against what
        // they remember rather than counting backwards in their head.
        Text(
            loc.dayMonth(System.currentTimeMillis() - days * 86_400_000L),
            style = typo(Typo.SUBHEADLINE, FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        StepperButton(label = "+", enabled = days < 30) { onChange((days + 1).coerceAtMost(30)) }
    }
}

@Composable
private fun StepperButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = Dw.colors
    Text(
        label,
        style = typo(Typo.TITLE),
        color = if (enabled) colors.accent else colors.tertiaryText,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier
            .size(Hit.minimum)
            .background(
                colors.accent.copy(alpha = if (enabled) 0.12f else 0.05f),
                androidx.compose.foundation.shape.CircleShape,
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(top = 6.dp),
    )
}
