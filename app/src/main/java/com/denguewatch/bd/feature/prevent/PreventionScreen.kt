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
 * The seasonal card is first on purpose: almost every piece of advice below it
 * is worth more in May than in December, and someone who knows which month they
 * are in reads the rest differently.
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
            item { ScreenTitle(loc.t("prevent.title"), Modifier.padding(horizontal = Space.card)) }
        }

        item {
            CardSection(
                title = loc.t("prevent.season.title"),
                subtitle = loc.t("prevent.season.subtitle"),
                modifier = Modifier.padding(horizontal = Space.card),
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
                    modifier = Modifier.padding(horizontal = Space.card),
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

            Card(Modifier.padding(horizontal = Space.card)) {
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
