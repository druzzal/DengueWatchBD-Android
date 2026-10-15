package com.denguewatch.bd.feature.health

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.model.MeasureInput
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.ui.theme.Card
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.Shapes
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.tabularFigures
import com.denguewatch.bd.ui.theme.typo
import java.util.Calendar
import java.util.TimeZone

/**
 * The parts the two recording screens are built from.
 *
 * Both of them are the same shape as their iOS counterparts: a grouped form of
 * plain rows, with the save action in the navigation bar rather than at the
 * bottom of the page. Keeping the parts here is what stops the vitals form and
 * the lab form drifting apart, which is how they came to look different on the
 * two platforms in the first place.
 */

/**
 * Lets a recording screen put its Save button in the navigation bar, where iOS
 * has it.
 *
 * The bar belongs to the Scaffold at the root, and the values being saved
 * belong to the screen, so one of them has to reach the other. The screen
 * publishes into this from a `SideEffect` — after composition, never during —
 * and the bar reads it. [enabled] is state so the button greys out as the form
 * fills in; [perform] is not, because a new lambda every recomposition would
 * invalidate the bar on every keystroke for no visible gain.
 */
@androidx.compose.runtime.Stable
class SaveSlot {
    var enabled by mutableStateOf(false)
    var perform: () -> Unit = {}

    /** Called as a screen leaves, so a stale Save cannot outlive its form. */
    fun clear() {
        enabled = false
        perform = {}
    }
}

/**
 * One labelled measurement: name on the left, the number right-aligned, its
 * unit after it, and whatever is wrong with it underneath.
 *
 * The field is borderless on purpose. iOS draws these as plain Form rows, and
 * an outlined box per row — which is what this screen used to have — turns five
 * readings into a page of boxes two and a half times as tall, reading as a
 * different app rather than a port of the same one.
 */
@Composable
internal fun MeasureRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    reading: MeasureInput.Reading,
    unitWidth: androidx.compose.ui.unit.Dp = 48.dp,
    invalidDetail: String?,
    /** A quiet line under the label — the temperature in the other scale. */
    note: String? = null,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val ink = if (reading.isProblem) colors.riskInk(RiskLevel.SEVERE) else colors.primaryText

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                // Said as one thing: without this a screen reader reaches five
                // fields that each announce themselves as "text field" and
                // nothing else, the visible label being a separate view.
                .semantics { contentDescription = "$label, $unit" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = typo(Typo.CALLOUT), modifier = Modifier.weight(1f))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = typo(Typo.CALLOUT)
                    .tabularFigures()
                    .copy(textAlign = TextAlign.End, color = ink),
                cursorBrush = SolidColor(colors.accent),
                // A decimal pad cannot type Bengali numerals — it has no globe
                // key — so a reader whose numbers are ৩৯.৫ could not enter them
                // at all. Bengali gets a keyboard that can be switched.
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (loc.language.usesBengaliDigits) {
                        KeyboardType.Text
                    } else {
                        KeyboardType.Decimal
                    },
                ),
                modifier = Modifier.width(96.dp),
            )
            Text(
                unit,
                style = typo(Typo.MICRO),
                color = colors.secondaryText,
                modifier = Modifier.padding(start = Space.tight).width(unitWidth),
            )
        }
        if (note != null) {
            Text(
                note,
                style = typo(Typo.CAPTION).tabularFigures(),
                color = colors.secondaryText,
            )
        }
        if (invalidDetail != null) {
            Text(
                invalidDetail,
                style = typo(Typo.MICRO),
                color = colors.riskInk(RiskLevel.SEVERE),
            )
        }
    }
}

/** The free-text note both screens end with: borderless, its label the placeholder. */
@Composable
internal fun NoteField(value: String, onValueChange: (String) -> Unit) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    Box(Modifier.fillMaxWidth()) {
        if (value.isEmpty()) {
            Text(
                loc.t("result.note"),
                style = typo(Typo.CALLOUT),
                color = colors.tertiaryText,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            minLines = 2,
            maxLines = 4,
            textStyle = typo(Typo.CALLOUT).copy(color = colors.primaryText),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * When a reading or a report is from: a label, then the date and the time as
 * two things to tap, the way the compact iOS picker splits them.
 *
 * This replaces an hours-ago stepper. The stepper was quicker for "two hours
 * ago" and useless for a report dated last Tuesday, which is the ordinary case
 * for a lab result collected the next day — and it could not reach the year
 * back that iOS allows at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateTimeRow(
    label: String,
    millis: Long,
    onChange: (Long) -> Unit,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = typo(Typo.CALLOUT), modifier = Modifier.weight(1f))
        Pill(loc.pickerDate(millis)) { pickingDate = true }
        Pill(loc.timeOfDay(millis), Modifier.padding(start = Space.tight)) { pickingTime = true }
    }

    if (pickingDate) InAppLocale {
        // A reading that has not been taken yet is not a reading, so the future
        // is closed off in the picker rather than corrected afterwards. A year
        // back is generous on purpose: entering an old fever into a new record
        // is a reasonable thing to do.
        //
        // The bound is today's local date expressed as UTC midnight, which is
        // the only thing the picker's callback can be compared against — it
        // offers each day as that day's midnight in UTC. Comparing against the
        // clock instead leaves today unselectable before 06:00 in Dhaka, and
        // padding the comparison by a day to paper over that — which this did
        // at first — hands the reader tomorrow.
        val endOfRange = utcMidnightOfLocalToday()
        val state = rememberDatePickerState(
            // The reading's own day, in the picker's UTC terms. Handing it the
            // raw instant made a 05:23 reading in Dhaka — still the previous
            // day in UTC — open on yesterday, and "Done" then moved it there.
            initialSelectedDateMillis = utcMidnightOfLocalDay(millis),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    utcTimeMillis in (endOfRange - 365L * 86_400_000L)..endOfRange
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(recordable(withDateOf(it, millis))) }
                    pickingDate = false
                }) { Text(loc.t("common.done")) }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) { Text(loc.t("common.cancel")) }
            },
            colors = DatePickerDefaults.colors(
                containerColor = colors.card,
                selectedDayContainerColor = colors.accent,
                todayDateBorderColor = colors.accent,
                todayContentColor = colors.accent,
            ),
        ) {
            DatePicker(
                state,
                // No title and no typed-entry mode: the iOS picker has neither,
                // and both are strings Material takes from the phone's language
                // rather than the app's.
                title = null,
                // Material formats this one from the phone's locale whatever
                // the configuration says, so it is written by the app instead.
                headline = {
                    Text(
                        state.selectedDateMillis?.let { loc.pickerDate(withDateOf(it, millis)) } ?: "",
                        style = typo(Typo.TITLE),
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 20.dp, bottom = 12.dp),
                    )
                },
                showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    containerColor = colors.card,
                    selectedDayContainerColor = colors.accent,
                    todayDateBorderColor = colors.accent,
                    todayContentColor = colors.accent,
                ),
            )
        }
    }

    if (pickingTime) InAppLocale {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        val state = rememberTimePickerState(
            initialHour = calendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(Calendar.MINUTE),
            // Twelve-hour, as the iOS picker is for en_BD and bn_BD alike.
            is24Hour = false,
        )
        Dialog(onDismissRequest = { pickingTime = false }) {
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(Space.row)) {
                    TimePicker(
                        state,
                        colors = TimePickerDefaults.colors(
                            containerColor = colors.card,
                            clockDialColor = colors.plane,
                            selectorColor = colors.accent,
                            timeSelectorUnselectedContainerColor = colors.plane,
                            periodSelectorUnselectedContainerColor = colors.card,
                            timeSelectorSelectedContainerColor = colors.accent.copy(alpha = 0.15f),
                            timeSelectorSelectedContentColor = colors.accent,
                            periodSelectorSelectedContainerColor = colors.accent.copy(alpha = 0.15f),
                            periodSelectorSelectedContentColor = colors.accent,
                        ),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { pickingTime = false }) {
                            Text(loc.t("common.cancel"))
                        }
                        TextButton(onClick = {
                            onChange(recordable(withTimeOf(millis, state.hour, state.minute)))
                            pickingTime = false
                        }) { Text(loc.t("common.done")) }
                    }
                }
            }
        }
    }
}

/** A tappable value, tinted like the iOS picker's. */
@Composable
private fun Pill(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = Dw.colors
    Text(
        text,
        style = typo(Typo.CALLOUT).tabularFigures(),
        color = colors.accent,
        modifier = modifier
            .clip(Shapes.control)
            .background(colors.accent.copy(alpha = 0.10f))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = Space.row, vertical = Space.tight - 2.dp),
    )
}

/**
 * The day from [dateMillis] with the time kept from [keepTimeFrom].
 *
 * The date picker works in UTC — it hands back midnight UTC for the day that
 * was tapped — while the time being kept is the device's. Reading the day's
 * fields in UTC and writing them into a local calendar is what stops a reading
 * taken at one in the morning in Dhaka landing on the previous day.
 */
internal fun withDateOf(dateMillis: Long, keepTimeFrom: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = dateMillis }
    return Calendar.getInstance().apply {
        timeInMillis = keepTimeFrom
        set(Calendar.YEAR, utc.get(Calendar.YEAR))
        set(Calendar.MONTH, utc.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/**
 * Today's local date as the millis the date picker would offer for it.
 *
 * The picker speaks UTC midnights, so today has to be named in the same terms
 * before the two can be compared at all.
 */
internal fun utcMidnightOfLocalToday(): Long = utcMidnightOfLocalDay(System.currentTimeMillis())

/** The local day [millis] falls on, as the picker's UTC midnight for it. */
internal fun utcMidnightOfLocalDay(millis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = millis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(
            local.get(Calendar.YEAR),
            local.get(Calendar.MONTH),
            local.get(Calendar.DAY_OF_MONTH),
        )
    }.timeInMillis
}

/**
 * Held inside the range iOS allows: up to now, and back exactly a year.
 *
 * Picking today and then an hour that has not arrived yet would otherwise stamp
 * a reading in the future, which the date picker alone cannot prevent. The
 * picker can only bound whole days, so on the day a year back it also offers
 * the hours before this moment; those are pulled forward to it.
 */
internal fun recordable(millis: Long, now: Long = System.currentTimeMillis()): Long =
    millis.coerceIn(now - 365L * 86_400_000L, now)

/**
 * The pickers in the app's language rather than the phone's, as iOS does by
 * handing its picker the app's locale. Material reads month and weekday names
 * and its own labels from the configuration, so a Bengali screen was opening
 * an English calendar.
 */
@Composable
private fun InAppLocale(content: @Composable () -> Unit) {
    val locale = LocalLocalization.current.language.locale
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val localized = remember(configuration, locale) {
        Configuration(configuration).apply { setLocale(locale) }
    }
    val localizedContext = remember(context, localized) { context.createConfigurationContext(localized) }
    CompositionLocalProvider(
        LocalConfiguration provides localized,
        LocalContext provides localizedContext,
        content = content,
    )
}

/** The same day, at the given hour and minute. */
internal fun withTimeOf(millis: Long, hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
