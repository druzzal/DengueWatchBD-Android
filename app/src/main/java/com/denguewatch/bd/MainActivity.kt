package com.denguewatch.bd

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {

    private lateinit var state: AppState

    private var hasNotificationPermission by mutableStateOf(true)

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) state.location.refresh()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        // The reader turned notifications on from the alerts screen; apply
        // whatever they had already switched on while it was refused.
        if (granted) state.applyAlertSettings()
    }

    /**
     * Background location, which geofencing needs and Android grants separately.
     *
     * From Android 11 the system will not show a dialog for it at all — the
     * request resolves immediately as denied and the reader has to grant it in
     * settings. So a refusal opens the app's settings page rather than leaving
     * a switch that silently does nothing.
     */
    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) state.applyAlertSettings() else openAppSettings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        state = ViewModelProvider(this, AppState.Factory(this))[AppState::class.java]
        state.start()

        hasNotificationPermission = checkNotificationPermission()
        if (state.location.isAuthorized) state.location.refresh()

        setContent {
            RootScreen(
                state = state,
                hasNotificationPermission = hasNotificationPermission,
                onCall = ::dial,
                onOpenUrl = ::openUrl,
                onOpenMapQuery = ::openMapQuery,
                onRequestLocation = ::requestLocation,
                onRequestNotifications = ::requestNotifications,
                onRequestBackgroundLocation = ::requestBackgroundLocation,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        hasNotificationPermission = checkNotificationPermission()
        state.onResume()
    }

    private fun checkNotificationPermission(): Boolean {
        // Runtime notification permission only exists from Android 13; before
        // that, having the channel is enough.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Geofencing needs foreground location before background location can even
     * be asked for, so this asks in that order rather than firing both at once
     * and having the second refused out of hand.
     */
    private fun requestBackgroundLocation() {
        if (!state.location.isAuthorized) {
            locationPermissionLauncher.launch(state.location.requiredPermissions)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            state.applyAlertSettings()
        }
    }

    private fun openAppSettings() {
        startSafely(
            Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            )
        )
    }

    private fun requestLocation() {
        if (state.location.isAuthorized) {
            state.location.refresh()
        } else {
            locationPermissionLauncher.launch(state.location.requiredPermissions)
        }
    }

    /**
     * Opens the dialler rather than placing the call.
     *
     * `ACTION_CALL` would need the CALL_PHONE permission and would dial 999 the
     * instant the button was pressed. Handing the number to the dialler keeps
     * the last step with the person, which is the right place for it.
     */
    private fun dial(number: String) {
        startSafely(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    }

    private fun openUrl(url: String) {
        startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    /**
     * Hands a place name to whichever maps app the reader has.
     *
     * A `geo:` search rather than a coordinate: hospital coordinates go stale
     * and can send someone to the wrong side of a city, where the name resolves
     * live against a map the reader's own app keeps up to date.
     */
    private fun openMapQuery(query: String) {
        val encoded = Uri.encode(query)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded"))
        if (intent.resolveActivity(packageManager) != null) {
            startSafely(intent)
        } else {
            // No maps app installed — fall back to the web, which every device has.
            openUrl("https://www.google.com/maps/search/?api=1&query=$encoded")
        }
    }

    private fun startSafely(intent: Intent) {
        runCatching { startActivity(intent) }
    }
}
