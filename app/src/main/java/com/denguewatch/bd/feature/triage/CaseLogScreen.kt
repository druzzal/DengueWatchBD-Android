package com.denguewatch.bd.feature.triage

import android.content.ClipData
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.AppState
import com.denguewatch.bd.feature.shared.CircleActionButton
import com.denguewatch.bd.feature.shared.EmptyStateView
import com.denguewatch.bd.feature.shared.GroupedSection
import com.denguewatch.bd.feature.shared.HairlineDivider
import com.denguewatch.bd.feature.shared.InlineNote
import com.denguewatch.bd.feature.shared.SecondaryActionButton
import com.denguewatch.bd.feature.shared.Sparkline
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.model.HealthLog
import com.denguewatch.bd.model.HealthLogDay
import com.denguewatch.bd.model.MeasureStatus
import com.denguewatch.bd.model.VitalKind
import com.denguewatch.bd.ui.theme.Card
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Shapes
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.tabularFigures
import com.denguewatch.bd.ui.theme.typo

/**
 * The reader's own record, grouped by the day it was taken.
 *
 * Vital signs and lab reports are two stores because they are two kinds of
 * record, but a person looking back at an illness thinks in days: "Tuesday my
 * temperature was 39.2 and my platelets were 96". The grouping lives in
 * [HealthLog]; this screen reads it.
 *
 * Symptom checks are deliberately not listed. They are not measurements — they
 * are what the reader felt, answered against WHO criteria — and they have their
 * own place in My health, where the latest one drives the care plan. A log
 * handed to a doctor is a list of numbers. Deleting everything still clears
 * them, because this is the only screen that can.
 *
 * This is the only part of the app holding anything the reader typed, which is
 * why the privacy footer is not optional.
 */
@Composable
fun CaseLogScreen(
    state: AppState,
    onOpenDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val context = LocalContext.current

    // The log is always ordered by date; this only decides which end the reader
    // starts from. Persisted, so the choice survives leaving the screen.
    val oldestFirst = state.preferences.logOldestFirst
    var editing by remember { mutableStateOf(false) }
    // Keyed by the day itself: the list is rebuilt from the stores on every
    // change, so holding the day objects would go stale the moment one record
    // in a day is removed.
    var selection by remember { mutableStateOf(emptySet<Long>()) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Long?>(null) }
    var confirmingDay by remember { mutableStateOf<Long?>(null) }
    /// Deleting the ticked days asked nothing, while deleting one by swipe
    /// asked — so the gesture that removes more records was the easier one.
    var confirmingSelection by remember { mutableStateOf(false) }

    val ordered = HealthLog.days(state.vitals.entries, state.labs.reports)
    val days = if (oldestFirst) ordered.reversed() else ordered
    val numbers = HealthLog.numbers(ordered)
    val temperatures = state.vitals.series(VitalKind.TEMPERATURE)

    /** The reader's name for a day, or its number. */
    fun title(day: HealthLogDay): String =
        state.logNames.name(day.dateMillis)
            ?: numbers[day.dateMillis]?.let { loc.t("log.defaultName", loc.num(it)) }
            ?: loc.dayMonth(day.dateMillis)

    fun deleteDay(day: HealthLogDay) {
        day.vitals.forEach { state.vitals.delete(it.id) }
        day.labs.forEach { state.labs.delete(it.id) }
        // A name must not outlive the day it belonged to, or tomorrow's records
        // would inherit it.
        state.logNames.forget(day.dateMillis)
        selection = selection - day.dateMillis
    }

    if (days.isEmpty()) {
        Box(modifier.fillMaxSize().background(colors.plane).padding(contentPadding)) {
            EmptyStateView(
                icon = DwIcons.log,
                title = loc.t("log.empty.title"),
                message = loc.t("log.empty.detail"),
                modifier = Modifier.padding(horizontal = Space.screen),
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.plane),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Space.stack),
    ) {
        item {
            LogToolbar(
                oldestFirst = oldestFirst,
                onSort = { state.preferences.logOldestFirst = it },
                editing = editing,
                onToggleEditing = {
                    editing = !editing
                    if (!editing) selection = emptySet()
                },
                onExport = {
                    // Built here, then handed to whatever the reader shares
                    // with. The file lives in the app's own cache, which is the
                    // only place its FileProvider will share from.
                    val file = HealthLogPdf.write(context, ordered, loc, state.preferences.temperatureUnit)
                    val uri = FileProvider.getUriForFile(
                        context, context.packageName + ".fileprovider", file
                    )
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                // So the share sheet can show the file it is
                                // sending, not an unlabelled attachment.
                                clipData = ClipData.newRawUri(file.name, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            },
                            loc.t("log.export"),
                        )
                    )
                },
                modifier = Modifier.padding(horizontal = Space.screen),
            )
        }

        // One reading is a dot, not a course. The chart earns its place from two.
        if (temperatures.size >= 2) {
            item {
                GroupedSection(
                    title = loc.t("log.temperature"),
                    modifier = Modifier.padding(horizontal = Space.screen),
                ) {
                    Sparkline(
                        values = temperatures.map { it.second },
                        color = colors.accent,
                        modifier = Modifier.fillMaxWidth().height(96.dp),
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            loc.dayMonth(temperatures.first().first),
                            style = typo(Typo.MICRO),
                            color = colors.secondaryText,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            loc.dayMonth(temperatures.last().first),
                            style = typo(Typo.MICRO),
                            color = colors.secondaryText,
                        )
                    }
                }
            }
        }

        item {
            GroupedSection(
                title = loc.t("log.entries"),
                modifier = Modifier.padding(horizontal = Space.screen),
            ) {
                days.forEachIndexed { index, day ->
                    if (index > 0) HairlineDivider()
                    LogDayRow(
                        day = day,
                        title = title(day),
                        editing = editing,
                        selected = day.dateMillis in selection,
                        onToggleSelected = {
                            selection = if (day.dateMillis in selection) {
                                selection - day.dateMillis
                            } else {
                                selection + day.dateMillis
                            }
                        },
                        onOpen = { onOpenDay(day.dateMillis) },
                        onRename = { renaming = day.dateMillis },
                        onDelete = { confirmingDay = day.dateMillis },
                    )
                }
            }
        }

        if (editing) {
            item {
                SecondaryActionButton(
                    title = if (selection.isEmpty()) {
                        loc.t("common.delete")
                    } else {
                        loc.t("log.delete.selected", loc.num(selection.size))
                    },
                    icon = DwIcons.delete,
                    tint = colors.riskTint(MeasureStatus.FAR_OUTSIDE.risk),
                    onClick = {
                        if (selection.isEmpty()) return@SecondaryActionButton
                        confirmingSelection = true
                    },
                    modifier = Modifier.padding(horizontal = Space.screen),
                )
            }
        }

        item {
            Column(
                Modifier.padding(horizontal = Space.screen),
                verticalArrangement = Arrangement.spacedBy(Space.tight),
            ) {
                SecondaryActionButton(
                    title = loc.t("common.deleteAll"),
                    icon = DwIcons.delete,
                    tint = colors.riskTint(MeasureStatus.FAR_OUTSIDE.risk),
                    onClick = { confirmingDelete = true },
                )
                InlineNote(icon = DwIcons.lock, detail = loc.t("log.privacyFooter"))
            }
        }

        item { Spacer(Modifier.height(Space.section)) }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(loc.t("log.confirmDelete"), style = typo(Typo.HEADLINE)) },
            confirmButton = {
                TextButton(onClick = {
                    // Symptom checks go too, although the log no longer lists
                    // them. This is the only screen that can clear them, and
                    // leaving the reader's answers on the phone after they
                    // asked to delete everything would be the wrong way to be
                    // literal about what the list shows.
                    state.caseLog.clear()
                    state.vitals.clear()
                    state.labs.clear()
                    state.logNames.clear()
                    confirmingDelete = false
                }) { Text(loc.t("common.deleteAll")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(loc.t("common.cancel"))
                }
            },
        )
    }

    confirmingDay?.let { dayMillis ->
        AlertDialog(
            onDismissRequest = { confirmingDay = null },
            title = { Text(loc.t("log.delete.day"), style = typo(Typo.HEADLINE)) },
            text = { Text(loc.t("log.delete.detail")) },
            confirmButton = {
                TextButton(onClick = {
                    days.firstOrNull { it.dateMillis == dayMillis }?.let(::deleteDay)
                    confirmingDay = null
                }) { Text(loc.t("common.delete")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDay = null }) { Text(loc.t("common.cancel")) }
            },
        )
    }

    if (confirmingSelection) {
        AlertDialog(
            onDismissRequest = { confirmingSelection = false },
            title = {
                Text(
                    loc.t("log.delete.days", loc.num(selection.size)),
                    style = typo(Typo.HEADLINE),
                )
            },
            text = { Text(loc.t("log.delete.detail")) },
            confirmButton = {
                TextButton(onClick = {
                    days.filter { it.dateMillis in selection }.forEach(::deleteDay)
                    selection = emptySet()
                    confirmingSelection = false
                }) { Text(loc.t("common.delete")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingSelection = false }) {
                    Text(loc.t("common.cancel"))
                }
            },
        )
    }

    renaming?.let { dayMillis ->
        RenameDialog(
            initial = state.logNames.name(dayMillis).orEmpty(),
            onDismiss = { renaming = null },
            onSave = {
                state.logNames.rename(dayMillis, it)
                renaming = null
            },
        )
    }
}

/** Which end to read from, and whether rows are being picked for deletion. */
@Composable
private fun LogToolbar(
    oldestFirst: Boolean,
    onSort: (Boolean) -> Unit,
    editing: Boolean,
    onToggleEditing: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    var open by remember { mutableStateOf(false) }

    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.tight),
    ) {
        Box {
            Row(
                Modifier
                    .clip(Shapes.chip)
                    .clickable(role = Role.Button) { open = true }
                    .padding(horizontal = Space.tight, vertical = 6.dp)
                    .semantics { contentDescription = loc.t("log.sort") },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    DwIcons.sort,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    loc.t(if (oldestFirst) "log.sort.oldest" else "log.sort.newest"),
                    style = typo(Typo.CAPTION, FontWeight.Medium),
                    color = colors.accent,
                )
            }
            DropdownMenu(open, onDismissRequest = { open = false }, containerColor = colors.card) {
                listOf(false to "log.sort.newest", true to "log.sort.oldest").forEach { (value, key) ->
                    DropdownMenuItem(
                        text = { Text(loc.t(key), style = typo(Typo.SUBHEADLINE)) },
                        trailingIcon = if (value == oldestFirst) {
                            {
                                Icon(
                                    DwIcons.check2,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else null,
                        onClick = { onSort(value); open = false },
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Text(
            loc.t("log.export"),
            style = typo(Typo.CAPTION, FontWeight.SemiBold),
            color = colors.accent,
            modifier = Modifier
                .clip(Shapes.chip)
                .clickable(role = Role.Button, onClick = onExport)
                .padding(horizontal = Space.tight, vertical = 6.dp),
        )

        Text(
            loc.t(if (editing) "common.done" else "common.edit"),
            style = typo(Typo.CAPTION, FontWeight.SemiBold),
            color = colors.accent,
            modifier = Modifier
                .clip(Shapes.chip)
                .clickable(role = Role.Button, onClick = onToggleEditing)
                .padding(horizontal = Space.tight, vertical = 6.dp),
        )
    }
}

/**
 * One day of the record, as a single line.
 *
 * A temperature at nine, a blood pressure at three and a blood count from the
 * clinic are one day of an illness, not three unrelated events. The line says
 * what the day holds; the detail holds it.
 */
@Composable
private fun LogDayRow(
    day: HealthLogDay,
    title: String,
    editing: Boolean,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val status = day.worstStatus
    // Coloured by the furthest a reading strayed that day, so a bad day is
    // visible without opening it.
    val accent = if (status == MeasureStatus.NORMAL) colors.accent else colors.riskInk(status.risk)
    var menu by remember { mutableStateOf(false) }

    val figures = buildList {
        day.peakTemperature?.let { add(loc.decimal(it, 1) + "°C") }
        day.latestBloodPressure?.let { (s, d) -> add("${loc.num(s)}/${loc.num(d)}") }
        day.lowestPlatelets?.let { add("${loc.t("lab.platelets")} ${loc.num(it.toInt())}") }
    }.joinToString(" · ")

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { if (editing) onToggleSelected() else onOpen() }
                .padding(vertical = Space.tight)
                .semantics {
                    contentDescription = listOf(
                        title,
                        loc.fullDate(day.dateMillis),
                        figures,
                        loc.t(status.labelKey),
                    ).filter { it.isNotEmpty() }.joinToString(", ")
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.row),
        ) {
            if (editing) {
                Icon(
                    if (selected) DwIcons.upToDate else DwIcons.circleOutline,
                    contentDescription = null,
                    tint = if (selected) colors.accent else colors.tertiaryText,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Spacer(
                    Modifier
                        .width(3.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent)
                )
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = typo(Typo.CALLOUT, FontWeight.Medium))
                Text(
                    loc.fullDate(day.dateMillis),
                    style = typo(Typo.MICRO),
                    color = colors.secondaryText,
                )
                if (figures.isNotEmpty()) {
                    Text(
                        figures,
                        style = typo(Typo.MICRO).tabularFigures(),
                        color = colors.secondaryText,
                    )
                }
            }

            Text(
                loc.num(day.itemsNewestFirst.size),
                style = typo(Typo.MICRO).tabularFigures(),
                color = colors.secondaryText,
            )

            if (!editing) {
                Icon(
                    DwIcons.more,
                    contentDescription = loc.t("log.rename"),
                    tint = colors.tertiaryText,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(Shapes.chip)
                        .clickable(role = Role.Button) { menu = true },
                )
            }
        }

        DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = colors.card) {
            DropdownMenuItem(
                text = { Text(loc.t("log.rename"), style = typo(Typo.SUBHEADLINE)) },
                onClick = { menu = false; onRename() },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        loc.t("log.delete.day"),
                        style = typo(Typo.SUBHEADLINE),
                        color = colors.riskInk(MeasureStatus.FAR_OUTSIDE.risk),
                    )
                },
                onClick = { menu = false; onDelete() },
            )
        }
    }
}

/** Naming a day something the reader will recognise. */
@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    var draft by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(loc.t("log.rename"), style = typo(Typo.HEADLINE)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.row)) {
                Text(
                    loc.t("log.rename.detail"),
                    style = typo(Typo.CAPTION),
                    color = colors.secondaryText,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(Shapes.control)
                        .background(colors.plane)
                        .padding(Space.row),
                ) {
                    if (draft.isEmpty()) {
                        Text(
                            loc.t("log.rename.placeholder"),
                            style = typo(Typo.CALLOUT),
                            color = colors.tertiaryText,
                        )
                    }
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        singleLine = true,
                        textStyle = typo(Typo.CALLOUT).copy(color = colors.primaryText),
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text(loc.t("common.save")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(loc.t("common.cancel")) } },
    )
}
