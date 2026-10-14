package com.denguewatch.bd.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.denguewatch.bd.feature.triage.TriageEngine
import com.denguewatch.bd.model.mapObjects
import com.denguewatch.bd.model.mapStrings
import com.denguewatch.bd.model.optStringOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.UUID

data class CaseLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val dateMillis: Long = System.currentTimeMillis(),
    val temperature: Double? = null,
    val symptomIds: List<String> = emptyList(),
    val outcomeOrdinal: Int = 0,
    val areaCode: String? = null,
    val note: String = "",
    /**
     * Days since the fever began, zero-based, as the checker records it. Null
     * when there was no fever to date from.
     *
     * Persisted so the fever timeline and the WHO plan survive the check that
     * produced them — without it, the day of illness is known for one screen
     * and then forgotten, which is the one number dengue care turns on.
     * Optional, so entries written before this existed still decode.
     */
    val feverDaysAgo: Int? = null,
    /**
     * The co-existing conditions WHO singles out, as ticked at the time. They
     * decide the WHO management group, so the plan cannot be rebuilt without
     * them.
     */
    val isPregnant: Boolean = false,
    val isUnderFiveOrOverSixty: Boolean = false,
    val hasChronicCondition: Boolean = false,
    val hadDengueBefore: Boolean = false,
) {
    val triageContext: TriageEngine.Context
        get() = TriageEngine.Context(
            feverDaysAgo = feverDaysAgo,
            isPregnant = isPregnant,
            isUnderFiveOrOverSixty = isUnderFiveOrOverSixty,
            hasChronicCondition = hasChronicCondition,
            hadDengueBefore = hadDengueBefore,
        )

    /** The day the fever began, for the timeline. */
    val feverStartedMillis: Long?
        get() = feverDaysAgo?.let { days ->
            val day = Calendar.getInstance().apply {
                timeInMillis = dateMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, -days)
            }
            day.timeInMillis
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("date", dateMillis)
        temperature?.let { put("temperature", it) }
        put("symptomIds", JSONArray(symptomIds))
        put("outcome", outcomeOrdinal)
        areaCode?.let { put("areaCode", it) }
        put("note", note)
        feverDaysAgo?.let { put("feverDaysAgo", it) }
        put("isPregnant", isPregnant)
        put("isUnderFiveOrOverSixty", isUnderFiveOrOverSixty)
        put("hasChronicCondition", hasChronicCondition)
        put("hadDengueBefore", hadDengueBefore)
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
            // Absent from entries written before the plan needed them, which
            // decode as "not asked" rather than as "answered no".
            feverDaysAgo = if (json.has("feverDaysAgo") && !json.isNull("feverDaysAgo")) {
                json.optInt("feverDaysAgo")
            } else null,
            isPregnant = json.optBoolean("isPregnant", false),
            isUnderFiveOrOverSixty = json.optBoolean("isUnderFiveOrOverSixty", false),
            hasChronicCondition = json.optBoolean("hasChronicCondition", false),
            hadDengueBefore = json.optBoolean("hadDengueBefore", false),
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
