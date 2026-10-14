package com.denguewatch.bd.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Keeps the figures, the armed geofences and the alerts current while the app
 * is closed.
 *
 * Three things otherwise depended on someone opening the app, and all three are
 * the kind of thing a reader would reasonably assume happens by itself:
 *
 *   1. The home-area risk alert was only evaluated at launch and on foreground,
 *      so a jump reported on Tuesday reached a reader who next opened the app on
 *      Friday — on Friday.
 *   2. The geofence list was armed from whatever the hotspots were the last time
 *      the app was open, so an area that turned high while it was closed was not
 *      watched at all.
 *   3. The weekly digest could not arrive at all.
 *
 * WorkManager decides when this actually runs and gives no guarantee — the
 * interval is a floor, not a schedule, and Doze will stretch it. So this is a
 * way of arriving sooner, not a promise. Everything still works if it never
 * runs; it just works later, when the reader next opens the app.
 */
class RefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val bundled = BundledFeedService(context)
        val store = DengueStore(bundled)
        val preferences = Preferences(context)
        val sync = FeedSync(context, bundled)

        // Start from what is already on disk, so the report date compared
        // against is the one the reader last saw.
        store.load()
        sync.attach(store)
        sync.sync(force = true)

        // Re-arm even when nothing new arrived: the fences may still be stale
        // from an older run.
        if (preferences.geofenceAlertsEnabled) {
            Geofencing.arm(context, store.areasToMonitor(preferences.alertThreshold))
            // The backstop. Geofences may be unregistered, deferred by Doze or
            // killed by the manufacturer; this notices the same thing from a
            // plain location fix, just later.
            AreaEntryWatch.check(context, store, preferences.alertThreshold)
        }

        if (preferences.alertsEnabled) {
            store.area(preferences.homeAreaCode)?.let { area ->
                Notifications.createChannels(context)
                Notifications.raiseRiskAlertIfNeeded(context, area, preferences.alertThreshold)
            }
        }

        return when (sync.status) {
            // A failed fetch is worth retrying; everything else has done its job
            // for this run, including "nothing new to say".
            is FeedSync.Status.Failed -> Result.retry()
            else -> Result.success()
        }
    }
}

/**
 * Puts the geofences back from the figures on disk. See [GeofenceRearmReceiver].
 */
class GeofenceRearmWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val preferences = Preferences(context)
        if (!preferences.geofenceAlertsEnabled) return Result.success()

        val store = DengueStore(BundledFeedService(context))
        store.load()
        Geofencing.arm(context, store.areasToMonitor(preferences.alertThreshold))
        return Result.success()
    }
}

/** Posts the Sunday-morning summary. */
class WeeklyDigestWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val preferences = Preferences(context)
        if (!preferences.weeklyDigestEnabled) return Result.success()

        val store = DengueStore(BundledFeedService(context))
        store.load()
        if (store.areas.isEmpty()) return Result.success()

        Notifications.createChannels(context)
        Notifications.postWeeklyDigest(context, store)
        return Result.success()
    }
}

object BackgroundRefresh {

    private const val REFRESH_WORK = "dengue-refresh"
    private const val DIGEST_WORK = "dengue-weekly-digest"
    private const val REARM_WORK = "dengue-geofence-rearm"

    /**
     * Re-arm the entry warning now, from what is on disk. One-off and with no
     * network constraint, because the point is not to wait.
     */
    fun rearmGeofences(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            REARM_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<GeofenceRearmWorker>().build(),
        )
    }

    /**
     * Called on every launch. `KEEP` rather than `UPDATE` so an app that is
     * opened several times a day does not keep resetting the timer and thereby
     * never run.
     */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    // No point waking to fetch with no connection, and a failed
                    // fetch would only burn a retry.
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            // DGHS publishes once a day, so six hours is already generous. The
            // flex window lets the system pick a moment that suits the device.
            .setInitialDelay(2, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            REFRESH_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Sunday morning, the start of the working week in Bangladesh.
     *
     * Scheduled with an initial delay to the next Sunday 09:00 rather than a
     * bare weekly period, which would otherwise fire at whatever time the reader
     * happened to switch it on.
     */
    fun scheduleWeeklyDigest(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(DIGEST_WORK)
            return
        }

        val request = PeriodicWorkRequestBuilder<WeeklyDigestWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(millisUntilNextSundayMorning(), TimeUnit.MILLISECONDS)
            .build()

        manager.enqueueUniquePeriodicWork(
            DIGEST_WORK,
            // UPDATE here, unlike the refresh: the reader toggling this on
            // expects the schedule to be (re)established from now.
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun millisUntilNextSundayMorning(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // Calendar.SUNDAY is 1. Move forward to the next one; if today is
            // Sunday but past nine, that is a week away, which is correct.
            while (get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY || timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return target.timeInMillis - now.timeInMillis
    }
}
