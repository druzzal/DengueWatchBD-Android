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
        // WHO 2025 lists oral intolerance as a reason to admit in its own
        // right. It used to ride along in the vomiting row's small print, where
        // someone who simply cannot drink — without vomiting — would not find it.
        Symptom("oralIntolerance", Symptom.Group.WARNING),
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
     * Higher-risk groups per WHO dengue guidance.
     *
     * Source: WHO, *Guidelines for clinical management of arboviral diseases:
     * dengue, chikungunya, Zika and yellow fever* (2025), section 2.6.
     */
    data class Context(
        val feverDaysAgo: Int? = null,
        val isPregnant: Boolean = false,
        /**
         * Asked separately from over 60 because a small child's blood pressure
         * and pulse are not an adult's, and the care plan reads them
         * differently.
         */
        val isUnderFive: Boolean = false,
        val isOverSixty: Boolean = false,
        /**
         * "Under 5 or over 60", as checks made before the two were asked
         * separately recorded it. Still a higher-risk group, but which end of
         * life it meant is unknown, so the readings are judged as an adult's.
         */
        val isUnderFiveOrOverSixty: Boolean = false,
        val hasChronicCondition: Boolean = false,
        val hadDengueBefore: Boolean = false,
    ) {
        /** At either extreme of age, however it was recorded. */
        val isAgeRiskGroup: Boolean
            get() = isUnderFive || isOverSixty || isUnderFiveOrOverSixty

        /**
         * The groups WHO 2025 names when it lists who is admitted: pregnancy
         * outright, and the extremes of age and underlying conditions as
         * patients clinicians "may elect to admit".
         *
         * Previous dengue is deliberately not here. WHO names a second
         * infection as raising the risk of severe disease, not as a reason to
         * admit, so it is shown as a reason to watch closely and changes no
         * advice on its own.
         */
        val isHigherRiskGroup: Boolean
            get() = isPregnant || isAgeRiskGroup || hasChronicCondition
    }

    fun evaluate(selected: Set<String>, context: Context): TriageOutcome {
        val chosen = symptoms.filter { it.id in selected }
        if (chosen.any { it.group == Symptom.Group.SEVERE }) return TriageOutcome.EMERGENCY

        val warnings = chosen.count { it.group == Symptom.Group.WARNING }
        if (warnings >= 1) return TriageOutcome.SEE_DOCTOR_TODAY

        // Anyone in a group WHO would consider admitting is seen the same day,
        // whatever the symptoms. This keeps the result in step with the care
        // plan, which already sends these groups for hospital assessment — the
        // two used to disagree for a pregnant reader with a fever alone.
        if (context.isHigherRiskGroup && chosen.isNotEmpty()) return TriageOutcome.SEE_DOCTOR_TODAY

        val core = chosen.count { it.group == Symptom.Group.CORE }
        if ("fever" in selected || core >= 2) return TriageOutcome.TEST_ADVISED
        return TriageOutcome.SELF_CARE
    }

    /**
     * True when a same-day result came from the reader's risk group rather
     * than from a warning sign they ticked, so the result can say which.
     * "You reported a warning sign" to someone who reported none reads as the
     * app not listening.
     */
    fun isRaisedByRiskGroup(selected: Set<String>, context: Context): Boolean {
        val chosen = symptoms.filter { it.id in selected }
        return evaluate(selected, context) == TriageOutcome.SEE_DOCTOR_TODAY &&
            chosen.none { it.group == Symptom.Group.WARNING }
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
