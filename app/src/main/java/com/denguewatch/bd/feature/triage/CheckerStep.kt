package com.denguewatch.bd.feature.triage

/**
 * The stages of the symptom check.
 *
 * Split up because the single form asked fourteen symptom questions, a date and
 * four risk questions on one screen, which is a lot to face when you feel ill.
 * One question at a time also lets the warning signs get a screen of their own,
 * which is the part that most needs to be read rather than skimmed.
 *
 * No clinical rule lives here. The steps only decide what is asked and in what
 * order; [TriageEngine.evaluate] still decides what the answers mean. Ported
 * from the iOS build, same five in the same order.
 */
enum class CheckerStep(private val slug: String) {
    FEVER("fever"),
    SYMPTOMS("symptoms"),
    WARNING_SIGNS("warning"),
    TIMELINE("timeline"),
    RISK_FACTORS("risk");

    val titleKey: String get() = "step.$slug.title"
    val subtitleKey: String get() = "step.$slug.subtitle"

    companion object {
        /**
         * The steps actually shown for this answer set.
         *
         * The timeline step is skipped when there is no fever: asking which day
         * of a fever someone is on when they have said they have none is
         * nonsense, and a step that cannot be answered is a step that gets
         * guessed at.
         */
        fun sequence(hasFever: Boolean): List<CheckerStep> =
            entries.filter { it != TIMELINE || hasFever }
    }
}
