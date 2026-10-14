package com.denguewatch.bd.feature.health

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.feature.shared.CardSection
import com.denguewatch.bd.feature.shared.InlineNote
import com.denguewatch.bd.feature.shared.ShrinkToFitText
import com.denguewatch.bd.feature.triage.TriageEngine
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.tabularFigures
import com.denguewatch.bd.ui.theme.typo
import java.util.Calendar

/**
 * The course of the illness as a strip of days, with the phase bands behind it
 * and today marked.
 *
 * The one thing this card exists to say is that dengue's dangerous days are not
 * its worst-feeling ones: the critical phase begins as the fever settles,
 * around day 4. A number on a card does not carry that. A band you can see
 * yourself approaching does.
 *
 * Phase boundaries come from [TriageEngine.FeverPhase], so the timeline and the
 * sentence under the symptom result can never disagree.
 */
@Composable
fun FeverTimelineCard(
    /** Day of illness, counting the first day of fever as day 1. */
    currentDay: Int,
    /** Temperatures already recorded, to mark the days that have readings. */
    temperatures: List<Pair<Long, Double>>,
    feverStartedMillis: Long,
    modifier: Modifier = Modifier,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val lastDay = 10
    val phase = TriageEngine.FeverPhase.of(maxOf(0, currentDay - 1))

    CardSection(
        title = loc.t("fever.timeline.title"),
        subtitle = loc.t("fever.timeline.subtitle"),
        modifier = modifier,
    ) {
        val dayLabel = loc.t("fever.timeline.day", loc.num(currentDay))
        val phaseName = loc.t(phase.nameKey)

        Row(
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "$dayLabel. $phaseName." },
            horizontalArrangement = Arrangement.spacedBy(Space.tight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                dayLabel,
                style = typo(Typo.SECTION_TITLE),
                color = colors.riskInk(phaseRisk(phase)),
            )
            Text(
                phaseName,
                style = typo(Typo.CAPTION, FontWeight.SemiBold),
                color = colors.riskInk(phaseRisk(phase)),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.riskSoft(phaseRisk(phase)))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }

        Strip(currentDay, lastDay, temperatures, feverStartedMillis, dayLabel, phaseName)
        Legend()
        InlineNote(detail = loc.t("fever.timeline.note"))
    }
}

/**
 * One column per day. The fill says which phase the day belongs to; the ring
 * says it is today; a dot says a temperature was recorded.
 */
@Composable
private fun Strip(
    currentDay: Int,
    lastDay: Int,
    temperatures: List<Pair<Long, Double>>,
    feverStartedMillis: Long,
    dayLabel: String,
    phaseName: String,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors

    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clearAndSetSemantics { contentDescription = "$dayLabel. $phaseName." },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        (1..lastDay).forEach { day ->
            val dayPhase = TriageEngine.FeverPhase.of(day - 1)
            val isToday = day == currentDay
            val isPast = day < currentDay
            val tint = colors.riskTint(phaseRisk(dayPhase))

            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                // Every column reserves the tallest bar's height, so the day
                // numbers share one baseline. Without it the column holding
                // today — whose bar is 10dp taller — centred itself and pushed
                // its own label 13px below the rest of the row, which reads as
                // a wobble in what is meant to be an axis.
                Box(
                    Modifier.fillMaxWidth().height(TODAY_BAR_HEIGHT),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(if (isToday) TODAY_BAR_HEIGHT else 24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(tint.copy(alpha = if (isPast || isToday) 0.9f else 0.22f))
                            .then(
                                if (isToday) {
                                    Modifier.border(
                                        2.dp,
                                        colors.riskInk(phaseRisk(dayPhase)),
                                        RoundedCornerShape(4.dp),
                                    )
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (hasReading(day, feverStartedMillis, temperatures)) {
                            Box(
                                Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                            )
                        }
                    }
                }
                // A plain Text, not ShrinkToFitText.
                //
                // Auto-sizing collapsed the Bengali numerals to a few pixels of
                // ink — a tenth of the card's width is ample for "১০", but the
                // measured width Bengali reports sent the stepper all the way
                // to its floor, so the same strip was legible in English and
                // unreadable in Bangla. Without a line cap nothing is clipped
                // either way: at an extreme text size the label wraps and the
                // strip grows, which is the behaviour this project prefers.
                Text(
                    loc.num(day),
                    style = typo(
                        Typo.MICRO,
                        if (isToday) FontWeight.Bold else FontWeight.Normal,
                    ).tabularFigures(),
                    color = if (isToday) colors.primaryText else colors.secondaryText,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Legend() {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(Space.row),
    ) {
        TriageEngine.FeverPhase.entries.forEach { item ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.riskTint(phaseRisk(item))),
                )
                ShrinkToFitText(
                    loc.t(item.nameKey),
                    style = typo(Typo.MICRO),
                    color = colors.secondaryText,
                    minScale = 0.8f,
                )
            }
        }
    }
}

/**
 * The critical phase is the one worth colouring as a caution. Recovery is not
 * "low risk" in the surveillance sense, but on this card it is the good end,
 * and the scale is the only one the app has.
 */
private val TODAY_BAR_HEIGHT = 34.dp

private fun phaseRisk(phase: TriageEngine.FeverPhase): RiskLevel = when (phase) {
    TriageEngine.FeverPhase.FEBRILE -> RiskLevel.MODERATE
    TriageEngine.FeverPhase.CRITICAL -> RiskLevel.HIGH
    TriageEngine.FeverPhase.RECOVERY -> RiskLevel.LOW
}

private fun hasReading(
    day: Int,
    feverStartedMillis: Long,
    temperatures: List<Pair<Long, Double>>,
): Boolean {
    fun dayOf(millis: Long) = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val target = dayOf(feverStartedMillis).apply { add(Calendar.DAY_OF_YEAR, day - 1) }.timeInMillis
    return temperatures.any { dayOf(it.first).timeInMillis == target }
}
