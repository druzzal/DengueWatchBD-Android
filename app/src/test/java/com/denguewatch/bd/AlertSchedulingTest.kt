package com.denguewatch.bd

import com.denguewatch.bd.data.AreaEntryWatch
import com.denguewatch.bd.data.BackgroundRefresh
import com.denguewatch.bd.data.Notifications
import com.denguewatch.bd.model.RiskLevel
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decisions behind the alerts, apart from Android's machinery: when the
 * Sunday digest is due, the once-a-day gate, and what counts as entering an
 * area. Each is a place where getting it slightly wrong turns a useful alert
 * into one the reader switches off.
 */
class AlertSchedulingTest {

    private val dhaka = TimeZone.getTimeZone("Asia/Dhaka")
    private val hour = 3_600_000L

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Calendar =
        Calendar.getInstance(dhaka).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }

    // MARK: - The Sunday digest

    @Test
    fun `the digest is due at nine on the next Sunday`() {
        // 2026-10-12 is a Monday.
        assertEquals(6 * 24 * hour - hour, BackgroundRefresh.millisUntilNextSundayMorning(at(2026, 10, 12, 10)))
        // Saturday night: ten hours to go.
        assertEquals(10 * hour, BackgroundRefresh.millisUntilNextSundayMorning(at(2026, 10, 17, 23)))
    }

    @Test
    fun `on Sunday it is this morning until nine, and next week after`() {
        assertEquals(hour, BackgroundRefresh.millisUntilNextSundayMorning(at(2026, 10, 18, 8)))
        assertEquals(7 * 24 * hour, BackgroundRefresh.millisUntilNextSundayMorning(at(2026, 10, 18, 9)))
        assertEquals(7 * 24 * hour - hour, BackgroundRefresh.millisUntilNextSundayMorning(at(2026, 10, 18, 10)))
    }

    // MARK: - Once a day

    @Test
    fun `an alert goes out once a day`() {
        val today = at(2026, 10, 17, 0).timeInMillis
        assertTrue("never sent", Notifications.isDueToday(0L, today))
        assertTrue("sent yesterday", Notifications.isDueToday(today - 24 * hour, today))
        assertFalse("sent today", Notifications.isDueToday(today, today))
        assertFalse("a clock moved back is not a reason to repeat", Notifications.isDueToday(today + 24 * hour, today))
    }

    // MARK: - Entering an area

    @Test
    fun `the first check only records where the reader is`() {
        assertFalse(AreaEntryWatch.isEntryWorthWarning(null, "DNCC", RiskLevel.SEVERE, RiskLevel.HIGH))
    }

    @Test
    fun `staying in an area is not entering it`() {
        assertFalse(AreaEntryWatch.isEntryWorthWarning("DNCC", "DNCC", RiskLevel.SEVERE, RiskLevel.HIGH))
    }

    @Test
    fun `moving into an area at or above the reader's band warns`() {
        assertTrue(AreaEntryWatch.isEntryWorthWarning("DSCC", "DNCC", RiskLevel.HIGH, RiskLevel.HIGH))
        assertTrue(AreaEntryWatch.isEntryWorthWarning("DSCC", "DNCC", RiskLevel.SEVERE, RiskLevel.MODERATE))
        assertFalse(AreaEntryWatch.isEntryWorthWarning("DSCC", "DNCC", RiskLevel.MODERATE, RiskLevel.HIGH))
    }
}
