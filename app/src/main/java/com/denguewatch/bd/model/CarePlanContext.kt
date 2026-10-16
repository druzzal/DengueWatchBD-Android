package com.denguewatch.bd.model

/**
 * Whether the reader's recorded readings deserve a mention alongside the advice
 * their symptom check produced.
 *
 * This is the join between the two records, and it is deliberately a narrow
 * one. The triage outcome still comes from symptoms alone — no reading here
 * changes it, upgrades it, or second-guesses it. All this decides is whether to
 * add a line saying some readings are well outside the usual range and should
 * be shown to someone.
 *
 * The distinction matters. "Your platelets are low, you may have severe dengue"
 * is a diagnosis. "Some of your readings are well outside the usual range, take
 * them to a doctor" is a prompt to get the diagnosis from somebody qualified to
 * give it.
 */
data class CarePlanContext(
    /** Readings far enough out to be worth raising. Empty means say nothing. */
    val notableVitals: List<VitalKind>,
    val notableLabs: List<LabMeasure>,
) {
    val hasAnything: Boolean
        get() = notableVitals.isNotEmpty() || notableLabs.isNotEmpty()

    companion object {
        /**
         * How recent a reading has to be to still be worth mentioning.
         *
         * A reading from last month says nothing about today, and raising it
         * beside today's advice would be noise that teaches people to ignore
         * the line entirely.
         */
        const val RECENCY_WINDOW_MILLIS = 3L * 24 * 60 * 60 * 1000

        fun from(
            vitals: List<Triple<VitalKind, Double, Long>>,
            labs: List<Triple<LabMeasure, Double, Long>>,
            now: Long = System.currentTimeMillis(),
        ): CarePlanContext {
            val cutoff = now - RECENCY_WINDOW_MILLIS

            // Only FAR_OUTSIDE, not merely outside. A haemoglobin a fraction
            // under the line every week would make this permanent furniture.
            val vitalsOut = vitals
                .filter { it.third >= cutoff && it.first.status(it.second) == MeasureStatus.FAR_OUTSIDE }
                .map { it.first }
            val labsOut = labs
                .filter { it.third >= cutoff && it.first.status(it.second) == MeasureStatus.FAR_OUTSIDE }
                .map { it.first }

            return CarePlanContext(
                notableVitals = vitalsOut.distinct().sortedBy { it.ordinal },
                notableLabs = labsOut.distinct().sortedBy { it.ordinal },
            )
        }

        /**
         * Whether the haematocrit has gone up on each of the last two reports.
         *
         * WHO 2025 lists a "progressive increase in haematocrit on at least two
         * consecutive measurements" among the reasons clinicians admit someone
         * with dengue. Read here as two rises in a row — three reports, each
         * higher than the one before — because one rise is a single
         * comparison, not a progression.
         *
         * It is raised beside the plan and never moves the group, for the same
         * reason no lab value does: this is a pattern for a doctor to act on,
         * and the app only points at it. A rise smaller than the assay's own
         * scatter does not count, and the newest report has to be recent.
         *
         * @param series (dateMillis, value) pairs, in any order.
         */
        fun haematocritIsRising(
            series: List<Pair<Long, Double>>,
            now: Long = System.currentTimeMillis(),
        ): Boolean {
            val ordered = series.sortedBy { it.first }
            val newest = ordered.lastOrNull() ?: return false
            if (ordered.size < 3 || now - newest.first > RECENCY_WINDOW_MILLIS) return false
            val (a, b, c) = ordered.takeLast(3).map { it.second }
            val step = LabMeasure.HAEMATOCRIT.minimumMeaningfulChange
            return b - a >= step && c - b >= step
        }
    }
}
