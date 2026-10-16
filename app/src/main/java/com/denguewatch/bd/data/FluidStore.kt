package com.denguewatch.bd.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.denguewatch.bd.model.FluidDay
import com.denguewatch.bd.model.FluidEntry
import com.denguewatch.bd.model.FluidLog
import com.denguewatch.bd.model.HealthLog
import org.json.JSONArray
import java.io.File

/**
 * The reader's fluid log, kept on device in the app's private files.
 *
 * Same shape and same promise as [VitalsStore]: nothing leaves the phone, no
 * account. Newest first, sorted on both write and read.
 */
class FluidStore(context: Context, filename: String = "fluids.json") {

    private val file = File(context.applicationContext.filesDir, filename)

    var entries: List<FluidEntry> by mutableStateOf(emptyList())
        private set

    init { load() }

    /** A drink with no amount, or an impossible one, is not a record. */
    fun add(entry: FluidEntry) {
        if (entry.kind == FluidEntry.Kind.DRINK &&
            entry.millilitres?.let { it in FluidLog.PLAUSIBLE_MILLILITRES } != true
        ) return
        entries = (listOf(entry) + entries).sortedByDescending { it.dateMillis }
        save()
    }

    fun delete(id: String) {
        entries = entries.filterNot { it.id == id }
        save()
    }

    fun clear() {
        entries = emptyList()
        save()
    }

    fun day(dateMillis: Long): FluidDay = FluidLog.day(dateMillis, entries)

    /** One day's entries, oldest first, the way a day is read. */
    fun entriesOn(dateMillis: Long): List<FluidEntry> {
        val start = HealthLog.startOfDay(dateMillis)
        return entries.filter { HealthLog.startOfDay(it.dateMillis) == start }.reversed()
    }

    private fun load() {
        if (!file.exists()) return
        entries = runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { FluidEntry.from(array.getJSONObject(it)) }
                .sortedByDescending { it.dateMillis }
        }.getOrDefault(emptyList())
    }

    private fun save() {
        runCatching {
            val array = JSONArray()
            entries.forEach { array.put(it.toJson()) }
            file.writeTextAtomically(array.toString())
        }
    }
}
