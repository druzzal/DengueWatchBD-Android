package com.denguewatch.bd.model

/**
 * How far a reading sits from its usual range, for colouring it.
 *
 * Three steps, because two are not enough: a haemoglobin a fraction under the
 * line and a platelet count at a third of it are both "outside the range", and
 * showing them identically wastes the one thing colour is good at. What this
 * does not do is say what any of it means — the wording beside the number is
 * unchanged, and still sends the reader to their report and their doctor.
 */
enum class MeasureStatus {
    NORMAL, OUTSIDE, FAR_OUTSIDE;

    val slug: String
        get() = when (this) {
            NORMAL -> "normal"
            OUTSIDE -> "outside"
            FAR_OUTSIDE -> "farOutside"
        }

    /** Spoken and written wording, so colour is never the only signal. */
    val labelKey: String get() = "status.$slug"

    /** Maps onto the app's risk palette, already checked for colour-vision separation. */
    val risk: RiskLevel
        get() = when (this) {
            NORMAL -> RiskLevel.LOW
            OUTSIDE -> RiskLevel.HIGH
            FAR_OUTSIDE -> RiskLevel.SEVERE
        }

    companion object {
        fun of(
            value: Double,
            usual: ClosedFloatingPointRange<Double>,
            far: ClosedFloatingPointRange<Double>,
        ): MeasureStatus = when {
            usual.contains(value) -> NORMAL
            far.contains(value) -> OUTSIDE
            else -> FAR_OUTSIDE
        }
    }
}

/**
 * Which side of the usual range a reading fell on, in words.
 *
 * [MeasureStatus] says how far out a reading is, which is what the colour
 * carries; it cannot say which way. "Outside the usual range" reads the same
 * for a platelet count of 40 and a temperature of 39.5, and a log handed to a
 * doctor should say "Low" for one and "High" for the other.
 */
enum class MeasureSide {
    LOW, NORMAL, HIGH;

    /** The same three words the lab table already uses. */
    val labelKey: String
        get() = when (this) {
            LOW -> "lab.flag.low"
            NORMAL -> "lab.flag.ok"
            HIGH -> "lab.flag.high"
        }

    companion object {
        fun of(value: Double, usual: ClosedFloatingPointRange<Double>): MeasureSide = when {
            value < usual.start -> LOW
            value > usual.endInclusive -> HIGH
            else -> NORMAL
        }

        /**
         * A blood pressure is one reading written as a pair, so it gets one
         * word: whichever half strayed further, systolic on a tie.
         */
        fun bloodPressure(systolic: Double, diastolic: Double): Pair<MeasureSide, MeasureStatus> {
            val top = VitalKind.SYSTOLIC.side(systolic) to VitalKind.SYSTOLIC.status(systolic)
            val bottom = VitalKind.DIASTOLIC.side(diastolic) to VitalKind.DIASTOLIC.status(diastolic)
            return if (bottom.second.risk.ordinal > top.second.risk.ordinal) bottom else top
        }
    }
}

fun VitalKind.side(value: Double): MeasureSide = MeasureSide.of(value, usual)

fun LabMeasure.side(value: Double): MeasureSide = MeasureSide.of(value, typical)
