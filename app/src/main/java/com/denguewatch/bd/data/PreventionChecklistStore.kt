package com.denguewatch.bd.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.denguewatch.bd.model.ChecklistDay
import com.denguewatch.bd.model.PreventionTask

/**
 * Which prevention tasks are done today, on this device.
 *
 * Scoped to a calendar day on purpose: the point is that these are daily
 * chores, so yesterday's ticks must not read as today's protection. The stored
 * day is compared rather than a timer being scheduled, so the reset survives
 * the app being closed, the device being asleep, and the clock being changed.
 *
 * The day arithmetic lives in [ChecklistDay], which has no Android types and is
 * tested directly. What is left here is storage.
 *
 * Nothing leaves the device and nothing is kept beyond the current day, so
 * there is no history to keep and none is kept.
 */
class PreventionChecklistStore(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    var completed: Set<PreventionTask> by mutableStateOf(emptySet())
        private set

    init {
        completed = ChecklistDay.restore(
            storedIds = prefs.getStringSet(DONE_KEY, emptySet()).orEmpty(),
            storedDay = prefs.getLong(DAY_KEY, 0L),
            now = clock(),
        )
    }

    val completedCount: Int get() = completed.size
    val total: Int get() = PreventionTask.entries.size
    val isComplete: Boolean get() = completedCount == total

    /**
     * 0…1, for a progress bar. Zero tasks would divide by zero if the enum were
     * ever emptied, so the denominator is guarded.
     */
    val fraction: Float get() = if (total > 0) completedCount.toFloat() / total else 0f

    fun isDone(task: PreventionTask): Boolean = task in completed

    fun toggle(task: PreventionTask) {
        refreshIfDayChanged()
        completed = if (task in completed) completed - task else completed + task
        prefs.edit {
            putStringSet(DONE_KEY, completed.map { it.id }.toSet())
            putLong(DAY_KEY, ChecklistDay.startOfDay(clock()))
        }
    }

    /**
     * Clears the ticks if the day has turned over since they were made.
     *
     * Called when the screen appears *and* when the app comes back to the
     * foreground. Appearance alone leaves one real gap: a phone locked
     * overnight on this tab comes back to the same screen without appearing
     * again, so yesterday's ticks would still read as today's. "5 of 5 done"
     * when nothing has been done today is a false all-clear, in the one part of
     * the app that is about not getting bitten.
     */
    fun refreshIfDayChanged() {
        if (ChecklistDay.isSameDay(prefs.getLong(DAY_KEY, 0L), clock())) return
        completed = emptySet()
        prefs.edit { remove(DONE_KEY); remove(DAY_KEY) }
    }

    private companion object {
        const val STORE = "prevention.checklist"
        const val DONE_KEY = "completed"
        const val DAY_KEY = "day"
    }
}
