package com.denguewatch.bd.feature.prevent

import com.denguewatch.bd.model.RiskLevel

data class PreventionTopic(val id: String, val stepCount: Int) {
    val titleKey: String get() = "prevent.$id.title"
    val summaryKey: String get() = "prevent.$id.summary"
    val stepKeys: List<String> get() = (1..stepCount).map { "prevent.$id.step$it" }
}

object PreventionContent {

    /**
     * Aedes aegypti is a day-biting, clean-water breeder that lives indoors and
     * rarely flies more than about 100 metres. Every topic follows from that.
     */
    val topics = listOf(
        PreventionTopic("breeding", 6),
        PreventionTopic("bites", 5),
        PreventionTopic("care", 6),
        PreventionTopic("community", 5),
        PreventionTopic("testing", 5),
    )

    data class SeasonMonth(val id: String, val level: RiskLevel) {
        val labelKey: String get() = "prevent.month.$id"
        val noteKey: String get() = "prevent.note.$id"
    }

    /**
     * Typical transmission through the year in Bangladesh. Months are an
     * ordered category, so they take the ordinal risk ramp.
     */
    val seasonalPattern = listOf(
        SeasonMonth("jan", RiskLevel.LOW),
        SeasonMonth("feb", RiskLevel.LOW),
        SeasonMonth("mar", RiskLevel.LOW),
        SeasonMonth("apr", RiskLevel.LOW),
        SeasonMonth("may", RiskLevel.MODERATE),
        SeasonMonth("jun", RiskLevel.MODERATE),
        SeasonMonth("jul", RiskLevel.HIGH),
        SeasonMonth("aug", RiskLevel.SEVERE),
        SeasonMonth("sep", RiskLevel.SEVERE),
        SeasonMonth("oct", RiskLevel.SEVERE),
        SeasonMonth("nov", RiskLevel.HIGH),
        SeasonMonth("dec", RiskLevel.MODERATE),
    )
}
