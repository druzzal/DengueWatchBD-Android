package com.denguewatch.bd.feature.triage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.AppState
import com.denguewatch.bd.feature.shared.HairlineDivider
import com.denguewatch.bd.feature.shared.InlineNote
import com.denguewatch.bd.feature.shared.ShrinkToFitText
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.model.DengueTest
import com.denguewatch.bd.model.HealthLog
import com.denguewatch.bd.model.HealthLogDay
import com.denguewatch.bd.model.LabMeasure
import com.denguewatch.bd.model.LabReport
import com.denguewatch.bd.model.MeasureStatus
import com.denguewatch.bd.model.TemperatureUnit
import com.denguewatch.bd.model.TestResult
import com.denguewatch.bd.model.VitalKind
import com.denguewatch.bd.model.VitalsEntry
import com.denguewatch.bd.ui.theme.Card
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.tabularFigures
import com.denguewatch.bd.ui.theme.typo

/**
 * One day of the record, in full.
 *
 * Everything written down that day, oldest moment first, so it reads as the day
 * happened rather than as a stack with the newest on top. This is the screen a
 * reader holds up to a doctor, so nothing is abbreviated: every reading is
 * named, given its unit, and said to be in or out of its usual range in words
 * as well as colour.
 */
@Composable
fun LogDayDetailScreen(
    state: AppState,
    dayMillis: Long,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val day = HealthLog.days(state.vitals.entries, state.labs.reports)
        .firstOrNull { it.dateMillis == dayMillis }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.plane),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Space.stack),
    ) {
        item {
            Text(
                loc.fullDate(dayMillis),
                style = typo(Typo.CAPTION),
                color = colors.secondaryText,
                modifier = Modifier.padding(horizontal = Space.screen),
            )
        }

        // Oldest first, deliberately: a day is read forwards.
        items@ for (item in day?.itemsNewestFirst?.reversed().orEmpty()) {
            item(key = item.id) {
                Card(Modifier.padding(horizontal = Space.screen)) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.row)) {
                        when (item) {
                            is HealthLogDay.Item.Vitals -> VitalsDetail(item.entry, state.preferences.temperatureUnit)
                            is HealthLogDay.Item.Lab -> LabDetail(item.report)
                        }
                    }
                }
            }
        }

        item {
            InlineNote(
                icon = DwIcons.lock,
                detail = loc.t("log.privacyFooter"),
                modifier = Modifier.padding(horizontal = Space.screen),
            )
        }

        item { Spacer(Modifier.height(Space.section)) }
    }
}

@Composable
private fun VitalsDetail(entry: VitalsEntry, unit: TemperatureUnit) {
    val loc = LocalLocalization.current
    val colors = Dw.colors

    Header(loc.t("vital.section"), loc.timeOfDay(entry.dateMillis))

    // Blood pressure is one reading written as two numbers, so it is shown as
    // one row rather than a systolic row and a diastolic row.
    val systolic = entry.systolic
    val diastolic = entry.diastolic
    if (systolic != null && diastolic != null) {
        MeasureRow(
            label = loc.t("vital.bloodPressure"),
            value = "${loc.num(systolic.toInt())}/${loc.num(diastolic.toInt())}",
            unit = loc.t(VitalKind.SYSTOLIC.unitKey),
            status = worseOf(
                VitalKind.SYSTOLIC.status(systolic),
                VitalKind.DIASTOLIC.status(diastolic),
            ),
        )
    }

    VitalKind.entries.forEach { kind ->
        // The two halves of a blood pressure are covered by the row above.
        if (kind == VitalKind.SYSTOLIC && diastolic != null) return@forEach
        if (kind == VitalKind.DIASTOLIC && systolic != null) return@forEach
        val value = entry.value(kind) ?: return@forEach
        // Temperature is stored in Celsius and has no unit in the table on
        // purpose — it is shown in whichever unit the reader chose, so both the
        // number and the symbol come from the preference rather than the key.
        val isTemperature = kind == VitalKind.TEMPERATURE
        MeasureRow(
            label = loc.t(kind.labelKey),
            value = loc.decimal(if (isTemperature) unit.fromCelsius(value) else value, kind.decimals),
            unit = if (isTemperature) unit.symbol else loc.t(kind.unitKey),
            status = kind.status(value),
        )
    }

    if (entry.note.isNotBlank()) {
        Text(entry.note, style = typo(Typo.CAPTION), color = colors.secondaryText)
    }
}

@Composable
private fun LabDetail(report: LabReport) {
    val loc = LocalLocalization.current
    val colors = Dw.colors

    Header(loc.t("lab.report"), loc.timeOfDay(report.dateMillis))

    LabMeasure.entries.forEach { measure ->
        val value = report.value(measure) ?: return@forEach
        MeasureRow(
            label = loc.t(measure.labelKey),
            value = loc.decimal(value, measure.decimals),
            unit = loc.t(measure.unitKey),
            status = measure.status(value),
        )
    }

    // A test that was not done says nothing, so it is left out rather than
    // listed as "Not done" three times on every report.
    DengueTest.entries.forEach { test ->
        val result = report.result(test)
        if (result == TestResult.NOT_DONE) return@forEach
        MeasureRow(
            label = loc.t(test.labelKey),
            value = loc.t(result.labelKey),
            unit = "",
            status = MeasureStatus.NORMAL,
            emphasise = result == TestResult.POSITIVE,
        )
    }

    if (report.note.isNotBlank()) {
        Text(report.note, style = typo(Typo.CAPTION), color = colors.secondaryText)
    }
}

@Composable
private fun Header(title: String, time: String) {
    val colors = Dw.colors
    Row(Modifier.fillMaxWidth()) {
        Text(title, style = typo(Typo.HEADLINE), modifier = Modifier.weight(1f))
        Text(time, style = typo(Typo.MICRO).tabularFigures(), color = colors.secondaryText)
    }
    HairlineDivider()
}

/**
 * One reading: what it is, what it was, and whether that is usual.
 *
 * The range is said in words as well as colour — a reader who cannot tell the
 * tints apart still learns that a count is outside its usual range.
 */
@Composable
private fun MeasureRow(
    label: String,
    value: String,
    unit: String,
    status: MeasureStatus,
    emphasise: Boolean = false,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val ink = when {
        status != MeasureStatus.NORMAL -> colors.riskInk(status.risk)
        emphasise -> colors.riskInk(MeasureStatus.OUTSIDE.risk)
        else -> colors.primaryText
    }

    // Every row keeps the same unit column, empty or not, so the figures all
    // end on one edge: sized to the unit's own text, "95" beside ×10³/µL and
    // "44.0" beside % stopped in different places and the column zig-zagged.
    // Sized from the text, so "×10³/µL" fits at any font size. Units sit on
    // the figures' baseline rather than at the top of the taller line.
    val unitStyle = typo(Typo.MICRO)
    val unitWidth = with(LocalDensity.current) { (unitStyle.fontSize * 4.2f).toDp() }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                label,
                style = typo(Typo.CALLOUT),
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
            Text(
                value,
                style = typo(Typo.CALLOUT, FontWeight.SemiBold).tabularFigures(),
                color = ink,
                modifier = Modifier.alignByBaseline(),
            )
            ShrinkToFitText(
                unit,
                style = unitStyle,
                color = colors.secondaryText,
                minScale = 0.7f,
                modifier = Modifier
                    .padding(start = Space.tight)
                    .width(unitWidth)
                    .alignByBaseline(),
            )
        }
        if (status != MeasureStatus.NORMAL) {
            Text(loc.t(status.labelKey), style = typo(Typo.MICRO), color = ink)
        }
    }
}

/** The worse of two statuses, for a reading written as a pair. */
private fun worseOf(a: MeasureStatus, b: MeasureStatus): MeasureStatus =
    if (b.risk.ordinal > a.risk.ordinal) b else a
