package com.denguewatch.bd.data

import android.content.Context
import com.denguewatch.bd.model.Area
import com.denguewatch.bd.model.RiskLevel
import org.json.JSONArray
import org.json.JSONObject

/**
 * What the app needs to raise an entry alert, without any of the rest of the app.
 *
 * When Android delivers a geofence transition the process may have been started
 * for that broadcast alone: no activity, no view model, no loaded feed. The
 * alert has to be composed from something already on disk. This is that
 * something — written whenever the geofences are armed, read when a region
 * fires.
 */
data class WatchedArea(
    val code: String,
    /**
     * English name. The Bengali one is looked up by [code] at fire time, so a
     * reader who switches language still gets the notification in it.
     */
    val name: String,
    val riskOrdinal: Int,
    val lastWeekCases: Int,
    /**
     * Enough to rebuild the region without the feed, so monitoring can be
     * re-armed the moment background location is granted rather than waiting
     * for the next time someone opens the app.
     */
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
) {
    val risk: RiskLevel get() = RiskLevel.entries.getOrNull(riskOrdinal) ?: RiskLevel.HIGH

    fun toJson(): JSONObject = JSONObject().apply {
        put("code", code)
        put("name", name)
        put("risk", riskOrdinal)
        put("lastWeekCases", lastWeekCases)
        put("latitude", latitude)
        put("longitude", longitude)
        put("radiusMeters", radiusMeters)
    }

    companion object {
        fun from(area: Area) = WatchedArea(
            code = area.code,
            name = area.name,
            riskOrdinal = area.risk.ordinal,
            lastWeekCases = area.lastWeekCases,
            latitude = area.latitude,
            longitude = area.longitude,
            radiusMeters = area.geofenceRadiusMeters,
        )

        fun from(json: JSONObject) = WatchedArea(
            code = json.optString("code"),
            name = json.optString("name"),
            riskOrdinal = json.optInt("risk", RiskLevel.HIGH.ordinal),
            lastWeekCases = json.optInt("lastWeekCases"),
            latitude = json.optDouble("latitude"),
            longitude = json.optDouble("longitude"),
            radiusMeters = json.optDouble("radiusMeters"),
        )
    }
}

/** The armed set, on disk. */
object WatchedAreaStore {

    private const val KEY = "geofence.watchedAreas"

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences("dengue_watch_prefs", Context.MODE_PRIVATE)

    fun save(context: Context, areas: List<WatchedArea>) {
        val array = JSONArray().apply { areas.forEach { put(it.toJson()) } }
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }

    fun load(context: Context): List<WatchedArea> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.let { WatchedArea.from(it) }
            }
        }.getOrDefault(emptyList())
    }

    fun area(context: Context, code: String): WatchedArea? =
        load(context).firstOrNull { it.code == code }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }
}
