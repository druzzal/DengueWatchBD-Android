package com.denguewatch.bd

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.feature.care.CareScreen
import com.denguewatch.bd.feature.dashboard.DashboardScreen
import com.denguewatch.bd.feature.dashboard.LanguageToggle
import com.denguewatch.bd.feature.map.AreaDetailScreen
import com.denguewatch.bd.feature.map.AreaMapScreen
import com.denguewatch.bd.feature.map.RiskDetailSheet
import com.denguewatch.bd.feature.prevent.AlertSettingsScreen
import com.denguewatch.bd.feature.prevent.PreventionScreen
import com.denguewatch.bd.feature.shared.AboutScreen
import com.denguewatch.bd.feature.shared.CircleChipButton
import com.denguewatch.bd.feature.shared.FirstRunDisclaimer
import com.denguewatch.bd.feature.shared.ShrinkToFitText
import com.denguewatch.bd.feature.triage.CaseLogScreen
import com.denguewatch.bd.feature.triage.SymptomCheckerScreen
import com.denguewatch.bd.feature.triage.TriageEngine
import com.denguewatch.bd.feature.triage.TriageOutcome
import com.denguewatch.bd.feature.triage.TriageResultScreen
import com.denguewatch.bd.i18n.LocalLanguage
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.i18n.displayName
import com.denguewatch.bd.model.Area
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.ui.theme.DengueWatchTheme
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Layout
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.readableColumn
import com.denguewatch.bd.ui.theme.typo
import kotlinx.coroutines.launch

/**
 * Which screen is in front.
 *
 * A sealed hierarchy rather than a navigation graph with string routes: there
 * are five tabs and four pushed screens, every one of which carries a typed
 * argument or none at all, and a route table would turn those arguments back
 * into strings for no gain.
 */
sealed interface Destination {
    data object Tabs : Destination
    data class AreaDetail(val areaCode: String) : Destination
    data object CaseLog : Destination
    data object TriageResult : Destination
    data object About : Destination
    data object AlertSettings : Destination
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootScreen(
    state: AppState,
    hasNotificationPermission: Boolean,
    onCall: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenMapQuery: (String) -> Unit,
    onRequestLocation: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestBackgroundLocation: () -> Unit,
) {
    val loc = state.localization

    DengueWatchTheme {
        CompositionLocalProvider(
            LocalAppState provides state,
            LocalLocalization provides loc,
            LocalLanguage provides loc.language,
        ) {
            val colors = Dw.colors

            // The notice is shown before any figure is: the two things it says
            // change how every number behind it should be read.
            if (!state.preferences.hasSeenDisclaimer) {
                Box(Modifier.fillMaxSize().background(colors.plane)) {
                    FirstRunDisclaimer(
                        onAccept = { state.preferences.hasSeenDisclaimer = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(WindowInsets.systemBars.asPaddingValues()),
                    )
                }
                return@CompositionLocalProvider
            }

            MainScaffold(
                state = state,
                hasNotificationPermission = hasNotificationPermission,
                onCall = onCall,
                onOpenUrl = onOpenUrl,
                onOpenMapQuery = onOpenMapQuery,
                onRequestLocation = onRequestLocation,
                onRequestNotifications = onRequestNotifications,
                onRequestBackgroundLocation = onRequestBackgroundLocation,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    state: AppState,
    hasNotificationPermission: Boolean,
    onCall: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenMapQuery: (String) -> Unit,
    onRequestLocation: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestBackgroundLocation: () -> Unit,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors
    val scope = rememberCoroutineScope()


    var destination by remember { mutableStateOf<Destination>(Destination.Tabs) }
    var showsList by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    // The check's own state lives here so the result screen can be a
    // destination rather than a nested navigation host.
    var selectedSymptoms by remember { mutableStateOf(emptySet<String>()) }
    var triageContext by remember { mutableStateOf(TriageEngine.Context()) }
    var feverDaysAgo by remember { mutableStateOf<Int?>(null) }

    var riskSheet by remember { mutableStateOf<RiskSheetArgs?>(null) }

    val outcome = remember(selectedSymptoms, triageContext, feverDaysAgo) {
        TriageEngine.evaluate(
            selectedSymptoms,
            triageContext.copy(feverDaysAgo = feverDaysAgo),
        )
    }

    BackHandler(enabled = destination != Destination.Tabs) {
        destination = Destination.Tabs
    }

    val title = when (val current = destination) {
        is Destination.Tabs -> when (state.selectedTab) {
            Tab.HOME -> loc.t("dash.title")
            Tab.MAP -> loc.t("map.title")
            Tab.CHECK -> loc.t("check.title")
            Tab.CARE -> loc.t("care.title")
            Tab.PREVENT -> loc.t("prevent.title")
        }
        is Destination.AreaDetail ->
            state.store.area(current.areaCode)?.displayName(loc.language) ?: ""
        Destination.CaseLog -> loc.t("log.title")
        Destination.TriageResult -> loc.t("result.title")
        Destination.About -> loc.t("about.title")
        Destination.AlertSettings -> loc.t("alerts.title")
    }

    Scaffold(
        containerColor = colors.plane,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    // Blank wherever the screen draws its own large title in the
                    // content, which is what iOS's large navigation title does.
                    // Map is the exception on purpose: iOS pins it to .inline,
                    // because a large title would eat the top of a full-bleed
                    // map for a name the tab bar already gives you.
                    val drawsOwnTitle = destination == Destination.Tabs &&
                        state.selectedTab in setOf(Tab.HOME, Tab.CHECK, Tab.CARE, Tab.PREVENT)
                    if (!drawsOwnTitle) {
                        // The bar's height is fixed, so this one genuinely has
                        // to hold a single line — wrapping would push the second
                        // line out of the bar. It shrinks to fit instead of
                        // clipping, which is how a long Bengali screen title
                        // stays readable in full.
                        ShrinkToFitText(
                            title,
                            style = typo(Typo.HEADLINE, FontWeight.SemiBold),
                            minScale = 0.75f,
                        )
                    }
                },
                navigationIcon = {
                    if (destination != Destination.Tabs) {
                        IconButton(onClick = { destination = Destination.Tabs }) {
                            Icon(DwIcons.back, contentDescription = loc.t("common.done"))
                        }
                    } else {
                        LanguageToggle(Modifier.padding(start = Space.row))
                    }
                },
                actions = {
                    if (destination == Destination.Tabs) {
                        when (state.selectedTab) {
                            Tab.HOME -> CircleChipButton(
                                contentDescription = loc.t("dash.about.a11y"),
                                onClick = { destination = Destination.About },
                                icon = DwIcons.info,
                                modifier = Modifier.padding(end = Space.row),
                            )
                            Tab.MAP -> CircleChipButton(
                                contentDescription = loc.t(
                                    if (showsList) "map.showMap" else "map.showList"
                                ),
                                onClick = { showsList = !showsList },
                                icon = if (showsList) DwIcons.map else DwIcons.list,
                                modifier = Modifier.padding(end = Space.row),
                            )
                            Tab.CHECK -> CircleChipButton(
                                contentDescription = loc.t("log.title"),
                                onClick = { destination = Destination.CaseLog },
                                icon = DwIcons.log,
                                modifier = Modifier.padding(end = Space.row),
                            )
                            else -> Unit
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colors.plane,
                    titleContentColor = colors.primaryText,
                    actionIconContentColor = colors.accent,
                    navigationIconContentColor = colors.accent,
                ),
            )
        },
        bottomBar = {
            if (destination == Destination.Tabs) {
                NavigationBar(containerColor = colors.card, tonalElevation = 0.dp) {
                    Tab.entries.forEach { tab ->
                        val isCurrent = state.selectedTab == tab
                        NavigationBarItem(
                            selected = isCurrent,
                            onClick = { state.show(tab) },
                            icon = {
                                Icon(
                                    when (tab) {
                                        Tab.HOME -> DwIcons.home
                                        Tab.MAP -> DwIcons.map
                                        Tab.CHECK -> DwIcons.check
                                        Tab.CARE -> DwIcons.care
                                        Tab.PREVENT -> DwIcons.prevent
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp),
                                )
                            },
                            // A tab slot is a fifth of the screen and cannot
                            // grow. At a 2.0 font scale "চিকিৎসা" wrapped onto a
                            // second line, pushing into the icon above it and
                            // running off the edge on the last tab. Shrinking
                            // keeps every label on one line and legible.
                            label = {
                                // The colour is passed rather than inherited.
                                // ShrinkToFitText resolves its own ink and does
                                // not read NavigationBarItemDefaults, so when the
                                // label became one it stopped following selection
                                // while the icon kept following it — the two
                                // drifted apart. Deriving both from `isCurrent`
                                // keeps them in step.
                                ShrinkToFitText(
                                    loc.t(tab.titleKey),
                                    style = typo(Typo.MICRO),
                                    color = if (isCurrent) colors.accent else colors.primaryText,
                                    minScale = 0.6f,
                                )
                            },
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
        },
    ) { padding ->
        val content = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 12.dp,
            start = padding.calculateStartPadding(LayoutDirection.Ltr),
            end = padding.calculateEndPadding(LayoutDirection.Ltr),
        )

        // Wide screens get margin rather than stretch. Without this the phone
        // layout simply expands: on a tablet a hospital row runs the full 800dp
        // with its name at one edge and its call button at the other, and a
        // landscape phone is already past the cap at 914dp. `readableColumn`
        // does the capping; this Box is what centres the capped column instead
        // of leaving it against the left edge.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when (val current = destination) {
            is Destination.Tabs -> {
                PullToRefreshBox(
                    contentAlignment = Alignment.TopCenter,
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            state.refreshNow()
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when (state.selectedTab) {
                        Tab.HOME -> DashboardScreen(
                            modifier = Modifier.readableColumn(),
                            state = state,
                            onOpenArea = { destination = Destination.AreaDetail(it.code) },
                            onOpenRiskDetail = { risk, name, incidence, cases ->
                                riskSheet = RiskSheetArgs(risk, name, incidence, cases)
                            },
                            contentPadding = content,
                        )

                        Tab.MAP -> AreaMapScreen(
                            state = state,
                            showsList = showsList,
                            onOpenArea = { destination = Destination.AreaDetail(it.code) },
                            onRequestLocation = onRequestLocation,
                            contentPadding = content,
                        )

                        Tab.CHECK -> SymptomCheckerScreen(
                            modifier = Modifier.readableColumn(),
                            state = state,
                            selected = selectedSymptoms,
                            onToggleSymptom = { id, on ->
                                selectedSymptoms = if (on) {
                                    selectedSymptoms + id
                                } else {
                                    selectedSymptoms - id
                                }
                            },
                            context = triageContext,
                            onContextChange = { triageContext = it },
                            feverDaysAgo = feverDaysAgo,
                            onFeverDaysChange = { feverDaysAgo = it },
                            onClearAll = {
                                selectedSymptoms = emptySet()
                                triageContext = TriageEngine.Context()
                                feverDaysAgo = null
                            },
                            onSeeResult = { destination = Destination.TriageResult },
                            contentPadding = content,
                        )

                        Tab.CARE -> CareScreen(
                            modifier = Modifier.readableColumn(),
                            state = state,
                            onCall = onCall,
                            onOpenMap = onOpenMapQuery,
                            onSearchNearby = {
                                onRequestLocation()
                                onOpenMapQuery("hospital")
                            },
                            contentPadding = content,
                        )

                        Tab.PREVENT -> PreventionScreen(
                            modifier = Modifier.readableColumn(),
                            state = state,
                            onOpenAlerts = { destination = Destination.AlertSettings },
                            contentPadding = content,
                        )
                    }
                }
            }

            is Destination.AreaDetail -> {
                val area = state.store.area(current.areaCode)
                if (area == null) {
                    destination = Destination.Tabs
                } else {
                    AreaDetailScreen(
                            modifier = Modifier.readableColumn(),
                        area = area,
                        state = state,
                        onOpenArea = { destination = Destination.AreaDetail(it.code) },
                        contentPadding = content,
                    )
                }
            }

            Destination.CaseLog -> CaseLogScreen(
                state = state,
                modifier = Modifier.readableColumn(),
                contentPadding = content,
            )

            Destination.TriageResult -> TriageResultScreen(
                            modifier = Modifier.readableColumn(),
                state = state,
                outcome = outcome,
                selected = selectedSymptoms,
                feverDaysAgo = feverDaysAgo,
                onFindCare = {
                    destination = Destination.Tabs
                    state.show(Tab.CARE)
                },
                onCall = onCall,
                contentPadding = content,
            )

            Destination.About -> AboutScreen(
                            modifier = Modifier.readableColumn(),
                state = state,
                onOpenUrl = onOpenUrl,
                contentPadding = content,
            )

            Destination.AlertSettings -> AlertSettingsScreen(
                            modifier = Modifier.readableColumn(),
                state = state,
                onChooseArea = {
                    destination = Destination.Tabs
                    state.show(Tab.MAP)
                },
                onRequestNotificationPermission = onRequestNotifications,
                onRequestBackgroundLocation = onRequestBackgroundLocation,
                hasNotificationPermission = hasNotificationPermission,
                contentPadding = content,
            )
        }
        }
    }

    riskSheet?.let { args ->
        RiskDetailSheet(
            risk = args.risk,
            areaName = args.areaName,
            incidence = args.incidence,
            last14Cases = args.last14Cases,
            lastUpdatedMillis = state.store.lastUpdated,
            onDismiss = { riskSheet = null },
        )
    }
}

private data class RiskSheetArgs(
    val risk: RiskLevel,
    val areaName: String,
    val incidence: Double,
    val last14Cases: Int,
)
