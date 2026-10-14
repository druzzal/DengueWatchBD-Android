package com.denguewatch.bd.feature.triage

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.denguewatch.bd.i18n.Localization
import com.denguewatch.bd.model.DengueTest
import com.denguewatch.bd.model.HealthLogDay
import com.denguewatch.bd.model.LabMeasure
import com.denguewatch.bd.model.LabReport
import com.denguewatch.bd.model.TemperatureUnit
import com.denguewatch.bd.model.TestResult
import com.denguewatch.bd.model.VitalKind
import com.denguewatch.bd.model.VitalsEntry
import java.io.File

/**
 * The record as a document a reader can hand to a doctor.
 *
 * Drawn rather than laid out: Android has no view-to-PDF renderer that would
 * take the Compose screen, so the page is painted directly. That is also what
 * keeps the file readable on a clinic computer with no Bengali font — the text
 * is drawn with the device's own typeface, which does have one.
 *
 * Written when the reader actually shares, never on opening the log: a month of
 * entries is four pages to lay out and rasterise, for a file most readers never
 * ask for. Building it on demand also means the export is always current, with
 * no cached copy to go stale behind a later entry.
 */
object HealthLogPdf {

    // A4 at 72dpi, which is what PdfDocument's page units are.
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    /** Between a figure and the unit column. */
    private const val UNIT_GAP = 4f

    /**
     * Writes the document into the app's own cache and returns the file.
     *
     * The same cache sub-folder the report capture uses, which is the only path
     * the app's FileProvider will share.
     */
    fun write(
        context: Context,
        days: List<HealthLogDay>,
        loc: Localization,
        unit: TemperatureUnit,
    ): File {
        val document = PdfDocument()
        val body = paint(10f)
        val label = paint(10f).apply { color = 0xFF5A5A66.toInt() }
        val heading = paint(13f, bold = true)
        val title = paint(20f, bold = true)
        val footnote = paint(8f).apply { color = 0xFF5A5A66.toInt() }

        var page = document.startPage(pageInfo(1))
        var canvas = page.canvas
        var y = MARGIN
        var pageNumber = 1

        fun newPage() {
            document.finishPage(page)
            pageNumber += 1
            page = document.startPage(pageInfo(pageNumber))
            canvas = page.canvas
            y = MARGIN
        }

        /** Moves down the page, starting a new one when this line would not fit. */
        fun advance(by: Float) {
            if (y + by > PAGE_HEIGHT - MARGIN) newPage() else y += by
        }

        canvas.drawText(loc.t("log.pdf.title"), MARGIN, y + 16f, title)
        y += 30f
        canvas.drawText(
            loc.t("log.pdf.generated", loc.fullDate(System.currentTimeMillis())),
            MARGIN, y, footnote,
        )
        y += 24f

        // Units in a column of their own at the right, wide enough for the
        // widest the document can print, and every figure ending where that
        // column starts. Drawn as one "value unit" string pushed against the
        // margin, "95 ×10³/µL" and "44.0 %" put their numbers in different
        // places and the figures zig-zagged down the page.
        val unitColumn = (
            LabMeasure.entries.map { loc.t(it.unitKey) } +
                VitalKind.entries.map { loc.t(it.unitKey) } +
                TemperatureUnit.entries.map { it.symbol }
            ).maxOf { body.measureText(it) }
        val unitX = PAGE_WIDTH - MARGIN - unitColumn

        days.forEach { day ->
            advance(20f)
            canvas.drawText(loc.fullDate(day.dateMillis), MARGIN, y, heading)
            advance(6f)

            // Oldest first, so the day reads as it happened.
            day.itemsNewestFirst.reversed().forEach { item ->
                val rows = when (item) {
                    is HealthLogDay.Item.Vitals -> vitalsRows(item.entry, loc, unit)
                    is HealthLogDay.Item.Lab -> labRows(item.report, loc)
                }
                val stamp = loc.timeOfDay(item.dateMillis)
                val kind = loc.t(
                    if (item is HealthLogDay.Item.Vitals) "log.pdf.vitals" else "log.pdf.labs"
                )
                rows.forEach { row ->
                    advance(14f)
                    canvas.drawText("$stamp  $kind", MARGIN, y, label)
                    canvas.drawText(row.name, MARGIN + 90f, y, body)
                    canvas.drawText(row.value, unitX - UNIT_GAP - body.measureText(row.value), y, body)
                    if (row.unit.isNotEmpty()) canvas.drawText(row.unit, unitX, y, body)
                }
                advance(4f)
            }
        }

        advance(28f)
        wrap(loc.t("log.pdf.disclaimer"), PAGE_WIDTH - 2 * MARGIN, footnote).forEach { line ->
            advance(11f)
            canvas.drawText(line, MARGIN, y, footnote)
        }

        document.finishPage(page)

        val folder = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(folder, "health-log.pdf")
        file.outputStream().use(document::writeTo)
        document.close()
        return file
    }

    private fun pageInfo(number: Int) =
        PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, number).create()

    private fun paint(size: Float, bold: Boolean = false) = Paint().apply {
        isAntiAlias = true
        textSize = size
        color = 0xFF10121A.toInt()
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    /** One line per reading: its name and what it was, with the unit. */
    private fun vitalsRows(
        entry: VitalsEntry,
        loc: Localization,
        unit: TemperatureUnit,
    ): List<PdfRow> = buildList {
        val systolic = entry.systolic
        val diastolic = entry.diastolic
        if (systolic != null && diastolic != null) {
            add(
                PdfRow(
                    loc.t("vital.bloodPressure"),
                    "${loc.num(systolic.toInt())}/${loc.num(diastolic.toInt())}",
                    loc.t(VitalKind.SYSTOLIC.unitKey),
                )
            )
        }
        VitalKind.entries.forEach { kind ->
            // The two halves are covered by the row above.
            if (kind == VitalKind.SYSTOLIC && diastolic != null) return@forEach
            if (kind == VitalKind.DIASTOLIC && systolic != null) return@forEach
            val value = entry.value(kind) ?: return@forEach
            // Temperature is stored in Celsius whatever it was typed in, so the
            // document shows it in the unit the reader actually uses.
            add(
                if (kind == VitalKind.TEMPERATURE) {
                    PdfRow(loc.t(kind.labelKey), loc.decimal(unit.fromCelsius(value), 1), unit.symbol)
                } else {
                    PdfRow(loc.t(kind.labelKey), loc.decimal(value, kind.decimals), loc.t(kind.unitKey))
                }
            )
        }
        if (entry.note.isNotBlank()) add(PdfRow(noteLabel(loc), entry.note))
    }

    private fun labRows(report: LabReport, loc: Localization): List<PdfRow> =
        buildList {
            LabMeasure.entries.forEach { measure ->
                val value = report.value(measure) ?: return@forEach
                add(
                    PdfRow(
                        loc.t(measure.labelKey),
                        loc.decimal(value, measure.decimals),
                        loc.t(measure.unitKey),
                    )
                )
            }
            DengueTest.entries.forEach { test ->
                val result = report.result(test)
                if (result == TestResult.NOT_DONE) return@forEach
                add(PdfRow(loc.t(test.labelKey), loc.t(result.labelKey)))
            }
            if (report.note.isNotBlank()) add(PdfRow(noteLabel(loc), report.note))
        }

    /** One line of a record: what was measured, the figure, and its unit. */
    private data class PdfRow(val name: String, val value: String, val unit: String = "")

    /**
     * "Note" rather than the form field's "Note, optional".
     *
     * The screen's label invites the reader to leave it blank; a document that
     * already has the note in it should not still be offering the option.
     */
    private fun noteLabel(loc: Localization): String =
        loc.t("result.note").substringBefore(',').trim()

    /** Greedy word wrap, because Paint draws a line at a time and will not wrap. */
    private fun wrap(text: String, width: Float, paint: Paint): List<String> {
        val lines = mutableListOf<String>()
        var line = StringBuilder()
        text.split(' ').forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) <= width) {
                line = StringBuilder(candidate)
            } else {
                if (line.isNotEmpty()) lines += line.toString()
                line = StringBuilder(word)
            }
        }
        if (line.isNotEmpty()) lines += line.toString()
        return lines
    }
}
