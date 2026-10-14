package com.denguewatch.bd.feature.prevent

/**
 * A belief that is common in Bangladesh and the fact that corrects it.
 *
 * Each entry exists because the myth causes a specific harm — someone stops
 * watching, delays care, or takes something that makes bleeding worse. The
 * facts restate what the app already says elsewhere rather than introducing new
 * clinical claims: the fever-falls warning, the daylight biting, the
 * hospital-only counting. Nothing here is novel advice.
 *
 * Ported from the iOS build, same six in the same order.
 */
data class MythFact(val id: String) {
    val mythKey: String get() = "myth.$id.myth"
    val factKey: String get() = "myth.$id.fact"

    companion object {
        val all: List<MythFact> = listOf(
            // The most dangerous belief in dengue, and the reason the fever
            // timeline exists at all.
            MythFact("feverGone"),
            // Drives people to buy papaya extract instead of seeking review.
            MythFact("papaya"),
            // Leads to nets at night and bare arms at dawn.
            MythFact("nightOnly"),
            // A second infection is more dangerous, not less.
            MythFact("onceOnly"),
            // Sends families chasing a number instead of watching the person.
            MythFact("platelets"),
            // The one that turns a survivable illness into a bleed.
            MythFact("painkillers"),
        )
    }
}
