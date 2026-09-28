package com.denguewatch.bd.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.denguewatch.bd.model.VitalKind
import com.denguewatch.bd.model.VitalsEntry
import com.denguewatch.bd.model.mapObjects
import org.json.JSONArray
import java.io.File

/**
 * The reader's own readings, kept on device in the app's private files.
 *
 * Newest first, sorted on both write and read. The forms let a reader say when
 * a reading was taken — a fever measured at three in the morning and entered at
 * nine — so insertion order and time order are two different things, and every
 * screen that says "latest" means the newest reading rather than the one most
 * recently typed.
 */
class VitalsStore(context: Context, filename: String = "vitals.json") {

    private val file = File(context.applicationContext.filesDir, filename)

    var entries: List<VitalsEntry> by mutableStateOf(emptyList())
        private set

    init { load() }

    /** An entry with no readings in it is not a record. */
    fun add(entry: VitalsEntry) {
        if (entry.isEmpty) return
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

    val latest: VitalsEntry? get() = entries.firstOrNull()

    /** Readings for one measure, oldest first, for charting a course. */
    fun series(kind: VitalKind): List<Pair<Long, Double>> =
        entries.mapNotNull { entry -> entry.value(kind)?.let { entry.dateMillis to it } }
            .sortedBy { it.first }

    private fun load() {
        if (!file.exists()) return
        entries = runCatching {
            JSONArray(file.readText()).mapObjects { VitalsEntry.from(it) }
                .sortedByDescending { it.dateMillis }
        }.getOrDefault(emptyList())
    }

    private fun save() {
        runCatching {
            val array = JSONArray()
            entries.forEach { array.put(it.toJson()) }
            file.writeText(array.toString())
        }
    }
}
