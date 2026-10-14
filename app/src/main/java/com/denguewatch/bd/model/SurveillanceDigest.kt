package com.denguewatch.bd.model

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Plain-language statements about what moved in the latest DGHS update.
 *
 * Every statement is derived from a value the feed actually carries. Nothing is
 * inferred, smoothed or filled in: when the data cannot support a claim the
 * statement is simply absent, which is why [items] returns a list that may be
 * short or empty rather than a fixed set of slots.
 *
 * Deliberately free of Compose and of the store, so the rules can be tested
 * directly on values. The view renders whatever comes back and decides nothing.
 */
object SurveillanceDigest {

    /** What a statement is about, so the view can weight it without parsing text. */
    enum class Kind {
        NATIONAL_RISE,
        NATIONAL_FALL,
        NATIONAL_STEADY,
        HOTSPOTS,
        STEEPEST_RISES,
        NO_BREAKDOWN,
    }

    data class Item(
        val kind: Kind,
        /** Localization key; the view resolves it so nothing here is language-bound. */
        val key: String,
        val arguments: List<String> = emptyList(),
    )

    /**
     * A week-on-week move smaller than this is reporting noise, not a trend.
     * DGHS figures wobble with reporting-day effects, and calling a 2% wobble a
     * rise would cry wolf every other week.
     */
    const val STEADY_BAND = 0.05

    /** Only areas moving at least this much are worth naming individually. */
    const val NOTABLE_AREA_RISE = 0.20

    /** How many areas to name in one sentence before it stops being readable. */
    const val MAX_NAMED_AREAS = 2

    /**
     * @param nationalChange week-on-week fraction, or null when the previous
     *   week is missing and a ratio would be meaningless.
     * @param hotspotCount areas currently at high risk or worse.
     * @param hasAreaBreakdown whether per-area figures arrived at all. An empty
     *   area list and "no areas are high risk" mean opposite things.
     * @param risingAreas name to change, for areas that rose, steepest first.
     * @param formatNumber renders a figure in the reader's own digits. The
     *   default is plain ASCII, which keeps these rules language-free for the
     *   tests; the screen passes its localised formatter. Without it a Bengali
     *   reader saw "4টি এলাকা উচ্চ ঝুঁকিতে" — a Latin 4 in a Bengali sentence,
     *   the only number on the screen not in their own digits.
     */
    fun items(
        nationalChange: Double?,
        hotspotCount: Int,
        hasAreaBreakdown: Boolean,
        risingAreas: List<Pair<String, Double>>,
        formatNumber: (Int) -> String = { it.toString() },
    ): List<Item> {
        val items = mutableListOf<Item>()

        if (nationalChange != null) {
            val percent = formatNumber((abs(nationalChange) * 100).roundToInt())
            items += when {
                nationalChange > STEADY_BAND ->
                    Item(Kind.NATIONAL_RISE, "digest.national.rise", listOf(percent))
                nationalChange < -STEADY_BAND ->
                    Item(Kind.NATIONAL_FALL, "digest.national.fall", listOf(percent))
                else ->
                    Item(Kind.NATIONAL_STEADY, "digest.national.steady")
            }
        }

        if (!hasAreaBreakdown) {
            // Saying "0 areas are high risk" when no area figures arrived would
            // be a reassurance the data cannot support.
            items += Item(Kind.NO_BREAKDOWN, "digest.noBreakdown")
            return items
        }

        items += Item(
            Kind.HOTSPOTS,
            if (hotspotCount == 1) "digest.hotspots.one" else "digest.hotspots.many",
            listOf(formatNumber(hotspotCount)),
        )

        val notable = risingAreas
            .filter { it.second >= NOTABLE_AREA_RISE }
            .take(MAX_NAMED_AREAS)
            .map { it.first }
        if (notable.isNotEmpty()) {
            items += Item(
                Kind.STEEPEST_RISES,
                if (notable.size == 1) "digest.rises.one" else "digest.rises.two",
                notable,
            )
        }

        return items
    }
}
