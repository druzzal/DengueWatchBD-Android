package com.denguewatch.bd.feature.prevent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.AppState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.denguewatch.bd.data.PreventionChecklistStore
import com.denguewatch.bd.feature.shared.HairlineDivider
import com.denguewatch.bd.model.PreventionTask
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.ui.theme.Hit
import com.denguewatch.bd.ui.theme.tabularFigures
import com.denguewatch.bd.feature.shared.CardSection
import com.denguewatch.bd.feature.shared.DisclosureRow
import com.denguewatch.bd.feature.shared.RiskBadge
import com.denguewatch.bd.feature.shared.RiskScaleLegend
import com.denguewatch.bd.feature.shared.ScreenTitle
import com.denguewatch.bd.feature.shared.ShrinkToFitText
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.i18n.labelKey
import com.denguewatch.bd.ui.theme.Card
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.typo
import java.util.Calendar

/**
 * What a reader can actually do about dengue, and when in the year it matters
 * most.
 *
 * In the iOS order: today's checklist, the seasonal pattern, the myths, the
 * topics, then alerts. The checklist leads because it is the only thing on the
 * screen that asks for something now; the seasonal card follows because almost
 * every piece of advice below it is worth more in May than in December, and
 * someone who knows which month they are in reads the rest differently.
 */
@Composable
fun PreventionScreen(
    state: AppState,
    onOpenAlerts: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val preferences = state.preferences

    var expanded by remember { mutableStateOf(setOf("breeding")) }
    val currentMonthIndex = remember { Calendar.getInstance().get(Calendar.MONTH) }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.plane),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Space.stack),
    ) {
        run {
            item { ScreenTitle(loc.t("prevent.title"), Modifier.padding(horizontal = Space.screen)) }
        }

        item {
            ChecklistCard(
                checklist = state.checklist,
                modifier = Modifier.padding(horizontal = Space.screen),
            )
        }

        item {
            CardSection(
                title = loc.t("prevent.season.title"),
                subtitle = loc.t("prevent.season.subtitle"),
                modifier = Modifier.padding(horizontal = Space.screen),
            ) {
                SeasonBars(currentMonthIndex)
                RiskScaleLegend()

                PreventionContent.seasonalPattern.getOrNull(currentMonthIndex)?.let { month ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        RiskBadge(month.level, compact = true)
                        Text(
                            loc.t(month.noteKey),
                            style = typo(Typo.CAPTION),
                            color = colors.secondaryText,
                        )
                    }
                }

                Text(
                    loc.t("prevent.season.footnote"),
                    style = typo(Typo.MICRO),
                    color = colors.secondaryText,
                )
            }
        }

        item {
            MythCard(
                isOpen = "myths" in expanded,
                onToggle = {
                    expanded = if ("myths" in expanded) expanded - "myths" else expanded + "myths"
                },
                modifier = Modifier.padding(horizontal = Space.screen),
            )
        }

        PreventionContent.topics.forEach { topic ->
            item(key = topic.id) {
                TopicCard(
                    topic = topic,
                    isOpen = topic.id in expanded,
                    onToggle = {
                        expanded = if (topic.id in expanded) {
                            expanded - topic.id
                        } else {
                            expanded + topic.id
                        }
                    },
                    modifier = Modifier.padding(horizontal = Space.screen),
                )
            }
        }

        item {
            /**
             * What the row says underneath its title.
             *
             * It used to report only the rise alert, so someone who had turned
             * on the high-risk entry warning — and nothing else — read "Off" on
             * the one row that was supposed to tell them their alerts worked.
             */
            val summary = when {
                preferences.alertsEnabled && preferences.geofenceAlertsEnabled ->
                    loc.t("alerts.state.both")
                preferences.alertsEnabled ->
                    loc.t("alerts.on", loc.t(preferences.alertThreshold.labelKey))
                preferences.geofenceAlertsEnabled ->
                    loc.t("alerts.state.entryOnly")
                else -> loc.t("alerts.off")
            }

            Card(Modifier.padding(horizontal = Space.screen)) {
                DisclosureRow(
                    title = loc.t("alerts.entry"),
                    detail = summary,
                    icon = DwIcons.alerts,
                    onClick = onOpenAlerts,
                )
            }
        }

        item { Spacer(Modifier.height(Space.section)) }
    }
}

/**
 * Today's five tasks, with an honest count and nothing else.
 *
 * No streak, no badge, no celebration beyond a quiet line when the last one is
 * ticked — the brief for this was explicitly that it must not become a game
 * people tire of.
 */
@Composable
private fun ChecklistCard(
    checklist: PreventionChecklistStore,
    modifier: Modifier = Modifier,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val progress = loc.t(
        "checklist.progress",
        loc.num(checklist.completedCount),
        loc.num(checklist.total),
    )

    // On appearance, and again whenever the app comes back to the front. The
    // second half matters: a phone locked overnight on this tab returns without
    // appearing again, and yesterday's ticks reading as today's would be a
    // false all-clear in the one part of the app that is about not being
    // bitten.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        checklist.refreshIfDayChanged()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) checklist.refreshIfDayChanged()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    CardSection(
        title = loc.t("checklist.title"),
        subtitle = loc.t("checklist.subtitle"),
        modifier = modifier,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                // Read as one thing: the bar on its own announces a bare
                // percentage, which says less than "3 of 5 done".
                .semantics(mergeDescendants = true) { contentDescription = progress },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.tight),
        ) {
            LinearProgressIndicator(
                progress = { checklist.fraction },
                color = colors.riskTint(RiskLevel.LOW),
                trackColor = colors.mutedInk.copy(alpha = 0.15f),
                drawStopIndicator = {},
                modifier = Modifier.weight(1f),
            )
            Text(
                progress,
                style = typo(Typo.CAPTION).tabularFigures(),
                color = colors.secondaryText,
            )
        }

        Column {
            PreventionTask.entries.forEach { task ->
                val done = checklist.isDone(task)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Hit.minimum)
                        .toggleable(
                            value = done,
                            role = Role.Checkbox,
                            onValueChange = { checklist.toggle(task) },
                        )
                        // State goes through the role and this value, not
                        // through the colour or the strikethrough, so a screen
                        // reader announces it as a checkbox.
                        .semantics {
                            stateDescription =
                                loc.t(if (done) "checklist.a11y.done" else "checklist.a11y.notDone")
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.row),
                ) {
                    Icon(
                        if (done) DwIcons.upToDate else DwIcons.circleOutline,
                        contentDescription = null,
                        tint = if (done) colors.riskTint(RiskLevel.LOW) else colors.secondaryText,
                        modifier = Modifier.size(20.dp),
                    )
                    // Decorative: the task's own words carry the meaning, and
                    // the row already announces itself as a checkbox.
                    Icon(
                        DwIcons.forTask(task.id),
                        contentDescription = null,
                        tint = colors.secondaryText,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        loc.t(task.titleKey),
                        style = typo(Typo.CALLOUT).copy(
                            textDecoration = if (done) TextDecoration.LineThrough else null,
                        ),
                        color = if (done) colors.secondaryText else colors.primaryText,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = checklist.isComplete,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(220)),
        ) {
            Text(
                loc.t("checklist.allDone"),
                style = typo(Typo.CAPTION),
                color = colors.riskTint(RiskLevel.LOW),
            )
        }
    }
}

/**
 * Beliefs that cost people time, paired with what is actually true.
 *
 * Myth first and fact second, visually distinct, because a reader skimming must
 * not come away remembering the myth as the message. The myth is set in the
 * muted colour and struck through; the fact is in the primary one.
 */
@Composable
private fun MythCard(
    isOpen: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors

    // The labels take the width of the wider of the two in whatever language is
    // on screen, so the myth and the fact share a left edge. A fixed width was
    // tried on iOS and "MYTH" already overflowed it in English; Bengali is
    // further off again, "ভ্রান্ত ধারণা" beside "সত্য".
    //
    // Measured rather than estimated from the character count: an estimate only
    // holds while the wider label happens to be the one with more letters in
    // it, which is a property of these two words and not of the layout.
    val measurer = rememberTextMeasurer()
    val labelStyle = typo(Typo.MICRO, FontWeight.Bold)
    val density = LocalDensity.current
    val labelWidth = remember(loc.language, labelStyle, density) {
        val widest = listOf("myth.label", "myth.fact.label")
            .maxOf { measurer.measure(loc.t(it).uppercase(), labelStyle).size.width }
        with(density) { widest.toDp() }
    }

    Card(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onToggle),
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(loc.t("myth.section"), style = typo(Typo.HEADLINE))
                    Text(
                        loc.t("myth.section.subtitle"),
                        style = typo(Typo.CAPTION),
                        color = colors.secondaryText,
                    )
                }
                Icon(
                    DwIcons.chevronDown,
                    contentDescription = null,
                    tint = colors.tertiaryText,
                    modifier = Modifier.size(20.dp).rotate(if (isOpen) 0f else -90f),
                )
            }

            AnimatedVisibility(
                visible = isOpen,
                enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                exit = fadeOut(tween(220)) + shrinkVertically(tween(220)),
            ) {
                Column {
                    MythFact.all.forEachIndexed { index, item ->
                        Column(
                            Modifier.padding(vertical = Space.row),
                            verticalArrangement = Arrangement.spacedBy(Space.tight),
                        ) {
                            MythFactLine(
                                label = loc.t("myth.label").uppercase(),
                                labelColor = colors.riskTint(RiskLevel.HIGH),
                                labelWidth = labelWidth,
                                text = loc.t(item.mythKey),
                                textColor = colors.secondaryText,
                                struckThrough = true,
                            )
                            MythFactLine(
                                label = loc.t("myth.fact.label").uppercase(),
                                labelColor = colors.riskTint(RiskLevel.LOW),
                                labelWidth = labelWidth,
                                text = loc.t(item.factKey),
                                textColor = colors.primaryText,
                                struckThrough = false,
                            )
                        }
                        if (index < MythFact.all.size - 1) HairlineDivider()
                    }
                }
            }
        }
    }
}

/** One half of a myth-and-fact pair: its label, then the claim. */
@Composable
private fun MythFactLine(
    label: String,
    labelColor: androidx.compose.ui.graphics.Color,
    labelWidth: Dp,
    text: String,
    textColor: androidx.compose.ui.graphics.Color,
    struckThrough: Boolean,
) {
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(Space.tight),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = typo(Typo.MICRO, FontWeight.Bold),
            color = labelColor,
            // Both labels get the measured width of the wider one, so the two
            // claims line up whatever language is on screen.
            modifier = Modifier.width(labelWidth),
        )
        Text(
            text,
            style = typo(Typo.CALLOUT).copy(
                textDecoration = if (struckThrough) TextDecoration.LineThrough else null,
            ),
            color = textColor,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The typical dengue year, as twelve bars on the ordinal risk ramp.
 *
 * "Now" is a caret above the bar rather than an outline drawn on it: an outline
 * fought the band colour it sat on, and the colour is what carries the meaning.
 */
@Composable
private fun SeasonBars(currentMonthIndex: Int) {
    val loc = LocalLocalization.current
    val colors = Dw.colors

    Row(
        Modifier.fillMaxWidth().height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        PreventionContent.seasonalPattern.forEachIndexed { index, month ->
            val isNow = index == currentMonthIndex
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(Modifier.height(10.dp), contentAlignment = Alignment.Center) {
                    if (isNow) {
                        Icon(
                            DwIcons.chevronDown,
                            contentDescription = null,
                            tint = colors.primaryText,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height((26 + month.level.ordinal * 9).dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.riskTint(month.level))
                )
                // Twelve columns share the width, so a Bengali month such as
                // "ফেব্রু" does not fit at full size and was being clipped to a
                // shorter word that looked deliberate. Shrinking keeps every
                // label whole and the columns aligned.
                ShrinkToFitText(
                    loc.t(month.labelKey),
                    style = typo(
                        Typo.MICRO,
                        if (isNow) FontWeight.Bold else FontWeight.Normal,
                    ).copy(textAlign = TextAlign.Center),
                    color = if (isNow) colors.primaryText else colors.secondaryText,
                    minScale = 0.55f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun TopicCard(
    topic: PreventionTopic,
    isOpen: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors

    Card(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onToggle),
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    DwIcons.forTopic(topic.id),
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(26.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(loc.t(topic.titleKey), style = typo(Typo.HEADLINE))
                    Text(
                        loc.t(topic.summaryKey),
                        style = typo(Typo.CAPTION),
                        color = colors.secondaryText,
                    )
                }
                Icon(
                    DwIcons.chevronDown,
                    contentDescription = null,
                    tint = colors.tertiaryText,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(if (isOpen) 0f else -90f),
                )
            }

            AnimatedVisibility(
                visible = isOpen,
                enter = fadeIn(tween(220)) + expandVertically(tween(220)),
                exit = fadeOut(tween(220)) + shrinkVertically(tween(220)),
            ) {
                Column(
                    Modifier.padding(start = 39.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    topic.stepKeys.forEach { key ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                Modifier
                                    .padding(top = 7.dp)
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent.copy(alpha = 0.5f))
                            )
                            Text(loc.t(key), style = typo(Typo.SUBHEADLINE))
                        }
                    }
                }
            }
        }
    }
}
