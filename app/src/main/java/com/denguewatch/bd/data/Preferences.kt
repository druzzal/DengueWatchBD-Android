package com.denguewatch.bd.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.denguewatch.bd.model.RiskLevel
import kotlin.reflect.KProperty

/**
 * User settings. Small enough to live in shared preferences, and none of it
 * leaves the device.
 *
 * Each property writes through on assignment, so a screen just sets the value
 * and both the UI and the store stay in step — the same shape as the iOS
 * build's `didSet` observers.
 */
class Preferences(context: Context) {

    private val defaults: SharedPreferences = context.applicationContext
        .getSharedPreferences("dengue_watch_prefs", Context.MODE_PRIVATE)

    /**
     * Deliberately a new key, not the old "homeDistrictCode".
     *
     * Geography changed meaning in this version: the app used to store one of
     * 64 district codes and now stores one of 10 reporting areas. Seven of the
     * old codes ("KHULNA", "SYLHET", …) are also valid area codes, so reusing
     * the key would silently promote someone's home district to the whole
     * division without telling them. A new key drops the stale value and asks
     * them to choose again.
     */
    var homeAreaCode: String? by persistedString("homeAreaCode")

    var alertsEnabled: Boolean by persistedBoolean("alertsEnabled", false)

    /** Raise a local alert once the home area reaches this band. */
    var alertThreshold: RiskLevel by persistedEnum("alertThresholdRawValue", RiskLevel.HIGH)

    var weeklyDigestEnabled: Boolean by persistedBoolean("weeklyDigestEnabled", false)

    var hasSeenDisclaimer: Boolean by persistedBoolean("hasSeenDisclaimer", false)

    /** Warn the user when they physically enter a high-risk area. */
    var geofenceAlertsEnabled: Boolean by persistedBoolean("geofenceAlertsEnabled", false)

    // MARK: - Write-through, observable delegates

    /**
     * Backed by Compose state so a read inside composition subscribes to it,
     * and by shared preferences so the value survives the process.
     */
    private inner class Persisted<T>(
        initial: T,
        private val write: (T) -> Unit,
    ) {
        private val state: MutableState<T> = mutableStateOf(initial)

        operator fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            if (state.value == value) return
            state.value = value
            write(value)
        }
    }

    private fun persistedString(key: String) = Persisted(defaults.getString(key, null)) { value ->
        defaults.edit().putString(key, value).apply()
    }

    private fun persistedBoolean(key: String, default: Boolean) =
        Persisted(defaults.getBoolean(key, default)) { value ->
            defaults.edit().putBoolean(key, value).apply()
        }

    private fun persistedEnum(key: String, default: RiskLevel) =
        Persisted(
            RiskLevel.entries.getOrNull(defaults.getInt(key, default.ordinal)) ?: default
        ) { value ->
            defaults.edit().putInt(key, value.ordinal).apply()
        }
}
