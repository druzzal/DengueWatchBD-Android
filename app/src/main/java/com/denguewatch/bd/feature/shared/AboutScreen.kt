package com.denguewatch.bd.feature.shared

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.AppState
import com.denguewatch.bd.data.FeedConfig
import com.denguewatch.bd.i18n.AppLanguage
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.typo
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import kotlinx.coroutines.launch

/**
 * Where the figures come from, what they do and do not count, and what the app
 * keeps.
 *
 * This screen is not boilerplate. A surveillance app that shows a national case
 * count without saying it counts hospital patients only is overstating what it
 * knows, so the caveats are given the same weight as the numbers.
 */
@Composable
fun AboutScreen(
    state: AppState,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val store = state.store

    val meta = store.meta
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.plane),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Space.stack),
    ) {
        // Language first, as on iOS: a Bengali reader should not have to read
        // the disclosures in English before finding the switch that fixes that.
        item {
            GroupedSection(loc.t("about.language")) {
                SegmentedPicker(
                    options = AppLanguage.entries,
                    selected = loc.language,
                    label = { it.nativeName },
                    onSelect = { loc.set(it) },
                )
            }
        }

        item {
            GroupedSection(loc.t("about.build")) {
                // The dataset's own name, as the feed states it, rather than a
                // label this app invents for it.
                LabeledBlock(
                    loc.t("about.dataset"),
                    meta?.sourceName ?: loc.t("about.sourceFallback"),
                )
                HairlineDivider()
                // The feed publishes its own "as of" string. Preferring it over
                // a date this app formats means the screen says exactly what
                // the source says, which is the point of the section.
                LabeledFigure(
                    loc.t("about.lastUpdated"),
                    meta?.lastUpdatedLabel
                        ?: meta?.lastUpdated
                        ?: store.lastUpdated?.let { loc.fullDate(it) }
                        ?: "—",
                )
                HairlineDivider()
                LabeledFigure(
                    loc.t("about.season"),
                    meta?.year?.let { loc.num(it).replace(",", "") } ?: "—",
                )
                HairlineDivider()
                LabeledFigure(loc.t("about.areas"), loc.num(store.areas.size))
            }
        }

        item {
            GroupedSection(loc.t("about.readFirst"), icon = DwIcons.riskHigh) {
                Text(loc.t("about.disclaimerText"), style = typo(Typo.CALLOUT))
            }
        }

        item {
            GroupedSection(
                loc.t("about.attribution"),
                footnote = loc.t("about.independent"),
            ) {
                Text(loc.t("about.attributionText"), style = typo(Typo.CALLOUT))
                HairlineDivider()
                // Two different things, both worth reaching: DGHS's own page so
                // a figure can be checked at the source, and the open feed so
                // the copying can be checked too.
                LinkRow(loc.t("about.openSource"), icon = DwIcons.external) {
                    onOpenUrl(meta?.sourceUrl ?: FeedConfig.SOURCE_URL)
                }
                HairlineDivider()
                LinkRow(loc.t("about.openFeed"), icon = DwIcons.external) {
                    onOpenUrl(FeedConfig.FEED_HOME_URL)
                }
            }
        }

        item {
            GroupedSection(loc.t("about.sync")) {
                InlineNote(
                    detail = state.sync.lastSuccessfulSync
                        ?.let { loc.t("sync.lastChecked", loc.relative(it)) }
                        ?: loc.t("sync.bundled"),
                )
                HairlineDivider()
                // The refresh the whole screen is about: everything above states
                // what the app currently holds, so the way to act on it belongs
                // here rather than buried in a pull gesture.
                LinkRow(
                    title = loc.t("sync.checkNow"),
                    enabled = !checking,
                ) {
                    checking = true
                    scope.launch {
                        state.refreshNow()
                        checking = false
                    }
                }
            }
        }

        item {
            GroupedSection(loc.t("about.howNumbers")) {
                Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    (1..5).forEach { index ->
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Box(
                                Modifier
                                    .padding(top = 7.dp)
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent.copy(alpha = 0.5f))
                            )
                            Text(loc.t("about.bullet$index"), style = typo(Typo.CALLOUT))
                        }
                    }
                }
            }
        }

        item {
            GroupedSection(loc.t("about.medical")) {
                Text(loc.t("about.medicalDetail"), style = typo(Typo.CALLOUT))
            }
        }

        item {
            GroupedSection(loc.t("about.privacy")) {
                Text(loc.t("about.privacyDetail"), style = typo(Typo.CALLOUT))
                HairlineDivider()
                LinkRow(loc.t("about.privacyPolicy"), icon = DwIcons.external) {
                    onOpenUrl(FeedConfig.PRIVACY_POLICY_URL)
                }
            }
        }

        item {
            GroupedSection(loc.t("about.goingLive")) {
                Text(
                    loc.t("about.goingLiveDetail"),
                    style = typo(Typo.CALLOUT),
                    color = colors.secondaryText,
                )
            }
        }

        item { Spacer(Modifier.height(Space.section)) }
    }
}

/**
 * An accent row that acts.
 *
 * [icon] carries the leaves-the-app arrow for the two outbound links; the
 * refresh below them stays bare, because it does not leave the app and an
 * arrow there would promise a browser that never opens.
 */
@Composable
private fun LinkRow(
    title: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = Dw.colors
    val ink = if (enabled) colors.accent else colors.tertiaryText
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = Space.tight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.row),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
        }
        Text(title, style = typo(Typo.CALLOUT), color = ink)
    }
}

/**
 * The notice shown before any figure is.
 *
 * A full-screen gate rather than a dismissible banner: the two things it says —
 * that these count hospital patients only, and that the app cannot diagnose —
 * change how every number and every piece of advice behind it should be read.
 */
@Composable
fun FirstRunDisclaimer(
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.plane)
            .padding(horizontal = Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.stack),
    ) {
        item { Spacer(Modifier.height(Space.section)) }

        item {
            // Centred, as on iOS, where this header sits in a centre-aligned
            // stack. It is the one screen with no content beside it to align
            // to, so a left edge here reads as a stray margin.
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Space.row),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    loc.t("dash.title"),
                    style = typo(Typo.HERO),
                    color = colors.accent,
                    textAlign = TextAlign.Center,
                )
                Text(
                    loc.t("intro.title"),
                    style = typo(Typo.TITLE),
                    textAlign = TextAlign.Center,
                )
            }
        }

        item {
            SegmentedPicker(
                options = AppLanguage.entries,
                selected = loc.language,
                label = { it.nativeName },
                onSelect = { loc.set(it) },
            )
        }

        (1..4).forEach { index ->
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.row),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        Modifier
                            .padding(top = 6.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(colors.accent)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(loc.t("intro.p$index.title"), style = typo(Typo.HEADLINE))
                        Text(
                            loc.t("intro.p$index.detail"),
                            style = typo(Typo.CALLOUT),
                            color = colors.secondaryText,
                        )
                    }
                }
            }
        }

        item {
            PrimaryActionButton(
                title = loc.t("intro.understand"),
                icon = DwIcons.check2,
                onClick = onAccept,
            )
        }

        item { Spacer(Modifier.height(Space.section)) }
    }
}
