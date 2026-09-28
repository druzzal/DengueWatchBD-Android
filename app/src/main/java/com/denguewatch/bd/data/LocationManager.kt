package com.denguewatch.bd.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager as SystemLocationManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * The device's position, and nothing more.
 *
 * Everything here stays on the phone: the fix names the reporting area the
 * reader is standing in, centres the map, and finds hospitals nearby. It is
 * never stored beyond the process and never sent anywhere — which is what the
 * privacy copy on the About screen promises.
 *
 * Two accuracy modes, because the app asks two different questions. Naming a
 * division only needs a cell-tower fix, so the background answer is
 * balanced-power. The map draws a "you are here" dot the reader will compare
 * against streets, so while it is open the app switches to GPS.
 */
class LocationManager(context: Context) {

    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)

    /** Latitude to longitude, or null until a fix arrives. */
    var lastKnownLocation: Pair<Double, Double>? by mutableStateOf(null)
        private set

    var isRequesting: Boolean by mutableStateOf(false)
        private set

    /** Whether the user has granted either level of location permission. */
    val isAuthorized: Boolean
        get() = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    /** Fine location unlocks the GPS-grade fix the map wants. */
    val hasPreciseLocation: Boolean
        get() = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    /**
     * Whether the device's location master switch is on.
     *
     * Worth separating from the permission: with location off system-wide, the
     * fused provider simply never calls back, and an app that only checked the
     * permission would sit on a spinner forever with nothing to tell the reader.
     */
    val isLocationEnabled: Boolean
        get() {
            val service = appContext.getSystemService(Context.LOCATION_SERVICE)
                as? SystemLocationManager ?: return false
            return service.isProviderEnabled(SystemLocationManager.GPS_PROVIDER) ||
                service.isProviderEnabled(SystemLocationManager.NETWORK_PROVIDER)
        }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Ask for one fix.
     *
     * The cached location is taken first when there is one — it is free and, at
     * division scale, never stale enough to matter. A fresh fix is requested
     * only when there is nothing cached at all.
     */
    @SuppressLint("MissingPermission")
    fun refresh() {
        if (!isAuthorized || isRequesting) return
        isRequesting = true

        client.lastLocation
            .addOnSuccessListener { cached ->
                if (cached != null) {
                    lastKnownLocation = cached.latitude to cached.longitude
                    isRequesting = false
                } else {
                    requestFresh()
                }
            }
            .addOnFailureListener { isRequesting = false }
    }

    @SuppressLint("MissingPermission")
    private fun requestFresh() {
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) lastKnownLocation = location.latitude to location.longitude
                isRequesting = false
            }
            .addOnFailureListener { isRequesting = false }
    }

    // MARK: - Live tracking, for the map

    private var callback: LocationCallback? = null

    /**
     * Follow the device while the map is on screen.
     *
     * Ten seconds is loose for a moving dot but generous for what it feeds: the
     * finest geography this app has is a division, and a tighter interval would
     * spend battery redrawing a bubble that has not moved. Stopped in
     * [stopTracking] as soon as the map goes away.
     */
    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (!isAuthorized || callback != null) return

        val priority = if (hasPreciseLocation) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val request = LocationRequest.Builder(priority, 10_000L)
            .setMinUpdateIntervalMillis(5_000L)
            .setMinUpdateDistanceMeters(50f)
            .build()

        val newCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    lastKnownLocation = it.latitude to it.longitude
                    isRequesting = false
                }
            }
        }
        callback = newCallback
        isRequesting = lastKnownLocation == null
        client.requestLocationUpdates(request, newCallback, appContext.mainLooper)
    }

    fun stopTracking() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
        isRequesting = false
    }

    /**
     * The permissions to ask for.
     *
     * Both, in one prompt: Android shows a single dialog with a precise/approximate
     * choice, and the reader who picks approximate still gets a working app —
     * the area lookup only needs the coarse answer.
     */
    val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )
}
