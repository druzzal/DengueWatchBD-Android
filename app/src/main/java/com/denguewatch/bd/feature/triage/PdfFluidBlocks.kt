package com.denguewatch.bd.feature.triage

import kotlin.math.pow
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import com.denguewatch.bd.feature.triage.HealthLogPdf.CONTENT
import com.denguewatch.bd.feature.triage.PdfDraw.Ink
import com.denguewatch.bd.feature.triage.PdfDraw.Weight
import com.denguewatch.bd.feature.triage.PdfDraw.baselineOf
import com.denguewatch.bd.feature.triage.PdfDraw.dashed
import com.denguewatch.bd.feature.triage.PdfDraw.drawText
import com.denguewatch.bd.feature.triage.PdfDraw.fill
import com.denguewatch.bd.feature.triage.PdfDraw.line
import com.denguewatch.bd.feature.triage.PdfDraw.lineHeight
import com.denguewatch.bd.feature.triage.PdfDraw.paint
import com.denguewatch.bd.feature.triage.PdfDraw.stroke
import com.denguewatch.bd.feature.triage.PdfDraw.text
import com.denguewatch.bd.feature.triage.PdfDraw.valueWithUnit
import com.denguewatch.bd.i18n.Localization
import com.denguewatch.bd.model.FluidEntry
import com.denguewatch.bd.model.FluidLog
import com.denguewatch.bd.model.FluidSummary
import com.denguewatch.bd.model.side
import kotlin.math.floor
import kotlin.math.log10

/*
 * The fluid log's pieces of the health-log PDF: the summary, the daily intake
 * and urine charts, and a day's drinks.
 */

// MARK: - Fluids

/**
 * The fluid log summed for the front page: days logged, the average drunk
 * on a whole day, and on how many days urine reached the bottom of WHO's
 * aim. Plain figures in ink — no status colour, because there is no usual
 * range for intake, and the urine aim is stated rather than graded.
 */
internal class FluidGlanceBlock(val loc: Localization, summary: FluidSummary) : Block() {
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

internal class BarSpec(
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
internal class FluidChartsBlock(val loc: Localization, val charts: List<BarSpec>) : Block() {
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
internal class FluidCardBlock(val loc: Localization, entries: List<FluidEntry>) : Block() {
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
