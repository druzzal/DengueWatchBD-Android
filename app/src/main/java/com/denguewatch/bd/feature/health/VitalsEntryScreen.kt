package com.denguewatch.bd.feature.health

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.AppState
import com.denguewatch.bd.feature.shared.GroupedSection
import com.denguewatch.bd.feature.shared.HairlineDivider
import com.denguewatch.bd.feature.shared.PlainSection
import com.denguewatch.bd.feature.shared.SegmentedPicker
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.model.MeasureInput
import com.denguewatch.bd.model.TemperatureUnit
import com.denguewatch.bd.model.VitalKind
import com.denguewatch.bd.model.VitalsEntry
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.Space

/**
 * Recording one set of readings.
 *
 * Every field is optional, as on iOS. Someone with a thermometer and no cuff
 * should be able to keep a useful record; demanding a full set would mean they
 * record nothing at all.
 *
 * Laid out as the iOS form is, section for section: when it was taken, then
 * temperature with its unit, then blood pressure, then pulse and oxygen
 * together under no heading of their own, then the note. Save sits in the
 * navigation bar rather than at the foot of the page — see [SaveSlot].
 */
@Composable
fun VitalsEntryScreen(
    state: AppState,
    saveSlot: SaveSlot,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val preferences = state.preferences
    val unit = preferences.temperatureUnit

    val text = remember { mutableStateMapOf<VitalKind, String>() }
    var note by remember { mutableStateOf("") }
    // Readings are often written up after the fact — a fever taken at three in
    // the morning and entered at nine. Stamping them "now" would put the spike
    // on the wrong day and in the wrong place on the chart, so the reader says
    // when.
    var takenAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // The figure a unit switch wrote into the temperature field, and the exact
    // Celsius behind it. Converting from the rounded figure instead drifted a
    // tenth on every switch: 101 °F, 38.3 °C, 100.9 °F.
    var converted by remember { mutableStateOf<Pair<String, Double>?>(null) }

    fun reading(kind: VitalKind) = MeasureInput.read(text[kind].orEmpty(), kind, unit)

    val invalid = VitalKind.entries.any { reading(it).isProblem }
    val anyValue = VitalKind.entries.any { reading(it).storedValue != null }

    fun save() {
        // Belt and braces: the button is disabled in this state, but a record
        // must not be able to arrive half-dropped by some later refactor.
        if (invalid) return
        var entry = VitalsEntry(dateMillis = takenAt, note = note)
        VitalKind.entries.forEach { kind ->
            val value = reading(kind).storedValue ?: return@forEach
            entry = when (kind) {
                VitalKind.TEMPERATURE -> entry.copy(temperature = value)
                VitalKind.PULSE -> entry.copy(pulse = value)
                VitalKind.SYSTOLIC -> entry.copy(systolic = value)
                VitalKind.DIASTOLIC -> entry.copy(diastolic = value)
                VitalKind.OXYGEN_SATURATION -> entry.copy(oxygenSaturation = value)
            }
        }
        state.vitals.add(entry)
        onSaved()
    }

    // Published after composition, never during. Nothing typed is nothing to
    // save; anything unreadable is blocked rather than quietly dropped, so a
    // record can never arrive missing a value the reader entered.
    SideEffect {
        saveSlot.enabled = anyValue && !invalid
        saveSlot.perform = ::save
    }
    DisposableEffect(Unit) { onDispose { saveSlot.clear() } }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.plane),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Space.stack),
    ) {
        item {
            PlainSection(Modifier.padding(horizontal = Space.screen)) {
                DateTimeRow(
                    label = loc.t("vital.takenAt"),
                    millis = takenAt,
                    onChange = { takenAt = it },
                )
            }
        }

        item {
            GroupedSection(
                title = loc.t("vital.temperature"),
                modifier = Modifier.padding(horizontal = Space.screen),
            ) {
                // The same reading in the other scale, under the label as on
                // iOS, so a Fahrenheit thermometer and a Celsius prescription
                // can be read together.
                val other = if (unit == TemperatureUnit.CELSIUS) TemperatureUnit.FAHRENHEIT
                            else TemperatureUnit.CELSIUS
                VitalRow(
                    VitalKind.TEMPERATURE, text, unit.symbol, ::reading,
                    note = reading(VitalKind.TEMPERATURE).storedValue?.let { celsius ->
                        "= " + loc.decimal(other.fromCelsius(celsius), 1) + other.symbol
                    },
                )
                HairlineDivider()
                SegmentedPicker(
                    options = TemperatureUnit.entries,
                    selected = unit,
                    label = { it.symbol },
                    // Stored Celsius either way, so switching re-labels the
                    // record rather than rewriting it.
                    onSelect = { next ->
                        // A reading that is good in the old unit is the same
                        // temperature in the new one, so it is converted —
                        // left alone, 38.6 typed in °C became 38.6 °F and was
                        // refused. One that is not good, 101 under °C, was
                        // most likely typed in the other unit, and switching
                        // is how the reader says so: that stays as typed.
                        val typed = text[VitalKind.TEMPERATURE].orEmpty()
                        val celsius = converted?.takeIf { it.first == typed }?.second
                            ?: reading(VitalKind.TEMPERATURE).storedValue
                        if (celsius != null) {
                            val shown = loc.decimal(next.fromCelsius(celsius), VitalKind.TEMPERATURE.decimals)
                            text[VitalKind.TEMPERATURE] = shown
                            converted = shown to celsius
                        }
                        preferences.temperatureUnit = next
                    },
                )
            }
        }

        item {
            GroupedSection(
                title = loc.t("vital.bloodPressure"),
                modifier = Modifier.padding(horizontal = Space.screen),
            ) {
                listOf(VitalKind.SYSTOLIC, VitalKind.DIASTOLIC).forEachIndexed { i, kind ->
                    if (i > 0) HairlineDivider()
                    VitalRow(kind, text, loc.t(kind.unitKey), ::reading)
                }
            }
        }

        item {
            // No heading, as on iOS: pulse and oxygen follow blood pressure as
            // more of the same thing, and a "Vital signs" heading inside a
            // screen called Record readings says nothing.
            PlainSection(Modifier.padding(horizontal = Space.screen)) {
                listOf(VitalKind.PULSE, VitalKind.OXYGEN_SATURATION).forEachIndexed { i, kind ->
                    if (i > 0) HairlineDivider()
                    VitalRow(kind, text, loc.t(kind.unitKey), ::reading)
                }
            }
        }

        item {
            // Said here, where readings are entered, rather than only where
            // they are shown.
            PlainSection(
                Modifier.padding(horizontal = Space.screen),
                footnote = loc.t("vital.outsideUsual.detail"),
            ) {
                NoteField(note) { note = it }
            }
        }

        item { Spacer(Modifier.height(Space.section)) }
    }
}

/** One reading, wired to the map that holds what has been typed so far. */
@Composable
private fun VitalRow(
    kind: VitalKind,
    text: androidx.compose.runtime.snapshots.SnapshotStateMap<VitalKind, String>,
    unitLabel: String,
    reading: (VitalKind) -> MeasureInput.Reading,
    note: String? = null,
) {
    val loc = LocalLocalization.current
    MeasureRow(
        label = loc.t(kind.labelKey),
        value = text[kind].orEmpty(),
        onValueChange = { text[kind] = it },
        unit = unitLabel,
        reading = reading(kind),
        invalidDetail = invalidDetail(reading(kind), kind.decimals),
        note = note,
    )
}

/** What is wrong with a reading, in the reader's own words, or nothing. */
@Composable
internal fun invalidDetail(reading: MeasureInput.Reading, decimals: Int): String? {
    val loc = LocalLocalization.current
    return when (reading) {
        is MeasureInput.Reading.NotANumber -> loc.t("measure.invalid.number")
        is MeasureInput.Reading.OutOfRange -> loc.t(
            "measure.invalid.range",
            loc.decimal(reading.range.start, decimals),
            loc.decimal(reading.range.endInclusive, decimals),
        )
        else -> null
    }
}
