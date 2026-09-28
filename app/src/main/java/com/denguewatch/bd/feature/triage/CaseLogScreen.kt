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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.AppState
import com.denguewatch.bd.feature.shared.CircleActionButton
import com.denguewatch.bd.feature.shared.EmptyStateView
import com.denguewatch.bd.feature.shared.InlineNote
import com.denguewatch.bd.feature.shared.SecondaryActionButton
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.ui.theme.Card
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.tabularFigures
import com.denguewatch.bd.ui.theme.typo

/**
 * The reader's own check-ins.
 *
 * Having the day-by-day course of a fever written down is genuinely useful to a
 * clinician, and this is the only part of the app that holds anything the
 * reader typed — which is why the privacy footer is not optional.
 */
@Composable
fun CaseLogScreen(
    state: AppState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val entries = state.caseLog.entries
    var confirmingDelete by remember { mutableStateOf(false) }

    if (entries.isEmpty()) {
        EmptyStateView(
            icon = DwIcons.log,
            title = loc.t("log.empty.title"),
            message = loc.t("log.empty.detail"),
            modifier = modifier.fillMaxSize().background(colors.plane).padding(contentPadding),
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.plane),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Space.row),
    ) {
        for (entry in entries) {
            item(key = entry.id) {
                val outcome = TriageOutcome.entries.getOrNull(entry.outcomeOrdinal)
                    ?: TriageOutcome.SELF_CARE
                val accent = when (outcome) {
                    TriageOutcome.SELF_CARE -> colors.downIsGood
                    TriageOutcome.TEST_ADVISED -> colors.riskTint(RiskLevel.MODERATE)
                    TriageOutcome.SEE_DOCTOR_TODAY -> colors.riskTint(RiskLevel.HIGH)
                    TriageOutcome.EMERGENCY -> colors.riskTint(RiskLevel.SEVERE)
                }

                Card(Modifier.padding(horizontal = Space.card)) {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.tight)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.tight),
                        ) {
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    loc.t(outcome.headlineKey),
                                    style = typo(Typo.SUBHEADLINE, FontWeight.SemiBold),
                                    color = accent,
                                )
                                Text(
                                    loc.dateTime(entry.dateMillis),
                                    style = typo(Typo.MICRO),
                                    color = colors.secondaryText,
                                )
                            }
                            CircleActionButton(
                                icon = DwIcons.close,
                                tint = colors.mutedInk,
                                contentDescription = loc.t("common.deleteAll"),
                                onClick = { state.caseLog.delete(entry.id) },
                            )
                        }

                        entry.temperature?.let { temperature ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.hair),
                            ) {
                                Text(
                                    loc.t("log.temperature"),
                                    style = typo(Typo.CAPTION),
                                    color = colors.secondaryText,
                                )
                                Text(
                                    "${loc.decimal(temperature)}°",
                                    style = typo(Typo.CAPTION, FontWeight.SemiBold)
                                        .tabularFigures(),
                                )
                            }
                        }

                        // The signs as drawings, the same ones the check used.
                        if (entry.symptomIds.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                entry.symptomIds.take(8).forEach { id ->
                                    TriageEngine.symptom(id)?.let { symptom ->
                                        SymptomIllustration(symptom.id, symptom.group, size = 30.dp)
                                    }
                                }
                            }
                        }

                        if (entry.note.isNotBlank()) {
                            Text(entry.note, style = typo(Typo.CAPTION))
                        }
                    }
                }
            }
        }

        item {
            Column(
                Modifier.padding(horizontal = Space.card),
                verticalArrangement = Arrangement.spacedBy(Space.row),
            ) {
                InlineNote(detail = loc.t("log.privacyFooter"))
                SecondaryActionButton(
                    title = loc.t("common.deleteAll"),
                    tint = colors.riskTint(RiskLevel.SEVERE),
                    onClick = { confirmingDelete = true },
                )
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
                    state.caseLog.clear()
                    confirmingDelete = false
                }) {
                    Text(
                        loc.t("common.deleteAll"),
                        color = colors.riskTint(RiskLevel.SEVERE),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(loc.t("common.cancel"))
                }
            },
            containerColor = colors.card,
        )
    }
}
