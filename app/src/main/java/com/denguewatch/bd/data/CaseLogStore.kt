package com.denguewatch.bd.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.denguewatch.bd.model.mapObjects
import com.denguewatch.bd.model.mapStrings
import com.denguewatch.bd.model.optStringOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class CaseLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val dateMillis: Long = System.currentTimeMillis(),
    val temperature: Double? = null,
    val symptomIds: List<String> = emptyList(),
    val outcomeOrdinal: Int = 0,
    val areaCode: String? = null,
    val note: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("date", dateMillis)
        temperature?.let { put("temperature", it) }
        put("symptomIds", JSONArray(symptomIds))
        put("outcome", outcomeOrdinal)
        areaCode?.let { put("areaCode", it) }
        put("note", note)
    }

    companion object {
        fun from(json: JSONObject) = CaseLogEntry(
            id = json.optStringOrNull("id") ?: UUID.randomUUID().toString(),
            dateMillis = json.optLong("date", System.currentTimeMillis()),
            temperature = if (json.has("temperature") && !json.isNull("temperature")) {
                json.optDouble("temperature")
            } else null,
            symptomIds = json.optJSONArray("symptomIds").mapStrings(),
            outcomeOrdinal = json.optInt("outcome", 0),
            areaCode = json.optStringOrNull("areaCode"),
            note = json.optString("note", ""),
        )
    }
}

/**
 * The user's own check-ins, kept on device in the app's private files
 * directory. Nothing here leaves the phone — the privacy footer on the log
 * screen says so, and this is what makes that true.
 */
class CaseLogStore(context: Context, filename: String = "case-log.json") {

    private val file = File(context.applicationContext.filesDir, filename)

    var entries: List<CaseLogEntry> by mutableStateOf(emptyList())
        private set

    init {
        load()
    }

    fun add(entry: CaseLogEntry) {
        entries = listOf(entry) + entries
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

    private fun load() {
        if (!file.exists()) return
        entries = runCatching {
            JSONArray(file.readText()).mapObjects { CaseLogEntry.from(it) }
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
