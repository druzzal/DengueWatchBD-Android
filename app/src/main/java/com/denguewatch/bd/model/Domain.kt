package com.denguewatch.bd.model

import kotlin.math.abs
import kotlin.math.floor

// MARK: - Geography
//
// The feed reports geography at division level plus the two Dhaka city
// corporations — ten reporting areas, which is as fine as DGHS publishes on the
// HEOC dashboard. There is no area breakdown to be had from this source.

enum class Division(val label: String) {
    BARISHAL("Barishal"),
    CHATTOGRAM("Chattogram"),
    DHAKA("Dhaka"),
    KHULNA("Khulna"),
    MYMENSINGH("Mymensingh"),
    RAJSHAHI("Rajshahi"),
    RANGPUR("Rangpur"),
    SYLHET("Sylhet");
}

/** One reporting area: a division, or a Dhaka city corporation. */
data class Area(
    val code: String,
    /** Name as the app shows it. `feedCategory` is what DGHS calls it. */
    val name: String,
    val division: Division,
    val latitude: Double,
    val longitude: Double,
    val populationThousands: Int,
    val seasonCases: Int,
    val seasonDeaths: Int,
    /** Cases per EPI week, aligned to `DengueStore.epiWeeks`. */
    val weeklyCases: List<Int>,
    /**
     * True when [weeklyCases] was apportioned from the parent division rather
     * than reported for this area directly — see [Geography.apportionedCodes].
     */
    val weeklyIsApportioned: Boolean,
    /** Roughly half the area's extent, for the entry geofence. */
    val geofenceRadiusMeters: Double,
) {
    val lastWeekCases: Int get() = weeklyCases.lastOrNull() ?: 0
    val previousWeekCases: Int get() = weeklyCases.dropLast(1).lastOrNull() ?: 0

    /** Two most recent EPI weeks — the window the risk bands are calibrated for. */
    val recentCases: Int get() = weeklyCases.takeLast(2).sum()

    /**
     * Week-on-week change as a fraction, e.g. `0.18` for +18%.
     * `null` when the prior week is empty and a ratio would be meaningless.
     */
    val weeklyChange: Double?
        get() {
            if (previousWeekCases <= 0) return null
            return (lastWeekCases.toDouble() - previousWeekCases) / previousWeekCases
        }

    /** Two-week attack rate per 100,000 people — what drives the risk band. */
    val incidencePer100k: Double
        get() {
            if (populationThousands <= 0) return 0.0
            return recentCases.toDouble() / populationThousands * 100
        }

    val risk: RiskLevel get() = RiskLevel.forIncidence(incidencePer100k)

    fun recentWeeks(count: Int): List<Int> = weeklyCases.takeLast(count)
}

/**
 * Where each reporting area sits and how many people live there.
 *
 * Coordinates are division centroids (city corporation centres for DNCC and
 * DSCC) — good enough to drop a pin and to answer "which area am I in", and
 * they degrade gracefully near a boundary.
 *
 * Populations are 2022 BBS census figures in thousands. They are only used as
 * the denominator for incidence, so approximation costs a band boundary at
 * worst, never a headline number.
 */
object Geography {

    data class Definition(
        val code: String,
        /** Exactly the category string DGHS uses in the feed. */
        val feedCategory: String,
        val name: String,
        val division: Division,
        val latitude: Double,
        val longitude: Double,
        val populationThousands: Int,
        /**
         * The series name in `affected_in_division_by_week`, which is spelled
         * differently again ("Barisal") and has no city-corporation rows.
         */
        val weeklySeriesName: String,
        /**
         * Roughly half the area's extent, used for the entry geofence.
         *
         * A single radius cannot serve both kinds of area: a city corporation
         * is about 12 km across where Chattogram division runs nearly 200 km
         * north to south.
         */
        val geofenceRadiusMeters: Double,
    )

    /**
     * Dhaka's three feed rows share one weekly series, so their weekly numbers
     * are apportioned by each one's share of the division's season cases.
     */
    val apportionedCodes = setOf("DHAKA_OUT_CC", "DNCC", "DSCC")

    val definitions = listOf(
        Definition("BARISHAL", "Barishal", "Barishal", Division.BARISHAL,
            22.70, 90.35, 9325, "Barisal", 55_000.0),
        Definition("CHATTOGRAM", "Chattogram", "Chattogram", Division.CHATTOGRAM,
            22.35, 91.83, 33202, "Chattogram", 90_000.0),
        Definition("DHAKA_OUT_CC", "Dhaka (Out of CC)", "Dhaka (outside city)", Division.DHAKA,
            23.95, 90.15, 33935, "Dhaka", 55_000.0),
        Definition("DNCC", "DNCC", "Dhaka North City", Division.DHAKA,
            23.80, 90.40, 5980, "Dhaka", 12_000.0),
        Definition("DSCC", "DSCC", "Dhaka South City", Division.DHAKA,
            23.71, 90.41, 4300, "Dhaka", 12_000.0),
        Definition("KHULNA", "Khulna", "Khulna", Division.KHULNA,
            22.85, 89.50, 17417, "Khulna", 75_000.0),
        Definition("MYMENSINGH", "Mymensingh", "Mymensingh", Division.MYMENSINGH,
            24.75, 90.40, 12368, "Mymensingh", 55_000.0),
        Definition("RAJSHAHI", "Rajshahi", "Rajshahi", Division.RAJSHAHI,
            24.37, 88.60, 20353, "Rajshahi", 70_000.0),
        Definition("RANGPUR", "Rangpur", "Rangpur", Division.RANGPUR,
            25.75, 89.25, 17610, "Rangpur", 65_000.0),
        Definition("SYLHET", "Sylhet", "Sylhet", Division.SYLHET,
            24.90, 91.87, 11048, "Sylhet", 60_000.0),
    )

    fun definition(code: String): Definition? = definitions.firstOrNull { it.code == code }

    /**
     * A coarse outline of Bangladesh, used to decide whether a coordinate is in
     * the country at all — and, on this platform, also to draw the map.
     *
     * Nearest-centroid alone cannot answer containment. The reporting areas are
     * large, so their centres sit closer to several Indian border cities than to
     * parts of Bangladesh itself: Shillong is 75 km from the Sylhet centre and
     * Kolkata 121 km from Khulna's, while St Martin's Island — which *is* in
     * Bangladesh — is 198 km from Chattogram's. No distance threshold separates
     * those, so containment is tested first and distance only guards against a
     * wildly wrong fix.
     *
     * Simplified to about thirty vertices, so it is right to a few kilometres
     * rather than exactly. A town sitting directly on the border can fall the
     * wrong side of it; anywhere further in is classified correctly.
     */
    val outline: List<Pair<Double, Double>> = listOf(
        26.63 to 88.35, 26.35 to 88.90, 26.20 to 89.45, 26.10 to 89.80,
        25.30 to 89.90, 25.35 to 90.60, 25.20 to 91.30, 25.20 to 92.00,
        24.30 to 92.35, 24.00 to 91.60, 23.70 to 91.40, 23.20 to 91.40,
        23.00 to 91.60, 22.60 to 92.30, 21.80 to 92.60, 20.75 to 92.35,
        21.40 to 91.90, 22.20 to 91.50, 22.00 to 91.00, 21.80 to 90.30,
        21.75 to 89.90, 21.60 to 89.20, 21.90 to 88.90, 22.60 to 88.90,
        23.20 to 88.70, 23.70 to 88.60, 24.40 to 88.10, 24.90 to 88.10,
        25.60 to 88.10,
    )

    /**
     * The two Dhaka city corporations, as coarse outlines.
     *
     * Nearest-centroid cannot separate these from the division around them:
     * all three areas share one metro, and DNCC and DSCC are small polygons
     * sitting inside the region they are carved out of. Measured against the
     * real centres, Savar landed in DNCC by 0.5 km, and Gazipur, Tongi,
     * Narayanganj and Keraniganj all resolved to a city corporation they are
     * outside. That is a band error, not a label error — the corporations read
     * High while the rest of the division reads Moderate — so someone in Savar
     * would have been shown a risk that is not theirs.
     */
    private val dnccOutline = listOf(
        23.885 to 90.345, 23.885 to 90.425, 23.845 to 90.437, 23.790 to 90.442,
        23.755 to 90.430, 23.752 to 90.392, 23.736 to 90.362, 23.758 to 90.330,
        23.810 to 90.328, 23.850 to 90.332,
    )

    private val dsccOutline = listOf(
        23.752 to 90.394, 23.750 to 90.432, 23.730 to 90.462, 23.690 to 90.470,
        23.668 to 90.440, 23.688 to 90.400, 23.708 to 90.372, 23.736 to 90.364,
    )

    /**
     * The city corporation a coordinate falls in, or `null` for anywhere else —
     * including the rest of Dhaka division.
     */
    fun cityCorporationCode(latitude: Double, longitude: Double): String? {
        if (contains(latitude, longitude, dnccOutline)) return "DNCC"
        if (contains(latitude, longitude, dsccOutline)) return "DSCC"
        return null
    }

    /** Ray casting against [outline]. */
    fun containsBangladesh(latitude: Double, longitude: Double): Boolean =
        contains(latitude, longitude, outline)

    private fun contains(
        latitude: Double,
        longitude: Double,
        polygon: List<Pair<Double, Double>>,
    ): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            if ((a.first > latitude) != (b.first > latitude)) {
                val slope = (b.second - a.second) * (latitude - a.first) / (b.first - a.first) + a.second
                if (longitude < slope) inside = !inside
            }
            j = i
        }
        return inside
    }

    /**
     * Great-circle distance in metres. Only used to rank area centres against a
     * fix, so the spherical approximation is far below the error already
     * present in using a centroid at all.
     */
    fun distanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double,
    ): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dp / 2) * Math.sin(dp / 2) +
            Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2)
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }
}

// MARK: - Series

data class DailyPoint(
    val dateMillis: Long,
    val cases: Int,
    val deaths: Int,
)

/** One epidemiological week of the current season. */
data class EpiWeekPoint(val week: String, val cases: Int)

data class YearSummary(
    val year: Int,
    val cases: Int,
    /**
     * `null` for past seasons: the feed publishes yearly case totals but no
     * yearly death total, and rendering that absence as 0 would claim a year
     * with 1,705 deaths had none.
     */
    val deaths: Int?,
)

/** One age band, split by sex. The feed publishes this for cases and deaths. */
data class AgeBand(val label: String, val male: Int, val female: Int) {
    val total: Int get() = male + female
}

data class SexSplit(val male: Int, val female: Int) {
    val total: Int get() = male + female
    val maleShare: Double get() = if (total > 0) male.toDouble() / total else 0.0
}

// MARK: - Risk

enum class RiskLevel {
    LOW, MODERATE, HIGH, SEVERE;

    val ordinalValue: Int get() = ordinal

    companion object {
        /** Bands are calibrated for a two-week attack rate per 100,000. */
        fun forIncidence(value: Double): RiskLevel = when {
            value < 5 -> LOW
            value < 20 -> MODERATE
            value < 60 -> HIGH
            else -> SEVERE
        }
    }
}

object Series {
    /** Trailing moving average, used to smooth the reporting-day sawtooth. */
    fun movingAverage(values: List<Int>, window: Int): List<Double> {
        if (window <= 1) return values.map { it.toDouble() }
        val out = ArrayList<Double>(values.size)
        for (index in values.indices) {
            val start = maxOf(0, index - window + 1)
            val slice = values.subList(start, index + 1)
            out.add(slice.sum().toDouble() / slice.size)
        }
        return out
    }

    /**
     * Split [value] across [weights] so the parts always sum back to [value].
     *
     * Rounding each share on its own drifts. On the real feed that missed the
     * Dhaka division's weekly figure in 9 weeks out of 35 and overstated the
     * season by 5 cases — which contradicted what the area page tells the
     * reader. Largest remainder keeps the total exact: floor every share, then
     * hand the leftover units to the largest fractional parts.
     */
    fun apportion(value: Int, weights: List<Int>): List<Int> {
        val total = weights.sum()
        if (total <= 0 || weights.isEmpty()) return List(weights.size) { 0 }

        val exact = weights.map { value.toDouble() * it / total }
        val parts = exact.map { floor(it).toInt() }.toMutableList()
        var leftover = value - parts.sum()

        // Ties break towards the earlier index, which is stable across runs.
        val order = exact.indices.sortedWith(compareByDescending<Int> {
            exact[it] - parts[it]
        }.thenBy { it })

        var cursor = 0
        while (leftover > 0 && order.isNotEmpty()) {
            parts[order[cursor % order.size]] += 1
            leftover -= 1
            cursor += 1
        }
        return parts
    }

    fun change(old: Int, new: Int): Double? {
        if (old <= 0) return null
        return (new.toDouble() - old) / old
    }
}

/** True when a change is large enough to describe as movement at all. */
fun Double?.isMeaningfulChange(): Boolean = this != null && abs(this) >= 0.005
