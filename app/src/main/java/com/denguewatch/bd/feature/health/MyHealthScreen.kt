package com.denguewatch.bd.feature.health

import com.denguewatch.bd.feature.shared.ShrinkToFitText
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.AppState
import com.denguewatch.bd.feature.shared.CardSection
import com.denguewatch.bd.feature.shared.ScreenTitle
import com.denguewatch.bd.feature.shared.SecondaryActionButton
import com.denguewatch.bd.feature.triage.TriageOutcome
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.typo
import com.denguewatch.bd.feature.shared.InlineNote
import com.denguewatch.bd.model.MeasureStatus
import com.denguewatch.bd.model.VitalKind
import com.denguewatch.bd.ui.theme.tabularFigures
import com.denguewatch.bd.model.LabMeasure
import com.denguewatch.bd.model.DengueTest
import com.denguewatch.bd.model.TestResult
import com.denguewatch.bd.feature.shared.HairlineDivider
import com.denguewatch.bd.i18n.Localization
import com.denguewatch.bd.model.CarePlanContext
import com.denguewatch.bd.model.WHOCarePlan
import java.util.Calendar
import com.denguewatch.bd.feature.shared.HealthTrendChart
import com.denguewatch.bd.feature.shared.TrendThreshold
import com.denguewatch.bd.model.HealthTrend
import com.denguewatch.bd.model.minimumMeaningfulChange
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.denguewatch.bd.feature.triage.ConfirmCheckDeletion
import com.denguewatch.bd.feature.triage.deleteLatest
import com.denguewatch.bd.ui.theme.Hit
import com.denguewatch.bd.ui.theme.Shapes

/**
 * What the reader has recorded about their own illness, in one place.
 *
 * The surveillance tabs describe the country; this one describes the person
 * holding the phone. Ported from the iOS build's My Health tab, which replaced
 * the standalone Check tab for the same reason: a symptom check taken once is
 * a moment, and dengue is judged over days — so the check belongs beside the
 * record of what came before it rather than on a tab of its own.
 *
 * The symptom checker itself is unchanged and not lost; it opens from here.
 */
@Composable
fun MyHealthScreen(
    state: AppState,
    onOpenCheck: () -> Unit,
    onOpenCheckHistory: () -> Unit,
    onAddDrink: () -> Unit,
    onOpenLog: () -> Unit,
    onRecordVitals: () -> Unit,
    onAddLabReport: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val entries = state.caseLog.entries
    // The latest check, once the reader has asked to delete it.
    var confirmingDelete by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.plane),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Space.stack),
    ) {
        item { ScreenTitle(loc.t("tab.health"), Modifier.padding(horizontal = Space.screen)) }

        item {
            CardSection(
                title = loc.t("check.title"),
                subtitle = loc.t("health.check.subtitle"),
                modifier = Modifier.padding(horizontal = Space.screen),
                accessory = {
                    if (entries.isNotEmpty()) {
                        CheckMenu(
                            onOpenHistory = onOpenCheckHistory,
                            onDeleteLatest = { confirmingDelete = true },
                        )
                    }
                },
            ) {
                val latest = entries.firstOrNull()
                if (latest != null) {
                    // Worked out from the check's answers by today's rules, so
                    // it always agrees with the care plan below it.
                    val outcome = latest.outcome
                    val summary = loc.t(outcome.headlineKey) + ", " +
                        loc.t("health.checkedAt", loc.relative(latest.dateMillis))
                    Row(
                        Modifier.fillMaxWidth().semantics { contentDescription = summary },
                        horizontalArrangement = Arrangement.spacedBy(Space.row),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            outcome.icon,
                            contentDescription = null,
                            tint = colors.riskTint(outcome.risk),
                            modifier = Modifier.size(22.dp),
                        )
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(loc.t(outcome.headlineKey), style = typo(Typo.SUBHEADLINE))
                            Text(
                                loc.t("health.checkedAt", loc.relative(latest.dateMillis)),
                                style = typo(Typo.MICRO),
                                color = colors.secondaryText,
                            )
                        }
                    }
                } else {
                    Text(
                        loc.t("health.empty"),
                        style = typo(Typo.CALLOUT),
                        color = colors.secondaryText,
                    )
                }

                SecondaryActionButton(
                    title = loc.t(if (entries.isEmpty()) "health.check" else "health.checkAgain"),
                    icon = DwIcons.check,
                    onClick = onOpenCheck,
                )
            }
        }

        // The course of the illness, while there is still an illness to chart.
        // Shown above the plan because the day of fever is the thing the plan
        // is reasoning from.
        entries.firstOrNull()?.let { latest ->
            val startedMillis = latest.feverStartedMillis
            val daysAgo = startedMillis?.let { daysBetween(it, System.currentTimeMillis()) }
            if (startedMillis != null && daysAgo != null && WHOCarePlan.showsTimeline(daysAgo)) {
                item {
                    FeverTimelineCard(
                        currentDay = daysAgo + 1,
                        temperatures = state.vitals.series(VitalKind.TEMPERATURE),
                        feverStartedMillis = startedMillis,
                        modifier = Modifier.padding(horizontal = Space.screen),
                    )
                }
            }
        }

        // What WHO advises for the record the reader has actually kept. Built
        // from the latest check plus readings recent enough to still describe
        // today — see WHOCarePlan, which never diagnoses and always shows its
        // reasons.
        entries.firstOrNull()?.let { latest ->
            item {
                val recentVitals = state.vitals.latest?.takeIf {
                    System.currentTimeMillis() - it.dateMillis <= CarePlanContext.RECENCY_WINDOW_MILLIS
                }
                // Days of fever as of today, not as of the day the check was
                // run — the illness moves on even when the reader does not
                // open the app.
                val feverDaysAgo = latest.feverStartedMillis?.let { daysBetween(it, System.currentTimeMillis()) }
                val plan = WHOCarePlan.evaluate(
                    symptoms = latest.symptomIds.toSet(),
                    vitals = recentVitals,
                    context = latest.triageContext,
                    feverDaysAgo = feverDaysAgo,
                )
                WHOCarePlanCard(
                    plan = plan,
                    checkedAtMillis = latest.dateMillis,
                    isCurrent = WHOCarePlan.describesToday(
                        daysBetween(latest.dateMillis, System.currentTimeMillis()),
                    ),
                    notableLabs = notableLabNames(state, loc),
                    onRecheck = onOpenCheck,
                    modifier = Modifier.padding(horizontal = Space.screen),
                    haematocritRising = CarePlanContext.haematocritIsRising(
                        state.labs.series(LabMeasure.HAEMATOCRIT),
                    ),
                )
            }
        }

        // WHO's oral fluids: what the reader drinks and how often they pass
        // urine, today.
        item {
            FluidCard(
                state = state,
                onAddDrink = onAddDrink,
                modifier = Modifier.padding(horizontal = Space.screen),
            )
        }

        item {
            val latest = state.vitals.latest
            CardSection(
                title = loc.t("vital.section"),
                subtitle = latest?.let { loc.t("vital.lastTaken", loc.dayAndTime(it.dateMillis)) },
                modifier = Modifier.padding(horizontal = Space.screen),
            ) {
                if (state.vitals.entries.isEmpty()) {
                    Text(
                        loc.t("vital.none"),
                        style = typo(Typo.CALLOUT),
                        color = colors.secondaryText,
                    )
                } else {
                    // Two across, as iOS lays them out. Diastolic is left out
                    // on purpose: it is shown with systolic when the reading is
                    // opened, and a tile of its own would spend a quarter of
                    // the card on the half of a blood pressure that changes
                    // least.
                    listOf(
                        VitalKind.TEMPERATURE to VitalKind.PULSE,
                        VitalKind.SYSTOLIC to VitalKind.OXYGEN_SATURATION,
                    ).forEach { (left, right) ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Space.row),
                        ) {
                            VitalTile(state, left, Modifier.weight(1f))
                            VitalTile(state, right, Modifier.weight(1f))
                        }
                    }

                    val unit = state.preferences.temperatureUnit
                    val temperatures = state.vitals.series(VitalKind.TEMPERATURE)
                    if (temperatures.size >= 2) {
                        HealthTrendChart(
                            title = loc.t("trend.chart.temperature"),
                            // Charted in the reader's chosen unit; stored Celsius.
                            points = temperatures.map { it.first to unit.fromCelsius(it.second) },
                            trend = HealthTrend.direction(
                                temperatures.map { it.second },
                                VitalKind.TEMPERATURE.minimumMeaningfulChange,
                            ),
                            tint = colors.accent,
                            threshold = TrendThreshold(
                                value = unit.fromCelsius(VitalKind.TEMPERATURE.usual.endInclusive),
                                concern = TrendThreshold.Concern.ABOVE,
                                word = loc.t("trend.threshold.fever"),
                            ),
                            format = { loc.decimal(it, 1) },
                            xAxisLabels = axisLabels(temperatures.map { it.first }, loc),
                        )
                    }

                    // Names what is outside its usual range and stops. What the
                    // readings mean together is a clinical question, and this
                    // app does not answer clinical questions.
                    val outside = VitalKind.entries.any { kind ->
                        latest?.value(kind)?.let { kind.status(it) != MeasureStatus.NORMAL } == true
                    }
                    if (outside) {
                        InlineNote(
                            icon = DwIcons.riskHigh,
                            detail = loc.t("vital.outsideUsual.detail"),
                        )
                    }
                }

                SecondaryActionButton(
                    title = loc.t("vital.record"),
                    icon = DwIcons.save,
                    onClick = onRecordVitals,
                )
            }
        }

        item {
            val latest = state.labs.latest
            CardSection(
                title = loc.t("lab.section"),
                subtitle = latest?.let { loc.t("lab.reported", loc.dayAndTime(it.dateMillis)) },
                modifier = Modifier.padding(horizontal = Space.screen),
            ) {
                if (state.labs.reports.isEmpty()) {
                    Text(
                        loc.t("lab.none"),
                        style = typo(Typo.CALLOUT),
                        color = colors.secondaryText,
                    )
                } else {
                    // The unit column, sized from the text rather than fixed, so
                    // it holds "×10³/µL" at any font size. With the unit sized
                    // to its own text instead, each number ended wherever its
                    // unit began: "95" and "3.2" beside ×10³/µL stopped well
                    // short of "44.0" beside %, and the column of figures
                    // zig-zagged.
                    val unitStyle = typo(Typo.MICRO)
                    val unitWidth = with(LocalDensity.current) { (unitStyle.fontSize * 4.2f).toDp() }
                    // Each measure's most recent value, not the latest report's:
                    // a report carrying only an NS1 result must not hide the
                    // platelet count from the one before it. As on iOS.
                    LabMeasure.entries.forEach { measure ->
                        val value = state.labs.mostRecent(measure)?.second ?: return@forEach
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                loc.t(measure.labelKey),
                                style = typo(Typo.CALLOUT),
                                color = colors.secondaryText,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                loc.decimal(value, measure.decimals),
                                style = typo(Typo.CALLOUT).tabularFigures(),
                                // On the risk palette, green in the typical
                                // range; the flag word beside it says the same.
                                color = colors.riskInk(measure.status(value).risk),
                            )
                            ShrinkToFitText(
                                loc.t(measure.unitKey),
                                style = unitStyle,
                                color = colors.secondaryText,
                                minScale = 0.7f,
                                modifier = Modifier.padding(start = Space.hair).width(unitWidth),
                            )
                            // Which side of the printed range the reading fell
                            // on, in words. The number was already tinted by its
                            // band, but a tint is not a reading: colour alone
                            // tells a colour-blind reader nothing, and on this
                            // card the number that matters most is a platelet
                            // count. iOS has always printed the word; the strings
                            // were translated here and never shown.
                            ShrinkToFitText(
                                loc.t(measure.flagKey(value)),
                                style = typo(Typo.MICRO)
                                    .copy(textAlign = TextAlign.End),
                                color = colors.riskInk(measure.status(value).risk),
                                modifier = Modifier
                                    .padding(start = Space.tight)
                                    .width(LAB_FLAG_WIDTH),
                            )
                        }
                    }

                    Text(
                        loc.t("lab.rangeNote"),
                        style = typo(Typo.MICRO),
                        color = colors.secondaryText,
                    )

                    // The platelet count over the course of the illness.
                    //
                    // The one line in dengue a doctor asks to see: a count that
                    // is falling says more than any single number does, which
                    // is why two counts are enough to draw it and one is not.
                    // The threshold is marked below rather than above — for a
                    // platelet count it is the floor that matters, and marking
                    // the top of the range would highlight the readings nobody
                    // is worried about.
                    val platelets = state.labs.series(LabMeasure.PLATELETS)
                    if (platelets.size >= 2) {
                        HairlineDivider()
                        HealthTrendChart(
                            title = loc.t("trend.chart.platelets"),
                            points = platelets,
                            trend = HealthTrend.direction(
                                platelets.map { it.second },
                                LabMeasure.PLATELETS.minimumMeaningfulChange,
                            ),
                            tint = colors.accent,
                            threshold = TrendThreshold(
                                value = LabMeasure.PLATELETS.typical.start,
                                concern = TrendThreshold.Concern.BELOW,
                                word = loc.t("lab.flag.low"),
                            ),
                            format = { loc.num(Math.round(it).toInt()) },
                            xAxisLabels = axisLabels(platelets.map { it.first }, loc),
                        )
                    }

                    // Serology, only where a test was actually done: each test's
                    // newest result, which may be on an older report than the
                    // latest. A positive result takes the alarm ink, as on iOS;
                    // the word beside it always says which it is.
                    val results = DengueTest.entries.mapNotNull { test ->
                        state.labs.mostRecentResult(test)?.let { test to it.second }
                    }
                    if (results.isNotEmpty()) {
                        HairlineDivider()
                        results.forEach { (test, result) ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    loc.t(test.labelKey),
                                    style = typo(Typo.CALLOUT),
                                    color = colors.secondaryText,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    loc.t(result.labelKey),
                                    style = typo(Typo.CALLOUT),
                                    color = if (result == TestResult.POSITIVE) {
                                        colors.riskInk(RiskLevel.HIGH)
                                    } else {
                                        colors.primaryText
                                    },
                                )
                            }
                        }
                    }
                }

                SecondaryActionButton(
                    title = loc.t("lab.add"),
                    icon = DwIcons.log,
                    onClick = onAddLabReport,
                )
            }
        }


        item { Spacer(Modifier.height(Space.section)) }
    }

    if (confirmingDelete) {
        ConfirmCheckDeletion(
            loc = loc,
            onConfirm = {
                state.caseLog.deleteLatest()
                confirmingDelete = false
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

/**
 * Past checks, and deleting the latest one. In a menu rather than on the card:
 * these are occasional, and a delete button beside "Check again" would sit one
 * slip away from the action people come here for.
 */
@Composable
private fun CheckMenu(onOpenHistory: () -> Unit, onDeleteLatest: () -> Unit) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Icon(
            DwIcons.more,
            contentDescription = loc.t("check.actions"),
            tint = colors.accent,
            modifier = Modifier
                .size(Hit.minimum)
                .clip(Shapes.chip)
                .clickable(role = Role.Button) { open = true }
                .padding(12.dp),
        )
        DropdownMenu(open, onDismissRequest = { open = false }, containerColor = colors.card) {
            DropdownMenuItem(
                text = { Text(loc.t("check.history.title"), style = typo(Typo.SUBHEADLINE)) },
                leadingIcon = {
                    Icon(DwIcons.history, contentDescription = null, tint = colors.accent,
                         modifier = Modifier.size(18.dp))
                },
                onClick = {
                    open = false
                    onOpenHistory()
                },
            )
            DropdownMenuItem(
                text = { Text(loc.t("check.delete"), style = typo(Typo.SUBHEADLINE)) },
                leadingIcon = {
                    Icon(DwIcons.delete, contentDescription = null,
                         tint = colors.riskTint(RiskLevel.SEVERE), modifier = Modifier.size(18.dp))
                },
                onClick = {
                    open = false
                    onDeleteLatest()
                },
            )
        }
    }
}

/** The glyph and band iOS pairs with each outcome, so the card reads the same. */
internal val TriageOutcome.icon
    get() = when (this) {
        TriageOutcome.SELF_CARE -> DwIcons.riskLow
        TriageOutcome.TEST_ADVISED -> DwIcons.riskModerate
        TriageOutcome.SEE_DOCTOR_TODAY -> DwIcons.riskHigh
        TriageOutcome.EMERGENCY -> DwIcons.riskSevere
    }

internal val TriageOutcome.risk
    get() = when (this) {
        TriageOutcome.SELF_CARE -> RiskLevel.LOW
        TriageOutcome.TEST_ADVISED -> RiskLevel.MODERATE
        TriageOutcome.SEE_DOCTOR_TODAY -> RiskLevel.HIGH
        TriageOutcome.EMERGENCY -> RiskLevel.SEVERE
    }

/** One measure's most recent reading, on the risk palette, with when it was taken. */
@Composable
private fun VitalTile(state: AppState, kind: VitalKind, modifier: Modifier = Modifier) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val unit = state.preferences.temperatureUnit
    val reading = state.vitals.entries.firstOrNull { it.value(kind) != null }
    val stored = reading?.value(kind)
    val status = stored?.let { kind.status(it) }

    val shown = when {
        stored == null -> "—"
        kind == VitalKind.TEMPERATURE -> loc.decimal(unit.fromCelsius(stored), kind.decimals)
        else -> loc.decimal(stored, kind.decimals)
    }
    val unitLabel =
        if (kind == VitalKind.TEMPERATURE) unit.symbol else loc.t(kind.unitKey)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            loc.t(kind.labelKey),
            style = typo(Typo.MICRO),
            color = colors.secondaryText,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                shown,
                style = typo(Typo.STAT_VALUE).tabularFigures(),
                // Green in the usual range, amber to red outside it — the risk
                // palette, as on iOS.
                color = status?.let { colors.riskInk(it.risk) } ?: colors.primaryText,
            )
            if (stored != null) {
                Text(unitLabel, style = typo(Typo.MICRO), color = colors.secondaryText)
            }
        }
        if (reading != null) {
            // Per tile, because a card can mix a temperature from this morning
            // with a blood pressure from two days ago, and the card's own
            // subtitle only dates the newest entry. As on iOS.
            ShrinkToFitText(
                loc.dayAndTime(reading.dateMillis),
                style = typo(Typo.MICRO),
                color = colors.mutedInk,
                minScale = 0.7f,
            )
        }
        if (status != null) {
            // The word beside the colour, so green and red are never the only
            // thing telling a normal reading from one that is not.
            Text(
                loc.t(status.labelKey),
                style = typo(Typo.MICRO),
                color = colors.riskInk(status.risk),
            )
        }
    }
}

/**
 * Whole days between two instants, counted by calendar day rather than by
 * elapsed hours: a check at 11pm and a glance at 1am are a day apart, and the
 * reader thinks of them that way.
 */
private fun daysBetween(fromMillis: Long, toMillis: Long): Int {
    fun startOfDay(millis: Long) = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val day = 24L * 60 * 60 * 1000
    return ((startOfDay(toMillis) - startOfDay(fromMillis)) / day).toInt()
}

/**
 * Lab measures far enough outside their range to be worth naming beside the
 * plan — never folded into it. See [CarePlanContext].
 */
private fun notableLabNames(state: AppState, loc: Localization): String? {
    val readings = LabMeasure.entries.flatMap { measure ->
        state.labs.series(measure).map { (dateMillis, value) ->
            Triple(measure, value, dateMillis)
        }
    }
    val context = CarePlanContext.from(vitals = emptyList(), labs = readings)
    if (context.notableLabs.isEmpty()) return null
    return context.notableLabs.joinToString(", ") { loc.t(it.labelKey) }
}

/**
 * Three dates across the foot of a chart: the first reading, the middle one and
 * the last. Fewer than three points gets what it has rather than a repeated
 * label.
 */
private fun axisLabels(dates: List<Long>, loc: Localization): List<String> = when {
    dates.isEmpty() -> emptyList()
    dates.size <= 2 -> dates.map { loc.dayMonth(it) }
    else -> listOf(dates.first(), dates[dates.size / 2], dates.last()).map { loc.dayMonth(it) }
}

/**
 * The column the lab flag word sits in.
 *
 * Fixed, so "Low", "High" and "Normal" — and Bengali's কম, বেশি, স্বাভাবিক —
 * all end on the same edge down the table instead of each being placed by the
 * value beside it.
 */
private val LAB_FLAG_WIDTH = 56.dp
