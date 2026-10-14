package com.denguewatch.bd.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * The names a reader has given individual days of their record.
 *
 * A day is "Log 3" until someone calls it something they will recognise — "the
 * day I went to hospital". Kept apart from the measurements because a name is
 * not a reading: deleting a day's readings must take its name with it, but a
 * name must never keep a day alive on its own.
 *
 * Keyed by the day's midnight, the same key the log groups by.
 */
class LogNameStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    /** Read through a snapshot so renaming redraws the list. */
    var names: Map<Long, String> by mutableStateOf(emptyMap())
        private set

    init { names = load() }

    fun name(dayMillis: Long): String? = names[dayMillis]?.takeIf { it.isNotBlank() }

    /**
     * Names a day, or forgets the name when given nothing.
     *
     * Blank means "go back to the number", which is what the rename sheet says
     * leaving it empty will do.
     */
    fun rename(dayMillis: Long, to: String) {
        val trimmed = to.trim()
        names = if (trimmed.isEmpty()) names - dayMillis else names + (dayMillis to trimmed)
        save()
    }

    /** Called when a day's records are deleted, so tomorrow cannot inherit its name. */
    fun forget(dayMillis: Long) {
        if (dayMillis !in names) return
        names = names - dayMillis
        save()
    }

    fun clear() {
        names = emptyMap()
        prefs.edit { clear() }
    }

    private fun save() {
        prefs.edit {
            clear()
            names.forEach { (day, name) -> putString(day.toString(), name) }
        }
    }

    private fun load(): Map<Long, String> =
        prefs.all.mapNotNull { (key, value) ->
            val day = key.toLongOrNull() ?: return@mapNotNull null
            val name = value as? String ?: return@mapNotNull null
            if (name.isBlank()) null else day to name
        }.toMap()

    private companion object {
        const val STORE = "log.names"
    }
}
