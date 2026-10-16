package com.denguewatch.bd.feature.triage

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.denguewatch.bd.i18n.Localization
import com.denguewatch.bd.model.FluidEntry
import com.denguewatch.bd.model.FluidLog
import com.denguewatch.bd.model.FluidSummary
import com.denguewatch.bd.model.DengueTest
import com.denguewatch.bd.model.HealthLog
import com.denguewatch.bd.model.HealthLogDay
import com.denguewatch.bd.model.LabMeasure
import com.denguewatch.bd.model.LabReport
import com.denguewatch.bd.model.MeasureSide
import com.denguewatch.bd.model.MeasureStatus
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.model.TemperatureUnit
import com.denguewatch.bd.model.TestResult
import com.denguewatch.bd.model.VitalKind
import com.denguewatch.bd.model.VitalsEntry
import com.denguewatch.bd.model.side
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

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
    private const val MARGIN = 36f
    private const val CONTENT = PAGE_WIDTH - 2 * MARGIN
    /** Room kept at the top and foot of a page for its running header and number. */
    private const val CHROME_TOP = 30f
    private const val CHROME_BOTTOM = 34f
    private const val GAP = 10f

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
                         "${loc.num(e.systolic.toInt())}/${loc.num(e.diastolic!!.toInt())}",
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

    // MARK: - Blocks

    private abstract class Block(val keepWithNext: Boolean = false) {
        abstract val height: Float
        abstract fun draw(c: Canvas, x: Float, top: Float)
    }

    private class HeaderBlock(
        val loc: Localization,
        val first: Long,
        val last: Long,
        val readings: Int,
        val labs: Int,
        val fluidDays: Int,
    ) : Block() {
        private val pad = 15f
        private val brand = paint(11f, Ink.ACCENT, Weight.MEDIUM)
        private val subtitle = paint(9f, Ink.SECONDARY)
        private val title = paint(25f, Ink.INK, Weight.BOLD)
        private val chips = listOf(
            if (first == last) loc.fullDate(first)
            else loc.t("pdf.period", loc.fullDate(first), loc.fullDate(last)),
            loc.t("pdf.counts", loc.num(readings), loc.num(labs)),
        ).plus(listOfNotNull(fluidDays.takeIf { it > 0 }?.let { loc.t("pdf.fluidDays", loc.num(it)) }))
            .map { Chip(it) }
        override val height = pad + 26f + 11f + lineHeight(title) + 6f + chips.maxOf { it.height } + pad

        override fun draw(c: Canvas, x: Float, top: Float) {
            val box = RectF(x, top, x + CONTENT, top + height)
            // Solid steps either end: a translucent colour inside a gradient
            // prints as a dark band.
            val wash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(box.left, box.top, box.right, box.bottom,
                                        Ink.HEADER_START, Ink.CARD, Shader.TileMode.CLAMP)
            }
            c.drawRoundRect(box, 14f, 14f, wash)
            var y = top + pad
            brandMark(c, x + pad, y, 26f)
            line(c, loc.t("dash.title"), x + pad + 36f, y + 1f, brand)
            line(c, loc.t("pdf.subtitle"), x + pad + 36f, y + 1f + lineHeight(brand) + 1f, subtitle)
            y += 26f + 11f
            line(c, loc.t("log.pdf.title"), x + pad, y, title)
            y += lineHeight(title) + 6f
            var chipX = x + pad
            chips.forEach { chip ->
                chip.draw(c, chipX, y)
                chipX += chip.width + 8f
            }
        }
    }

    private class Chip(val text: String) {
        val paint = paint(9f, Ink.INK, Weight.MEDIUM)
        val width = paint.measureText(text) + 16f
        val height = lineHeight(paint) + 8f
        fun draw(c: Canvas, x: Float, top: Float) {
            val box = RectF(x, top, x + width, top + height)
            c.drawRoundRect(box, height / 2, height / 2, fill(Ink.WHITE))
            c.drawRoundRect(box, height / 2, height / 2, stroke(Ink.HAIRLINE))
            line(c, text, x + 8f, top + 4f, paint)
        }
    }

    private class Tile(
        val label: String, val value: String, val unit: String,
        val side: MeasureSide, val status: MeasureStatus, val date: String,
    )

    private class GlanceBlock(val loc: Localization, val tiles: List<Tile>) : Block() {
        private val heading = paint(13f, Ink.INK, Weight.BOLD)
        private val tileWidth = (CONTENT - 8f * (tiles.size - 1)) / tiles.size
        private val labelPaint = paint(8.5f, Ink.SECONDARY, Weight.MEDIUM)
        private val unitPaint = paint(8f, Ink.SECONDARY)
        private val datePaint = paint(7.5f, Ink.SECONDARY)
        // Wrapped, never cut: a tile grows to its longest label.
        private val labels = tiles.map { text(it.label, labelPaint, tileWidth - 18f) }
        private val pills = tiles.map { Pill(loc.t(it.side.labelKey), it.status) }
        private val labelHeight = labels.maxOf { it.height.toFloat() }
        private val tileHeight = 9f + labelHeight + 4f + lineHeight(valuePaint(MeasureStatus.NORMAL)) +
            4f + pills.maxOf { it.height } + 4f + lineHeight(datePaint) + 9f
        override val height = lineHeight(heading) + 8f + tileHeight

        private fun valuePaint(status: MeasureStatus) = paint(19f, inkFor(status), Weight.BOLD)

        override fun draw(c: Canvas, x: Float, top: Float) {
            line(c, loc.t("pdf.glance"), x, top, heading)
            val tileTop = top + lineHeight(heading) + 8f
            tiles.forEachIndexed { i, tile ->
                val left = x + i * (tileWidth + 8f)
                val box = RectF(left, tileTop, left + tileWidth, tileTop + tileHeight)
                c.drawRoundRect(box, 10f, 10f, fill(Ink.CARD))
                c.drawRoundRect(box, 10f, 10f, stroke(Ink.HAIRLINE))
                var y = tileTop + 9f
                drawText(c, labels[i], left + 9f, y)
                y += labelHeight + 4f
                val value = valuePaint(tile.status)
                valueWithUnit(c, tile.value, value, tile.unit, unitPaint, left + 9f, y)
                y += lineHeight(value) + 4f
                pills[i].draw(c, left + 9f, y)
                y += pills[i].height + 4f
                line(c, tile.date, left + 9f, y, datePaint)
            }
        }
    }

    private class ChartSpec(
        val title: String,
        val unitLabel: String,
        val points: List<Triple<Long, Double, MeasureStatus>>,
        val usual: ClosedFloatingPointRange<Double>,
        val decimals: Int,
    )

    private class ChartsBlock(val loc: Localization, val charts: List<ChartSpec>) : Block() {
        private val plotHeight = 104f
        private val titlePaint = paint(11f, Ink.INK, Weight.BOLD)
        private val unitPaint = paint(8.5f, Ink.SECONDARY)
        private val legendPaint = paint(7.5f, Ink.SECONDARY)
        private val axisPaint = paint(7f, Ink.SECONDARY)
        override val height = 10f + lineHeight(titlePaint) + 8f + plotHeight + 4f + lineHeight(axisPaint) + 10f

        override fun draw(c: Canvas, x: Float, top: Float) {
            val width = (CONTENT - 8f * (charts.size - 1)) / charts.size
            charts.forEachIndexed { i, chart -> drawChart(c, chart, x + i * (width + 8f), top, width) }
        }

        private fun drawChart(c: Canvas, chart: ChartSpec, left: Float, top: Float, width: Float) {
            val box = RectF(left, top, left + width, top + height)
            c.drawRoundRect(box, 12f, 12f, fill(Ink.WHITE))
            c.drawRoundRect(box, 12f, 12f, stroke(Ink.HAIRLINE))

            // Title and unit, and the band's key on the right.
            val headY = top + 10f
            line(c, chart.title, left + 10f, headY, titlePaint)
            val titleWidth = titlePaint.measureText(chart.title)
            line(c, chart.unitLabel, left + 14f + titleWidth,
                 headY + baselineOf(titlePaint) - baselineOf(unitPaint), unitPaint)
            val legend = loc.t("pdf.band")
            val legendX = left + width - 10f - legendPaint.measureText(legend)
            val swatchTop = headY + (lineHeight(titlePaint) - 8f) / 2
            c.drawRoundRect(RectF(legendX - 16f, swatchTop, legendX - 4f, swatchTop + 8f), 1.5f, 1.5f,
                            fill(withAlpha(riskTint(RiskLevel.LOW), 0.16f)))
            line(c, legend, legendX, headY + (lineHeight(titlePaint) - lineHeight(legendPaint)) / 2, legendPaint)

            // Domain: the readings and the usual range, padded.
            val values = chart.points.map { it.second } + listOf(chart.usual.start, chart.usual.endInclusive)
            val low = values.min()
            val high = values.max()
            val pad = max((high - low) * 0.12, if (chart.decimals == 0) 10.0 else 0.3)
            val yMin = low - pad
            val yMax = high + pad
            val earliest = chart.points.first().first
            val latest = chart.points.last().first
            // Padded either side, or the last date's label is cut at the edge.
            val timePad = max(6L * 3_600_000L, ((latest - earliest) * 0.06).toLong())
            val xMin = earliest - timePad
            val xMax = latest + timePad

            val plotTop = headY + lineHeight(titlePaint) + 8f
            val plotLeft = left + 34f
            val plotRight = left + width - 12f
            val plotBottom = plotTop + plotHeight
            fun px(t: Long) = plotLeft + ((t - xMin).toFloat() / (xMax - xMin).toFloat()) * (plotRight - plotLeft)
            fun py(v: Double) = plotBottom - ((v - yMin) / (yMax - yMin)).toFloat() * plotHeight

            // Horizontal grid with values.
            // About five lines: one degree on a fever chart, a hundred on platelets.
            val step = niceStep((yMax - yMin) / 5)
            var tick = ceil(yMin / step) * step
            while (tick <= yMax) {
                val y = py(tick)
                c.drawRect(plotLeft, y, plotRight, y + 0.5f, fill(Ink.HAIRLINE))
                val label = loc.decimal(tick, chart.decimals)
                line(c, label, plotLeft - 5f - axisPaint.measureText(label), y - lineHeight(axisPaint) / 2, axisPaint)
                tick += step
            }

            // Whole days along the bottom, at most about six labels. Automatic
            // ticks fell every twelve hours on a short log and printed each
            // date twice.
            val dayMillis = 86_400_000L
            val spanDays = ((latest - earliest) / dayMillis).toInt()
            val stride = max(1, ceil(spanDays / 6.0).toInt())
            val calendar = Calendar.getInstance().apply {
                timeInMillis = xMin
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, 1)
            }
            val labelTop = plotBottom + 4f
            while (calendar.timeInMillis <= xMax) {
                val t = calendar.timeInMillis
                val xPos = px(t)
                c.drawRect(xPos, plotTop, xPos + 0.5f, plotBottom, fill(Ink.HAIRLINE))
                val label = loc.dayMonth(t)
                val w = axisPaint.measureText(label)
                if (xPos - w / 2 >= left + 4f && xPos + w / 2 <= left + width - 4f) {
                    line(c, label, xPos - w / 2, labelTop, axisPaint)
                }
                calendar.add(Calendar.DAY_OF_YEAR, stride)
            }

            // The usual range, shaded.
            val bandTop = py(minOf(chart.usual.endInclusive, yMax))
            val bandBottom = py(maxOf(chart.usual.start, yMin))
            c.drawRect(plotLeft, bandTop, plotRight, bandBottom, fill(withAlpha(riskTint(RiskLevel.LOW), 0.10f)))

            // The readings: a line, then a point for each, coloured by status.
            val path = Path()
            chart.points.forEachIndexed { i, (t, v, _) ->
                if (i == 0) path.moveTo(px(t), py(v)) else path.lineTo(px(t), py(v))
            }
            c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 1.6f; color = Ink.ACCENT
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            })
            chart.points.forEach { (t, v, status) ->
                c.drawCircle(px(t), py(v), 2.6f,
                             fill(riskTint(status.risk)))
            }
        }

        /** 1, 2, 2.5 or 5 times a power of ten — gridlines a person reads easily. */
        private fun niceStep(rough: Double): Double {
            val magnitude = 10.0.pow(floor(log10(abs(rough).coerceAtLeast(1e-9))))
            val residual = rough / magnitude
            val nice = when {
                residual <= 1 -> 1.0
                residual <= 2 -> 2.0
                residual <= 2.5 -> 2.5
                residual <= 5 -> 5.0
                else -> 10.0
            }
            return nice * magnitude
        }
    }

    private class DayHeadingBlock(
        val loc: Localization,
        val dateMillis: Long,
        val number: Int?,
        val name: String?,
    ) : Block(keepWithNext = true) {
        private val badgePaint = paint(9f, Ink.WHITE, Weight.BOLD)
        private val datePaint = paint(13f, Ink.INK, Weight.BOLD)
        private val namePaint = paint(10f, Ink.SECONDARY, Weight.MEDIUM)
        override val height = 6f + lineHeight(datePaint)

        override fun draw(c: Canvas, x: Float, top: Float) {
            val y = top + 6f
            var cursor = x
            number?.let {
                val text = loc.t("fever.timeline.day", loc.num(it))
                val w = badgePaint.measureText(text) + 14f
                val h = lineHeight(badgePaint) + 6f
                val badgeTop = y + (lineHeight(datePaint) - h) / 2
                c.drawRoundRect(RectF(cursor, badgeTop, cursor + w, badgeTop + h), h / 2, h / 2, fill(Ink.ACCENT))
                line(c, text, cursor + 7f, badgeTop + 3f, badgePaint)
                cursor += w + 8f
            }
            val date = loc.fullDate(dateMillis)
            line(c, date, cursor, y, datePaint)
            cursor += datePaint.measureText(date) + 8f
            name?.let {
                line(c, it, cursor, y + baselineOf(datePaint) - baselineOf(namePaint), namePaint)
            }
        }
    }

    private class Cell(
        val label: String, val value: String, val unit: String,
        val side: MeasureSide, val status: MeasureStatus, val usual: String,
    )

    /**
     * One record as the app shows it: a card with a coloured edge, its kind and
     * time, and a grid of readings — each with Normal / Low / High and the
     * usual range it was judged against, so the word is never a verdict out of
     * nowhere and a doctor can apply their own range instead.
     */
    private class RecordCardBlock(
        val loc: Localization,
        val title: String,
        val tint: Int,
        val dateMillis: Long,
        val cells: List<Cell>,
        val tests: List<Triple<String, String, Boolean>>,
        val note: String,
    ) : Block() {
        private val pad = 10f
        private val innerLeft = pad + 3f
        private val innerWidth = CONTENT - innerLeft - pad
        private val cellWidth = (innerWidth - 6f * 3) / 4
        private val titlePaint = paint(10f, Ink.INK, Weight.MEDIUM)
        private val timePaint = paint(9f, Ink.SECONDARY, Weight.MEDIUM)
        private val cellLabel = paint(8f, Ink.SECONDARY, Weight.MEDIUM)
        private val cellUnit = paint(7.5f, Ink.SECONDARY)
        // Seven points, not six and a half: smaller, Android sets the space
        // so thin that "Usual 150–450" reads as one word.
        private val cellUsual = paint(7f, Ink.SECONDARY)
        private val testPaint = paint(8.5f, Ink.INK, Weight.MEDIUM)
        private val notePaint = paint(9f, Ink.SECONDARY, Weight.ITALIC)
        private val pills = cells.map { Pill(loc.t(it.side.labelKey), it.status) }
        // Labels and ranges wrap rather than being cut, and every cell takes
        // the height of the tallest so the grid stays square.
        private val labelLayouts = cells.map { text(it.label, cellLabel, cellWidth - 14f) }
        private val usualLayouts = cells.map { text(loc.t("pdf.usual", it.usual), cellUsual, cellWidth - 14f) }
        private val labelHeight = labelLayouts.maxOfOrNull { it.height.toFloat() } ?: 0f
        private val usualHeight = usualLayouts.maxOfOrNull { it.height.toFloat() } ?: 0f
        private val cellHeight = 7f + labelHeight + 3f + lineHeight(cellValue(MeasureStatus.NORMAL)) +
            3f + (pills.maxOfOrNull { it.height } ?: 0f) + 3f + usualHeight + 7f
        private val rows = ceil(cells.size / 4.0).toInt()
        private val gridHeight = if (rows == 0) 0f else rows * cellHeight + (rows - 1) * 6f
        private val testHeight = if (tests.isEmpty()) 0f else 7f + lineHeight(testPaint) + 6f
        private val noteLayout = note.takeIf { it.isNotBlank() }?.let { text(it, notePaint, innerWidth) }
        override val height = pad + lineHeight(titlePaint) + 7f + gridHeight + testHeight +
            (noteLayout?.let { 7f + it.height } ?: 0f) + pad

        private fun cellValue(status: MeasureStatus) = paint(14f, inkFor(status), Weight.BOLD)

        override fun draw(c: Canvas, x: Float, top: Float) {
            val box = RectF(x, top, x + CONTENT, top + height)
            c.drawRoundRect(box, 11f, 11f, fill(Ink.CARD))
            // The coloured edge, clipped to the card's own rounded corners.
            c.save()
            c.clipPath(Path().apply { addRoundRect(box, 11f, 11f, Path.Direction.CW) })
            c.drawRect(x, top, x + 3.5f, top + height, fill(tint))
            c.restore()

            val left = x + innerLeft
            var y = top + pad
            c.drawCircle(left + 3f, y + lineHeight(titlePaint) / 2, 3f, fill(tint))
            line(c, title, left + 11f, y, titlePaint)
            val time = loc.timeOfDay(dateMillis)
            line(c, time, left + innerWidth - timePaint.measureText(time),
                 y + baselineOf(titlePaint) - baselineOf(timePaint), timePaint)
            y += lineHeight(titlePaint) + 7f

            cells.forEachIndexed { i, cell ->
                val cx = left + (i % 4) * (cellWidth + 6f)
                val cy = y + (i / 4) * (cellHeight + 6f)
                c.drawRoundRect(RectF(cx, cy, cx + cellWidth, cy + cellHeight), 8f, 8f, fill(Ink.WHITE))
                var yy = cy + 7f
                drawText(c, labelLayouts[i], cx + 7f, yy)
                yy += labelHeight + 3f
                val value = cellValue(cell.status)
                valueWithUnit(c, cell.value, value, cell.unit, cellUnit, cx + 7f, yy)
                yy += lineHeight(value) + 3f
                pills[i].draw(c, cx + 7f, yy)
                yy += pills[i].height + 3f
                drawText(c, usualLayouts[i], cx + 7f, yy)
            }
            y += gridHeight

            if (tests.isNotEmpty()) {
                y += 7f
                var tx = left
                tests.forEach { (name, result, positive) ->
                    val ink = if (positive) riskInk(RiskLevel.SEVERE) else Ink.INK
                    val namePaint = paint(8.5f, ink, Weight.MEDIUM)
                    val resultPaint = paint(8.5f, ink, Weight.BOLD)
                    val w = namePaint.measureText(name) + 4f + resultPaint.measureText(result) + 16f
                    val h = lineHeight(testPaint) + 6f
                    val chip = RectF(tx, y, tx + w, y + h)
                    if (positive) {
                        c.drawRoundRect(chip, h / 2, h / 2, fill(withAlpha(riskTint(RiskLevel.SEVERE), 0.12f)))
                    } else {
                        c.drawRoundRect(chip, h / 2, h / 2, fill(Ink.WHITE))
                        c.drawRoundRect(chip, h / 2, h / 2, stroke(Ink.HAIRLINE))
                    }
                    line(c, name, tx + 8f, y + 3f, namePaint)
                    line(c, result, tx + 12f + namePaint.measureText(name), y + 3f, resultPaint)
                    tx += w + 6f
                }
                y += testHeight - 7f
            }

            noteLayout?.let {
                y += 7f
                drawText(c, it, left, y)
            }
        }
    }

    /** The one thing this document must not be mistaken for. */
    private class FooterBlock(val loc: Localization) : Block() {
        private val pad = 12f
        private val textWidth = CONTENT - 2 * pad - 18f
        private val disclaimer = text(loc.t("pdf.disclaimer"), paint(8.5f, Ink.INK), textWidth)
        private val privacy = text(loc.t("log.privacyFooter"), paint(8f, Ink.SECONDARY), textWidth)
        override val height = 6f + pad + disclaimer.height + 4f + privacy.height + pad

        override fun draw(c: Canvas, x: Float, top: Float) {
            val boxTop = top + 6f
            c.drawRoundRect(RectF(x, boxTop, x + CONTENT, top + height), 10f, 10f, fill(Ink.ACCENT_SOFT))
            val iconY = boxTop + pad + 5f
            c.drawCircle(x + pad + 5f, iconY, 5f, fill(Ink.ACCENT))
            val i = paint(7f, Ink.WHITE, Weight.BOLD)
            line(c, "i", x + pad + 5f - i.measureText("i") / 2, iconY - lineHeight(i) / 2, i)
            drawText(c, disclaimer, x + pad + 18f, boxTop + pad)
            drawText(c, privacy, x + pad + 18f, boxTop + pad + disclaimer.height + 4f)
        }
    }

    /**
     * Normal / Low / High as a pill: risk ink on its own soft wash, the way the
     * app marks a reading — green in the usual range, amber to red outside it.
     * Always with its word and a dot, so the page still reads when printed in
     * black and white.
     */
    private class Pill(val word: String, val status: MeasureStatus) {
        private val paint = paint(8f, inkFor(status), Weight.MEDIUM)
        val width = paint.measureText(word) + 19f
        val height = lineHeight(paint) + 4f

        fun draw(c: Canvas, x: Float, top: Float) {
            val box = RectF(x, top, x + width, top + height)
            c.drawRoundRect(box, height / 2, height / 2, fill(withAlpha(riskTint(status.risk), 0.14f)))
            c.drawCircle(x + 8.2f, top + height / 2, 2.2f, fill(riskInk(status.risk)))
            line(c, word, x + 13f, top + 2f, paint)
        }
    }

    // MARK: - Fluids

    /**
     * The fluid log summed for the front page: days logged, the average drunk
     * on a whole day, and on how many days urine reached the bottom of WHO's
     * aim. Plain figures in ink — no status colour, because there is no usual
     * range for intake, and the urine aim is stated rather than graded.
     */
    private class FluidGlanceBlock(val loc: Localization, summary: FluidSummary) : Block() {
        private val heading = paint(13f, Ink.INK, Weight.BOLD)
        private val labelPaint = paint(8.5f, Ink.SECONDARY, Weight.MEDIUM)
        private val valuePaint = paint(19f, Ink.INK, Weight.BOLD)
        private val unitPaint = paint(8f, Ink.SECONDARY)
        private val notePaint = paint(7.5f, Ink.SECONDARY)
        private val figures = buildList {
            add(Triple(loc.t("pdf.fluids.days"), loc.num(summary.daysLogged), ""))
            summary.averageMillilitres?.let {
                add(Triple(loc.t("pdf.fluids.average"), loc.num(it), loc.t("fluid.unit.ml")))
            }
            if (summary.daysWithUrineLogged > 0) {
                add(Triple(
                    loc.t("pdf.fluids.aimMet", loc.num(FluidLog.URINATION_AIM.first)),
                    loc.t("pdf.fluids.ofDays", loc.num(summary.daysUrineAimMet),
                          loc.num(summary.daysWithUrineLogged)),
                    "",
                ))
            }
        }
        private val tileWidth = (CONTENT - 8f * (figures.size - 1)) / figures.size
        private val labels = figures.map { text(it.first, labelPaint, tileWidth - 18f) }
        // Every tile as tall as the tallest label, so the figures line up.
        private val labelHeight = labels.maxOf { it.height.toFloat() }
        private val tileHeight = 9f + labelHeight + 4f + lineHeight(valuePaint) + 9f
        private val note = if (summary.includesToday) text(loc.t("pdf.fluids.wholeDays"), notePaint, CONTENT) else null
        override val height = lineHeight(heading) + 8f + tileHeight + (note?.let { 6f + it.height } ?: 0f)

        override fun draw(c: Canvas, x: Float, top: Float) {
            line(c, loc.t("fluid.log.title"), x, top, heading)
            val tileTop = top + lineHeight(heading) + 8f
            figures.forEachIndexed { i, (_, value, unit) ->
                val left = x + i * (tileWidth + 8f)
                val box = RectF(left, tileTop, left + tileWidth, tileTop + tileHeight)
                c.drawRoundRect(box, 10f, 10f, fill(Ink.CARD))
                c.drawRoundRect(box, 10f, 10f, stroke(Ink.HAIRLINE))
                drawText(c, labels[i], left + 9f, tileTop + 9f)
                valueWithUnit(c, value, valuePaint, unit, unitPaint, left + 9f, tileTop + 9f + labelHeight + 4f)
            }
            note?.let { drawText(c, it, x, tileTop + tileHeight + 6f) }
        }
    }

    private class BarSpec(
        val title: String,
        val unitLabel: String,
        /** (day, value, still in progress) */
        val bars: List<Triple<Long, Double, Boolean>>,
        /** WHO's aim, drawn as a dashed line; null where there is none. */
        val aim: Double?,
    )

    /**
     * One bar per logged day, two charts side by side. Today's bar is paler and
     * labelled, because it is still being filled in and would otherwise read as
     * a bad day. The urine chart carries WHO's aim as a dashed line.
     */
    private class FluidChartsBlock(val loc: Localization, val charts: List<BarSpec>) : Block() {
        private val plotHeight = 96f
        private val titlePaint = paint(11f, Ink.INK, Weight.BOLD)
        private val unitPaint = paint(8.5f, Ink.SECONDARY)
        private val legendPaint = paint(7.5f, Ink.SECONDARY)
        private val axisPaint = paint(7f, Ink.SECONDARY)
        override val height = 10f + lineHeight(titlePaint) + 4f + lineHeight(legendPaint) + 8f +
            plotHeight + 4f + lineHeight(axisPaint) + 10f

        override fun draw(c: Canvas, x: Float, top: Float) {
            val width = (CONTENT - 8f * (charts.size - 1)) / charts.size
            charts.forEachIndexed { i, chart -> drawChart(c, chart, x + i * (width + 8f), top, width) }
        }

        private fun drawChart(c: Canvas, chart: BarSpec, left: Float, top: Float, width: Float) {
            val box = RectF(left, top, left + width, top + height)
            c.drawRoundRect(box, 12f, 12f, fill(Ink.WHITE))
            c.drawRoundRect(box, 12f, 12f, stroke(Ink.HAIRLINE))

            val headY = top + 10f
            line(c, chart.title, left + 10f, headY, titlePaint)
            line(c, chart.unitLabel, left + 14f + titlePaint.measureText(chart.title),
                 headY + baselineOf(titlePaint) - baselineOf(unitPaint), unitPaint)

            // The legend on a line of its own: beside a half-width title it
            // squeezed the title, and Bangla titles run longer still.
            val legendY = headY + lineHeight(titlePaint) + 4f
            val mid = legendY + lineHeight(legendPaint) / 2
            var lx = left + 10f
            chart.aim?.let { aim ->
                c.drawLine(lx, mid, lx + 12f, mid, dashed(Ink.INK))
                lx += 15f
                val label = loc.t("pdf.fluids.aim", loc.num(aim.toInt()))
                line(c, label, lx, legendY, legendPaint)
                lx += legendPaint.measureText(label) + 8f
            }
            if (chart.bars.any { it.third }) {
                c.drawRoundRect(RectF(lx, mid - 4f, lx + 8f, mid + 4f), 1.5f, 1.5f, fill(Ink.ACCENT_MUTED))
                line(c, loc.t("pdf.fluids.today"), lx + 11f, legendY, legendPaint)
            }

            val plotTop = legendY + lineHeight(legendPaint) + 8f
            val plotLeft = left + 34f
            val plotRight = left + width - 10f
            val plotBottom = plotTop + plotHeight
            val ceiling = maxOf(chart.bars.maxOf { it.second }, chart.aim ?: 0.0, 1.0) * 1.15
            fun py(v: Double) = plotBottom - (v / ceiling).toFloat() * plotHeight

            // Gridlines at whole numbers: a tick at 2.5 urinations would label
            // something that cannot happen.
            val step = wholeStep(ceiling / 4)
            var tick = 0.0
            while (tick <= ceiling) {
                val y = py(tick)
                c.drawRect(plotLeft, y, plotRight, y + 0.5f, fill(Ink.HAIRLINE))
                val label = loc.num(tick.toInt())
                line(c, label, plotLeft - 5f - axisPaint.measureText(label), y - lineHeight(axisPaint) / 2, axisPaint)
                tick += step
            }

            val slot = (plotRight - plotLeft) / chart.bars.size
            val barWidth = minOf(slot * 0.7f, 40f)
            chart.bars.forEachIndexed { i, (day, value, partial) ->
                val cx = plotLeft + slot * (i + 0.5f)
                val barTop = py(value)
                c.drawRoundRect(RectF(cx - barWidth / 2, barTop, cx + barWidth / 2, plotBottom), 2f, 2f,
                                fill(if (partial) Ink.ACCENT_MUTED else Ink.ACCENT))
                val label = loc.dayMonth(day)
                // Every label where they fit, else every other one.
                val every = if (axisPaint.measureText(label) + 4f > slot) 2 else 1
                if (i % every == 0) {
                    line(c, label, cx - axisPaint.measureText(label) / 2, plotBottom + 4f, axisPaint)
                }
            }
            chart.aim?.let { aim ->
                val y = py(aim)
                c.drawLine(plotLeft, y, plotRight, y, dashed(Ink.INK))
            }
        }

        /** 1, 2 or 5 times a power of ten, never below one. */
        private fun wholeStep(rough: Double): Double {
            if (rough <= 1) return 1.0
            val magnitude = 10.0.pow(floor(log10(rough)))
            val residual = rough / magnitude
            return magnitude * when {
                residual <= 1 -> 1.0
                residual <= 2 -> 2.0
                residual <= 5 -> 5.0
                else -> 10.0
            }
        }
    }

    /**
     * A day's fluids as WHO asks for them to be reviewed: the total drunk, how
     * many drinks, and how many times urine was passed against WHO's aim. No
     * Normal / Low word: there is no volume target to judge it against.
     */
    private class FluidCardBlock(val loc: Localization, entries: List<FluidEntry>) : Block() {
        private val pad = 10f
        private val innerLeft = pad + 3f
        private val innerWidth = CONTENT - innerLeft - pad
        private val cellWidth = (innerWidth - 6f * 3) / 4
        private val titlePaint = paint(10f, Ink.INK, Weight.MEDIUM)
        private val cellLabel = paint(8f, Ink.SECONDARY, Weight.MEDIUM)
        private val cellValue = paint(14f, Ink.INK, Weight.BOLD)
        private val cellUnit = paint(7.5f, Ink.SECONDARY)
        private val cellDetail = paint(7f, Ink.SECONDARY)
        private val day = FluidLog.day(entries.first().dateMillis, entries)
        private val cells = listOf(
            Triple(loc.t("fluid.drunk"), loc.num(day.drunkMillilitres), loc.t("fluid.unit.ml")),
            Triple(loc.t("fluid.drinks"), loc.num(day.drinks), ""),
            Triple(loc.t("fluid.urine"), loc.num(day.urinations), loc.t("fluid.unit.times")),
        )
        private val aim = text(
            loc.t("fluid.urine.aim", loc.num(FluidLog.URINATION_AIM.first), loc.num(FluidLog.URINATION_AIM.last)),
            cellDetail, cellWidth - 14f,
        )
        private val labels = cells.map { text(it.first, cellLabel, cellWidth - 14f) }
        private val labelHeight = labels.maxOf { it.height.toFloat() }
        private val cellHeight = 7f + labelHeight + 3f + lineHeight(cellValue) + 3f + aim.height + 7f
        override val height = pad + lineHeight(titlePaint) + 7f + cellHeight + pad

        override fun draw(c: Canvas, x: Float, top: Float) {
            val box = RectF(x, top, x + CONTENT, top + height)
            c.drawRoundRect(box, 11f, 11f, fill(Ink.CARD))
            c.save()
            c.clipPath(Path().apply { addRoundRect(box, 11f, 11f, Path.Direction.CW) })
            c.drawRect(x, top, x + 3.5f, top + height, fill(Ink.ACCENT))
            c.restore()

            val left = x + innerLeft
            var y = top + pad
            c.drawCircle(left + 3f, y + lineHeight(titlePaint) / 2, 3f, fill(Ink.ACCENT))
            line(c, loc.t("fluid.log.title"), left + 11f, y, titlePaint)
            y += lineHeight(titlePaint) + 7f

            cells.forEachIndexed { i, (_, value, unit) ->
                val cx = left + i * (cellWidth + 6f)
                c.drawRoundRect(RectF(cx, y, cx + cellWidth, y + cellHeight), 8f, 8f, fill(Ink.WHITE))
                drawText(c, labels[i], cx + 7f, y + 7f)
                val valueTop = y + 7f + labelHeight + 3f
                valueWithUnit(c, value, cellValue, unit, cellUnit, cx + 7f, valueTop)
                // The aim under the urine count, for the doctor to read rather
                // than graded here.
                if (i == 2) drawText(c, aim, cx + 7f, valueTop + lineHeight(cellValue) + 3f)
            }
        }
    }

    // MARK: - Drawing helpers

    private enum class Weight { REGULAR, MEDIUM, BOLD, ITALIC }

    /** Ink and surfaces for paper: the light steps of the app's own palette. */
    private object Ink {
        const val INK = 0xFF1C1C1E.toInt()
        const val SECONDARY = 0xFF6B6B70.toInt()
        const val CARD = 0xFFF6F7F9.toInt()
        const val HAIRLINE = 0xFFE3E4E8.toInt()
        const val ACCENT = 0xFF007A9C.toInt()
        /** The accent at about a tenth, flattened onto white. */
        const val ACCENT_SOFT = 0xFFE6F2F5.toInt()
        /** The accent at about forty per cent, flattened onto white, for a bar still being filled in. */
        const val ACCENT_MUTED = 0xFF99CAD8.toInt()
        const val HEADER_START = 0xFFDCEDF2.toInt()
        const val LAB = 0xFF2A78D6.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
    }

    private fun riskTint(level: RiskLevel): Int = when (level) {
        RiskLevel.LOW -> 0xFF157F3B.toInt()
        RiskLevel.MODERATE -> 0xFFF0B400.toInt()
        RiskLevel.HIGH -> 0xFFE2600A.toInt()
        RiskLevel.SEVERE -> 0xFF9E1B13.toInt()
    }

    private fun riskInk(level: RiskLevel): Int = when (level) {
        RiskLevel.LOW -> 0xFF0F6B30.toInt()
        RiskLevel.MODERATE -> 0xFF8A6100.toInt()
        RiskLevel.HIGH -> 0xFF9C3F00.toInt()
        RiskLevel.SEVERE -> 0xFF8F1710.toInt()
    }

    /** A reading's ink: green in the usual range, amber to red outside it. */
    private fun inkFor(status: MeasureStatus): Int = riskInk(status.risk)

    private fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((alpha * 255).toInt() shl 24)

    private fun paint(size: Float, color: Int, weight: Weight = Weight.REGULAR) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = when (weight) {
                Weight.REGULAR -> Typeface.DEFAULT
                Weight.MEDIUM -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
                Weight.BOLD -> Typeface.DEFAULT_BOLD
                Weight.ITALIC -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            }
        }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun stroke(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = 0.75f
    }

    private fun dashed(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = 0.8f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f, 2f), 0f)
    }

    private fun lineHeight(paint: Paint): Float = paint.fontMetrics.let { it.descent - it.ascent }

    /** Distance from a line's top to its baseline. */
    private fun baselineOf(paint: Paint): Float = -paint.fontMetrics.ascent

    /** One line of text with its top at [top]. */
    private fun line(c: Canvas, text: String, x: Float, top: Float, paint: Paint) {
        c.drawText(text, x, top + baselineOf(paint), paint)
    }

    /** A figure with its unit beside it, on the figure's baseline. */
    private fun valueWithUnit(c: Canvas, value: String, valuePaint: Paint, unit: String,
                              unitPaint: Paint, x: Float, top: Float) {
        line(c, value, x, top, valuePaint)
        if (unit.isNotEmpty()) {
            c.drawText(unit, x + valuePaint.measureText(value) + 2f, top + baselineOf(valuePaint), unitPaint)
        }
    }

    /** Wrapped text: Paint draws a line at a time and will not wrap on its own. */
    private fun text(value: String, paint: TextPaint, width: Float): StaticLayout =
        StaticLayout.Builder.obtain(value, 0, value.length, paint, max(1, width.toInt()))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()

    private fun drawText(c: Canvas, layout: StaticLayout, x: Float, top: Float) {
        c.save()
        c.translate(x, top)
        layout.draw(c)
        c.restore()
    }

    /** The app's mark, drawn rather than loaded: a droplet in the theme colour. */
    private fun brandMark(c: Canvas, x: Float, top: Float, size: Float) {
        c.drawRoundRect(RectF(x, top, x + size, top + size), size * 0.27f, size * 0.27f, fill(Ink.ACCENT))
        val cx = x + size / 2
        val cy = top + size * 0.58f
        val r = size * 0.19f
        val drop = Path().apply {
            moveTo(cx, top + size * 0.2f)
            lineTo(cx + r * 0.93f, cy - r * 0.37f)
            lineTo(cx - r * 0.93f, cy - r * 0.37f)
            close()
            addCircle(cx, cy, r, Path.Direction.CW)
        }
        c.drawPath(drop, fill(Ink.WHITE))
    }
}
