package com.denguewatch.bd

import com.denguewatch.bd.data.FeedDate
import com.denguewatch.bd.i18n.Localization
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How old a report is, in the calendar days the feed dates it by — the same
 * count iOS makes, so the two apps say "yesterday" about the same report.
 */
class ReportAgeTest {

    private val report = FeedDate.iso("2026-10-16")!!

    @Test
    fun `a report is today, yesterday, or some days old`() {
        assertEquals(0, Localization.reportAgeInDays(report, LocalDate.of(2026, 10, 16)))
        assertEquals(1, Localization.reportAgeInDays(report, LocalDate.of(2026, 10, 17)))
        assertEquals(5, Localization.reportAgeInDays(report, LocalDate.of(2026, 10, 21)))
    }

    @Test
    fun `a report dated ahead of the phone's clock counts as today`() {
        assertEquals(0, Localization.reportAgeInDays(report, LocalDate.of(2026, 10, 15)))
    }
}
