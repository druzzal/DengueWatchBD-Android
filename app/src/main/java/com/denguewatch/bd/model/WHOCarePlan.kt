package com.denguewatch.bd.model

import com.denguewatch.bd.feature.triage.Symptom
import com.denguewatch.bd.feature.triage.TriageEngine

/**
 * What WHO advises for someone who has recorded these symptoms and readings.
 *
 * This is a lookup, not a judgement. WHO's dengue guidance sorts patients into
 * three management groups by criteria a person can report about themselves —
 * warning signs, the co-existing conditions that raise risk, and the signs of
 * severe disease. The app already collects exactly those in the symptom check;
 * this states which group WHO's guidance puts them in and what that guidance
 * says to do.
 *
 * What it never does is diagnose. It does not say the reader has dengue, and
 * nothing here decides that from a lab value. Every line is traceable to a
 * criterion the reader themselves ticked or a number they themselves wrote
 * down, and each is named on screen so they can see what produced it.
 *
 * Source: WHO, *Dengue: guidelines for diagnosis, treatment, prevention and
 * control* (2009), chapter 2 — the A / B / C management groups.
 */
data class WHOCarePlan(
    val group: Group,
    /** Ordered most serious first, so the first line is the one that matters. */
    val reasons: List<Reason>,
    val phase: TriageEngine.FeverPhase?,
    /** Day of illness, counting the first day of fever as day 1. */
    val feverDay: Int?,
) {

    /** WHO's three management groups. */
    enum class Group {
        /** May be managed at home, with daily review. */
        HOME,

        /** Should be referred for in-hospital care. */
        REFERRAL,

        /** Requires emergency treatment. */
        EMERGENCY;

        /**
         * WHO's own letter for the group, which is what a clinician here will
         * recognise on a referral slip.
         */
        val letter: String
            get() = when (this) {
                HOME -> "A"
                REFERRAL -> "B"
                EMERGENCY -> "C"
            }

        private val slug: String
            get() = when (this) {
                HOME -> "home"
                REFERRAL -> "referral"
                EMERGENCY -> "emergency"
            }

        val titleKey: String get() = "who.group.$slug.title"
        val detailKey: String get() = "who.group.$slug.detail"

        /** What WHO says to do, as a short list. */
        val adviceKeys: List<String>
            get() = (1..(if (this == EMERGENCY) 3 else 4)).map { "who.group.$slug.advice$it" }

        val risk: RiskLevel
            get() = when (this) {
                HOME -> RiskLevel.LOW
                REFERRAL -> RiskLevel.HIGH
                EMERGENCY -> RiskLevel.SEVERE
            }
    }

    /**
     * Why the reader is in this group — shown so the plan is never a verdict
     * out of nowhere.
     */
    enum class Reason {
        /** A sign of severe dengue was reported. */
        SEVERE_SIGN,

        /** One or more WHO warning signs. */
        WARNING_SIGN,

        /** A co-existing condition WHO singles out. */
        CO_MORBIDITY,

        /** Days 4-7, when plasma leak begins. */
        CRITICAL_PHASE,

        /** Systolic − diastolic ≤ 20 mmHg. */
        NARROW_PULSE_PRESSURE,
        LOW_BLOOD_PRESSURE,
        FAST_PULSE,
        LOW_OXYGEN,
        NONE_OF_THESE;

        private val slug: String
            get() = name.split("_").mapIndexed { index, part ->
                if (index == 0) part.lowercase()
                else part.lowercase().replaceFirstChar { it.uppercase() }
            }.joinToString("")

        val labelKey: String get() = "who.reason.$slug"
    }

    /**
     * Reasons that came from measured numbers rather than ticked boxes.
     * Separated because they carry a caveat: a home blood-pressure cuff is not
     * a clinician's hands.
     */
    val measuredReasons: List<Reason>
        get() = reasons.filter { it in MEASURED }

    companion object {
        private val MEASURED = setOf(
            Reason.NARROW_PULSE_PRESSURE,
            Reason.LOW_BLOOD_PRESSURE,
            Reason.FAST_PULSE,
            Reason.LOW_OXYGEN,
        )

        /**
         * How long a plan still describes the person who made it.
         *
         * A symptom check is a snapshot. Dengue changes day to day — that is
         * the whole reason the critical phase matters — so a plan from last
         * week is a record of last week, not advice for today. Past this it is
         * still shown, because it is the reader's own record, but it is
         * labelled as old and asks to be run again.
         */
        const val FRESH_FOR_DAYS = 2

        /**
         * How long the fever timeline keeps describing a live illness.
         *
         * Dengue runs about ten days. Beyond a fortnight the card would be
         * counting "day 41 of a fever" from a check nobody has updated, with no
         * day of the strip marked as today — a timeline of an illness that
         * ended.
         */
        const val ILLNESS_WINDOW_DAYS = 14

        /** Whether a plan made this many days ago still speaks for today. */
        fun describesToday(checkedDaysAgo: Int): Boolean =
            checkedDaysAgo in 0..FRESH_FOR_DAYS

        /**
         * Whether a fever that began this many days ago is still a live illness
         * worth charting. Negative days mean a clock that moved backwards, and
         * are no more chartable than a fever from last month.
         */
        fun showsTimeline(feverDaysAgo: Int): Boolean =
            feverDaysAgo >= 0 && feverDaysAgo < ILLNESS_WINDOW_DAYS

        /**
         * The readings WHO's guidance actually speaks to, as thresholds.
         *
         * Deliberately conservative, and deliberately few. WHO's bedside signs
         * of shock include cold clammy skin and capillary refill, which no
         * phone can record; these four are the ones a home monitor gives
         * honestly.
         */
        const val NARROW_PULSE_PRESSURE = 20.0
        const val LOW_SYSTOLIC = 90.0
        const val FAST_PULSE = 120.0
        const val LOW_OXYGEN = 94.0

        /**
         * @param symptoms ids ticked in the symptom check.
         * @param vitals the most recent set of readings, if any are recent
         *   enough to speak to how the reader is now.
         * @param context the co-existing conditions WHO singles out.
         * @param feverDaysAgo days since fever began, zero-based as the checker
         *   records it. Null when there is no fever to date from.
         */
        fun evaluate(
            symptoms: Set<String>,
            vitals: VitalsEntry?,
            context: TriageEngine.Context,
            feverDaysAgo: Int?,
        ): WHOCarePlan {
            val reasons = mutableListOf<Reason>()

            val chosen = TriageEngine.symptoms.filter { it.id in symptoms }
            val hasSevere = chosen.any { it.group == Symptom.Group.SEVERE }
            val hasWarning = chosen.any { it.group == Symptom.Group.WARNING }

            if (hasSevere) reasons += Reason.SEVERE_SIGN
            if (hasWarning) reasons += Reason.WARNING_SIGN

            // Measured signs. Order matters: pulse pressure first, because it is
            // the one that changes before the others do.
            val measured = measuredConcerns(vitals)
            reasons += measured

            if (context.hasCoMorbidity) reasons += Reason.CO_MORBIDITY

            val phase = TriageEngine.phase(feverDaysAgo)
            if (phase == TriageEngine.FeverPhase.CRITICAL) reasons += Reason.CRITICAL_PHASE

            // The group is WHO's, and the measured signs only ever raise it.
            var group = Group.HOME
            if (hasWarning || context.hasCoMorbidity) group = Group.REFERRAL
            if (measured.isNotEmpty()) group = maxOf(group, Group.REFERRAL)
            if (hasSevere ||
                Reason.NARROW_PULSE_PRESSURE in measured ||
                Reason.LOW_BLOOD_PRESSURE in measured
            ) {
                group = Group.EMERGENCY
            }

            return WHOCarePlan(
                group = group,
                reasons = reasons.ifEmpty { listOf(Reason.NONE_OF_THESE) },
                phase = phase,
                feverDay = feverDaysAgo?.plus(1),
            )
        }

        /**
         * Readings that cross a threshold WHO's guidance names.
         *
         * Null readings are not concerns: a blood pressure never taken is not a
         * normal blood pressure, and a missing number must never quietly move
         * someone into a calmer group.
         */
        private fun measuredConcerns(vitals: VitalsEntry?): List<Reason> {
            if (vitals == null) return emptyList()
            val found = mutableListOf<Reason>()

            val systolic = vitals.systolic
            val diastolic = vitals.diastolic
            if (systolic != null && diastolic != null &&
                systolic - diastolic <= NARROW_PULSE_PRESSURE
            ) {
                found += Reason.NARROW_PULSE_PRESSURE
            }
            if (systolic != null && systolic < LOW_SYSTOLIC) found += Reason.LOW_BLOOD_PRESSURE
            vitals.pulse?.let { if (it > FAST_PULSE) found += Reason.FAST_PULSE }
            vitals.oxygenSaturation?.let { if (it < LOW_OXYGEN) found += Reason.LOW_OXYGEN }
            return found
        }
    }
}
