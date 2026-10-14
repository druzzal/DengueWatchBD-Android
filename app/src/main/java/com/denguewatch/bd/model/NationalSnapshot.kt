package com.denguewatch.bd.model

/**
 * The figures the home screen leads with, and — just as important — which of
 * them are actually known.
 *
 * Every one of these can be missing on a bad feed day, and every one has a zero
 * that means something real. "No deaths in the last 24 hours" is good news
 * worth showing. "We could not read the deaths figure" is not news at all, and
 * rendering the second as the first is the one mistake a surveillance app does
 * not get to make.
 *
 * The store's own roll-ups cannot tell the two apart: they sum an empty series
 * and hand back 0. So each figure here is nullable, and the card leaves out a
 * tile it has nothing for rather than printing a confident zero.
 *
 * The same distinction already exists in [DengueStore.hotspotsToMonitor]; this
 * puts it on the home screen too.
 */
data class NationalSnapshot(
    /** Cases since the start of the season. */
    val seasonCases: Int?,
    /** Deaths since the start of the season. */
    val seasonDeaths: Int?,
    /** The first day of the daily series, for "since 1 Jan". */
    val seasonStartMillis: Long?,
    /** The last seven days. */
    val weekCases: Int?,
    /**
     * How that compares with the seven before it. Null when there is no earlier
     * week to compare against — a season three days old has no "last week", and
     * 0 → 40 is not "up 100%".
     */
    val weeklyChange: Double?,
    /** What DGHS leads its daily release with. */
    val last24Cases: Int?,
    val last24Deaths: Int?,
    /**
     * Null when there is no area breakdown at all, which is a different thing
     * from no area being high risk. An empty breakdown says "we cannot tell";
     * zero hotspots says "nothing is high right now". They print identically if
     * you let them.
     */
    val highRiskAreas: Int?,
    /**
     * The denominator the app can actually speak to: the reporting areas in the
     * feed, not the 64 districts of Bangladesh.
     */
    val reportingAreas: Int,
) {
    /**
     * How many tiles the snapshot will actually draw. Each is omitted when its
     * figure is unknown, so the count is not a constant.
     */
    val tileCount: Int
        get() = listOf(last24Cases, weekCases, seasonCases, seasonDeaths, highRiskAreas)
            .count { it != null }

    companion object {
        /**
         * Built from the pieces rather than from the store, so the rules above
         * can be tested without standing up a feed.
         *
         * @param headline the DGHS summary block, which is the figure of record.
         * @param national the daily series, used only where the headline is silent.
         * @param areas the area breakdown; empty means "no breakdown", not "no hotspots".
         */
        fun from(
            headline: FeedHeadline?,
            national: List<DailyPoint>,
            areas: List<Area>,
        ): NationalSnapshot {
            // The headline speaks first; the series is only a fallback for what
            // the headline leaves out. Summing an empty series gives 0, which
            // would be a figure we never received dressed up as a figure of none.
            val fromSeries = { pick: (DailyPoint) -> Int ->
                if (national.isEmpty()) null else national.sumOf(pick)
            }
            val week = if (national.isEmpty()) null else national.takeLast(7).sumOf { it.cases }
            val previousWeek = national.dropLast(7).takeLast(7).sumOf { it.cases }

            return NationalSnapshot(
                seasonCases = headline?.ytdCases ?: fromSeries { it.cases },
                seasonDeaths = headline?.ytdDeaths ?: fromSeries { it.deaths },
                seasonStartMillis = national.firstOrNull()?.dateMillis,
                weekCases = week,
                weeklyChange = Series.change(previousWeek, week ?: 0),
                last24Cases = headline?.last24Cases ?: national.lastOrNull()?.cases,
                last24Deaths = headline?.last24Deaths ?: national.lastOrNull()?.deaths,
                highRiskAreas = if (areas.isEmpty()) {
                    null
                } else {
                    areas.count { it.risk >= RiskLevel.HIGH }
                },
                reportingAreas = areas.size,
            )
        }
    }
}

/**
 * How many columns a grid of stat tiles should use, ported from iOS.
 *
 * Four columns and five tiles leaves one stranded on a row of its own. Picking
 * a width that divides the tiles more evenly is a better answer than either
 * stretching the last tile or padding the row with an empty one.
 */
object StatGrid {
    fun columns(tiles: Int, wide: Boolean): Int {
        val preferred = if (wide) 4 else 2
        if (tiles <= 0) return preferred
        // Walk down from the preferred width to the first that does not leave
        // a single tile alone on the last row.
        return (preferred downTo 2).firstOrNull { tiles % it != 1 } ?: preferred
    }
}
