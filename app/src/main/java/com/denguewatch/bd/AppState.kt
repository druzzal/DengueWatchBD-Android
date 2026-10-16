package com.denguewatch.bd

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.denguewatch.bd.data.AreaEntryWatch
import com.denguewatch.bd.data.BackgroundRefresh
import com.denguewatch.bd.data.BundledFeedService
import com.denguewatch.bd.data.CaseLogStore
import com.denguewatch.bd.data.LabStore
import com.denguewatch.bd.data.FluidStore
import com.denguewatch.bd.model.HealthLog
import com.denguewatch.bd.model.HealthLogDay
import com.denguewatch.bd.data.VitalsStore
import com.denguewatch.bd.data.DengueStore
import com.denguewatch.bd.data.FeedSync
import com.denguewatch.bd.data.Geofencing
import com.denguewatch.bd.data.Notifications
import com.denguewatch.bd.data.LocationManager
import com.denguewatch.bd.data.Preferences
import com.denguewatch.bd.data.LogNameStore
import com.denguewatch.bd.data.PreventionChecklistStore
import com.denguewatch.bd.i18n.Localization
import kotlinx.coroutines.launch

/** The five tabs, and the icons and titles they carry. */
enum class Tab {
    HOME, MAP, HEALTH, CARE, PREVENT;

    val titleKey: String get() = "tab.${name.lowercase()}"
}

/**
 * Everything the screens share, owned for the life of the process.
 *
 * A `ViewModel` rather than a set of remembered objects: the stores must
 * outlive a configuration change, and the feed sync holds a registered network
 * callback that has to be torn down exactly once.
 */
class AppState(context: Context) : ViewModel() {

    private val appContext = context.applicationContext
    private val bundled = BundledFeedService(context)

    val store = DengueStore(bundled)
    val caseLog = CaseLogStore(context)

    /** Readings the user takes themselves, and what a lab reported back. */
    val vitals = VitalsStore(context)
    val labs = LabStore(context)

    /** What the reader drank and how often they passed urine — WHO's oral fluids. */
    val fluids = FluidStore(context)

    /** The whole health record by day, newest first: every screen reads it from here. */
    val healthLogDays: List<HealthLogDay>
        get() = HealthLog.days(vitals.entries, labs.reports, fluids.entries)

    /** Today's prevention tasks, cleared whenever the day turns over. */
    val checklist = PreventionChecklistStore(context)

    /** Names the reader has given individual days of their record. */
    val logNames = LogNameStore(context)

    val preferences = Preferences(context)
    val localization = Localization(context)
    val location = LocationManager(context)
    val sync = FeedSync(context, bundled)

    /**
     * Lets a card on one screen send the reader to another tab — the map
     * preview opening the full map, an alert opening the area list.
     */
    var selectedTab: Tab by mutableStateOf(Tab.HOME)

    /** The area the reader has drilled into, or null when none is open. */
    var openedAreaCode: String? by mutableStateOf(null)

    private var hasStarted = false
    /**
     * True once launch has loaded the figures on disk and checked for newer
     * ones. Until then a resume does not check: on a cold start `onResume`
     * fires while the store is still empty, so its check could not see that
     * today's report was already here and downloaded the whole feed again —
     * forty kilobytes on every launch, on a metered connection.
     */
    private var hasSyncedOnce = false

    fun start() {
        if (hasStarted) return
        hasStarted = true

        Notifications.createChannels(appContext)
        BackgroundRefresh.schedule(appContext)

        viewModelScope.launch {
            store.load()
            sync.start(store, viewModelScope)
            sync.sync(force = false)
            hasSyncedOnce = true
            // The feed has landed, so the hotspot list and the home-area reading
            // are both real now. Anything depending on them is applied here
            // rather than at construction, when the store was still empty.
            applyAlertSettings()
            checkAreaEntry()
        }
    }

    /**
     * Notice that the reader has moved into an area worth warning about.
     *
     * Guarded on the store being loaded: this runs from both `start` and
     * `onResume`, which both fire on a cold launch, and the resume path used to
     * win the race and check against an empty area list — doing nothing, and
     * leaving the reader's position unrecorded.
     */
    private suspend fun checkAreaEntry() {
        if (!preferences.geofenceAlertsEnabled) return
        if (store.areas.isEmpty()) return
        AreaEntryWatch.check(appContext, store, preferences.alertThreshold)
    }

    /** Called when the app comes back to the front. */
    fun onResume() {
        viewModelScope.launch {
            if (hasSyncedOnce) sync.sync(force = false)
            applyAlertSettings()
            // Coming back to the app is a cheap chance to notice the reader has
            // moved, without waiting for the worker's next turn.
            checkAreaEntry()
        }
        if (location.isAuthorized) location.refresh()
    }

    /**
     * Push the current preferences out to the things that act on them.
     *
     * Called after a sync rather than from the settings screen alone, because
     * the armed fences depend on the hotspot list and the alert depends on the
     * home area's band — both of which change when new figures arrive, not when
     * a switch is flipped.
     */
    fun applyAlertSettings() {
        if (preferences.geofenceAlertsEnabled) {
            Geofencing.arm(appContext, store.areasToMonitor(preferences.alertThreshold))
        } else {
            Geofencing.disarm(appContext)
        }

        BackgroundRefresh.scheduleWeeklyDigest(appContext, preferences.weeklyDigestEnabled)

        if (preferences.alertsEnabled) {
            store.area(preferences.homeAreaCode)?.let { area ->
                Notifications.raiseRiskAlertIfNeeded(appContext, area, preferences.alertThreshold)
            }
        }
    }

    suspend fun refreshNow() {
        sync.sync(force = true)
        store.refresh()
    }

    fun show(tab: Tab) {
        selectedTab = tab
    }

    override fun onCleared() {
        sync.stop()
        super.onCleared()
    }

    /**
     * The area the reader is being told about: their chosen area, else the area
     * they are standing in, else nothing (which the UI reads as nationwide).
     */
    val focusArea
        get() = store.area(preferences.homeAreaCode)
            ?: location.lastKnownLocation?.let { (lat, lon) -> store.nearestArea(lat, lon) }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AppState(context.applicationContext) as T
    }
}

val LocalAppState = compositionLocalOf<AppState> { error("No AppState provided") }

@Composable
fun appState(): AppState = LocalAppState.current
