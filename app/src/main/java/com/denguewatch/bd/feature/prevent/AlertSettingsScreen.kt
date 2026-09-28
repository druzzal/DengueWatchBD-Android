package com.denguewatch.bd.feature.prevent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.denguewatch.bd.AppState
import com.denguewatch.bd.data.Geofencing
import com.denguewatch.bd.feature.shared.CardSection
import com.denguewatch.bd.feature.shared.HairlineDivider
import com.denguewatch.bd.feature.shared.InlineNote
import com.denguewatch.bd.feature.shared.RiskBadge
import com.denguewatch.bd.feature.shared.SecondaryActionButton
import com.denguewatch.bd.feature.triage.ToggleRow
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.i18n.displayName
import com.denguewatch.bd.i18n.labelKey
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.typo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import com.denguewatch.bd.feature.shared.DisclosureRow
import com.denguewatch.bd.i18n.guidanceKey

/**
 * Alert settings.
 *
 * Everything here is worked out on the phone from data already downloaded — the
 * app never tells anyone which area a reader watches. The preview row exists so
 * that promise is checkable: it shows exactly what the reader would be told if
 * an alert fired right now.
 */
@Composable
fun AlertSettingsScreen(
    state: AppState,
    onChooseArea: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestBackgroundLocation: () -> Unit,
    hasNotificationPermission: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val context = LocalContext.current
    val preferences = state.preferences
    val homeArea = state.store.area(preferences.homeAreaCode)

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.plane),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Space.stack),
    ) {
        if (!hasNotificationPermission) {
            item {
                CardSection(
                    title = loc.t("alerts.denied.title"),
                    modifier = Modifier.padding(horizontal = Space.card),
                ) {
                    Text(
                        loc.t("alerts.denied.message"),
                        style = typo(Typo.CALLOUT),
                        color = colors.secondaryText,
                    )
                    SecondaryActionButton(
                        title = loc.t("alerts.notifications"),
                        icon = DwIcons.alerts,
                        onClick = onRequestNotificationPermission,
                    )
                }
            }
        }

        item {
            CardSection(
                title = loc.t("geo.section"),
                modifier = Modifier.padding(horizontal = Space.card),
            ) {
                ToggleRow(
                    title = loc.t("geo.toggle"),
                    checked = preferences.geofenceAlertsEnabled,
                    onCheckedChange = {
                        preferences.geofenceAlertsEnabled = it
                        if (it) {
                            if (!hasNotificationPermission) onRequestNotificationPermission()
                            // Arming needs background location, which Android
                            // grants separately from the foreground one.
                            if (!Geofencing.hasPermission(context)) {
                                onRequestBackgroundLocation()
                            }
                        }
                        state.applyAlertSettings()
                    },
                )
                Text(
                    loc.t("geo.footer"),
                    style = typo(Typo.MICRO),
                    color = colors.secondaryText,
                )
                if (preferences.geofenceAlertsEnabled) {
                    val status = Geofencing.status(context)
                    val monitored = state.store.areasAtOrAbove(preferences.alertThreshold)
                    if (Geofencing.hasPermission(context)) {
                        // Report what actually happened, not what was asked for.
                        // The switch being on says nothing about whether Android
                        // accepted the regions, and a warning the reader believes
                        // is armed when it is not is worse than one they know is
                        // off.
                        when {
                            status.startsWith(Geofencing.ARMED_FAILED) -> InlineNote(
                                icon = DwIcons.riskHigh,
                                detail = loc.t("geo.unavailable"),
                            )
                            monitored.isEmpty() -> InlineNote(
                                detail = loc.t("geo.nothingHigh"),
                            )
                            // How the warning is armed on the left, how much it
                            // is watching on the right — the pair iOS shows.
                            // Either half alone leaves a question open.
                            else -> Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    loc.t("geo.status.always"),
                                    style = typo(Typo.CAPTION),
                                    color = colors.secondaryText,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    loc.t("geo.monitoring", loc.num(monitored.size)),
                                    style = typo(Typo.CAPTION),
                                    color = colors.secondaryText,
                                )
                            }
                        }
                    } else {
                        // The switch is on but nothing is armed. Saying so is the
                        // whole point: a warning that silently cannot fire is
                        // worse than one the reader knows is off.
                        InlineNote(
                            icon = DwIcons.riskHigh,
                            detail = loc.t("geo.permissionDetail"),
                        )
                        SecondaryActionButton(
                            title = loc.t("geo.permission"),
                            icon = DwIcons.myLocation,
                            onClick = onRequestBackgroundLocation,
                        )
                    }
                }
            }
        }

        item {
            CardSection(
                title = loc.t("alerts.myArea"),
                modifier = Modifier.padding(horizontal = Space.card),
            ) {
                // The chosen area reads as a fact with its band beside it, and
                // changing it is a separate row — as on iOS. Rolling both into
                // one button made the area itself look like an action.
                if (homeArea != null) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            homeArea.displayName(loc.language),
                            style = typo(Typo.SUBHEADLINE),
                            modifier = Modifier.weight(1f),
                        )
                        RiskBadge(homeArea.risk, compact = true)
                    }
                } else {
                    Text(
                        loc.t("alerts.noArea"),
                        style = typo(Typo.SUBHEADLINE),
                        color = colors.secondaryText,
                    )
                }
                HairlineDivider()
                DisclosureRow(
                    title = loc.t("alerts.chooseArea"),
                    onClick = onChooseArea,
                )
                Text(
                    loc.t("alerts.areaFooter"),
                    style = typo(Typo.MICRO),
                    color = colors.secondaryText,
                )
            }
        }

        item {
            CardSection(
                title = loc.t("alerts.notifications"),
                modifier = Modifier.padding(horizontal = Space.card),
            ) {
                ToggleRow(
                    title = loc.t("alerts.riseToggle"),
                    checked = preferences.alertsEnabled,
                    onCheckedChange = {
                        preferences.alertsEnabled = it
                        if (it && !hasNotificationPermission) onRequestNotificationPermission()
                        state.applyAlertSettings()
                    },
                )

                if (preferences.alertsEnabled) {
                    HairlineDivider()
                    // A value row that opens a menu, as on iOS, rather than a
                    // segmented strip: the label reads "High risk and above",
                    // which says what the setting means. A strip showing only
                    // "High" leaves the "and above" unsaid.
                    ThresholdRow(
                        selected = preferences.alertThreshold,
                        onSelect = {
                            preferences.alertThreshold = it
                            state.applyAlertSettings()
                        },
                    )
                }

                HairlineDivider()
                ToggleRow(
                    title = loc.t("alerts.weekly"),
                    checked = preferences.weeklyDigestEnabled,
                    onCheckedChange = {
                        preferences.weeklyDigestEnabled = it
                        if (it && !hasNotificationPermission) onRequestNotificationPermission()
                        state.applyAlertSettings()
                    },
                )

                Text(
                    loc.t("alerts.footer"),
                    style = typo(Typo.MICRO),
                    color = colors.secondaryText,
                )
            }
        }

        // What the reader would actually be told, right now.
        //
        // Shown whenever an area is chosen, as on iOS — not only while alerts
        // are on. "No alert" is exactly the sentence someone checking whether
        // this thing works needs to see, and hiding it when the toggle is off
        // removes the answer at the moment they are looking for it.
        if (homeArea != null) {
            item {
                CardSection(
                    title = loc.t("alerts.preview"),
                    modifier = Modifier.padding(horizontal = Space.card),
                ) {
                    val name = homeArea.displayName(loc.language)
                    val band = loc.t(homeArea.risk.labelKey)
                    val would = homeArea.risk.ordinal >= preferences.alertThreshold.ordinal
                    Text(
                        loc.t(
                            if (would) "alerts.preview.would" else "alerts.preview.wouldNot",
                            name,
                            band,
                        ),
                        style = typo(Typo.SUBHEADLINE),
                    )
                    Text(
                        loc.t(homeArea.risk.guidanceKey),
                        style = typo(Typo.CAPTION),
                        color = colors.secondaryText,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(Space.section)) }
    }
}

/**
 * "Tell me at — High risk and above", opening a menu of the four bands.
 *
 * The wording carries the "and above" that a bare band name would drop, which
 * is the difference between a threshold and a filter.
 */
@Composable
private fun ThresholdRow(
    selected: RiskLevel,
    onSelect: (RiskLevel) -> Unit,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    var open by remember { mutableStateOf(false) }

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { open = true }
                .padding(vertical = Space.tight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                loc.t("alerts.threshold"),
                style = typo(Typo.SUBHEADLINE),
                modifier = Modifier.weight(1f),
            )
            Text(
                loc.t("alerts.thresholdOption", loc.t(selected.labelKey)),
                style = typo(Typo.SUBHEADLINE),
                color = colors.accent,
            )
            Icon(
                DwIcons.chevronRight,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.padding(start = Space.hair).size(16.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            RiskLevel.entries.forEach { level ->
                DropdownMenuItem(
                    text = {
                        Text(
                            loc.t("alerts.thresholdOption", loc.t(level.labelKey)),
                            style = typo(Typo.SUBHEADLINE),
                        )
                    },
                    trailingIcon = if (level == selected) {
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
                        onSelect(level)
                        open = false
                    },
                )
            }
        }
    }
}
