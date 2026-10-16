package com.denguewatch.bd.feature.location

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.feature.shared.PrimaryActionButton
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Hit
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.typo

/**
 * Explains what location is for before Android shows its own prompt.
 *
 * The system dialog is not a free retry: decline it twice and Android stops
 * showing it, and the reader has to find the app's settings to undo that. So
 * the reason comes first, in plain language, with an obvious way to decline
 * that does not spend one of those asks. The same sheet iOS shows from its map.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPermissionSheet(onAllow: () -> Unit, onDismiss: () -> Unit) {
    val loc = LocalLocalization.current
    val colors = Dw.colors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.plane,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen)
                .padding(bottom = Space.card)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.stack),
        ) {
            Icon(DwIcons.myLocation, contentDescription = null, tint = colors.accent, modifier = Modifier.size(52.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.tight)) {
                Text(loc.t("locperm.title"), style = typo(Typo.TITLE), textAlign = TextAlign.Center)
                Text(
                    loc.t("locperm.body"),
                    style = typo(Typo.CALLOUT),
                    color = colors.secondaryText,
                    textAlign = TextAlign.Center,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(Space.row)) {
                Reason(DwIcons.map, loc.t("locperm.reason1"))
                Reason(DwIcons.care, loc.t("locperm.reason2"))
                Reason(DwIcons.lock, loc.t("locperm.reason3"))
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.tight)) {
                PrimaryActionButton(
                    title = loc.t("locperm.allow"),
                    icon = DwIcons.myLocation,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onDismiss()
                        onAllow()
                    },
                )
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Hit.minimum)) {
                    Text(loc.t("locperm.notNow"), style = typo(Typo.CALLOUT), color = colors.secondaryText)
                }
            }
        }
    }
}

@Composable
private fun Reason(icon: ImageVector, text: String) {
    val colors = Dw.colors
    Row(horizontalArrangement = Arrangement.spacedBy(Space.row), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
        Text(text, style = typo(Typo.CALLOUT), modifier = Modifier.weight(1f))
    }
}
