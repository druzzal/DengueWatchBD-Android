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
    }
}
