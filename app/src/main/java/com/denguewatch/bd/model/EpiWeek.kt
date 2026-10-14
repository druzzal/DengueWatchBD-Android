package com.denguewatch.bd.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * DGHS's epidemiological weeks, which follow the MMWR convention: Sunday to
 * Saturday, with week 1 the first week holding at least four days of the year.
 * Checked against the feed — summing the daily series over Sunday-to-Saturday
 * windows reproduces `affected_in_division_by_week` to the case.
 */
object EpiWeek {

    /**
     * How many of the weekly buckets [labels] describes are complete, given the
     * date of the newest daily figure.
     *
     * DGHS publishes the week in progress alongside the finished ones. On a
     * Tuesday that bucket holds three days, so reading it as a week made every
     * area look as if cases had roughly halved, and its two-week incidence —
     * the figure behind the risk band — was really ten days' worth. When the
     * last label is the week the report date falls in, and that date is not
     * its Saturday, the week is dropped. Anything that cannot be matched with
     * confidence is left alone.
     */
    fun completeCount(labels: List<String>, reportDayMillis: Long?): Int {
        if (labels.isEmpty() || reportDayMillis == null) return labels.size
        val reportDate = Instant.ofEpochMilli(reportDayMillis).atZone(ZoneOffset.UTC).toLocalDate()
        if (reportDate.dayOfWeek == DayOfWeek.SATURDAY) return labels.size
        val lastWeek = labels.last().trim().removePrefix("W").toIntOrNull() ?: return labels.size
        return if (lastWeek == number(reportDate)) labels.size - 1 else labels.size
    }

    /** The MMWR week number [date] falls in. */
    fun number(date: LocalDate): Int {
        val weekStart = sunday(date)
        // A week belongs to the year its Wednesday falls in, which is the same
        // as saying it holds at least four of that year's days.
        val year = weekStart.plusDays(3).year
        val jan1 = LocalDate.of(year, 1, 1)
        var firstWeek = sunday(jan1)
        if (firstWeek.plusDays(3).year < year) firstWeek = firstWeek.plusWeeks(1)
        return ChronoUnit.WEEKS.between(firstWeek, weekStart).toInt() + 1
    }

    private fun sunday(date: LocalDate): LocalDate =
        date.minusDays((date.dayOfWeek.value % 7).toLong())
}
