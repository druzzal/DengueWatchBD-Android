package com.denguewatch.bd.feature.triage

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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.AppState
import com.denguewatch.bd.data.CaseLogEntry
import com.denguewatch.bd.data.CaseLogStore
import com.denguewatch.bd.feature.health.icon
import com.denguewatch.bd.feature.health.risk
import com.denguewatch.bd.feature.shared.EmptyStateView
import com.denguewatch.bd.feature.shared.GroupedSection
import com.denguewatch.bd.feature.shared.HairlineDivider
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.i18n.Localization
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Shapes
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.typo

/**
 * Every symptom check the reader has finished, newest first, each one deletable
 * on its own.
 *
 * The health log lists measurements, not checks, so this is the only place a
 * single check can be removed. That matters because the latest check drives
 * the care plan and the fever timeline: a mis-tap or a trial run would
 * otherwise go on giving advice until another check replaced it. Ported from
 * the iOS build.
 */
@Composable
fun CheckHistoryScreen(
    state: AppState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val entries = state.caseLog.entries
    // Held until the reader confirms: a check cannot be rebuilt, so one tap in
    // a menu is not enough.
    var pending by remember { mutableStateOf<CaseLogEntry?>(null) }

    if (entries.isEmpty()) {
        Box(modifier.fillMaxSize().background(colors.plane).padding(contentPadding)) {
            EmptyStateView(
                icon = DwIcons.history,
                title = loc.t("check.history.title"),
                message = loc.t("health.empty"),
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
            GroupedSection(
                title = loc.t("check.history.title"),
                footnote = loc.t("check.history.footer"),
                modifier = Modifier.padding(horizontal = Space.screen),
            ) {
                entries.forEachIndexed { index, entry ->
                    if (index > 0) HairlineDivider()
                    CheckRow(entry, isLatest = index == 0, onDelete = { pending = entry })
                }
            }
        }
        item { Spacer(Modifier.height(Space.section)) }
    }

    pending?.let { entry ->
        ConfirmCheckDeletion(
            loc = loc,
            onConfirm = {
                state.caseLog.delete(entry.id)
                pending = null
            },
            onDismiss = { pending = null },
        )
    }
}

/** One finished check: what it advised, when, and a menu to delete it. */
@Composable
private fun CheckRow(entry: CaseLogEntry, isLatest: Boolean, onDelete: () -> Unit) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val outcome = entry.outcome
    var menu by remember { mutableStateOf(false) }
    val headline = loc.t(outcome.headlineKey)
    val when_ = loc.dayAndTime(entry.dateMillis)

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = Space.tight)
                .testTag("checkRow")
                .semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(
                        headline, when_, loc.t("check.history.latest").takeIf { isLatest },
                    ).joinToString(", ")
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.row),
        ) {
            Icon(
                outcome.icon,
                contentDescription = null,
                tint = colors.riskTint(outcome.risk),
                modifier = Modifier.size(22.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(headline, style = typo(Typo.CALLOUT, FontWeight.Medium))
                Text(when_, style = typo(Typo.MICRO), color = colors.secondaryText)
                if (isLatest) {
                    // Said on the row, so deleting it is not a surprise to the
                    // care plan the reader reads next.
                    Text(loc.t("check.history.latest"), style = typo(Typo.MICRO), color = colors.accent)
                }
            }
            Icon(
                DwIcons.more,
                contentDescription = loc.t("check.actions"),
                tint = colors.tertiaryText,
                modifier = Modifier
                    .size(22.dp)
                    .clip(Shapes.chip)
                    .clickable(role = Role.Button) { menu = true },
            )
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }, containerColor = colors.card) {
            DropdownMenuItem(
                text = { Text(loc.t("common.delete"), style = typo(Typo.SUBHEADLINE)) },
                leadingIcon = {
                    Icon(DwIcons.delete, contentDescription = null,
                         tint = colors.riskTint(com.denguewatch.bd.model.RiskLevel.SEVERE),
                         modifier = Modifier.size(18.dp))
                },
                onClick = {
                    menu = false
                    onDelete()
                },
            )
        }
    }
}

/**
 * The one confirmation for deleting a symptom check, shared by the card in My
 * health and the list of past checks so both ask the same question.
 */
@Composable
fun ConfirmCheckDeletion(loc: Localization, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(loc.t("check.delete.confirm"), style = typo(Typo.HEADLINE)) },
        text = { Text(loc.t("check.delete.detail")) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(loc.t("common.delete")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(loc.t("common.cancel")) } },
    )
}

/** Deletes the newest check, which the care plan then stops following. */
fun CaseLogStore.deleteLatest() {
    entries.firstOrNull()?.let { delete(it.id) }
}
