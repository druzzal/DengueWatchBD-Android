package com.denguewatch.bd

import com.denguewatch.bd.model.DailyPoint
import com.denguewatch.bd.model.FeedHeadline
import com.denguewatch.bd.model.NationalSnapshot
import com.denguewatch.bd.model.StatGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Telling "none" from "we do not know".
 *
 * The home screen used to read these off the store, whose roll-ups sum an empty
 * series and hand back 0. On a day DGHS published no deaths figure the card
 * said "0 deaths" — which is good news the app had no business delivering.
 */
class NationalSnapshotTest {

    private fun day(cases: Int, deaths: Int) = DailyPoint(0L, cases, deaths)

    @Test
    fun `an absent figure is unknown, not zero`() {
        val snapshot = NationalSnapshot.from(
            headline = FeedHeadline(),
            national = emptyList(),
            areas = emptyList(),
        )
        assertNull(snapshot.seasonCases)
        assertNull(snapshot.seasonDeaths)
        assertNull(snapshot.weekCases)
        assertNull(snapshot.last24Cases)
        assertNull(snapshot.last24Deaths)
        assertNull(snapshot.highRiskAreas)
    }

    @Test
    fun `a real zero is still reported`() {
        // No deaths yesterday is news worth showing, and it must survive the
        // same path that hides an unknown.
        val snapshot = NationalSnapshot.from(
            headline = FeedHeadline(ytdCases = 90_356, ytdDeaths = 0, last24Deaths = 0),
            national = listOf(day(12, 0)),
            areas = emptyList(),
        )
        assertEquals(0, snapshot.seasonDeaths)
        assertEquals(0, snapshot.last24Deaths)
    }

    @Test
    fun `the headline is the figure of record, the series only a fallback`() {
        val snapshot = NationalSnapshot.from(
            headline = FeedHeadline(ytdCases = 90_356, last24Cases = 1_996),
            national = listOf(day(1, 0), day(2, 0)),
            areas = emptyList(),
        )
        assertEquals(90_356, snapshot.seasonCases)
        assertEquals(1_996, snapshot.last24Cases)
        // Silent in the headline, so the series answers.
        assertEquals(0, snapshot.seasonDeaths)
    }

    @Test
    fun `no area breakdown is not the same as no hotspots`() {
        val none = NationalSnapshot.from(FeedHeadline(), emptyList(), emptyList())
        assertNull("an empty breakdown cannot say how many areas are high", none.highRiskAreas)
    }

    /**
     * The weekly tile is a rolling seven days, and DGHS's published weekly
     * figure is not.
     *
     * On 7 October 2026 the dashboard's W40 tile read 7,847 — Sunday to
     * Wednesday, four days of a week still running — while the last seven days
     * held 12,596. Both are right about different windows. The tile used to be
     * labelled "Cases this week" beside an official-source badge, which invited
     * the reader to check it against the dashboard and find the app off by
     * sixty per cent.
     *
     * The label now says seven days. This pins the arithmetic to match it.
     */
    @Test
    fun `the weekly figure is seven days and not the published EPI week`() {
        val fortnight = listOf(
            900, 950, 1_000, 1_050, 1_100, 1_150, 1_200,
            2_089, 1_902, 1_996, 1_860, 1_500, 1_600, 1_700,
        )
        val published = FeedHeadline(epiWeek = "W40", weekCases = 7_847, weekDeaths = 33)
        val snapshot = NationalSnapshot.from(
            headline = published,
            national = fortnight.map { day(it, 0) },
            areas = emptyList(),
        )
        assertEquals(fortnight.takeLast(7).sum(), snapshot.weekCases)
        assertNotEquals(
            "the published EPI week is a different window and must not leak in",
            published.weekCases,
            snapshot.weekCases,
        )
    }

    @Test
    fun `a week with nothing before it has no comparison`() {
        val snapshot = NationalSnapshot.from(
            headline = FeedHeadline(),
            national = listOf(day(40, 0)),
            areas = emptyList(),
        )
        assertEquals(40, snapshot.weekCases)
        assertNull("0 to 40 is not a 100% rise", snapshot.weeklyChange)
    }

    @Test
    fun `a missing figure takes its tile with it`() {
        val none = NationalSnapshot.from(FeedHeadline(), emptyList(), emptyList())
        assertEquals(0, none.tileCount)
        assertNull(none.seasonStartMillis)

        val some = NationalSnapshot.from(
            headline = FeedHeadline(ytdCases = 90_356, last24Cases = 1_996),
            national = listOf(DailyPoint(1_000L, 1, 0), DailyPoint(2_000L, 2, 0)),
            areas = emptyList(),
        )
        // 24 hours, the week, season cases and season deaths; no area breakdown.
        assertEquals(4, some.tileCount)
        assertEquals(1_000L, some.seasonStartMillis)
        assertEquals(0, some.reportingAreas)
    }

    @Test
    fun `the grid never strands a single tile when it can help it`() {
        // Ported from the iOS StatGrid rule.
        assertEquals(2, StatGrid.columns(tiles = 4, wide = false))
        assertEquals(2, StatGrid.columns(tiles = 5, wide = false))
        assertEquals(3, StatGrid.columns(tiles = 5, wide = true))
        assertEquals(4, StatGrid.columns(tiles = 4, wide = true))
        assertEquals(2, StatGrid.columns(tiles = 0, wide = false))
    }
}
