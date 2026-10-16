package com.denguewatch.bd.feature.triage

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.denguewatch.bd.feature.triage.PdfDraw.Ink
import com.denguewatch.bd.feature.triage.PdfDraw.Weight
import com.denguewatch.bd.feature.triage.PdfDraw.baselineOf
import com.denguewatch.bd.feature.triage.PdfDraw.brandMark
import com.denguewatch.bd.feature.triage.PdfDraw.dashed
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
import com.denguewatch.bd.model.MeasureStatus
import com.denguewatch.bd.model.RiskLevel
import kotlin.math.max

/**
 * Paints, inks and text helpers for drawing the health log onto paper.
 * Shared by HealthLogPdf and its blocks; nothing here knows about the report.
 */
internal object PdfDraw {

    // MARK: - Drawing helpers

    internal enum class Weight { REGULAR, MEDIUM, BOLD, ITALIC }

    /** Ink and surfaces for paper: the light steps of the app's own palette. */
    internal object Ink {
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

    internal fun riskTint(level: RiskLevel): Int = when (level) {
        RiskLevel.LOW -> 0xFF157F3B.toInt()
        RiskLevel.MODERATE -> 0xFFF0B400.toInt()
        RiskLevel.HIGH -> 0xFFE2600A.toInt()
        RiskLevel.SEVERE -> 0xFF9E1B13.toInt()
    }

    internal fun riskInk(level: RiskLevel): Int = when (level) {
        RiskLevel.LOW -> 0xFF0F6B30.toInt()
        RiskLevel.MODERATE -> 0xFF8A6100.toInt()
        RiskLevel.HIGH -> 0xFF9C3F00.toInt()
        RiskLevel.SEVERE -> 0xFF8F1710.toInt()
    }

    /** A reading's ink: green in the usual range, amber to red outside it. */
    internal fun inkFor(status: MeasureStatus): Int = riskInk(status.risk)

    internal fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((alpha * 255).toInt() shl 24)

    internal fun paint(size: Float, color: Int, weight: Weight = Weight.REGULAR) =
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

    internal fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    internal fun stroke(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = 0.75f
    }

    internal fun dashed(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = 0.8f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f, 2f), 0f)
    }

    internal fun lineHeight(paint: Paint): Float = paint.fontMetrics.let { it.descent - it.ascent }

    /** Distance from a line's top to its baseline. */
    internal fun baselineOf(paint: Paint): Float = -paint.fontMetrics.ascent

    /** One line of text with its top at [top]. */
    internal fun line(c: Canvas, text: String, x: Float, top: Float, paint: Paint) {
        c.drawText(text, x, top + baselineOf(paint), paint)
    }

    /** A figure with its unit beside it, on the figure's baseline. */
    internal fun valueWithUnit(c: Canvas, value: String, valuePaint: Paint, unit: String,
                              unitPaint: Paint, x: Float, top: Float) {
        line(c, value, x, top, valuePaint)
        if (unit.isNotEmpty()) {
            c.drawText(unit, x + valuePaint.measureText(value) + 2f, top + baselineOf(valuePaint), unitPaint)
        }
    }

    /** Wrapped text: Paint draws a line at a time and will not wrap on its own. */
    internal fun text(value: String, paint: TextPaint, width: Float): StaticLayout =
        StaticLayout.Builder.obtain(value, 0, value.length, paint, max(1, width.toInt()))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()

    internal fun drawText(c: Canvas, layout: StaticLayout, x: Float, top: Float) {
        c.save()
        c.translate(x, top)
        layout.draw(c)
        c.restore()
    }

    /** The app's mark, drawn rather than loaded: a droplet in the theme colour. */
    internal fun brandMark(c: Canvas, x: Float, top: Float, size: Float) {
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
