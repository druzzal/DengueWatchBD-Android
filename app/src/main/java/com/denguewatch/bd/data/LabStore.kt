package com.denguewatch.bd.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.denguewatch.bd.model.DengueTest
import com.denguewatch.bd.model.LabMeasure
import com.denguewatch.bd.model.LabReport
import com.denguewatch.bd.model.TestResult
import com.denguewatch.bd.model.mapObjects
import org.json.JSONArray
import java.io.File

/**
 * Lab reports as the reader entered them. See `VitalsStore` on ordering.
 */
class LabStore(private val file: File) {

    constructor(context: Context, filename: String = "lab-reports.json") :
        this(File(context.applicationContext.filesDir, filename))

    var reports: List<LabReport> by mutableStateOf(emptyList())
        private set

    init { load() }

    fun add(report: LabReport) {
        if (report.isEmpty) return
        reports = (listOf(report) + reports).sortedByDescending { it.dateMillis }
        save()
    }

    fun delete(id: String) {
        reports = reports.filterNot { it.id == id }
        save()
    }

    fun clear() {
        reports = emptyList()
        save()
    }

    val latest: LabReport? get() = reports.firstOrNull()

    /** One measure over the course of an illness, oldest first. */
    fun series(measure: LabMeasure): List<Pair<Long, Double>> =
        reports.mapNotNull { report -> report.value(measure)?.let { report.dateMillis to it } }
            .sortedBy { it.first }

    /**
     * The newest value recorded for this measure, which may come from an older
     * report than [latest] if the most recent one did not include it.
     */
    fun mostRecent(measure: LabMeasure): Pair<Long, Double>? = series(measure).lastOrNull()

    /**
     * The newest report that actually recorded this test, which may be older
     * than `latest` if the most recent report did not include it.
     */
    fun mostRecentResult(test: DengueTest): Pair<Long, TestResult>? =
        reports.firstOrNull { it.result(test) != TestResult.NOT_DONE }
            ?.let { it.dateMillis to it.result(test) }

    private fun load() {
        if (!file.exists()) return
        reports = runCatching {
            JSONArray(file.readText()).mapObjects { LabReport.from(it) }
                .sortedByDescending { it.dateMillis }
        }.getOrDefault(emptyList())
    }

    private fun save() {
        runCatching {
            val array = JSONArray()
            reports.forEach { array.put(it.toJson()) }
            file.writeTextAtomically(array.toString())
        }
    }
}
