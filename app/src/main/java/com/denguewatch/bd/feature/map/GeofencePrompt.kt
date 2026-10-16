package com.denguewatch.bd.feature.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.AppState
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.typo
import androidx.compose.ui.platform.LocalContext
import com.denguewatch.bd.data.Geofencing

/**
 * "Warn me when I enter a high-risk area", offered inline on the map.
 *
 * Three states, because a warning that cannot fire must not claim it can:
 * off (offer to turn it on), on-but-unarmed (Android granted foreground
 * location only, so nothing is actually being watched), and armed (say how
 * many areas). The middle state is the one worth the code — without it the app
 * would show "alerts on" to someone who would never receive one.
 */
@Composable
internal fun GeofencePrompt(
    state: AppState,
    onRequestBackgroundLocation: () -> Unit,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val context = LocalContext.current
    val preferences = state.preferences
    val armed = Geofencing.hasPermission(context)

    when {
        !preferences.geofenceAlertsEnabled -> PromptRow(
            title = loc.t("geo.prompt.title"),
            action = loc.t("geo.prompt.enable"),
            icon = DwIcons.alerts,
        ) {
            preferences.geofenceAlertsEnabled = true
            if (!armed) onRequestBackgroundLocation()
            state.applyAlertSettings()
        }

        !armed -> PromptRow(
            title = loc.t("geo.prompt.title"),
            action = loc.t("geo.permission"),
            icon = DwIcons.riskHigh,
            onClick = onRequestBackgroundLocation,
        )

        else -> {
            val watching = state.store.areasAtOrAbove(preferences.alertThreshold)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.hair + 2.dp),
            ) {
                Icon(
                    DwIcons.alerts,
                    contentDescription = null,
                    tint = colors.secondaryText,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    loc.t("geo.monitoring", loc.num(watching.size)),
                    style = typo(Typo.MICRO),
                    color = colors.secondaryText,
                )
            }
        }
    }
}

/** A line of explanation with the action that follows from it, in accent. */
@Composable
internal fun PromptRow(
    title: String,
    action: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val colors = Dw.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Space.hair),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.tight),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(14.dp),
        )
        Text(
            title,
            style = typo(Typo.MICRO),
            color = colors.secondaryText,
            modifier = Modifier.weight(1f),
        )
        Text(action, style = typo(Typo.MICRO, FontWeight.SemiBold), color = colors.accent)
    }
}
