package com.denguewatch.bd

import com.denguewatch.bd.model.ChecklistDay
import com.denguewatch.bd.model.PreventionTask
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Yesterday's ticks must not read as today's protection.
 *
 * The checklist's whole claim is that these are *daily* chores. "5 of 5 done"
 * on a morning when nothing has been done is a false all-clear about not being
 * bitten — and the failure mode is a reset that never fires, which looks
 * exactly like a working app right up until the clock rolls over. So the day
 * boundary is tested against stated hours rather than trusted.
 */
class PreventionChecklistTest {

    /** A wall-clock instant, stated rather than relative to when this runs. */
    private fun at(daysAgo: Int, hour: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -daysAgo)
    }.timeInMillis

    private val allIds = PreventionTask.entries.map { it.id }.toSet()

    @Test
    fun `ticks made earlier today still count`() {
        val morning = at(daysAgo = 0, hour = 7)
        val evening = at(daysAgo = 0, hour = 21)
        val restored = ChecklistDay.restore(allIds, ChecklistDay.startOfDay(morning), evening)
        assertEquals(PreventionTask.entries.size, restored.size)
    }

    @Test
    fun `ticks made yesterday do not`() {
        val yesterday = at(daysAgo = 1, hour = 12)
        val today = at(daysAgo = 0, hour = 12)
        assertTrue(ChecklistDay.restore(allIds, ChecklistDay.startOfDay(yesterday), today).isEmpty())
    }

    @Test
    fun `a tick at eleven at night is gone by one in the morning`() {
        // Two hours apart and two different days — exactly what a comparison
        // of elapsed time gets wrong.
        val lateLastNight = at(daysAgo = 1, hour = 23)
        val smallHours = at(daysAgo = 0, hour = 1)

        assertTrue(smallHours - lateLastNight < 3 * 3_600_000L)
        assertFalse(ChecklistDay.isSameDay(ChecklistDay.startOfDay(lateLastNight), smallHours))
        assertTrue(
            ChecklistDay.restore(allIds, ChecklistDay.startOfDay(lateLastNight), smallHours)
                .isEmpty(),
        )
    }

    @Test
    fun `a tick at one in the morning still counts that same evening`() {
        // The other side of the same boundary: being early is not being stale.
        val smallHours = at(daysAgo = 0, hour = 1)
        val evening = at(daysAgo = 0, hour = 22)
        assertTrue(ChecklistDay.isSameDay(ChecklistDay.startOfDay(smallHours), evening))
    }

    @Test
    fun `nothing stored means nothing done`() {
        // A zero day is "never written", not "the epoch", and must not be read
        // as a day that happens to match.
        assertFalse(ChecklistDay.isSameDay(0L, at(daysAgo = 0, hour = 12)))
        assertTrue(ChecklistDay.restore(allIds, 0L, at(daysAgo = 0, hour = 12)).isEmpty())
    }

    @Test
    fun `an id no longer in the build is dropped rather than throwing`() {
        val now = at(daysAgo = 0, hour = 12)
        val restored = ChecklistDay.restore(
            storedIds = setOf("standingWater", "somethingRemovedLater"),
            storedDay = ChecklistDay.startOfDay(now),
            now = now,
        )
        assertEquals(setOf(PreventionTask.STANDING_WATER), restored)
    }

    @Test
    fun `every task has its own id and key`() {
        val ids = PreventionTask.entries.map { it.id }
        assertEquals("ids must be unique — they are what is written to storage", ids.size, ids.toSet().size)
        PreventionTask.entries.forEach {
            assertEquals("checklist.task.${it.id}", it.titleKey)
            assertEquals(it, PreventionTask.from(it.id))
        }
    }
}
