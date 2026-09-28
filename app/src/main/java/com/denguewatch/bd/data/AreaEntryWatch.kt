package com.denguewatch.bd.data

import android.annotation.SuppressLint
import android.content.Context
import com.denguewatch.bd.model.Area
import com.denguewatch.bd.model.RiskLevel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The second way of noticing you have entered a risky area.
 *
 * Geofences are the fast path: Play Services wakes the app within a minute or
 * two of a boundary crossing. They are also the fragile one. Registration is
 * refused outright when the device's location mode cannot support it
 * (`GEOFENCE_NOT_AVAILABLE`, which is what a stock emulator does and what a
 * GPS-only device does), Doze defers transitions, and several manufacturers
 * kill the delivering process outright to save battery. A warning that only
 * works when all of that goes right is not a warning anyone should rely on.
 *
 * So the periodic refresh also asks a much simpler question: where is this
 * phone now, which reporting area is that, and is it one the reader asked to
 * hear about? Slower — it arrives on the worker's schedule rather than at the
 * boundary — but it depends on nothing except a location fix the app can
 * already get.
 *
 * The two share one per-area daily rate limit, so a reader whose geofences work
 * does not get told twice.
 */
object AreaEntryWatch {

    private const val KEY_LAST_AREA = "entry.lastAreaCode"

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences("dengue_watch_prefs", Context.MODE_PRIVATE)

    /**
     * Resolve the current area and warn if it is one worth warning about.
     *
     * @return the area alerted about, or null when there was nothing to say.
     */
    @SuppressLint("MissingPermission")
    suspend fun check(
        context: Context,
        store: DengueStore,
        threshold: RiskLevel,
    ): Area? {
        if (!Geofencing.hasPermission(context)) return null
        if (store.areas.isEmpty()) return null

        val fix = currentLocation(context) ?: return null
        val area = store.nearestArea(fix.first, fix.second) ?: return null

        val previous = prefs(context).getString(KEY_LAST_AREA, null)
        // Always record where we are, even when we say nothing. Otherwise the
        // first check after the reader crosses back into a risky area they were
        // already in would read as a fresh entry.
        prefs(context).edit().putString(KEY_LAST_AREA, area.code).apply()

        // The first check establishes where the reader is; it does not report an
        // arrival. Without this, switching the warning on while sitting at home
        // in a risky area fires immediately — "you are entering Dhaka North
        // City" to someone who has been there all week. That is a false alarm,
        // and it is the same one the geofence path avoids by not passing
        // INITIAL_TRIGGER_ENTER.
        if (previous == null) return null

        // Staying put is not entering. Only a change of area counts, which is
        // what makes this an entry warning rather than a daily nag about where
        // the reader lives.
        if (previous == area.code) return null
        if (area.risk.ordinal < threshold.ordinal) return null

        val watched = WatchedArea.from(area)
        Notifications.createChannels(context)
        // Shares the geofence path's per-area daily gate, so a reader whose
        // geofences fired is not told the same thing twice.
        return if (Notifications.raiseGeofenceAlert(context, watched)) area else null
    }

    /** Forget where we were, so the next check treats the area as newly entered. */
    fun reset(context: Context) {
        prefs(context).edit().remove(KEY_LAST_AREA).apply()
    }

    /**
     * Where the phone is now, not where it was.
     *
     * `lastLocation` alone is the wrong question for an entry warning: it
     * returns whatever the fused provider happens to hold, which can be hours
     * old, so a reader who drove into a severe area would be told about the one
     * they left. This asks for a current fix and only falls back to the cached
     * one when none can be obtained.
     *
     * Balanced power, not high accuracy — the finest geography this app has is a
     * division, so a GPS-grade fix would spend battery to answer a question a
     * cell-tower fix already answers.
     */
    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(context: Context): Pair<Double, Double>? {
        val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

        val fresh = suspendCancellableCoroutine { continuation ->
            runCatching {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener { location ->
                        continuation.resume(location?.let { it.latitude to it.longitude })
                    }
                    .addOnFailureListener { continuation.resume(null) }
            }.onFailure { continuation.resume(null) }
        }
        if (fresh != null) return fresh

        return suspendCancellableCoroutine { continuation ->
            runCatching {
                client.lastLocation
                    .addOnSuccessListener { location ->
                        continuation.resume(location?.let { it.latitude to it.longitude })
                    }
                    .addOnFailureListener { continuation.resume(null) }
            }.onFailure { continuation.resume(null) }
        }
    }
}
