package com.denguewatch.bd.feature.location

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.denguewatch.bd.feature.shared.PrimaryActionButton
import com.denguewatch.bd.feature.shared.SecondaryActionButton
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.ui.theme.Card
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.typo

/**
 * Says what location is for *before* Android's own prompt appears, and always
 * offers a way to carry on without it.
 *
 * A permission dialog with no context is the commonest reason people deny
 * location to a health app, and this one stays useful either way: picking an
 * area by hand covers everything except "the risk exactly where I am standing".
 * Ported from the iOS build, which shows the same card in Care and on the map.
 *
 * [denied] separates "not asked yet" from "asked and refused". The two need
 * different words and different buttons — offering "Allow" to someone who has
 * already refused just sends them round the same loop, because Android will not
 * show the system prompt a second time.
 */
@Composable
fun LocationPermissionCard(
    denied: Boolean,
    onAllow: () -> Unit,
    modifier: Modifier = Modifier,
    onChooseManually: (() -> Unit)? = null,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors

    Card(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.row)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.row),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (denied) DwIcons.noLocation else DwIcons.myLocation,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        loc.t(if (denied) "location.denied.title" else "location.why.title"),
                        style = typo(Typo.HEADLINE),
                    )
                    Text(
                        loc.t(if (denied) "location.denied.message" else "location.why.message"),
                        style = typo(Typo.CAPTION),
                        color = colors.secondaryText,
                    )
                }
            }

            if (denied) {
                // Nothing to ask for any more: Android will not re-prompt once a
                // permission has been refused, so the only honest offer left is
                // the manual route.
                if (onChooseManually != null) {
                    SecondaryActionButton(
                        title = loc.t("location.why.manual"),
                        icon = DwIcons.list,
                        onClick = onChooseManually,
                    )
                }
            } else {
                PrimaryActionButton(
                    title = loc.t("location.why.allow"),
                    icon = DwIcons.myLocation,
                    onClick = onAllow,
                )
                if (onChooseManually != null) {
                    SecondaryActionButton(
                        title = loc.t("location.why.manual"),
                        icon = DwIcons.list,
                        onClick = onChooseManually,
                    )
                }
            }
        }
    }
}
