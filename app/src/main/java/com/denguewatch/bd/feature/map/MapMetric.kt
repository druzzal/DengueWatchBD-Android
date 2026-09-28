package com.denguewatch.bd.feature.map

import com.denguewatch.bd.model.Area

/**
 * What a circle's area stands for on the risk map.
 *
 * The two readings answer different questions and neither is sufficient alone.
 * [TOTAL] is where the cases are, which is where hospitals need beds; it always
 * favours the big cities, because that is where the people are. [RATE] is how
 * likely a given resident is to be affected, which is what a reader asking
 * about their own street actually wants, and it lets a city corporation be
 * compared with a whole division.
 */
enum class MapMetric {
    TOTAL,
    RATE;

    private val slug: String get() = name.lowercase()

    val labelKey: String get() = "map.metric.$slug"

    val explanationKey: String get() = "map.explain.$slug"

    /** The figure the circle is sized from. */
    fun value(area: Area): Double = when (this) {
        TOTAL -> area.seasonCases.toDouble()
        RATE -> area.incidencePer100k
    }
}
