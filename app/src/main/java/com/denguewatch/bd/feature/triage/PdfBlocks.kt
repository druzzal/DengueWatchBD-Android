package com.denguewatch.bd.feature.triage

import kotlin.math.pow
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.denguewatch.bd.feature.triage.HealthLogPdf.CONTENT
import com.denguewatch.bd.feature.triage.PdfDraw.Ink
import com.denguewatch.bd.feature.triage.PdfDraw.Weight
import com.denguewatch.bd.feature.triage.PdfDraw.baselineOf
import com.denguewatch.bd.feature.triage.PdfDraw.brandMark
import com.denguewatch.bd.feature.triage.PdfDraw.drawText
import com.denguewatch.bd.feature.triage.PdfDraw.fill
import com.denguewatch.bd.feature.triage.PdfDraw.inkFor
import com.denguewatch.bd.feature.triage.PdfDraw.line
import com.denguewatch.bd.feature.triage.PdfDraw.lineHeight
import com.denguewatch.bd.feature.triage.PdfDraw.paint
import com.denguewatch.bd.feature.triage.PdfDraw.riskInk
import com.denguewatch.bd.feature.triage.PdfDraw.riskTint
import com.denguewatch.bd.feature.triage.PdfDraw.stroke
import com.denguewatch.bd.feature.triage.PdfDraw.text
import com.denguewatch.bd.feature.triage.PdfDraw.valueWithUnit
import com.denguewatch.bd.feature.triage.PdfDraw.withAlpha
import com.denguewatch.bd.i18n.Localization
import com.denguewatch.bd.model.MeasureSide
import com.denguewatch.bd.model.MeasureStatus
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.model.side
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max

/*
 * The pieces a health-log page is built from: the report header, the glance
 * tiles, the charts, a day heading, a record card, the footer. Each knows its
 * own height, so HealthLogPdf can paginate before drawing anything.
 */

// MARK: - Blocks

internal abstract class Block(val keepWithNext: Boolean = false) {
    abstract val height: Float
    abstract fun draw(c: Canvas, x: Float, top: Float)
}

internal class HeaderBlock(
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

internal class Chip(val text: String) {
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

internal class Tile(
    val label: String, val value: String, val unit: String,
    val side: MeasureSide, val status: MeasureStatus, val date: String,
)

internal class GlanceBlock(val loc: Localization, val tiles: List<Tile>) : Block() {
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

internal class ChartSpec(
    val title: String,
    val unitLabel: String,
    val points: List<Triple<Long, Double, MeasureStatus>>,
    val usual: ClosedFloatingPointRange<Double>,
    val decimals: Int,
)

internal class ChartsBlock(val loc: Localization, val charts: List<ChartSpec>) : Block() {
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

internal class DayHeadingBlock(
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

internal class Cell(
    val label: String, val value: String, val unit: String,
    val side: MeasureSide, val status: MeasureStatus, val usual: String,
)

/**
 * One record as the app shows it: a card with a coloured edge, its kind and
 * time, and a grid of readings — each with Normal / Low / High and the
 * usual range it was judged against, so the word is never a verdict out of
 * nowhere and a doctor can apply their own range instead.
 */
internal class RecordCardBlock(
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
internal class FooterBlock(val loc: Localization) : Block() {
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
internal class Pill(val word: String, val status: MeasureStatus) {
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
