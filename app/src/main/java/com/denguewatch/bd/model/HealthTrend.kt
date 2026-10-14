package com.denguewatch.bd.model

import kotlin.math.abs

/**
 * Which way a series of the reader's own readings has moved.
 *
 * A direction is a fact about the numbers. What that direction means is not,
 * and nothing here attempts it: "your platelet count has fallen across these
 * three reports" is a description, while "your platelets are dropping, this may
 * be severe dengue" is a diagnosis. This type only ever produces the first.
 */
enum class HealthTrend {
    RISING,
    FALLING,
    STEADY,

    /** Fewer than two readings — a single point has no direction. */
    NOT_ENOUGH_DATA;

    private val slug: String
        get() = when (this) {
            RISING -> "rising"
            FALLING -> "falling"
            STEADY -> "steady"
            NOT_ENOUGH_DATA -> "notEnoughData"
        }

    val labelKey: String get() = "trend.$slug"

    companion object {
        /**
         * Direction of the reader's readings, first to last.
         *
         * @param minimumChange how much movement counts as a direction rather
         *   than noise. Expressed in the measure's own units, because a
         *   fraction does not travel between them: 5% of a temperature is a
         *   third of a degree, while 5% of a platelet count is nothing at all.
         */
        fun direction(values: List<Double>, minimumChange: Double): HealthTrend {
            if (values.size < 2) return NOT_ENOUGH_DATA
            val change = values.last() - values.first()
            if (abs(change) < minimumChange) return STEADY
            return if (change > 0) RISING else FALLING
        }
    }
}

/** Movement below this is thermometer scatter, not a change in condition. */
val VitalKind.minimumMeaningfulChange: Double
    get() = when (this) {
        VitalKind.TEMPERATURE -> 0.3        // °C
        VitalKind.PULSE -> 8.0              // bpm
        VitalKind.SYSTOLIC -> 8.0           // mmHg
        VitalKind.DIASTOLIC -> 6.0          // mmHg
        VitalKind.OXYGEN_SATURATION -> 2.0  // percentage points
    }

/** Movement below this is within the run-to-run variation of the assay. */
val LabMeasure.minimumMeaningfulChange: Double
    get() = when (this) {
        LabMeasure.PLATELETS -> 20.0      // ×10³/µL
        LabMeasure.HAEMATOCRIT -> 2.0     // percentage points
        LabMeasure.WHITE_CELLS -> 0.8     // ×10³/µL
        LabMeasure.HAEMOGLOBIN -> 0.8     // g/dL
    }
