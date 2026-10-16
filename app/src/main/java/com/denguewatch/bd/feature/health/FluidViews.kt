package com.denguewatch.bd.feature.health

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.AppState
import com.denguewatch.bd.feature.shared.CardSection
import com.denguewatch.bd.feature.shared.GroupedSection
import com.denguewatch.bd.feature.shared.HairlineDivider
import com.denguewatch.bd.feature.shared.InlineNote
import com.denguewatch.bd.feature.shared.PlainSection
import com.denguewatch.bd.feature.shared.SecondaryActionButton
import com.denguewatch.bd.feature.shared.SegmentedPicker
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.model.FluidEntry
import com.denguewatch.bd.model.FluidLog
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Hit
import com.denguewatch.bd.ui.theme.Radius
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.tabularFigures
import com.denguewatch.bd.ui.theme.typo

/**
 * Today's fluids: what the reader has drunk and how often they have passed
 * urine, against the one target WHO gives patients themselves.
 *
 * It records and totals. The only line it adds of its own is the six-hour urine
 * gap, which is the symptom check's own warning sign, said where the gap shows
 * up rather than left for the reader to notice. Ported from the iOS build.
 */
@Composable
fun FluidCard(
    state: AppState,
    onAddDrink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val now = System.currentTimeMillis()
    val today = state.fluids.day(now)
    val todays = state.fluids.entriesOn(now)
    var showingEntries by rememberSaveable { mutableStateOf(false) }

    CardSection(
        title = loc.t("fluid.section"),
        subtitle = loc.t("fluid.subtitle"),
        modifier = modifier,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.row)) {
            FluidTile(
                label = loc.t("fluid.drunk"),
                value = loc.num(today.drunkMillilitres),
                unit = loc.t("fluid.unit.ml"),
                detail = "${loc.t("fluid.drinks")}: ${loc.num(today.drinks)}",
                modifier = Modifier.weight(1f).testTag("fluidIntakeTile"),
            )
            FluidTile(
                label = loc.t("fluid.urine"),
                value = loc.num(today.urinations),
                unit = loc.t("fluid.unit.times"),
                detail = urineAim(),
                modifier = Modifier.weight(1f).testTag("fluidUrineTile"),
            )
        }

        if (FluidLog.urineGapIsLong(state.fluids.entries, now)) {
            InlineNote(
                detail = loc.t("fluid.urine.gap", loc.num(FluidLog.URINE_GAP_HOURS.toInt())),
                icon = DwIcons.riskHigh,
                tint = colors.riskInk(RiskLevel.HIGH),
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.row)) {
            SecondaryActionButton(
                title = loc.t("fluid.addDrink"),
                icon = DwIcons.drink,
                onClick = onAddDrink,
                modifier = Modifier.weight(1f),
            )
            // One tap, timed now: this is logged several times a day, often by
            // someone looking after a patient. A different time can still be
            // chosen on the entry screen.
            SecondaryActionButton(
                title = loc.t("fluid.addUrine"),
                icon = DwIcons.urine,
                onClick = { state.fluids.add(FluidEntry(kind = FluidEntry.Kind.URINE)) },
                modifier = Modifier.weight(1f),
            )
        }

        if (todays.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Hit.minimum)
                    .clickable(role = Role.Button) { showingEntries = !showingEntries },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    loc.t("fluid.todayEntries", loc.num(todays.size)),
                    style = typo(Typo.CALLOUT),
                    color = colors.accent,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    if (showingEntries) DwIcons.chevronDown else DwIcons.chevronRight,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (showingEntries) FluidEntryList(state, todays, allowsDeletion = true)
        }

        InlineNote(detail = loc.t("fluid.whatToDrink"))
    }
}

@Composable
private fun urineAim(): String {
    val loc = LocalLocalization.current
    return loc.t("fluid.urine.aim", loc.num(FluidLog.URINATION_AIM.first), loc.num(FluidLog.URINATION_AIM.last))
}

/** One of the two totals: a figure, its unit, and a line of context. */
@Composable
private fun FluidTile(
    label: String,
    value: String,
    unit: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    val colors = Dw.colors
    Column(
        modifier
            .clip(RoundedCornerShape(Radius.control))
            .background(colors.plane)
            .padding(Space.row)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = typo(Typo.MICRO), color = colors.secondaryText)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = typo(Typo.STAT_VALUE).tabularFigures())
            Text(unit, style = typo(Typo.MICRO), color = colors.secondaryText)
        }
        Text(detail, style = typo(Typo.MICRO), color = colors.mutedInk)
    }
}

/** A day's fluid entries, oldest first. */
@Composable
fun FluidEntryList(state: AppState, entries: List<FluidEntry>, allowsDeletion: Boolean = false) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    Column {
        entries.forEachIndexed { index, entry ->
            if (index > 0) HairlineDivider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = Hit.minimum),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.row),
            ) {
                Icon(
                    if (entry.kind == FluidEntry.Kind.DRINK) DwIcons.drink else DwIcons.urine,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    when (entry.kind) {
                        FluidEntry.Kind.DRINK ->
                            "${loc.num(entry.millilitres ?: 0)} ${loc.t("fluid.unit.ml")}"
                        FluidEntry.Kind.URINE -> loc.t("fluid.urine")
                    },
                    style = typo(Typo.CALLOUT),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    loc.timeOfDay(entry.dateMillis),
                    style = typo(Typo.MICRO).tabularFigures(),
                    color = colors.secondaryText,
                )
                if (allowsDeletion) {
                    IconButton(onClick = { state.fluids.delete(entry.id) }) {
                        Icon(
                            DwIcons.close,
                            contentDescription = loc.t("common.delete"),
                            tint = colors.mutedInk,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/** One day's fluids in the log: the totals, then each entry. */
@Composable
fun FluidDaySummary(state: AppState, entries: List<FluidEntry>) {
    val loc = LocalLocalization.current
    val day = FluidLog.day(entries.firstOrNull()?.dateMillis ?: System.currentTimeMillis(), entries)
    Column(verticalArrangement = Arrangement.spacedBy(Space.row)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.tight)) {
            Icon(DwIcons.drink, contentDescription = null, tint = Dw.colors.accent, modifier = Modifier.size(18.dp))
            Text(loc.t("fluid.log.title"), style = typo(Typo.SUBHEADLINE, FontWeight.SemiBold))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.row)) {
            FluidTile(
                label = loc.t("fluid.drunk"),
                value = loc.num(day.drunkMillilitres),
                unit = loc.t("fluid.unit.ml"),
                detail = "${loc.t("fluid.drinks")}: ${loc.num(day.drinks)}",
                modifier = Modifier.weight(1f),
            )
            FluidTile(
                label = loc.t("fluid.urine"),
                value = loc.num(day.urinations),
                unit = loc.t("fluid.unit.times"),
                detail = urineAim(),
                modifier = Modifier.weight(1f),
            )
        }
        FluidEntryList(state, entries)
    }
}

/**
 * Adding one drink, or a time urine was passed that was not logged then. Save
 * sits in the navigation bar, as on the other recording screens.
 */
@Composable
fun FluidEntryScreen(
    state: AppState,
    saveSlot: SaveSlot,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    var kind by remember { mutableStateOf(FluidEntry.Kind.DRINK) }
    var millilitres by remember { mutableIntStateOf(200) }
    var takenAt by remember { mutableLongStateOf(System.currentTimeMillis()) }

    fun save() {
        state.fluids.add(
            FluidEntry(
                dateMillis = takenAt,
                kind = kind,
                millilitres = millilitres.takeIf { kind == FluidEntry.Kind.DRINK },
            )
        )
        onSaved()
    }

    SideEffect {
        saveSlot.enabled = true
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
                SegmentedPicker(
                    options = listOf(FluidEntry.Kind.DRINK, FluidEntry.Kind.URINE),
                    selected = kind,
                    label = {
                        loc.t(if (it == FluidEntry.Kind.DRINK) "fluid.kind.drink" else "fluid.urine")
                    },
                    onSelect = { kind = it },
                )
            }
        }

        if (kind == FluidEntry.Kind.DRINK) {
            item {
                GroupedSection(
                    title = loc.t("fluid.amount"),
                    footnote = loc.t("fluid.amount.footer"),
                    modifier = Modifier.padding(horizontal = Space.screen),
                ) {
                    FluidLog.PRESET_MILLILITRES.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.tight)) {
                            row.forEach { amount ->
                                AmountChip(
                                    text = "${loc.num(amount)} ${loc.t("fluid.unit.ml")}",
                                    selected = millilitres == amount,
                                    onClick = { millilitres = amount },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    // Any other amount, in steps of fifty, within what a single
                    // drink could plausibly be.
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${loc.num(millilitres)} ${loc.t("fluid.unit.ml")}",
                            style = typo(Typo.CALLOUT).tabularFigures(),
                            modifier = Modifier.weight(1f),
                        )
                        StepButton("−", loc.t("fluid.amount"), enabled = millilitres - 50 >= FluidLog.PLAUSIBLE_MILLILITRES.first) {
                            millilitres -= 50
                        }
                        StepButton("+", loc.t("fluid.amount"), enabled = millilitres + 50 <= FluidLog.PLAUSIBLE_MILLILITRES.last) {
                            millilitres += 50
                        }
                    }
                }
            }
        }

        item {
            PlainSection(Modifier.padding(horizontal = Space.screen)) {
                DateTimeRow(
                    label = loc.t("fluid.when"),
                    millis = takenAt,
                    onChange = { takenAt = it },
                )
            }
        }

        item { Spacer(Modifier.height(Space.section)) }
    }
}

@Composable
private fun AmountChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Dw.colors
    Text(
        text,
        style = typo(Typo.CALLOUT).tabularFigures(),
        color = if (selected) Color.White else colors.accent,
        modifier = modifier
            .clip(RoundedCornerShape(Radius.control))
            .background(if (selected) colors.accent else colors.accent.copy(alpha = 0.12f))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = Hit.minimum)
            .padding(vertical = Space.row)
            .semantics { this.selected = selected },
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun StepButton(symbol: String, label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = Dw.colors
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.semantics { contentDescription = "$label $symbol" }) {
        Text(
            symbol,
            style = typo(Typo.TITLE),
            color = if (enabled) colors.accent else colors.tertiaryText,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}
