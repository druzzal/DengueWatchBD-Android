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
