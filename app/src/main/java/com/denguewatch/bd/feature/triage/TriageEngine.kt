package com.denguewatch.bd.feature.triage

/**
 * One question in the symptom check. Text lives in the string tables; this
 * model carries only the identity and the clinical grouping.
 */
data class Symptom(val id: String, val group: Group) {

    enum class Group {
        CORE, WARNING, SEVERE;

        val key: String get() = name.lowercase()
        val titleKey: String get() = "symptom.group.$key"
        val footnoteKey: String get() = "symptom.group.$key.footnote"
    }

    val titleKey: String get() = "symptom.$id.title"
    val detailKey: String get() = "symptom.$id.detail"
}

enum class TriageOutcome {
    SELF_CARE, TEST_ADVISED, SEE_DOCTOR_TODAY, EMERGENCY;

    private val slug: String
        get() = when (this) {
            SELF_CARE -> "selfCare"
            TEST_ADVISED -> "testAdvised"
            SEE_DOCTOR_TODAY -> "seeDoctor"
            EMERGENCY -> "emergency"
        }

    val headlineKey: String get() = "outcome.$slug.headline"
    val summaryKey: String get() = "outcome.$slug.summary"

    val actionKeys: List<String>
        get() {
            val count = when (this) {
                SELF_CARE -> 3
                TEST_ADVISED -> 4
                SEE_DOCTOR_TODAY -> 4
                EMERGENCY -> 3
            }
            return (1..count).map { "outcome.$slug.action$it" }
        }
}

object TriageEngine {

    val symptoms = listOf(
        Symptom("fever", Symptom.Group.CORE),
        Symptom("headache", Symptom.Group.CORE),
        Symptom("aches", Symptom.Group.CORE),
        Symptom("rash", Symptom.Group.CORE),
        Symptom("nausea", Symptom.Group.CORE),

        Symptom("abdominal", Symptom.Group.WARNING),
        Symptom("vomiting", Symptom.Group.WARNING),
        Symptom("bleeding", Symptom.Group.WARNING),
        Symptom("fatigue", Symptom.Group.WARNING),
        Symptom("fluid", Symptom.Group.WARNING),
        Symptom("noUrine", Symptom.Group.WARNING),

        Symptom("faint", Symptom.Group.SEVERE),
        Symptom("confusion", Symptom.Group.SEVERE),
        Symptom("heavyBleed", Symptom.Group.SEVERE),
    )

    fun symptoms(group: Symptom.Group) = symptoms.filter { it.group == group }

    fun symptom(id: String) = symptoms.firstOrNull { it.id == id }

    /**
     * Higher-risk groups per WHO dengue guidance — they shift borderline
     * results up a level.
     */
    data class Context(
        val feverDaysAgo: Int? = null,
        val isPregnant: Boolean = false,
        val isUnderFiveOrOverSixty: Boolean = false,
        val hasChronicCondition: Boolean = false,
        val hadDengueBefore: Boolean = false,
    ) {
        val hasCoMorbidity: Boolean
            get() = isPregnant || isUnderFiveOrOverSixty ||
                hasChronicCondition || hadDengueBefore
    }

    fun evaluate(selected: Set<String>, context: Context): TriageOutcome {
        val chosen = symptoms.filter { it.id in selected }
        if (chosen.any { it.group == Symptom.Group.SEVERE }) return TriageOutcome.EMERGENCY

        val warnings = chosen.count { it.group == Symptom.Group.WARNING }
        if (warnings >= 1) return TriageOutcome.SEE_DOCTOR_TODAY

        val core = chosen.count { it.group == Symptom.Group.CORE }
        val hasFever = "fever" in selected

        if (hasFever && core >= 2) {
            return if (context.hasCoMorbidity) {
                TriageOutcome.SEE_DOCTOR_TODAY
            } else {
                TriageOutcome.TEST_ADVISED
            }
        }
        if (hasFever) return TriageOutcome.TEST_ADVISED
        if (core >= 2) return TriageOutcome.TEST_ADVISED
        return TriageOutcome.SELF_CARE
    }

    /**
     * Which phase of illness the person is in. The critical phase is when
     * plasma leak starts, and it is not when people feel worst.
     *
     * Introduced so the sentence under the result, the care plan and the fever
     * timeline read the same boundaries from one place. The boundaries
     * themselves are unchanged: this wraps the rule that was already here
     * rather than restating it, so the three can never drift apart.
     */
    enum class FeverPhase {
        FEBRILE, CRITICAL, RECOVERY;

        private val slug: String
            get() = name.lowercase()

        val key: String get() = "check.phase.$slug"
        val nameKey: String get() = "phase.$slug.name"

        /**
         * Days of fever this phase covers, counting the first day as day 1.
         * `feverDaysAgo` is zero-based, so these run one higher.
         */
        val dayRange: IntRange
            get() = when (this) {
                FEBRILE -> 1..3
                CRITICAL -> 4..7
                RECOVERY -> 8..10
            }

        companion object {
            fun of(feverDaysAgo: Int): FeverPhase = when (feverDaysAgo) {
                in 0..2 -> FEBRILE
                in 3..6 -> CRITICAL
                else -> RECOVERY
            }
        }
    }

    fun phase(feverDaysAgo: Int?): FeverPhase? =
        feverDaysAgo?.let(FeverPhase::of)

    fun phaseKey(feverDaysAgo: Int?): String? = phase(feverDaysAgo)?.key
}
