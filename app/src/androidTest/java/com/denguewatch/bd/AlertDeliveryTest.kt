package com.denguewatch.bd

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.denguewatch.bd.data.Geofencing
import com.denguewatch.bd.data.Notifications
import com.denguewatch.bd.data.WatchedArea
import com.denguewatch.bd.data.WatchedAreaStore
import com.denguewatch.bd.model.Area
import com.denguewatch.bd.model.Division
import com.denguewatch.bd.model.RiskLevel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The alerts as Android actually delivers them.
 *
 * The decisions behind them — once a day, only at or above the reader's band —
 * are unit-tested as plain functions. This checks the rest on a device: that a
 * notification really lands in the shade, on the right channel, in the
 * reader's language, and that the daily gate holds against the real
 * preferences file. And that arming the entry warning records what happened,
 * which is what the settings screen reports back to the reader.
 */
@RunWith(AndroidJUnit4::class)
class AlertDeliveryTest {

    private lateinit var context: Context
    private lateinit var shade: NotificationManager

    /** An area whose two-week rate puts it in [risk]'s band. */
    private fun area(code: String, name: String, risk: RiskLevel): Area {
        // 1,000 thousand people, so cases per fortnight are the rate per 100,000 × 10.
        val perWeek = when (risk) {
            RiskLevel.LOW -> 10
            RiskLevel.MODERATE -> 60
            RiskLevel.HIGH -> 200
            RiskLevel.SEVERE -> 500
        }
        return Area(
            code = code, name = name, division = Division.DHAKA,
            latitude = 23.8, longitude = 90.4, populationThousands = 1_000,
            seasonCases = perWeek * 10, seasonDeaths = 0,
            weeklyCases = listOf(perWeek, perWeek, perWeek),
            weeklyIsApportioned = false, geofenceRadiusMeters = 8_000.0,
        ).also { assertEquals("test area is in the intended band", risk, it.risk) }
    }

    private val prefs get() = context.getSharedPreferences("dengue_watch_prefs", Context.MODE_PRIVATE)

    @Before
    fun cleanShade() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        context = instrumentation.targetContext
        val ui = instrumentation.uiAutomation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ui.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        ui.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        ui.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        shade = context.getSystemService(NotificationManager::class.java)
        shade.cancelAll()
        // English, and no alert sent yet today.
        prefs.edit().clear().putString("appLanguage", "en").commit()
        Notifications.createChannels(context)
    }

    @After
    fun tidy() {
        shade.cancelAll()
        prefs.edit().clear().commit()
        WatchedAreaStore.clear(context)
    }

    private fun posted(channel: String) =
        shade.activeNotifications.filter { it.notification.channelId == channel }

    @Test
    fun theHomeAreaAlertIsPostedOnceADay() {
        val dhaka = area("DNCC", "Dhaka North City", RiskLevel.HIGH)

        assertTrue(Notifications.raiseRiskAlertIfNeeded(context, dhaka, RiskLevel.HIGH))
        val shown = posted(Notifications.CHANNEL_RISK).single()
        val title = shown.notification.extras.getString("android.title").orEmpty()
        assertTrue("the title names the area: $title", title.startsWith("Dhaka North City"))

        assertFalse("a second time today is held back",
                    Notifications.raiseRiskAlertIfNeeded(context, dhaka, RiskLevel.HIGH))
        assertEquals(1, posted(Notifications.CHANNEL_RISK).size)
    }

    @Test
    fun anAreaBelowTheChosenBandRaisesNothing() {
        assertFalse(Notifications.raiseRiskAlertIfNeeded(
            context, area("SYL", "Sylhet", RiskLevel.MODERATE), RiskLevel.HIGH))
        assertTrue(posted(Notifications.CHANNEL_RISK).isEmpty())
    }

    @Test
    fun theAlertArrivesInTheReadersLanguage() {
        prefs.edit().putString("appLanguage", "bn").commit()
        assertTrue(Notifications.raiseRiskAlertIfNeeded(
            context, area("DNCC", "Dhaka North City", RiskLevel.SEVERE), RiskLevel.HIGH))
        val title = posted(Notifications.CHANNEL_RISK).single()
            .notification.extras.getString("android.title").orEmpty()
        assertTrue("a Bangla title: $title", title.any { it in 'ঀ'..'৿' })
    }

    @Test
    fun anEntryAlertIsPostedOncePerAreaPerDay() {
        val north = WatchedArea.from(area("DNCC", "Dhaka North City", RiskLevel.HIGH))
        val south = WatchedArea.from(area("DSCC", "Dhaka South City", RiskLevel.HIGH))

        assertTrue(Notifications.raiseGeofenceAlert(context, north))
        assertFalse("the same area again today", Notifications.raiseGeofenceAlert(context, north))
        assertTrue("a different area is news", Notifications.raiseGeofenceAlert(context, south))
        assertEquals(2, posted(Notifications.CHANNEL_ENTRY).size)
    }

    @Test
    fun armingWithNothingHighSaysSo() {
        Geofencing.arm(context, emptyList())
        assertEquals(Geofencing.ARMED_NONE, Geofencing.status(context))
        assertTrue(WatchedAreaStore.load(context).isEmpty())
    }

    @Test
    fun armingAnAreaRecordsTheOutcome() {
        val dhaka = area("DNCC", "Dhaka North City", RiskLevel.HIGH)
        prefs.edit().putString("geofence.status", "pending").commit()

        Geofencing.arm(context, listOf(dhaka))

        // Registration answers asynchronously, and on a device without a
        // location provider it is refused. Either way the outcome must be
        // recorded: a switch reading "on" with nothing watching was the bug.
        val deadline = System.currentTimeMillis() + 15_000
        while (Geofencing.status(context) == "pending" && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
        }
        val status = Geofencing.status(context)
        if (status == "1") {
            assertEquals(listOf("DNCC"), WatchedAreaStore.load(context).map { it.code })
        } else {
            assertTrue("an outcome was recorded: $status", status.startsWith(Geofencing.ARMED_FAILED))
            assertTrue("nothing claims to be watched", WatchedAreaStore.load(context).isEmpty())
        }
    }
}
