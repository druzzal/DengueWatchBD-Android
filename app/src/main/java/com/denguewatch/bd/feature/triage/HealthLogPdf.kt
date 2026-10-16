package com.denguewatch.bd.feature.triage

import android.content.Context
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import com.denguewatch.bd.feature.triage.PdfDraw.Ink
import com.denguewatch.bd.feature.triage.PdfDraw.Weight
import com.denguewatch.bd.feature.triage.PdfDraw.brandMark
import com.denguewatch.bd.feature.triage.PdfDraw.fill
import com.denguewatch.bd.feature.triage.PdfDraw.line
import com.denguewatch.bd.feature.triage.PdfDraw.paint
import com.denguewatch.bd.feature.triage.PdfDraw.text
import com.denguewatch.bd.i18n.Localization
import com.denguewatch.bd.model.DengueTest
import com.denguewatch.bd.model.FluidLog
import com.denguewatch.bd.model.HealthLog
import com.denguewatch.bd.model.HealthLogDay
import com.denguewatch.bd.model.LabMeasure
import com.denguewatch.bd.model.LabReport
import com.denguewatch.bd.model.MeasureSide
import com.denguewatch.bd.model.TemperatureUnit
import com.denguewatch.bd.model.TestResult
import com.denguewatch.bd.model.VitalKind
import com.denguewatch.bd.model.VitalsEntry
import com.denguewatch.bd.model.side
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The record as a document a reader can hand to a doctor — laid out as the
 * iOS build lays it out, block for block.
 *
 * Drawn rather than laid out: Android has no view-to-PDF renderer that would
 * take the Compose screen, so each block is painted directly. That is also what
 * keeps the file readable on a clinic computer with no Bengali font — the text
 * is drawn with the device's own typeface, which does have one.
 *
 * Paginated by block — header, summary, charts, one card per record — so a
 * card, a chart or a reading is never cut across two pages.
 *
 * Written when the reader actually shares, never on opening the log, and off
 * the main thread by its caller.
 */
object HealthLogPdf {

    // A4 at 72dpi, which is what PdfDocument's page units are.
    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842
    internal const val MARGIN = 36f
    internal const val CONTENT = PAGE_WIDTH - 2 * MARGIN
    /** Room kept at the top and foot of a page for its running header and number. */
    internal const val CHROME_TOP = 30f
    internal const val CHROME_BOTTOM = 34f
    internal const val GAP = 10f

    /**
     * Writes the document into the app's own cache and returns the file.
     *
     * The same cache sub-folder the report capture uses, which is the only path
     * the app's FileProvider will share.
     *
     * @param numbers each day's "Day N", counted across the whole log. Passed in
     *   when exporting part of it, so the third day of an illness is still
     *   "Day 3" on its own page rather than "Day 1".
     */
    fun write(
        context: Context,
        days: List<HealthLogDay>,
        loc: Localization,
        unit: TemperatureUnit,
        names: Map<Long, String> = emptyMap(),
        numbers: Map<Long, Int>? = null,
        generatedAt: Long = System.currentTimeMillis(),
    ): File = write(File(context.cacheDir, "reports"), days, loc, unit, names, numbers, generatedAt)

    fun write(
        folder: File,
        days: List<HealthLogDay>,
        loc: Localization,
        unit: TemperatureUnit,
        names: Map<Long, String> = emptyMap(),
        numbers: Map<Long, Int>? = null,
        generatedAt: Long = System.currentTimeMillis(),
    ): File {
        val dayNumbers = numbers ?: HealthLog.numbers(days)
        val blocks = Report(days, unit, names, dayNumbers, loc, generatedAt).blocks()
        val pages = paginate(blocks)

        val document = PdfDocument()
        pages.forEachIndexed { index, placed ->
            val page = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
            )
            placed.forEach { (block, top) -> block.draw(page.canvas, MARGIN, top) }
            drawChrome(page.canvas, index + 1, pages.size, loc, generatedAt)
            document.finishPage(page)
        }

        folder.mkdirs()
        // One export at a time: a reader who shares a dozen times should not be
        // carrying a dozen PDFs of their health in the app's cache.
        folder.listFiles()
            ?.filter { it.name.startsWith("DengueWatch-health-log") || it.name == "health-log.pdf" }
            ?.forEach { it.delete() }
        val name = if (days.size == 1) {
            filename(days[0].dateMillis, dayNumbers[days[0].dateMillis])
        } else {
            filename(generatedAt)
        }
        val file = File(folder, "$name.pdf")
        file.outputStream().use(document::writeTo)
        document.close()
        return file
    }

    /**
     * Dated, so a reader who shares twice does not overwrite the first file in
     * their share target. One day is named for that day; the whole log for the
     * day it left.
     */
    fun filename(millis: Long, day: Int? = null): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))
        return if (day == null) "DengueWatch-health-log-$stamp" else "DengueWatch-health-log-day-$day-$stamp"
    }

    // MARK: - Pagination

    /**
     * Greedy, block by block. A block that would cross the foot of the page
     * starts the next one; a day's heading is never left alone at the bottom
     * of a page without its first record.
     */
    private fun paginate(blocks: List<Block>): List<List<Pair<Block, Float>>> {
        // Page 1 opens with the report's own header, not the running one.
        val firstTop = MARGIN
        val top = MARGIN + CHROME_TOP
        val bottom = PAGE_HEIGHT - MARGIN - CHROME_BOTTOM
        val pages = mutableListOf(mutableListOf<Pair<Block, Float>>())
        var cursor = firstTop
        blocks.forEachIndexed { index, block ->
            var needed = block.height
            if (block.keepWithNext && index + 1 < blocks.size) needed += GAP + blocks[index + 1].height
            val pageTop = if (pages.size == 1) firstTop else top
            if (cursor + needed > bottom && cursor > pageTop) {
                pages += mutableListOf<Pair<Block, Float>>()
                cursor = top
            }
            pages.last() += block to cursor
            cursor += block.height + GAP
        }
        return pages
    }

    private fun drawChrome(c: Canvas, page: Int, of: Int, loc: Localization, generatedAt: Long) {
        if (page > 1) {
            brandMark(c, MARGIN, MARGIN - 4f, 14f)
            val label = paint(8.5f, Ink.SECONDARY, Weight.MEDIUM)
            line(c, "${loc.t("dash.title")} · ${loc.t("log.pdf.title")}", MARGIN + 20f, MARGIN - 2f, label)
        }
        val rule = PAGE_HEIGHT - MARGIN - 10f
        c.drawRect(MARGIN, rule, PAGE_WIDTH - MARGIN, rule + 0.75f, fill(Ink.HAIRLINE))
        val small = paint(8f, Ink.SECONDARY)
        line(c, loc.t("log.pdf.generated", loc.dayAndTime(generatedAt)), MARGIN, rule + 7f, small)
        val number = loc.t("pdf.page", loc.num(page), loc.num(of))
        line(c, number, PAGE_WIDTH - MARGIN - small.measureText(number), rule + 7f, small)
    }

    // MARK: - What goes on the pages

    private class Report(
        val days: List<HealthLogDay>,
        val unit: TemperatureUnit,
        val names: Map<Long, String>,
        val numbers: Map<Long, Int>,
        val loc: Localization,
        val generatedAt: Long,
    ) {
        val vitals = days.flatMap { it.vitals }.sortedBy { it.dateMillis }
        val labs = days.flatMap { it.labs }.sortedBy { it.dateMillis }
        val fluids = days.flatMap { it.fluids }.sortedBy { it.dateMillis }

        fun blocks(): List<Block> = buildList {
            val dates = days.map { it.dateMillis }.sorted()
            val fluidDays = FluidLog.daily(fluids)
            add(HeaderBlock(loc, dates.firstOrNull() ?: generatedAt, dates.lastOrNull() ?: generatedAt,
                            vitals.size, labs.size, fluidDays.size))
            glanceTiles().takeIf { it.isNotEmpty() }?.let { add(GlanceBlock(loc, it)) }
            // Its own block rather than more glance tiles: those grade each
            // figure Normal / Low / High, and fluids have no range to grade.
            if (fluids.isNotEmpty()) add(FluidGlanceBlock(loc, FluidLog.summary(fluids, generatedAt)))

            // Side by side when there are two: stacked, a pair of charts took a
            // third of the first page and pushed the records off it.
            val charts = buildList {
                val temperatures = vitals.mapNotNull { e -> e.temperature?.let { e.dateMillis to it } }
                if (temperatures.size >= 2) {
                    val usual = VitalKind.TEMPERATURE.usual
                    add(ChartSpec(
                        title = loc.t("vital.temperature"),
                        unitLabel = unit.symbol,
                        points = temperatures.map { (t, c) ->
                            Triple(t, unit.fromCelsius(c), VitalKind.TEMPERATURE.status(c))
                        },
                        usual = unit.fromCelsius(usual.start)..unit.fromCelsius(usual.endInclusive),
                        decimals = 1,
                    ))
                }
                val platelets = labs.mapNotNull { r -> r.platelets?.let { r.dateMillis to it } }
                if (platelets.size >= 2) {
                    add(ChartSpec(
                        title = loc.t("lab.platelets"),
                        unitLabel = loc.t("lab.unit.platelets"),
                        points = platelets.map { (t, v) -> Triple(t, v, LabMeasure.PLATELETS.status(v)) },
                        usual = LabMeasure.PLATELETS.typical,
                        decimals = 0,
                    ))
                }
            }
            if (charts.isNotEmpty()) add(ChartsBlock(loc, charts))

            // Intake and urine in two charts, never one: they are different
            // units, and this app draws no dual-axis chart. Two days are the
            // least that show a direction, as with the trend charts above.
            if (fluidDays.size >= 2) {
                val today = HealthLog.startOfDay(generatedAt)
                add(FluidChartsBlock(loc, listOf(
                    BarSpec(loc.t("pdf.fluids.chart.intake"), loc.t("fluid.unit.ml"),
                            fluidDays.map { (d, f) -> Triple(d, f.drunkMillilitres.toDouble(), d == today) }, null),
                    BarSpec(loc.t("pdf.fluids.chart.urine"), loc.t("fluid.unit.times"),
                            fluidDays.map { (d, f) -> Triple(d, f.urinations.toDouble(), d == today) },
                            FluidLog.URINATION_AIM.first.toDouble()),
                )))
            }

            days.forEach { day ->
                add(DayHeadingBlock(loc, day.dateMillis, numbers[day.dateMillis], names[day.dateMillis]))
                if (day.fluids.isNotEmpty()) add(FluidCardBlock(loc, day.fluids))
                day.itemsNewestFirst.forEach { item ->
                    add(when (item) {
                        is HealthLogDay.Item.Vitals -> vitalsCard(item.entry)
                        is HealthLogDay.Item.Lab -> labCard(item.report)
                    })
                }
            }
            add(FooterBlock(loc))
        }

        private fun glanceTiles(): List<Tile> = buildList {
            vitals.filter { it.temperature != null }.maxByOrNull { it.temperature!! }?.let { e ->
                val c = e.temperature!!
                add(Tile(loc.t("pdf.glance.peakTemp"), loc.decimal(unit.fromCelsius(c), 1), unit.symbol,
                         VitalKind.TEMPERATURE.side(c), VitalKind.TEMPERATURE.status(c), loc.dayMonth(e.dateMillis)))
            }
            labs.filter { it.platelets != null }.minByOrNull { it.platelets!! }?.let { r ->
                val v = r.platelets!!
                add(Tile(loc.t("pdf.glance.lowestPlatelets"), loc.num(Math.round(v).toInt()),
                         loc.t("lab.unit.platelets"), LabMeasure.PLATELETS.side(v),
                         LabMeasure.PLATELETS.status(v), loc.dayMonth(r.dateMillis)))
            }
            vitals.lastOrNull { it.systolic != null && it.diastolic != null }?.let { e ->
                val (side, status) = MeasureSide.bloodPressure(e.systolic!!, e.diastolic!!)
                add(Tile(loc.t("pdf.glance.latestBP"),
                         "${loc.num(e.systolic.toInt())}/${loc.num(e.diastolic.toInt())}",
                         loc.t("vital.unit.systolic"), side, status, loc.dayMonth(e.dateMillis)))
            }
            vitals.filter { it.oxygenSaturation != null }.minByOrNull { it.oxygenSaturation!! }?.let { e ->
                val v = e.oxygenSaturation!!
                add(Tile(loc.t("pdf.glance.lowestOxygen"), loc.num(Math.round(v).toInt()), "%",
                         VitalKind.OXYGEN_SATURATION.side(v), VitalKind.OXYGEN_SATURATION.status(v),
                         loc.dayMonth(e.dateMillis)))
            }
        }

        private fun range(r: ClosedFloatingPointRange<Double>, places: Int) =
            "${loc.decimal(r.start, places)}–${loc.decimal(r.endInclusive, places)}"

        private fun vitalsCard(entry: VitalsEntry): Block {
            val cells = buildList {
                entry.temperature?.let { c ->
                    val usual = VitalKind.TEMPERATURE.usual
                    add(Cell(loc.t("vital.temperature"), loc.decimal(unit.fromCelsius(c), 1), unit.symbol,
                             VitalKind.TEMPERATURE.side(c), VitalKind.TEMPERATURE.status(c),
                             range(unit.fromCelsius(usual.start)..unit.fromCelsius(usual.endInclusive), 1)))
                }
                val systolic = entry.systolic
                val diastolic = entry.diastolic
                if (systolic != null && diastolic != null) {
                    val (side, status) = MeasureSide.bloodPressure(systolic, diastolic)
                    add(Cell(loc.t("vital.bloodPressure"),
                             "${loc.num(systolic.toInt())}/${loc.num(diastolic.toInt())}",
                             loc.t("vital.unit.systolic"), side, status,
                             "${range(VitalKind.SYSTOLIC.usual, 0)}/${range(VitalKind.DIASTOLIC.usual, 0)}"))
                }
                listOf(VitalKind.SYSTOLIC, VitalKind.DIASTOLIC, VitalKind.PULSE, VitalKind.OXYGEN_SATURATION)
                    .forEach { kind ->
                        // The two halves of a full blood pressure are the cell above.
                        if (kind == VitalKind.SYSTOLIC && diastolic != null) return@forEach
                        if (kind == VitalKind.DIASTOLIC && systolic != null) return@forEach
                        val v = entry.value(kind) ?: return@forEach
                        add(Cell(loc.t(kind.labelKey), loc.decimal(v, kind.decimals), loc.t(kind.unitKey),
                                 kind.side(v), kind.status(v), range(kind.usual, kind.decimals)))
                    }
            }
            return RecordCardBlock(loc, loc.t("vital.section"), Ink.ACCENT, entry.dateMillis,
                                   cells, emptyList(), entry.note)
        }

        private fun labCard(report: LabReport): Block {
            val cells = LabMeasure.entries.mapNotNull { measure ->
                val v = report.value(measure) ?: return@mapNotNull null
                Cell(loc.t(measure.labelKey), loc.decimal(v, measure.decimals), loc.t(measure.unitKey),
                     measure.side(v), measure.status(v), range(measure.typical, measure.decimals))
            }
            // Tests that were not done are left out: "NS1 not done" is not a
            // result, and a doctor reading this wants the ones that were.
            val tests = DengueTest.entries.mapNotNull { test ->
                val result = report.result(test)
                if (result == TestResult.NOT_DONE) null
                else Triple(loc.t(test.labelKey), loc.t(result.labelKey), result == TestResult.POSITIVE)
            }
            return RecordCardBlock(loc, loc.t("lab.report"), Ink.LAB, report.dateMillis,
                                   cells, tests, report.note)
        }
    }

}
