package com.denguewatch.bd.feature.care

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.AppState
import com.denguewatch.bd.feature.shared.CardSection
import com.denguewatch.bd.feature.shared.HairlineDivider
import com.denguewatch.bd.feature.shared.InlineNote
import com.denguewatch.bd.feature.location.LocationPermissionCard
import com.denguewatch.bd.feature.shared.PrimaryActionButton
import com.denguewatch.bd.feature.shared.ScreenTitle
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.i18n.displayName
import com.denguewatch.bd.model.Division
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Hit
import com.denguewatch.bd.ui.theme.Shapes
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.typo

/**
 * Where to go for help: the national lines first, then the hospitals known to
 * run dengue wards.
 *
 * The iOS build also searches Maps for hospitals nearby. That search is a
 * MapKit service with no free equivalent here — Google's Places API needs a
 * billed key — so this build hands the same job to whichever maps app the
 * reader already has, which is where the directions were going anyway.
 */
@Composable
fun CareScreen(
    state: AppState,
    onCall: (String) -> Unit,
    onOpenMap: (String) -> Unit,
    onSearchNearby: () -> Unit,
    onRequestLocation: () -> Unit,
    /**
     * True once the reader has refused. Android will not show its prompt again
     * after that, so the card has to stop offering a button that does nothing.
     */
    deniedLocation: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    var divisionFilter by remember { mutableStateOf<Division?>(null) }
    var filterExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.plane),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Space.stack),
    ) {
        run {
            item { ScreenTitle(loc.t("care.title"), Modifier.padding(horizontal = Space.screen)) }
        }

        // MARK: Emergency numbers
        item {
            CardSection(
                title = loc.t("care.sos.title"),
                modifier = Modifier.padding(horizontal = Space.screen),
            ) {
                Column {
                    // Stacked at large text sizes, as iOS does at its
                    // accessibility sizes. Side by side, the chip holds its
                    // width while the name gives ground, so "999 — National
                    // Emergency Service" broke into a column of fragments —
                    // and the people who set a large font are the ones who
                    // most need to read an emergency number.
                    val stacked = LocalDensity.current.fontScale >= 1.5f
                    EmergencyNumber.all.forEachIndexed { index, number ->
                        val details = @Composable { detailModifier: Modifier ->
                            Column(
                                detailModifier,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    loc.t(number.nameKey),
                                    style = typo(Typo.SUBHEADLINE, FontWeight.SemiBold),
                                )
                                Text(
                                    loc.t(number.detailKey),
                                    style = typo(Typo.CAPTION),
                                    color = colors.secondaryText,
                                )
                            }
                        }
                        if (stacked) {
                            Column(
                                Modifier.fillMaxWidth().padding(vertical = 11.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                details(Modifier)
                                CallChip(
                                    isEmergency = number.isEmergency,
                                    onClick = { onCall(number.dial) },
                                )
                            }
                        } else {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Space.row),
                            ) {
                                details(Modifier.weight(1f))
                                CallChip(
                                    isEmergency = number.isEmergency,
                                    onClick = { onCall(number.dial) },
                                )
                            }
                        }
                        if (index < EmergencyNumber.all.size - 1) HairlineDivider()
                    }
                }
                Text(
                    loc.t("care.sos.footer"),
                    style = typo(Typo.MICRO),
                    color = colors.secondaryText,
                )
            }
        }

        // MARK: Nearby
        item {
            CardSection(
                title = loc.t("care.nearby.title"),
                subtitle = loc.t("care.nearby.subtitle"),
                modifier = Modifier.padding(horizontal = Space.screen),
            ) {
                // Without permission the search cannot say anything about
                // "near you", so iOS explains first and offers the manual
                // route. Sending someone to a maps app that then asks for
                // location itself would push the same question one screen
                // further away rather than answering it.
                if (state.location.isAuthorized) {
                    PrimaryActionButton(
                        title = loc.t("care.nearby.search"),
                        icon = DwIcons.myLocation,
                        onClick = onSearchNearby,
                    )
                } else {
                    LocationPermissionCard(
                        denied = deniedLocation,
                        onAllow = onRequestLocation,
                    )
                }
            }
        }

        // MARK: Curated directory
        item {
            val hospitals = HospitalDirectory.hospitals(divisionFilter)
            CardSection(
                title = loc.t("care.dghs.title"),
                subtitle = loc.t("care.dghs.subtitle"),
                modifier = Modifier.padding(horizontal = Space.screen),
            ) {
                Box {
                    Row(
                        Modifier
                            // Pulled back by its own padding so the filter's
                            // icon starts on the same left edge as the card's
                            // title and the hospital names below it. The padding
                            // stays — it is what gives the control a tap target
                            // and a ripple wider than the words — but without
                            // the offset it alone sat 6dp in from everything
                            // else in the card.
                            .offset(x = (-6).dp)
                            .clip(Shapes.chip)
                            .clickable(role = Role.Button) { filterExpanded = true }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            DwIcons.filter,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            divisionFilter?.displayName(loc.language)
                                ?: loc.t("care.division.all"),
                            style = typo(Typo.CAPTION, FontWeight.Medium),
                            color = colors.accent,
                        )
                    }

                    DropdownMenu(
                        expanded = filterExpanded,
                        onDismissRequest = { filterExpanded = false },
                        containerColor = colors.card,
                    ) {
                        DropdownMenuItem(
                            text = { Text(loc.t("care.division.all"), style = typo(Typo.SUBHEADLINE)) },
                            onClick = {
                                divisionFilter = null
                                filterExpanded = false
                            },
                        )
                        Division.entries.forEach { division ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        division.displayName(loc.language),
                                        style = typo(Typo.SUBHEADLINE),
                                    )
                                },
                                onClick = {
                                    divisionFilter = division
                                    filterExpanded = false
                                },
                            )
                        }
                    }
                }

                Column {
                    hospitals.forEachIndexed { index, hospital ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { onOpenMap(hospital.mapQuery) }
                                .padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.row),
                        ) {
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    hospital.name(loc.language),
                                    style = typo(Typo.SUBHEADLINE),
                                )
                                Text(
                                    hospital.city(loc.language),
                                    style = typo(Typo.CAPTION),
                                    color = colors.secondaryText,
                                )
                            }
                            Icon(
                                DwIcons.map,
                                contentDescription = loc.t("care.directions"),
                                tint = colors.accent,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        if (index < hospitals.size - 1) HairlineDivider()
                    }
                }

                InlineNote(
                    detail = loc.t("care.dghs.footer"),
                    icon = DwIcons.riskHigh,
                )
            }
        }

        item { Spacer(Modifier.height(Space.section)) }
    }
}

/**
 * The call affordance.
 *
 * 999 is filled; the advice lines are outlined. That difference is the whole
 * point — under stress the reader should be able to find the emergency number
 * without reading three labels.
 */
@Composable
private fun CallChip(isEmergency: Boolean, onClick: () -> Unit) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val fill = if (isEmergency) colors.riskTint(RiskLevel.SEVERE) else colors.accent

    Row(
        Modifier
            .heightIn(min = Hit.minimum - 8.dp)
            .clip(Shapes.chip)
            .then(
                if (isEmergency) {
                    Modifier.background(fill)
                } else {
                    Modifier
                        .background(fill.copy(alpha = 0.12f))
                        .border(1.dp, fill.copy(alpha = 0.35f), Shapes.chip)
                }
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            DwIcons.phone,
            contentDescription = null,
            tint = if (isEmergency) Color.White else fill,
            modifier = Modifier.size(16.dp),
        )
        Text(
            loc.t("care.call"),
            style = typo(Typo.CAPTION, FontWeight.SemiBold),
            color = if (isEmergency) Color.White else fill,
        )
    }
}
