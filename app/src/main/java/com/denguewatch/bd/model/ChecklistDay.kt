package com.denguewatch.bd.model

import java.util.Calendar

/**
 * Which day a set of ticks belongs to, and whether that day is still today.
 *
 * Split out from the store because this is the half that can be wrong, and the
 * half that cannot be seen going wrong: a reset that never fires looks exactly
 * like a working app until the clock rolls over. Keeping it free of Android
 * types means it can be tested against a stated hour on a stated day, which is
 * the only way to reach the cases that matter — 23:00 and 01:00 are two hours
 * apart and two different days.
 */
object ChecklistDay {

    /** Midnight at the start of the day containing [millis], in the local zone. */
    fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /**
     * Whether ticks stamped [storedDay] still count at [now].
     *
     * Calendar days are compared rather than elapsed time, because the question
     * is "is this still today", not "how long ago was this".
     */
    fun isSameDay(storedDay: Long, now: Long): Boolean =
        storedDay != 0L && storedDay == startOfDay(now)

    /**
     * The tasks a stored set means at [now] — empty once the day has turned.
     *
     * Unknown ids are dropped rather than throwing: a task removed in a later
     * build must not break an older device's stored state.
     */
    fun restore(storedIds: Set<String>, storedDay: Long, now: Long): Set<PreventionTask> =
        if (!isSameDay(storedDay, now)) {
            emptySet()
        } else {
            storedIds.mapNotNull(PreventionTask::from).toSet()
        }
}
