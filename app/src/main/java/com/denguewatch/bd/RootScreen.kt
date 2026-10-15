package com.denguewatch.bd

import com.denguewatch.bd.feature.triage.ExportLogButton
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.denguewatch.bd.feature.share.ShareAreaButton
import com.denguewatch.bd.feature.care.CareScreen
import com.denguewatch.bd.feature.dashboard.DashboardScreen
import com.denguewatch.bd.feature.dashboard.LanguageToggle
import com.denguewatch.bd.feature.map.AreaDetailScreen
import com.denguewatch.bd.data.CaseLogEntry
import com.denguewatch.bd.feature.health.LabEntryScreen
import com.denguewatch.bd.feature.health.SaveSlot
import com.denguewatch.bd.feature.health.MyHealthScreen
import com.denguewatch.bd.feature.health.VitalsEntryScreen
import com.denguewatch.bd.feature.map.AreaMapScreen
import com.denguewatch.bd.feature.map.RiskDetailSheet
import com.denguewatch.bd.feature.prevent.AlertSettingsScreen
import com.denguewatch.bd.feature.prevent.AreaPickerScreen
import com.denguewatch.bd.feature.prevent.PreventionScreen
import com.denguewatch.bd.feature.shared.AboutScreen
import com.denguewatch.bd.feature.shared.CircleChipButton
import com.denguewatch.bd.feature.shared.FirstRunDisclaimer
import com.denguewatch.bd.feature.shared.ShrinkToFitText
import com.denguewatch.bd.model.HealthLog
import com.denguewatch.bd.feature.triage.CaseLogScreen
import com.denguewatch.bd.feature.triage.LogDayDetailScreen
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
import com.denguewatch.bd.ui.theme.windowSizeDp
import com.denguewatch.bd.ui.theme.ShortHeightBreakpoint
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
    /**
     * Where Back leads. Every screen is opened from the tabs except a log day,
     * which is opened from the log and goes back there — sending it to the
     * tabs skipped the list the reader had just picked it from — and the area
     * picker, which belongs to the alerts screen.
     */
    val parent: Destination get() = Tabs

    data object Tabs : Destination
    data class AreaDetail(val areaCode: String) : Destination
    data object SymptomCheck : Destination
    data object RecordVitals : Destination
    data object AddLabReport : Destination
    data object CaseLog : Destination
    data class LogDay(val dayMillis: Long) : Destination {
        override val parent: Destination get() = CaseLog
    }
    data object TriageResult : Destination
    data object About : Destination
    data object AlertSettings : Destination
    data object AreaPicker : Destination {
        override val parent: Destination get() = AlertSettings
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootScreen(
    state: AppState,
    hasNotificationPermission: Boolean,
    locationDenied: Boolean,
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
                locationDenied = locationDenied,
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
    locationDenied: Boolean,
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
    // Width now, not the shorter side: a phone turned sideways is wide enough
    // for the rail and short enough to need it.
    val wide = windowSizeDp().width >= Layout.regularWidthBreakpoint
    // Lets the two recording screens put their Save button in the bar, where
    // iOS has it, without the bar having to know what is in their fields.
    val saveSlot = remember { SaveSlot() }
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
        destination = destination.parent
    }

    val title = when (val current = destination) {
        is Destination.Tabs -> when (state.selectedTab) {
            Tab.HOME -> loc.t("dash.title")
            Tab.MAP -> loc.t("map.title")
            Tab.HEALTH -> loc.t("tab.health")
            Tab.CARE -> loc.t("care.title")
            Tab.PREVENT -> loc.t("prevent.title")
        }
        is Destination.AreaDetail ->
            state.store.area(current.areaCode)?.displayName(loc.language) ?: ""
        Destination.SymptomCheck -> loc.t("check.title")
        Destination.RecordVitals -> loc.t("vital.record")
        Destination.AddLabReport -> loc.t("lab.add")
        Destination.CaseLog -> loc.t("log.title")
        // The day's own name, so the reader knows which record they opened.
        is Destination.LogDay -> {
            val days = HealthLog.days(state.vitals.entries, state.labs.reports)
            val numbers = HealthLog.numbers(days)
            state.logNames.name(current.dayMillis)
                ?: numbers[current.dayMillis]?.let { loc.t("log.defaultName", loc.num(it)) }
                ?: loc.dayMonth(current.dayMillis)
        }
        Destination.TriageResult -> loc.t("result.title")
        Destination.About -> loc.t("about.title")
        Destination.AlertSettings -> loc.t("alerts.title")
        Destination.AreaPicker -> loc.t("area.picker.title")
    }

    val showRail = wide && destination == Destination.Tabs
    // The rail runs the full height of the screen with the top bar beside it,
    // as Material lays one out. Under the top bar it lost a quarter of a
    // sideways phone's height, and the fifth tab fell off the bottom. Each
    // side takes only its own insets: the rail the start edge, the bar and
    // content the rest.
    Row(Modifier.fillMaxSize().background(colors.plane)) {
    if (showRail) TabRail(state)
    Scaffold(
        modifier = Modifier.weight(1f),
        containerColor = colors.plane,
        contentWindowInsets = if (showRail) {
            ScaffoldDefaults.contentWindowInsets
                .only(WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.End)
        } else {
            ScaffoldDefaults.contentWindowInsets
        },
        topBar = {
            CenterAlignedTopAppBar(
                windowInsets = if (showRail) {
                    TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.End)
                } else {
                    TopAppBarDefaults.windowInsets
                },
                title = {
                    // Blank wherever the screen draws its own large title in the
                    // content, which is what iOS's large navigation title does.
                    // Map is the exception on purpose: iOS pins it to .inline,
                    // because a large title would eat the top of a full-bleed
                    // map for a name the tab bar already gives you.
                    val drawsOwnTitle = destination == Destination.Tabs &&
                        state.selectedTab in setOf(Tab.HOME, Tab.HEALTH, Tab.CARE, Tab.PREVENT)
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
                        IconButton(onClick = { destination = destination.parent }) {
                            Icon(DwIcons.back, contentDescription = loc.t("common.done"))
                        }
                    } else {
                        LanguageToggle(Modifier.padding(start = Space.row))
                    }
                },
                actions = {
                    // What the recording screens save, named as iOS names it.
                    val saveLabel = when (destination) {
                        Destination.RecordVitals -> loc.t("vital.save")
                        Destination.AddLabReport -> loc.t("lab.save")
                        else -> null
                    }
                    if (saveLabel != null) {
                        TextButton(
                            onClick = { saveSlot.perform() },
                            enabled = saveSlot.enabled,
                            modifier = Modifier.padding(end = Space.tight),
                        ) {
                            ShrinkToFitText(
                                saveLabel,
                                style = typo(Typo.CALLOUT, FontWeight.SemiBold),
                                color = if (saveSlot.enabled) colors.accent else colors.tertiaryText,
                                minScale = 0.8f,
                            )
                        }
                    }
                    // The log as a PDF — all of it from My log, one day from a
                    // day's own screen — in the bar where iOS puts it.
                    when (val current = destination) {
                        Destination.CaseLog -> if (
                            state.vitals.entries.isNotEmpty() || state.labs.reports.isNotEmpty()
                        ) {
                            ExportLogButton(state, dayMillis = null, modifier = Modifier.padding(end = Space.tight))
                        }
                        is Destination.LogDay -> ExportLogButton(
                            state, dayMillis = current.dayMillis, modifier = Modifier.padding(end = Space.tight),
                        )
                        else -> Unit
                    }
                    // The area's own figures as a card someone can send on,
                    // in the bar where iOS puts it.
                    (destination as? Destination.AreaDetail)
                        ?.let { state.store.area(it.areaCode) }
                        ?.let { area ->
                            ShareAreaButton(
                                area = area,
                                lastUpdatedMillis = state.store.lastUpdated,
                                asIcon = true,
                            )
                        }
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
                            Tab.HEALTH -> CircleChipButton(
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
            // Compact width only. From 600dp — a tablet, or a phone on its side,
            // where a bottom bar spends a fifth of the little height there is —
            // the tabs move to a rail at the side instead.
            if (destination == Destination.Tabs && !wide) {
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
                            onRequestBackgroundLocation = onRequestBackgroundLocation,
                            showsList = showsList,
                            onOpenArea = { destination = Destination.AreaDetail(it.code) },
                            onRequestLocation = onRequestLocation,
                            contentPadding = content,
                        )

                        Tab.HEALTH -> MyHealthScreen(
                            modifier = Modifier.readableColumn(),
                            state = state,
                            onOpenCheck = { destination = Destination.SymptomCheck },
                            onOpenLog = { destination = Destination.CaseLog },
                            onRecordVitals = { destination = Destination.RecordVitals },
                            onAddLabReport = { destination = Destination.AddLabReport },
                            contentPadding = content,
                        )

                        Tab.CARE -> CareScreen(
                            modifier = Modifier.readableColumn(),
                            state = state,
                            onRequestLocation = onRequestLocation,
                            deniedLocation = locationDenied,
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

Destination.SymptomCheck -> SymptomCheckerScreen(
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
                onSeeResult = {
                    // Recorded as the check completes, rather than behind a
                    // button on the result. The checker's job is to say what WHO
                    // advises; keeping the answers is the app's job, and putting
                    // that behind an opt-in meant My log and the latest-check
                    // card quietly depended on a step most readers would skip.
                    // This is what the iOS build does.
                    state.caseLog.add(
                        CaseLogEntry(
                            symptomIds = selectedSymptoms.toList(),
                            outcomeOrdinal = outcome.ordinal,
                            areaCode = state.preferences.homeAreaCode,
                            // The day of illness and the co-existing
                            // conditions are what the WHO plan is rebuilt
                            // from. Without them the plan could only be
                            // computed on the screen that produced it, and the
                            // card in My health would have nothing to show
                            // tomorrow.
                            feverDaysAgo = feverDaysAgo,
                            isPregnant = triageContext.isPregnant,
                            isUnderFiveOrOverSixty = triageContext.isUnderFiveOrOverSixty,
                            hasChronicCondition = triageContext.hasChronicCondition,
                            hadDengueBefore = triageContext.hadDengueBefore,
                        )
                    )
                    destination = Destination.TriageResult
                },
                contentPadding = content,
            )

            Destination.RecordVitals -> VitalsEntryScreen(
                state = state,
                saveSlot = saveSlot,
                modifier = Modifier.readableColumn(),
                onSaved = { destination = Destination.Tabs },
                contentPadding = content,
            )

            Destination.AddLabReport -> LabEntryScreen(
                state = state,
                saveSlot = saveSlot,
                modifier = Modifier.readableColumn(),
                onSaved = { destination = Destination.Tabs },
                contentPadding = content,
            )

            Destination.CaseLog -> CaseLogScreen(
                state = state,
                modifier = Modifier.readableColumn(),
                onOpenDay = { destination = Destination.LogDay(it) },
                contentPadding = content,
            )

            is Destination.LogDay -> LogDayDetailScreen(
                state = state,
                dayMillis = current.dayMillis,
                modifier = Modifier.readableColumn(),
                contentPadding = content,
            )

            Destination.TriageResult -> {
                // Cleared as the reader leaves the result, not as it opens:
                // clearing while it is still on screen would blank the answers
                // it is explaining. Leaving means that check is finished, and a
                // half-filled form from a previous illness is a worse starting
                // point than an empty one — stale ticks would be carried
                // silently into the next result.
                DisposableEffect(Unit) {
                    onDispose {
                        selectedSymptoms = emptySet()
                        triageContext = TriageEngine.Context()
                        feverDaysAgo = null
                    }
                }
                TriageResultScreen(
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
            }

            Destination.About -> AboutScreen(
                            modifier = Modifier.readableColumn(),
                state = state,
                onOpenUrl = onOpenUrl,
                contentPadding = content,
            )

            Destination.AlertSettings -> AlertSettingsScreen(
                            modifier = Modifier.readableColumn(),
                state = state,
                onChooseArea = { destination = Destination.AreaPicker },
                onRequestNotificationPermission = onRequestNotifications,
                onRequestBackgroundLocation = onRequestBackgroundLocation,
                hasNotificationPermission = hasNotificationPermission,
                contentPadding = content,
            )

            Destination.AreaPicker -> AreaPickerScreen(
                modifier = Modifier.readableColumn(),
                state = state,
                onPicked = { destination = Destination.AlertSettings },
                contentPadding = content,
            )
        }
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

/** The tabs down the side, for a wide screen. Same items and colours as the bar. */
@Composable
private fun TabRail(state: AppState, modifier: Modifier = Modifier) {
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
private fun TabIcon(tab: Tab) {
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
private fun TabLabel(tab: Tab, isCurrent: Boolean) {
    val colors = Dw.colors
    ShrinkToFitText(
        LocalLocalization.current.t(tab.titleKey),
        style = typo(Typo.MICRO),
        color = if (isCurrent) colors.accent else colors.primaryText,
        minScale = 0.6f,
    )
}

private data class RiskSheetArgs(
    val risk: RiskLevel,
    val areaName: String,
    val incidence: Double,
    val last14Cases: Int,
)
