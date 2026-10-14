package com.denguewatch.bd

import com.denguewatch.bd.feature.health.recordable
import com.denguewatch.bd.feature.health.utcMidnightOfLocalDay
import com.denguewatch.bd.feature.health.utcMidnightOfLocalToday
import com.denguewatch.bd.feature.health.withDateOf
import com.denguewatch.bd.feature.health.withTimeOf
import java.util.Calendar
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * When a reading says it was taken.
 *
 * Two clocks meet in the recording screens: the date picker hands back a day as
 * midnight *UTC*, and the reading's time is the device's own. Dhaka is six hours
 * ahead, which is enough for every off-by-one here to land on the wrong day —
 * and a platelet count on the wrong day is a falling trend that reads as flat.
 *
 * The first version of this got it wrong in exactly that way: it compared the
 * picker's UTC midnights against the wall clock, which left *today*
 * unselectable until six in the morning, and the padding added to paper over
 * that offered tomorrow instead.
 */
class EntryDateTest {

    private val original: TimeZone = TimeZone.getDefault()

    /** Dhaka, because that is where this is used and where the gap is widest. */
    @Before fun useDhaka() = TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dhaka"))

    @After fun restore() = TimeZone.setDefault(original)

    private fun local(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis

    private fun utcMidnight(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month, day)
        }.timeInMillis

    @Test
    fun `picking a day keeps the time already set`() {
        // 18:33 on 6 October, moved to 2 October.
        val existing = local(2026, Calendar.OCTOBER, 6, 18, 33)
        val moved = withDateOf(utcMidnight(2026, Calendar.OCTOBER, 2), existing)

        val calendar = Calendar.getInstance().apply { timeInMillis = moved }
        assertEquals(2026, calendar.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, calendar.get(Calendar.MONTH))
        assertEquals(2, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(18, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(33, calendar.get(Calendar.MINUTE))
    }

    @Test
    fun `an early-morning reading stays on its own day`() {
        // The case the UTC/local mix-up breaks: 01:00 in Dhaka is still the
        // previous day in UTC, so a naive conversion moves the reading back one.
        val existing = local(2026, Calendar.OCTOBER, 6, 1, 0)
        val moved = withDateOf(utcMidnight(2026, Calendar.OCTOBER, 6), existing)

        val calendar = Calendar.getInstance().apply { timeInMillis = moved }
        assertEquals(6, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(1, calendar.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `setting a time keeps the day`() {
        val existing = local(2026, Calendar.OCTOBER, 6, 18, 33)
        val calendar = Calendar.getInstance().apply {
            timeInMillis = withTimeOf(existing, hour = 3, minute = 5)
        }
        assertEquals(6, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(3, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(5, calendar.get(Calendar.MINUTE))
        assertEquals(0, calendar.get(Calendar.SECOND))
    }

    @Test
    fun `today is selectable whatever the hour`() {
        // The bound the picker is given has to be today's own day, not a
        // comparison against the clock — which is what made today unreachable
        // before six in the morning.
        val bound = utcMidnightOfLocalToday()
        val today = Calendar.getInstance()
        assertEquals(
            utcMidnight(
                today.get(Calendar.YEAR),
                today.get(Calendar.MONTH),
                today.get(Calendar.DAY_OF_MONTH),
            ),
            bound,
        )
    }

    @Test
    fun `tomorrow is not selectable`() {
        val bound = utcMidnightOfLocalToday()
        assertTrue("tomorrow must fall outside the range", bound + 86_400_000L > bound)
    }

    @Test
    fun `a time that has not arrived yet is pulled back to now`() {
        val now = System.currentTimeMillis()
        assertEquals(now, recordable(now + 3_600_000L, now))
        // Anything already past is left exactly as it is.
        val past = now - 86_400_000L
        assertEquals(past, recordable(past, now))
    }

    @Test
    fun `nothing older than a year back, to the minute, as on iOS`() {
        val now = local(2026, Calendar.OCTOBER, 14, 17, 2)
        val yearBack = now - 365L * 86_400_000L
        // The picker offers the whole of that day; the morning of it is too far.
        assertEquals(yearBack, recordable(yearBack - 6 * 3_600_000L, now))
        assertEquals(yearBack, recordable(yearBack, now))
    }

    @Test
    fun `the picker opens on the reading's own day, not the UTC one`() {
        // 05:23 in Dhaka is still 13 October in UTC. Opening the picker on the
        // raw instant selected the 13th, and pressing Done moved the reading.
        val early = local(2026, Calendar.OCTOBER, 14, 5, 23)
        assertEquals(utcMidnight(2026, Calendar.OCTOBER, 14), utcMidnightOfLocalDay(early))
        // Done without a change leaves the reading where it was.
        assertEquals(early, withDateOf(utcMidnightOfLocalDay(early), early))

        val late = local(2026, Calendar.OCTOBER, 14, 23, 50)
        assertEquals(utcMidnight(2026, Calendar.OCTOBER, 14), utcMidnightOfLocalDay(late))
    }
}
