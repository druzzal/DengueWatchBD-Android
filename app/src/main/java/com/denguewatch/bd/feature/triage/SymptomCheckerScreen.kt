package com.denguewatch.bd.feature.triage

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.AppState
import com.denguewatch.bd.feature.shared.PrimaryActionButton
import com.denguewatch.bd.feature.shared.SecondaryActionButton
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.ui.theme.Card
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Hit
import com.denguewatch.bd.ui.theme.Shapes
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.typo

/**
 * The symptom check, one question at a time.
 *
 * The single scrolling form this replaces asked fourteen symptom questions, a
 * date and four risk questions on one screen — a lot to face when you feel ill,
 * and enough that the warning signs scrolled past as just more ticks. Stepping
 * it gives those signs a screen of their own, which is the part that most needs
 * to be read rather than skimmed.
 *
 * Symptoms are still grouped the way WHO groups them. Which group a sign falls
 * in is what decides the advice, and a reader who understands that grouping can
 * act on it without re-running the check.
 *
 * Nothing clinical lives here. [CheckerStep] decides what is asked and in what
 * order; [TriageEngine.evaluate] still decides what the answers mean.
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

    var step by rememberSaveable { mutableStateOf(CheckerStep.FEVER) }
    val steps = CheckerStep.sequence(hasFever = "fever" in selected)
    // The step can fall out of the sequence under the reader: answering "no
    // fever" while standing on the timeline step removes that step. Coercing
    // the index rather than the step keeps them on the same position in a now
    // shorter flow instead of throwing them back to the start.
    val stepIndex = steps.indexOf(step).let { if (it >= 0) it else 0 }
    val current = steps[stepIndex]
    val isLastStep = stepIndex == steps.lastIndex

    fun advance() {
        steps.getOrNull(stepIndex + 1)?.let { step = it }
    }

    fun retreat() {
        steps.getOrNull(stepIndex - 1)?.let { step = it }
    }

    Column(modifier.fillMaxSize().background(colors.plane)) {
        StepProgress(
            count = steps.size,
            index = stepIndex,
            label = loc.t("step.progress", loc.num(stepIndex + 1), loc.num(steps.size)),
            modifier = Modifier.padding(
                top = contentPadding.calculateTopPadding(),
                start = Space.card,
                end = Space.card,
            ),
        )

        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Space.stack),
            contentPadding = PaddingValues(top = Space.row, bottom = Space.section),
        ) {
            item {
                Column(
                    Modifier.padding(horizontal = Space.screen),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(loc.t(current.titleKey), style = typo(Typo.TITLE))
                    Text(
                        loc.t(current.subtitleKey),
                        style = typo(Typo.CALLOUT),
                        color = colors.secondaryText,
                    )
                }
            }

            item {
                AnimatedContent(
                    targetState = current,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "checker step",
                ) { shown ->
                    Column(
                        Modifier.padding(horizontal = Space.screen),
                        verticalArrangement = Arrangement.spacedBy(Space.tight),
                    ) {
                        when (shown) {
                            CheckerStep.FEVER -> FeverStep(
                                hasFever = "fever" in selected,
                                answered = stepIndex > 0,
                                onAnswer = { yes ->
                                    onToggleSymptom("fever", yes)
                                    if (!yes) onFeverDaysChange(null)
                                    advance()
                                },
                            )

                            CheckerStep.SYMPTOMS ->
                                // Fever is asked on its own screen, so it is not
                                // asked again here.
                                TriageEngine.symptoms(Symptom.Group.CORE)
                                    .filter { it.id != "fever" }
                                    .forEach { symptom ->
                                        SymptomTile(
                                            symptom = symptom,
                                            isOn = symptom.id in selected,
                                            emphasis = false,
                                            onToggle = { onToggleSymptom(symptom.id, it) },
                                        )
                                    }

                            CheckerStep.WARNING_SIGNS -> WarningStep(
                                selected = selected,
                                onToggleSymptom = onToggleSymptom,
                                onNone = {
                                    warningAndSevere().forEach { onToggleSymptom(it.id, false) }
                                    advance()
                                },
                            )

                            CheckerStep.TIMELINE -> TimelineStep(
                                feverDaysAgo = feverDaysAgo,
                                onFeverDaysChange = onFeverDaysChange,
                            )

                            CheckerStep.RISK_FACTORS -> RiskStep(
                                context = context,
                                onContextChange = onContextChange,
                            )
                        }
                    }
                }
            }

            if (selected.isNotEmpty() && isLastStep) {
                item {
                    SecondaryActionButton(
                        title = loc.t("check.clearAll"),
                        tint = colors.riskTint(RiskLevel.SEVERE),
                        onClick = {
                            onClearAll()
                            step = CheckerStep.FEVER
                        },
                        modifier = Modifier.padding(horizontal = Space.screen),
                    )
                }
            }

            item { Spacer(Modifier.height(Space.tight)) }
        }

        // Pinned rather than scrolled to, so the way forward is always in reach
        // on a screen that can be a dozen tiles long.
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.card)
                .padding(
                    start = Space.card,
                    end = Space.card,
                    top = Space.row,
                    bottom = Space.row + contentPadding.calculateBottomPadding(),
                ),
            horizontalArrangement = Arrangement.spacedBy(Space.row),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (stepIndex > 0) {
                SecondaryActionButton(
                    title = loc.t("step.back"),
                    icon = DwIcons.back,
                    onClick = { retreat() },
                    modifier = Modifier.weight(1f),
                )
            }
            PrimaryActionButton(
                title = loc.t(if (isLastStep) "step.seeResult" else "step.next"),
                // The engine tolerates an empty set, but a result from no
                // answers tells the reader nothing, so the last step waits for
                // one.
                enabled = !isLastStep || selected.isNotEmpty(),
                onClick = { if (isLastStep) onSeeResult() else advance() },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Every sign that can change the advice, in the order the warning step shows them. */
private fun warningAndSevere(): List<Symptom> =
    TriageEngine.symptoms(Symptom.Group.WARNING) + TriageEngine.symptoms(Symptom.Group.SEVERE)

/**
 * Position in the flow, as segments rather than a percentage.
 *
 * How many questions are left is the thing someone actually wants to know, and
 * "60%" does not answer it.
 */
@Composable
private fun StepProgress(
    count: Int,
    index: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = Dw.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = Space.tight)
            .semantics(mergeDescendants = true) { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(count) { position ->
            Spacer(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(if (position <= index) colors.accent else colors.grid)
            )
        }
    }
}

/**
 * The opening question, as two large choices.
 *
 * Tapping either answers and moves on — there is nothing else on the screen to
 * do, and making someone tap a choice and then Continue is a tap for nothing.
 */
@Composable
private fun FeverStep(hasFever: Boolean, answered: Boolean, onAnswer: (Boolean) -> Unit) {
    val loc = LocalLocalization.current
    ChoiceTile(
        title = loc.t("step.fever.yes"),
        icon = DwIcons.temperature,
        isOn = hasFever,
    ) { onAnswer(true) }
    ChoiceTile(
        title = loc.t("step.fever.no"),
        icon = DwIcons.temperatureLow,
        // Only once the question has been answered: before that, "no fever"
        // would show as chosen simply because nothing has been ticked.
        isOn = !hasFever && answered,
    ) { onAnswer(false) }
}

/**
 * Warning and severe signs share a screen, drawn louder than the core symptoms,
 * because these are the answers that change the advice.
 */
@Composable
private fun WarningStep(
    selected: Set<String>,
    onToggleSymptom: (String, Boolean) -> Unit,
    onNone: () -> Unit,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    warningAndSevere().forEach { symptom ->
        SymptomTile(
            symptom = symptom,
            isOn = symptom.id in selected,
            emphasis = true,
            onToggle = { onToggleSymptom(symptom.id, it) },
        )
    }
    Text(
        loc.t("step.warning.none"),
        style = typo(Typo.SUBHEADLINE),
        color = colors.accent,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Hit.minimum)
            .clip(Shapes.control)
            .clickable(role = Role.Button, onClick = onNone)
            .padding(Space.row),
    )
}

/** Which day of the fever this is, and what that phase means. */
@Composable
private fun TimelineStep(feverDaysAgo: Int?, onFeverDaysChange: (Int?) -> Unit) {
    val loc = LocalLocalization.current
    val colors = Dw.colors

    Card {
        Column(verticalArrangement = Arrangement.spacedBy(Space.row)) {
            ToggleRow(
                title = loc.t("check.fever.knowDate"),
                checked = feverDaysAgo != null,
                onCheckedChange = { onFeverDaysChange(if (it) 0 else null) },
            )

            if (feverDaysAgo == null) {
                Text(
                    loc.t("step.timeline.unknown"),
                    style = typo(Typo.CAPTION),
                    color = colors.secondaryText,
                )
            } else {
                Text(
                    loc.t("check.fever.started"),
                    style = typo(Typo.CAPTION),
                    color = colors.secondaryText,
                )
                // A stepper rather than a date picker: the question the triage
                // actually asks is "which day of fever is this", and counting
                // days is how someone ill answers it.
                DayStepper(days = feverDaysAgo, onChange = onFeverDaysChange)
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
}

/** The four things that make dengue more serious for some people. */
@Composable
private fun RiskStep(
    context: TriageEngine.Context,
    onContextChange: (TriageEngine.Context) -> Unit,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(Space.tight)) {
            ToggleRow(loc.t("check.risk.pregnant"), context.isPregnant) {
                onContextChange(context.copy(isPregnant = it))
            }
            ToggleRow(loc.t("check.risk.age"), context.isUnderFiveOrOverSixty) {
                onContextChange(context.copy(isUnderFiveOrOverSixty = it))
            }
            ToggleRow(loc.t("check.risk.chronic"), context.hasChronicCondition) {
                onContextChange(context.copy(hasChronicCondition = it))
            }
            ToggleRow(loc.t("check.risk.previous"), context.hadDengueBefore) {
                onContextChange(context.copy(hadDengueBefore = it))
            }
            Text(
                loc.t("check.risk.footer"),
                style = typo(Typo.MICRO),
                color = colors.secondaryText,
            )
        }
    }
}

/** One of two large answers to a single question. */
@Composable
private fun ChoiceTile(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isOn: Boolean,
    onClick: () -> Unit,
) {
    val colors = Dw.colors
    val ink = if (isOn) Color.White else colors.primaryText
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(Shapes.card)
            .background(if (isOn) colors.accent else colors.card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.card)
            .semantics { selected = isOn },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.row),
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(24.dp))
        Text(title, style = typo(Typo.HEADLINE), color = ink, modifier = Modifier.weight(1f))
        if (isOn) {
            Icon(
                DwIcons.upToDate,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * A symptom carries its own illustration — the drawing does the work of
 * recognition before the words are read, which matters when someone is ill or
 * reading in their second language.
 */
@Composable
private fun SymptomTile(
    symptom: Symptom,
    isOn: Boolean,
    emphasis: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    // Warning and severe signs are tinted with the risk colour rather than the
    // app's accent, so the screen that changes the advice looks different from
    // the one that does not.
    val tint = if (emphasis) colors.riskTint(RiskLevel.HIGH) else colors.accent

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Hit.minimum)
            .clip(Shapes.control)
            .background(if (isOn) tint.copy(alpha = 0.10f) else colors.card)
            .border(
                1.dp,
                if (isOn) tint.copy(alpha = 0.45f) else colors.hairline,
                Shapes.control,
            )
            .clickable(role = Role.Checkbox) { onToggle(!isOn) }
            .padding(Space.row)
            .semantics { selected = isOn },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.row),
    ) {
        Icon(
            if (isOn) DwIcons.upToDate else DwIcons.circleOutline,
            contentDescription = null,
            tint = if (isOn) tint else colors.tertiaryText,
            modifier = Modifier.size(22.dp),
        )
        SymptomIllustration(symptom.id, symptom.group, size = 42.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(loc.t(symptom.titleKey), style = typo(Typo.CALLOUT, FontWeight.Medium))
            Text(
                loc.t(symptom.detailKey),
                style = typo(Typo.MICRO),
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
                checkedThumbColor = Color.White,
            ),
        )
    }
}

/** Counts days of fever up and down, bounded to the window the advice covers. */
@Composable
private fun DayStepper(days: Int, onChange: (Int) -> Unit) {
    val loc = LocalLocalization.current

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
            textAlign = TextAlign.Center,
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
        textAlign = TextAlign.Center,
        modifier = Modifier
            .size(Hit.minimum)
            .background(colors.accent.copy(alpha = if (enabled) 0.12f else 0.05f), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(top = 6.dp),
    )
}
