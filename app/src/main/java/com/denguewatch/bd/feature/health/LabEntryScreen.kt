package com.denguewatch.bd.feature.health

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt
import com.denguewatch.bd.AppState
import com.denguewatch.bd.feature.shared.GroupedSection
import com.denguewatch.bd.feature.shared.HairlineDivider
import com.denguewatch.bd.feature.shared.PlainSection
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.model.DengueTest
import com.denguewatch.bd.model.LabMeasure
import com.denguewatch.bd.model.LabReport
import com.denguewatch.bd.model.LabReportParser
import com.denguewatch.bd.model.MeasureInput
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.model.TestResult
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.typo

/**
 * Adding one lab report, as it was printed.
 *
 * Everything is optional: a report that gave only a platelet count is still
 * worth recording, and demanding a full blood count would mean it never got
 * entered. Values are stored exactly as reported — the app does not decide
 * what they mean, because a positive NS1 with a negative IgM on day two is a
 * conversation with a clinician, not a conclusion an app should reach.
 *
 * Scanning and uploading read the values off the page with an on-device text
 * recogniser. What they produce is a *draft*: every number lands in the fields
 * below for the reader to check before anything is saved. A misread platelet
 * count is worse than no platelet count — one is a number somebody can see is
 * wrong, the other is a number they will believe.
 *
 * Section for section this follows the iOS form: the two ways of reading a
 * report, when it is from, the counts, the dengue tests, the note. Save sits in
 * the navigation bar — see [SaveSlot].
 */
@Composable
fun LabEntryScreen(
    state: AppState,
    saveSlot: SaveSlot,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors

    // Saveable: photographing a report hands the screen to the camera, and
    // Android may stop this app while it is away. Coming back to an empty form
    // would lose every value already read off the page.
    val text = rememberSaveable(saver = enumKeyedMapSaver<LabMeasure, String>({ it }, { it })) {
        mutableStateMapOf()
    }
    val results = rememberSaveable(
        saver = enumKeyedMapSaver<DengueTest, TestResult>({ it.name }, { name ->
            TestResult.entries.firstOrNull { it.name == name }
        }),
    ) { mutableStateMapOf() }
    var note by rememberSaveable { mutableStateOf("") }
    // Date and time both: two reports from one day are ordinary during dengue —
    // a morning count and an evening one — and a date alone leaves the log
    // unable to say which came first, which is the whole point of watching a
    // platelet count fall.
    var reportDate by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }

    fun reading(measure: LabMeasure) = MeasureInput.read(text[measure].orEmpty(), measure)

    val invalid = LabMeasure.entries.any { reading(it).isProblem }
    val anyValue = LabMeasure.entries.any { reading(it).storedValue != null } ||
        results.values.any { it != TestResult.NOT_DONE }

    var busy by remember { mutableStateOf(false) }
    var readFailed by remember { mutableStateOf(false) }
    var foundNothing by remember { mutableStateOf(false) }

    /**
     * Puts a recognised draft into the fields, leaving anything already typed
     * alone. Deliberately does not save: the reader sees every value in the
     * same field they would have typed it into, and corrects whatever the
     * camera misread before it becomes a record.
     */
    fun apply(draft: LabReport) {
        var found = false
        LabMeasure.entries.forEach { measure ->
            val value = draft.value(measure) ?: return@forEach
            if (text[measure].isNullOrEmpty()) {
                text[measure] = formatForField(value, measure)
                found = true
            }
        }
        DengueTest.entries.forEach { test ->
            val result = draft.result(test)
            if (result != TestResult.NOT_DONE &&
                (results[test] ?: TestResult.NOT_DONE) == TestResult.NOT_DONE
            ) {
                results[test] = result
                found = true
            }
        }
        foundNothing = !found
    }

    // Photos or Files first, as iOS asks: the report is either a picture the
    // reader took, which lives in the gallery, or a file a diagnostic centre
    // sent, which lives in Files — and either picker alone hides the other.
    var choosingSource by remember { mutableStateOf(false) }

    val scanner = rememberReportScanner(
        onStarted = { busy = true; readFailed = false; foundNothing = false },
        onText = { recognised -> busy = false; apply(LabReportParser.draft(recognised)) },
        onFailed = { busy = false; readFailed = true },
    )

    fun save() {
        if (invalid) return
        state.labs.add(
            LabReport(
                dateMillis = reportDate,
                platelets = reading(LabMeasure.PLATELETS).storedValue,
                haematocrit = reading(LabMeasure.HAEMATOCRIT).storedValue,
                whiteCells = reading(LabMeasure.WHITE_CELLS).storedValue,
                haemoglobin = reading(LabMeasure.HAEMOGLOBIN).storedValue,
                ns1 = results[DengueTest.NS1] ?: TestResult.NOT_DONE,
                igm = results[DengueTest.IGM] ?: TestResult.NOT_DONE,
                igg = results[DengueTest.IGG] ?: TestResult.NOT_DONE,
                note = note,
            )
        )
        onSaved()
    }

    SideEffect {
        saveSlot.enabled = anyValue && !invalid
        saveSlot.perform = ::save
    }
    DisposableEffect(Unit) { onDispose { saveSlot.clear() } }

    if (choosingSource) {
        AlertDialog(
            onDismissRequest = { choosingSource = false },
            containerColor = colors.card,
            title = { Text(loc.t("lab.upload.source"), style = typo(Typo.HEADLINE)) },
            text = {
                Column {
                    TextButton(onClick = { choosingSource = false; scanner.pickImage() }) {
                        Text(loc.t("lab.upload.photos"), style = typo(Typo.BODY), color = colors.accent)
                    }
                    TextButton(onClick = { choosingSource = false; scanner.pickFile() }) {
                        Text(loc.t("lab.upload.files"), style = typo(Typo.BODY), color = colors.accent)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { choosingSource = false }) {
                    Text(loc.t("common.cancel"), color = colors.secondaryText)
                }
            },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.plane),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Space.stack),
    ) {
        item {
            // No heading: the screen is already called "Add a report", and iOS
            // leads with the two rows themselves.
            PlainSection(
                Modifier.padding(horizontal = Space.screen),
                footnote = loc.t("lab.scan.note"),
            ) {
                ActionRow(loc.t("lab.scan"), DwIcons.scan, enabled = !busy) { scanner.scan() }
                HairlineDivider()
                ActionRow(loc.t("lab.upload"), DwIcons.upload, enabled = !busy) {
                    choosingSource = true
                }
                if (busy) {
                    Text(
                        loc.t("lab.upload.reading"),
                        style = typo(Typo.MICRO),
                        color = colors.secondaryText,
                    )
                }
                if (readFailed) {
                    Text(
                        loc.t("lab.upload.unreadable"),
                        style = typo(Typo.MICRO),
                        color = colors.riskInk(RiskLevel.SEVERE),
                    )
                }
                if (foundNothing) {
                    Text(
                        loc.t("lab.scan.nothing"),
                        style = typo(Typo.MICRO),
                        color = colors.riskInk(RiskLevel.SEVERE),
                    )
                }
            }
        }

        item {
            PlainSection(Modifier.padding(horizontal = Space.screen)) {
                DateTimeRow(
                    label = loc.t("lab.reportDate"),
                    millis = reportDate,
                    onChange = { reportDate = it },
                )
            }
        }

        item {
            GroupedSection(
                title = loc.t("lab.counts"),
                footnote = loc.t("lab.rangeNote"),
                modifier = Modifier.padding(horizontal = Space.screen),
            ) {
                LabMeasure.entries.forEachIndexed { index, measure ->
                    if (index > 0) HairlineDivider()
                    MeasureRow(
                        label = loc.t(measure.labelKey),
                        value = text[measure].orEmpty(),
                        onValueChange = { text[measure] = it },
                        unit = loc.t(measure.unitKey),
                        reading = reading(measure),
                        unitWidth = 58.dp,
                        invalidDetail = invalidDetail(reading(measure), measure.decimals),
                    )
                }
            }
        }

        item {
            GroupedSection(
                title = loc.t("lab.serology"),
                footnote = loc.t("lab.timingNote"),
                modifier = Modifier.padding(horizontal = Space.screen),
            ) {
                DengueTest.entries.forEachIndexed { index, test ->
                    if (index > 0) HairlineDivider()
                    TestResultRow(
                        test = test,
                        selected = results[test] ?: TestResult.NOT_DONE,
                        onSelect = { results[test] = it },
                    )
                }
            }
        }

        item {
            PlainSection(Modifier.padding(horizontal = Space.screen)) {
                NoteField(note) { note = it }
            }
        }

        item { Spacer(Modifier.height(Space.section)) }
    }
}

/** NS1 / IgM / IgG, each not done, negative or positive. */
@Composable
private fun TestResultRow(
    test: DengueTest,
    selected: TestResult,
    onSelect: (TestResult) -> Unit,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    var open by remember { mutableStateOf(false) }

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { open = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                loc.t(test.labelKey),
                style = typo(Typo.CALLOUT),
                modifier = Modifier.weight(1f),
            )
            Text(
                loc.t(selected.labelKey),
                style = typo(Typo.CALLOUT),
                color = colors.accent,
            )
            Icon(
                DwIcons.chevronDown,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TestResult.entries.forEach { result ->
                DropdownMenuItem(
                    text = { Text(loc.t(result.labelKey), style = typo(Typo.CALLOUT)) },
                    trailingIcon = if (result == selected) {
                        {
                            Icon(
                                DwIcons.check2,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else null,
                    onClick = {
                        onSelect(result)
                        open = false
                    },
                )
            }
        }
    }
}

/** A row that starts one of the two ways of reading a report. */
@Composable
private fun ActionRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = Dw.colors
    val ink = if (enabled) colors.accent else colors.tertiaryText
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick)
                else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.row),
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
        Text(title, style = typo(Typo.CALLOUT), color = ink)
    }
}

/**
 * Renders a recognised value back into what the field expects.
 *
 * Latin digits and a full stop whatever the reader's language: this goes into
 * a text field that [MeasureInput] will read back, and it accepts the same
 * form it asks people to type.
 */
internal fun formatForField(value: Double, measure: LabMeasure): String =
    if (measure.decimals == 0) {
        value.roundToInt().toString()
    } else {
        // Built by concatenation rather than interpolation. The interpolated
        // version shipped once as a literal "%.${measure.decimals}f", which
        // Formatter read as a precision of nonsense and threw on — in a code
        // path only a successful scan reaches, so nothing caught it until a
        // report was actually read.
        String.format(Locale.ENGLISH, "%." + measure.decimals + "f", value)
    }
