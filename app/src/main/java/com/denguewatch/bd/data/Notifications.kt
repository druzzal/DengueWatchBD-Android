package com.denguewatch.bd.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.denguewatch.bd.MainActivity
import com.denguewatch.bd.R
import com.denguewatch.bd.i18n.Localization
import com.denguewatch.bd.i18n.PlaceNames
import com.denguewatch.bd.i18n.displayName
import com.denguewatch.bd.i18n.guidanceKey
import com.denguewatch.bd.i18n.headlineKey
import com.denguewatch.bd.i18n.labelKey
import com.denguewatch.bd.model.Area
import com.denguewatch.bd.model.RiskLevel
import java.util.Calendar

/**
 * Local notifications only — nothing is registered with a push server, and no
 * location or identity leaves the phone. Text is localised at the moment of
 * posting, from the language stored on the device, so an alert raised by a
 * background worker still arrives in the reader's own language.
 *
 * Two channels rather than one, because the two alerts differ in kind. A rise
 * in your own area is news you should see today; crossing into a high-risk area
 * is something to act on now, and Android should be allowed to treat it that
 * way.
 */
object Notifications {

    const val CHANNEL_RISK = "risk-alerts"
    const val CHANNEL_ENTRY = "area-entry"

    private const val KEY_LAST_RISK_ALERT = "lastRiskAlertDay"
    private const val KEY_LAST_GEOFENCE_PREFIX = "lastGeofenceAlert."

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences("dengue_watch_prefs", Context.MODE_PRIVATE)

    /**
     * Channels must exist before anything is posted, and creating one twice is
     * a no-op, so this is safe to call on every launch and from a worker.
     */
    fun createChannels(context: Context) {
        // No SDK check: minSdk is 26, which is where channels arrived.
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val loc = Localization(context)

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RISK,
                loc.t("alerts.title"),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = loc.t("alerts.footer") }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ENTRY,
                loc.t("geo.section"),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = loc.t("geo.footer") }
        )
    }

    /**
     * Whether the app may post at all.
     *
     * Checked before every post rather than assumed: the reader can revoke the
     * permission at any time, and a worker that ignored that would throw.
     */
    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            // SINGLE_TOP with CLEAR_TOP brings the running activity forward.
            // CLEAR_TOP alone finishes and recreates it, which threw away a
            // half-entered reading or symptom check behind the alert.
            Intent(context, MainActivity::class.java)
                .setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /**
     * The once-a-day gate both alerts share: due unless one already went out
     * on or after the start of today. A stored day later than today — a clock
     * moved backwards — still counts as sent, so a wrong clock can delay an
     * alert by a day but never repeat one.
     */
    internal fun isDueToday(lastAlertDayStart: Long, todayStart: Long): Boolean =
        lastAlertDayStart < todayStart

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    // MARK: - Home area risk

    /**
     * Fires at most once a day, and only when the home area has actually
     * reached the band the reader asked to hear about.
     *
     * The daily gate is the whole reason anyone keeps these switched on. DGHS
     * publishes every day and a division can sit in the same band for weeks; an
     * alert on every refresh would be noise within a week, and the reader would
     * turn off the one channel that might matter later.
     */
    fun raiseRiskAlertIfNeeded(
        context: Context,
        area: Area,
        threshold: RiskLevel,
    ): Boolean {
        if (area.risk.ordinal < threshold.ordinal) return false
        if (!canPost(context)) return false

        val today = startOfToday()
        val store = prefs(context)
        if (!isDueToday(store.getLong(KEY_LAST_RISK_ALERT, 0L), today)) return false
        store.edit().putLong(KEY_LAST_RISK_ALERT, today).apply()

        val loc = Localization(context)
        val name = area.displayName(loc.language)
        // A newline, not a space. The figures line ends on a number and the
        // guidance begins with a word, so joining them with a space produced
        // "প্রতি লাখে ১৮.৬ সংক্রমণ ছড়াচ্ছে" — which reads as "18.6 infections"
        // rather than as a rate followed by a separate sentence. BigTextStyle
        // honours the break, so the two stay apart in the shade.
        val body = loc.t(
            "dash.area.detail",
            loc.num(area.lastWeekCases),
            loc.decimal(area.incidencePer100k),
        ) + "\n" + loc.t(area.risk.guidanceKey)

        post(
            context = context,
            channel = CHANNEL_RISK,
            id = ("risk-" + area.code).hashCode(),
            title = "$name: ${loc.t(area.risk.headlineKey)}",
            body = body,
        )
        return true
    }

    // MARK: - Entering a high-risk area

    /**
     * Raised when the device crosses into a monitored high-risk area.
     *
     * Takes the persisted snapshot rather than an [Area], because this runs from
     * a broadcast where the feed has not loaded and there is no store to ask.
     *
     * Rate-limited per area per day, so a commute across a boundary does not
     * produce a stream of notifications.
     */
    fun raiseGeofenceAlert(context: Context, area: WatchedArea): Boolean {
        if (!canPost(context)) return false

        val key = KEY_LAST_GEOFENCE_PREFIX + area.code
        val today = startOfToday()
        val store = prefs(context)
        if (!isDueToday(store.getLong(key, 0L), today)) return false
        store.edit().putLong(key, today).apply()

        val loc = Localization(context)
        val name = PlaceNames.area(area.code, area.name, loc.language)

        post(
            context = context,
            channel = CHANNEL_ENTRY,
            id = ("geo-" + area.code).hashCode(),
            title = loc.t("geo.notification.title", name),
            body = loc.t(
                "geo.notification.body",
                name,
                loc.t(area.risk.labelKey),
                loc.num(area.lastWeekCases),
            ),
            highPriority = true,
        )
        return true
    }

    // MARK: - Weekly digest

    /** Posted by [WeeklyDigestWorker]; the schedule lives there. */
    fun postWeeklyDigest(context: Context, store: DengueStore) {
        if (!canPost(context)) return
        val loc = Localization(context)
        val body = loc.t(
            "dash.home.detail",
            loc.num(store.last7Cases),
            loc.decimal(store.nationalIncidencePer100k),
        )
        post(
            context = context,
            channel = CHANNEL_RISK,
            id = "weekly-digest".hashCode(),
            title = loc.t("alerts.weekly"),
            body = body,
        )
    }

    private fun post(
        context: Context,
        channel: String,
        id: Int,
        title: String,
        body: String,
        highPriority: Boolean = false,
    ) {
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            // Without this the body is one clipped line. These messages carry a
            // case count and a piece of advice; both need to survive.
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setPriority(
                if (highPriority) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .build()

        // Checked inline, not via canPost(). Every caller already gates on that
        // helper, but the check has to be visible at the call site: lint cannot
        // see through a function boundary, and neither can a reader skimming
        // this. The reader can also revoke the permission between the two
        // checks, which is why runCatching stays as the backstop.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }
}
