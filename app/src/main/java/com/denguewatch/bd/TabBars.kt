package com.denguewatch.bd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.feature.shared.ShrinkToFitText
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.ShortHeightBreakpoint
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.typo
import com.denguewatch.bd.ui.theme.windowSizeDp

/**
 * The tabs along the bottom, for a compact screen.
 *
 * From 600dp — a tablet, or a phone on its side, where a bottom bar spends a
 * fifth of the little height there is — the tabs move to [TabRail] instead.
 */
@Composable
internal fun TabBar(state: AppState) {
    val colors = Dw.colors
    NavigationBar(containerColor = colors.card, tonalElevation = 0.dp) {
        Tab.entries.forEach { tab ->
            val isCurrent = state.selectedTab == tab
            NavigationBarItem(
                selected = isCurrent,
                onClick = { state.show(tab) },
                icon = { TabIcon(tab) },
                label = { TabLabel(tab, isCurrent) },
                // The current tab in accent, the rest in ink.
                // `primaryText` rather than a literal black so the
                // bar stays legible in dark mode, where that token
                // is near-white.
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.accent,
                    selectedTextColor = colors.accent,
                    unselectedIconColor = colors.primaryText,
                    unselectedTextColor = colors.primaryText,
                    indicatorColor = colors.accent.copy(alpha = 0.12f),
                ),
            )
        }
    }
}

/** The tabs down the side, for a wide screen. Same items and colours as the bar. */
@Composable
internal fun TabRail(state: AppState, modifier: Modifier = Modifier) {
    val colors = Dw.colors
    // On the page's own background rather than the bar's card colour, so it
    // reads as part of the page rather than as a slab beside it.
    // A phone on its side is wide enough for the rail but only ~300dp tall
    // below the top bar, and five labelled items do not fit: the fifth was cut
    // off. There, only the current tab keeps its label, as Material does for
    // short rails.
    val short = windowSizeDp().height < ShortHeightBreakpoint
    NavigationRail(modifier = modifier, containerColor = colors.plane) {
        // Centred in the height, so the five sit within reach of a thumb on a
        // tablet held at the sides rather than bunched at the top — and able to
        // scroll if a large font size still makes them taller than the screen,
        // rather than losing the last one off the bottom.
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
        Tab.entries.forEach { tab ->
            val isCurrent = state.selectedTab == tab
            NavigationRailItem(
                selected = isCurrent,
                onClick = { state.show(tab) },
                icon = { TabIcon(tab) },
                label = { TabLabel(tab, isCurrent) },
                alwaysShowLabel = !short,
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = colors.accent,
                    selectedTextColor = colors.accent,
                    unselectedIconColor = colors.primaryText,
                    unselectedTextColor = colors.primaryText,
                    indicatorColor = colors.accent.copy(alpha = 0.12f),
                ),
            )
        }
        }
        }
    }
}

@Composable
internal fun TabIcon(tab: Tab) {
    Icon(
        when (tab) {
            Tab.HOME -> DwIcons.home
            Tab.MAP -> DwIcons.map
            Tab.HEALTH -> DwIcons.health
            Tab.CARE -> DwIcons.care
            Tab.PREVENT -> DwIcons.prevent
        },
        contentDescription = null,
        modifier = Modifier.size(22.dp),
    )
}

/**
 * A tab's name on one line, whatever the font size.
 *
 * A tab slot is a fifth of the screen and cannot grow. At a 2.0 font scale
 * "চিকিৎসা" wrapped onto a second line, pushing into the icon above it and
 * running off the edge on the last tab. Shrinking keeps every label on one line
 * and legible.
 *
 * The colour is passed rather than inherited. ShrinkToFitText resolves its own
 * ink and does not read the item colours, so when the label became one it
 * stopped following selection while the icon kept following it. Deriving both
 * from [isCurrent] keeps them in step.
 */
@Composable
internal fun TabLabel(tab: Tab, isCurrent: Boolean) {
    val colors = Dw.colors
    ShrinkToFitText(
        LocalLocalization.current.t(tab.titleKey),
        style = typo(Typo.MICRO),
        color = if (isCurrent) colors.accent else colors.primaryText,
        minScale = 0.6f,
    )
}

