package com.denguewatch.bd

import com.denguewatch.bd.model.EpiWeek
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The week in progress must not be read as a finished week. With the report of
 * Tuesday 13 October 2026, W41 held three days and every area showed roughly
 * -47% week on week while national cases were rising.
 */
class EpiWeekTest {

    private fun millis(date: String) =
        LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private val weeks = (1..41).map { "W$it" }

    @Test
    fun `week numbers follow MMWR`() {
        // 1 January 2026 is a Thursday, so week 1 starts on Sunday 4 January.
        assertEquals(1, EpiWeek.number(LocalDate.parse("2026-01-04")))
        assertEquals(1, EpiWeek.number(LocalDate.parse("2026-01-10")))
        assertEquals(41, EpiWeek.number(LocalDate.parse("2026-10-11")))
        assertEquals(41, EpiWeek.number(LocalDate.parse("2026-10-17")))
        assertEquals(42, EpiWeek.number(LocalDate.parse("2026-10-18")))
        // Those three January days belong to the last week of 2025.
        assertEquals(53, EpiWeek.number(LocalDate.parse("2026-01-01")))
        // 2025 began on a Wednesday, so its week 1 began in December 2024.
        assertEquals(1, EpiWeek.number(LocalDate.parse("2024-12-29")))
    }

    @Test
    fun `the week in progress is dropped`() {
        assertEquals(40, EpiWeek.completeCount(weeks, millis("2026-10-13")))
        assertEquals(40, EpiWeek.completeCount(weeks, millis("2026-10-11")))
    }

    @Test
    fun `a week reported through its Saturday is kept`() {
        assertEquals(41, EpiWeek.completeCount(weeks, millis("2026-10-17")))
    }

    @Test
    fun `weeks are kept when the report date does not match the last label`() {
        // The weekly chart lagging the daily one must not cost a finished week.
        assertEquals(41, EpiWeek.completeCount(weeks, millis("2026-10-20")))
        assertEquals(41, EpiWeek.completeCount(weeks, null))
        assertEquals(2, EpiWeek.completeCount(listOf("A", "B"), millis("2026-10-13")))
        assertEquals(0, EpiWeek.completeCount(emptyList(), millis("2026-10-13")))
    }
}
