package com.denguewatch.bd

import com.denguewatch.bd.model.HealthLog
import com.denguewatch.bd.model.LabReport
import com.denguewatch.bd.model.MeasureStatus
import com.denguewatch.bd.model.VitalsEntry
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Turning two stores into one record read by day.
 *
 * A reader handing this to a doctor thinks in days — "Tuesday my temperature
 * was 39.2 and my platelets were 96" — not in stores. The grouping is what
 * makes that reading possible, and it is the part that can be quietly wrong:
 * a reading at one in the morning landing on the previous day, or a day's
 * number shifting under it when a new reading arrives.
 */
class HealthLogTest {

    private fun at(daysAgo: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -daysAgo)
        }.timeInMillis

    @Test
    fun `readings and reports taken the same day become one day`() {
        val days = HealthLog.days(
            vitals = listOf(VitalsEntry(dateMillis = at(1, 9), temperature = 39.2)),
            labs = listOf(LabReport(dateMillis = at(1, 15), platelets = 96.0)),
        )
        assertEquals(1, days.size)
        assertEquals(1, days[0].vitals.size)
        assertEquals(1, days[0].labs.size)
        assertEquals("the day holds both records", 2, days[0].itemsNewestFirst.size)
    }

    @Test
    fun `a day reads newest first`() {
        val morning = VitalsEntry(dateMillis = at(0, 8), temperature = 38.0)
        val evening = VitalsEntry(dateMillis = at(0, 20), temperature = 39.0)
        val day = HealthLog.days(vitals = listOf(morning, evening)).single()
        assertEquals(evening.id, day.itemsNewestFirst.first().id)
        assertEquals(morning.id, day.itemsNewestFirst.last().id)
    }

    @Test
    fun `days come back newest first`() {
        val days = HealthLog.days(
            vitals = listOf(
                VitalsEntry(dateMillis = at(3, 12)),
                VitalsEntry(dateMillis = at(1, 12)),
                VitalsEntry(dateMillis = at(2, 12)),
            ),
        )
        assertEquals(3, days.size)
        assertTrue(days[0].dateMillis > days[1].dateMillis)
        assertTrue(days[1].dateMillis > days[2].dateMillis)
    }

    @Test
    fun `a reading in the small hours stays on its own day`() {
        // 01:00 and 23:00 on the same date are one day apart in neither
        // direction — they are the same day, and an hours-based split would
        // put them on two.
        val days = HealthLog.days(
            vitals = listOf(
                VitalsEntry(dateMillis = at(0, 1)),
                VitalsEntry(dateMillis = at(0, 23)),
            ),
        )
        assertEquals(1, days.size)
        assertEquals(2, days.single().vitals.size)
    }

    @Test
    fun `a day with nothing in it is absent, not empty`() {
        // A gap is a day the reader wrote nothing down. Inventing a row for it
        // would suggest they recorded "nothing wrong".
        val days = HealthLog.days(
            vitals = listOf(
                VitalsEntry(dateMillis = at(0, 12)),
                VitalsEntry(dateMillis = at(4, 12)),
            ),
        )
        assertEquals("only the two days that have records", 2, days.size)
        assertTrue(days.none { it.isEmpty })
    }

    @Test
    fun `numbering counts from the oldest so a day keeps its number`() {
        val older = VitalsEntry(dateMillis = at(2, 12))
        val newer = VitalsEntry(dateMillis = at(1, 12))

        val before = HealthLog.days(vitals = listOf(older, newer))
        val numbersBefore = HealthLog.numbers(before)
        val oldestDay = before.last().dateMillis

        // A new reading arrives today — the oldest day must still be "Log 1".
        val after = HealthLog.days(vitals = listOf(older, newer, VitalsEntry(dateMillis = at(0, 12))))
        val numbersAfter = HealthLog.numbers(after)

        assertEquals(1, numbersBefore[oldestDay])
        assertEquals("the oldest day keeps its number", 1, numbersAfter[oldestDay])
        assertEquals(3, numbersAfter[after.first().dateMillis])
    }

    @Test
    fun `the day takes the colour of its worst reading`() {
        val calm = HealthLog.days(
            vitals = listOf(VitalsEntry(dateMillis = at(0, 9), temperature = 36.8, pulse = 72.0)),
        ).single()
        assertEquals(MeasureStatus.NORMAL, calm.worstStatus)

        val bad = HealthLog.days(
            vitals = listOf(VitalsEntry(dateMillis = at(0, 9), temperature = 36.8)),
            labs = listOf(LabReport(dateMillis = at(0, 10), platelets = 18.0)),
        ).single()
        assertTrue(
            "a collapsed platelet count must not read as a normal day",
            bad.worstStatus != MeasureStatus.NORMAL,
        )
    }

    @Test
    fun `the summary figures pick the right reading from the day`() {
        val day = HealthLog.days(
            vitals = listOf(
                VitalsEntry(dateMillis = at(0, 20), temperature = 38.1, systolic = 110.0, diastolic = 70.0),
                VitalsEntry(dateMillis = at(0, 8), temperature = 39.4),
            ),
            labs = listOf(
                LabReport(dateMillis = at(0, 11), platelets = 140.0),
                LabReport(dateMillis = at(0, 17), platelets = 96.0),
            ),
        ).single()

        // The peak temperature, not the latest: a fever's height is the point.
        assertEquals(39.4, day.peakTemperature!!, 0.01)
        // The latest blood pressure, because it is the current one.
        assertEquals(110 to 70, day.latestBloodPressure)
        // The lowest platelet count, which is the one that matters in dengue.
        assertEquals(96.0, day.lowestPlatelets!!, 0.01)
    }

    @Test
    fun `an empty record produces no days`() {
        assertTrue(HealthLog.days(emptyList(), emptyList()).isEmpty())
        assertTrue(HealthLog.numbers(emptyList()).isEmpty())
    }
}
