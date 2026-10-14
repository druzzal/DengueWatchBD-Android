package com.denguewatch.bd.feature.prevent

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.AppState
import com.denguewatch.bd.feature.shared.GroupedSection
import com.denguewatch.bd.feature.shared.HairlineDivider
import com.denguewatch.bd.feature.shared.SearchField
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.i18n.displayName
import com.denguewatch.bd.model.Division
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Hit
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.typo

/**
 * Choosing the area the rise alert watches, ported from iOS's area picker.
 *
 * "Choose area" used to switch to the Map tab, leaving the reader to find an
 * area there and look for "Set as my area" on its page — three screens away
 * from the setting they had come to change, with nothing to bring them back.
 * Here it is a list grouped by division, as on iOS, and picking one returns to
 * the alerts screen with it chosen.
 */
@Composable
fun AreaPickerScreen(
    state: AppState,
    onPicked: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val preferences = state.preferences
    var query by remember { mutableStateOf("") }

    val grouped = Division.entries.mapNotNull { division ->
        val members = state.store.areas
            .filter { it.division == division }
            .filter {
                query.isBlank() ||
                    it.name.contains(query.trim(), ignoreCase = true) ||
                    it.displayName(loc.language).contains(query.trim(), ignoreCase = true)
            }
            .sortedBy { it.name }
        if (members.isEmpty()) null else division to members
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.plane),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Space.stack),
    ) {
        item {
            SearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = loc.t("area.picker.search"),
                modifier = Modifier.padding(horizontal = Space.screen),
            )
        }

        grouped.forEach { (division, areas) ->
            item(key = division.name) {
                GroupedSection(
                    title = division.displayName(loc.language),
                    modifier = Modifier.padding(horizontal = Space.screen),
                ) {
                    Column {
                        areas.forEachIndexed { index, area ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = Hit.minimum)
                                    .clickable(role = Role.Button) {
                                        preferences.homeAreaCode = area.code
                                        onPicked()
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    area.displayName(loc.language),
                                    style = typo(Typo.SUBHEADLINE),
                                    modifier = Modifier.weight(1f),
                                )
                                if (preferences.homeAreaCode == area.code) {
                                    Icon(
                                        DwIcons.check2,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            if (index < areas.size - 1) HairlineDivider()
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(Space.section)) }
    }
}
