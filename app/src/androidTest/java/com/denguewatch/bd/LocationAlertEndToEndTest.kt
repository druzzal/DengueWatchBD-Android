package com.denguewatch.bd

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.location.Location
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.denguewatch.bd.data.AreaEntryWatch
import com.denguewatch.bd.data.BundledFeedService
import com.denguewatch.bd.data.DengueStore
import com.denguewatch.bd.data.Geofencing
import com.denguewatch.bd.data.Notifications
import com.denguewatch.bd.data.WatchedAreaStore
import com.denguewatch.bd.model.RiskLevel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The entry warning, end to end, with the phone moved between real places.
 *
 * The fused location provider is put in mock mode and handed positions — the
 * centre of Sylhet, then of Chattogram — so the app goes through the same
 * steps it would on a bus: get a fix, work out the reporting area, decide it
 * is an arrival, and post the notification. Then the same for a real Play
 * Services geofence, which only fires after the reader has stayed inside for
 * the two-minute dwell.
 *
 * Uses the bundled feed and the reader's lowest band, so every area counts as
 * one to warn about and the figures on the day do not decide the outcome.
 */
@RunWith(AndroidJUnit4::class)
class LocationAlertEndToEndTest {

    private lateinit var context: Context
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var shade: NotificationManager
    private lateinit var store: DengueStore

    private val sylhet = 24.90 to 91.87
    private val chattogram = 22.35 to 91.83

    @Before
    fun mockLocation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        context = instrumentation.targetContext
        val ui = instrumentation.uiAutomation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ui.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        ui.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        ui.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        // Lets this app feed the fused provider its own positions.
        // Read to the end: the command runs asynchronously, and asking for mock
        // mode before it has finished is refused on a fresh install.
        ParcelFileDescriptor.AutoCloseInputStream(
            ui.executeShellCommand("appops set ${context.packageName} android:mock_location allow"),
        ).use { it.readBytes() }

        fused = LocationServices.getFusedLocationProviderClient(context)
        Tasks.await(fused.setMockMode(true), 10, TimeUnit.SECONDS)

        shade = context.getSystemService(NotificationManager::class.java)
        shade.cancelAll()
        context.getSharedPreferences("dengue_watch_prefs", Context.MODE_PRIVATE)
            .edit().clear().putString("appLanguage", "en").commit()
        Notifications.createChannels(context)

        store = DengueStore(BundledFeedService(context))
        runBlocking { store.load() }
        assertTrue("the bundled feed has areas", store.areas.isNotEmpty())
    }

    @After
    fun stopMocking() {
        // Guarded: if setting up failed part-way, the failure worth reading is
        // that one, not a second one from tidying what was never made.
        if (::fused.isInitialized) runCatching { Tasks.await(fused.setMockMode(false), 10, TimeUnit.SECONDS) }
        if (!::context.isInitialized) return
        Geofencing.disarm(context)
        WatchedAreaStore.clear(context)
        if (::shade.isInitialized) shade.cancelAll()
        context.getSharedPreferences("dengue_watch_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun moveTo(place: Pair<Double, Double>) {
        val location = Location("test").apply {
            latitude = place.first
            longitude = place.second
            accuracy = 10f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        Tasks.await(fused.setMockLocation(location), 10, TimeUnit.SECONDS)
    }

    private fun entryAlerts() =
        shade.activeNotifications.filter { it.notification.channelId == Notifications.CHANNEL_ENTRY }

    @Test
    fun movingBetweenAreasRaisesTheEntryWarningOnce() = runBlocking {
        moveTo(sylhet)
        assertNull("the first fix only records where the reader is",
                   AreaEntryWatch.check(context, store, RiskLevel.LOW))

        moveTo(chattogram)
        val arrived = AreaEntryWatch.check(context, store, RiskLevel.LOW)
        assertEquals("CHATTOGRAM", arrived?.code)
        val title = entryAlerts().single().notification.extras.getString("android.title").orEmpty()
        assertTrue("the alert names the area: $title", title.contains("Chattogram"))

        assertNull("staying put is not entering", AreaEntryWatch.check(context, store, RiskLevel.LOW))
        assertEquals(1, entryAlerts().size)
    }

    @Test
    fun aGeofenceFiresAfterTheReaderStaysInside() {
        moveTo(sylhet)
        val chattogramArea = store.areas.single { it.code == "CHATTOGRAM" }
        Geofencing.arm(context, listOf(chattogramArea))
        val deadline = System.currentTimeMillis() + 15_000
        while (Geofencing.status(context) == Geofencing.ARMED_NONE && System.currentTimeMillis() < deadline) {
            Thread.sleep(200)
        }
        // A device with no network location cannot register geofences at all.
        // That is a fact about the device, and the location-based check above
        // is the backstop for it; there is nothing further to test here.
        assumeTrue("geofences unavailable here: ${Geofencing.status(context)}", Geofencing.status(context) == "1")

        // Into the fence, and kept there past the two-minute dwell. Positions
        // keep arriving, as they would from a phone in someone's pocket.
        val until = System.currentTimeMillis() + 240_000
        while (entryAlerts().isEmpty() && System.currentTimeMillis() < until) {
            moveTo(chattogram)
            Thread.sleep(5_000)
        }
        val title = entryAlerts().singleOrNull()?.notification?.extras?.getString("android.title").orEmpty()
        assertTrue("the geofence raised the entry alert: '$title'", title.contains("Chattogram"))
    }
}
