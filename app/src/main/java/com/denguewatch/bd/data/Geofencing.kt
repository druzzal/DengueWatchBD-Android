package com.denguewatch.bd.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.denguewatch.bd.model.Area

/**
 * Warns the reader when they physically enter a high-risk area.
 *
 * The armed set is the hotspot list, capped and stored on disk so a transition
 * can be described without the feed being loaded — a geofence broadcast may
 * start the process on its own.
 *
 * Entry only. Android also offers exit and dwell; neither says anything a
 * reader needs, and every extra transition is another chance to interrupt
 * someone for nothing.
 */
object Geofencing {

    /**
     * Android allows 100 geofences per app and the feed has ten areas, so the
     * cap is never reached in practice. It is here because the limit is
     * enforced by rejecting the whole request, not by trimming it.
     */
    private const val MAX_FENCES = 50

    /**
     * Whether the app can actually watch regions in the background.
     *
     * Fine location is required for geofencing, and from Android 10 background
     * location is a separate grant made in system settings. Without it the
     * regions register but only fire while the app is open, which is not what
     * the setting promises.
     */
    fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context.applicationContext,
            0,
            Intent(context.applicationContext, GeofenceReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )

    /**
     * Replace the armed set with the current hotspots.
     *
     * @param areas the hotspots, or null when the feed carries no area data at
     *   all. Those mean opposite things: an empty list says disarm, nothing is
     *   high right now; null says we cannot tell, so leave what is armed alone
     *   rather than silently stopping the warnings.
     */
    @SuppressLint("MissingPermission")
    fun arm(context: Context, areas: List<Area>?) {
        if (areas == null) return
        if (!hasPermission(context)) return

        val client = LocationServices.getGeofencingClient(context.applicationContext)
        val watched = areas.take(MAX_FENCES).map { WatchedArea.from(it) }

        if (watched.isEmpty()) {
            client.removeGeofences(pendingIntent(context))
            WatchedAreaStore.clear(context)
            recordStatus(context, ARMED_NONE)
            return
        }

        val fences = watched.map { area ->
            Geofence.Builder()
                .setRequestId(area.code)
                .setCircularRegion(area.latitude, area.longitude, area.radiusMeters.toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                // The reader has to be inside for a couple of minutes before we
                // say anything. Without it, driving past the edge of a division
                // raises an alert about somewhere they never stopped.
                .setLoiteringDelay(120_000)
                .build()
        }

        val request = GeofencingRequest.Builder()
            // Deliberately not INITIAL_TRIGGER_ENTER: arming while the reader is
            // already standing in a high-risk area would fire immediately, which
            // reads as a false alarm rather than as news.
            .setInitialTrigger(0)
            .addGeofences(fences)
            .build()

        // Chained, not fired side by side. Both calls are asynchronous against
        // the same PendingIntent, so issuing them together lets the removal land
        // after the registration and quietly wipe the fences just added.
        client.removeGeofences(pendingIntent(context)).addOnCompleteListener {
            client.addGeofences(request, pendingIntent(context))
                .addOnSuccessListener {
                    WatchedAreaStore.save(context, watched)
                    recordStatus(context, watched.size.toString())
                }
                .addOnFailureListener { error ->
                    // Silence here was the bug worth fixing: a failed
                    // registration left the switch reading "on" with nothing
                    // watching, and no way for the reader or for us to tell.
                    WatchedAreaStore.clear(context)
                    val code = (error as? com.google.android.gms.common.api.ApiException)?.statusCode
                    recordStatus(
                        context,
                        ARMED_FAILED + (code?.let { GeofenceStatusCodes.getStatusCodeString(it) }
                            ?: error.message.orEmpty()),
                    )
                }
        }
    }

    const val ARMED_NONE = "none"
    const val ARMED_FAILED = "failed:"
    private const val KEY_STATUS = "geofence.status"

    /**
     * What happened last time we tried to arm.
     *
     * Read by the alert settings screen so it can say "watching 3 areas",
     * "nothing is high right now", or that registration failed — rather than
     * showing a switch that is on and leaving the reader to assume it works.
     */
    fun status(context: Context): String =
        context.applicationContext
            .getSharedPreferences("dengue_watch_prefs", Context.MODE_PRIVATE)
            .getString(KEY_STATUS, ARMED_NONE) ?: ARMED_NONE

    private fun recordStatus(context: Context, value: String) {
        context.applicationContext
            .getSharedPreferences("dengue_watch_prefs", Context.MODE_PRIVATE)
            .edit().putString(KEY_STATUS, value).apply()
    }

    fun disarm(context: Context) {
        LocationServices.getGeofencingClient(context.applicationContext)
            .removeGeofences(pendingIntent(context))
        WatchedAreaStore.clear(context)
    }
}

/**
 * Receives the boundary crossing and raises the alert.
 *
 * Everything it needs is on disk, because this can run with no activity alive
 * and no feed loaded.
 */
class GeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            // Nothing useful to tell the reader about a Play Services error, and
            // an alert saying "something went wrong" on a health app is worse
            // than silence.
            GeofenceStatusCodes.getStatusCodeString(event.errorCode)
            return
        }
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return

        Notifications.createChannels(context)
        event.triggeringGeofences.orEmpty().forEach { fence ->
            WatchedAreaStore.area(context, fence.requestId)?.let { area ->
                Notifications.raiseGeofenceAlert(context, area)
            }
        }
    }
}
