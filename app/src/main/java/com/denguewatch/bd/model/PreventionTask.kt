package com.denguewatch.bd.model

/**
 * The small set of things worth doing today to stop mosquitoes breeding.
 *
 * Deliberately short and deliberately unrewarded. A prevention habit that needs
 * streaks and confetti to hold attention is one people abandon; the only
 * feedback is an honest count of what is done. Ported from the iOS build, same
 * five tasks in the same order.
 */
enum class PreventionTask(val id: String) {
    STANDING_WATER("standingWater"),
    PLANT_TRAYS("plantTrays"),
    CONTAINERS("containers"),
    STORED_WATER("storedWater"),
    PROTECTION("protection");

    /** Localisation key. Text lives in the string tables, never inline. */
    val titleKey: String get() = "checklist.task.$id"

    companion object {
        /**
         * The task with this id, or null.
         *
         * Null rather than an exception: a task dropped in a later build must
         * not break an older device's stored state.
         */
        fun from(id: String): PreventionTask? = entries.firstOrNull { it.id == id }
    }
}
