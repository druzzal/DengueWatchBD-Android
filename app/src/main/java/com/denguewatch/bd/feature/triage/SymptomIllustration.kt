package com.denguewatch.bd.feature.triage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.ui.theme.Dw
import kotlin.math.cos
import kotlin.math.sin

/**
 * One illustration per symptom, composed from a shared set of motifs.
 *
 * Everything is drawn in a normalised 100×100 space and scaled to the frame, so
 * a motif is identical at 46dp in a list row and at 66dp on the result screen.
 * Drawing them rather than shipping bitmaps keeps the app small, keeps both
 * colour schemes correct, and avoids any question of image licensing.
 *
 * The severity tint carries the same meaning as everywhere else in the app:
 * core symptoms in the series blue, warning and emergency signs on the ordinal
 * risk ramp. Colour is never the only cue — each drawing differs in shape, and
 * every use sits beside its label.
 */
@Composable
fun SymptomIllustration(
    symptomId: String,
    group: Symptom.Group,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val colors = Dw.colors
    val accent = when (group) {
        Symptom.Group.CORE -> colors.cases
        Symptom.Group.WARNING -> colors.riskTint(RiskLevel.HIGH)
        Symptom.Group.SEVERE -> colors.riskTint(RiskLevel.SEVERE)
    }

    Box(modifier.size(size).clearAndSetSemantics { }) {
        Canvas(Modifier.size(size)) {
            drawCircle(accent.copy(alpha = 0.12f), radius = this.size.minDimension / 2)
            val padding = this.size.minDimension * 0.16f
            inset(padding) {
                drawMotif(symptomId, accent)
            }
        }
    }
}

// MARK: - Normalised drawing space

/** Map a point given in 0…100 space onto the current canvas. */
private fun DrawScope.n(x: Float, y: Float) = Offset(size.width * x / 100f, size.height * y / 100f)

/** Scale a length given in 0…100 space. */
private fun DrawScope.s(value: Float) = size.minDimension * value / 100f

private fun DrawScope.line(
    from: Pair<Float, Float>,
    to: Pair<Float, Float>,
    color: Color,
    width: Float,
) = drawLine(
    color,
    n(from.first, from.second),
    n(to.first, to.second),
    strokeWidth = s(width),
    cap = StrokeCap.Round,
)

private fun DrawScope.dot(at: Pair<Float, Float>, radius: Float, color: Color) =
    drawCircle(color, radius = s(radius), center = n(at.first, at.second))

private fun DrawScope.ring(at: Pair<Float, Float>, radius: Float, width: Float, color: Color) =
    drawCircle(
        color,
        radius = s(radius),
        center = n(at.first, at.second),
        style = Stroke(width = s(width)),
    )

/**
 * Radiating pain lines, drawn around a focus point. Used wherever a motif has
 * to say "this hurts here" rather than just naming a body part.
 */
private fun DrawScope.radiating(
    origin: Pair<Float, Float>,
    count: Int,
    innerRadius: Float,
    outerRadius: Float,
    arc: Float,
    startAngle: Float,
    color: Color,
    width: Float,
) {
    val centre = n(origin.first, origin.second)
    for (index in 0 until count) {
        val fraction = if (count == 1) 0f else index.toFloat() / (count - 1)
        val radians = Math.toRadians((startAngle + fraction * arc).toDouble())
        val inner = Offset(
            centre.x + (cos(radians) * s(innerRadius)).toFloat(),
            centre.y + (sin(radians) * s(innerRadius)).toFloat(),
        )
        val outer = Offset(
            centre.x + (cos(radians) * s(outerRadius)).toFloat(),
            centre.y + (sin(radians) * s(outerRadius)).toFloat(),
        )
        drawLine(color, inner, outer, strokeWidth = s(width), cap = StrokeCap.Round)
    }
}

/** Head-and-shoulders silhouette used by the motifs that point at a body part. */
private fun DrawScope.torsoPath(): Path {
    val path = Path()
    path.addOval(Rect(n(38f, 12f), Size(s(24f), s(26f))))
    path.moveTo(n(44f, 38f).x, n(44f, 38f).y)
    path.lineTo(n(44f, 46f).x, n(44f, 46f).y)
    path.cubicTo(
        n(38f, 48f).x, n(38f, 48f).y,
        n(26f, 52f).x, n(26f, 52f).y,
        n(22f, 62f).x, n(22f, 62f).y,
    )
    path.lineTo(n(18f, 92f).x, n(18f, 92f).y)
    path.lineTo(n(82f, 92f).x, n(82f, 92f).y)
    path.lineTo(n(78f, 62f).x, n(78f, 62f).y)
    path.cubicTo(
        n(74f, 52f).x, n(74f, 52f).y,
        n(62f, 48f).x, n(62f, 48f).y,
        n(56f, 46f).x, n(56f, 46f).y,
    )
    path.lineTo(n(56f, 38f).x, n(56f, 38f).y)
    path.close()
    return path
}

/** A profile head, for the motifs about headache and consciousness. */
private fun DrawScope.headProfilePath(): Path {
    val path = Path()
    path.moveTo(n(62f, 88f).x, n(62f, 88f).y)
    path.lineTo(n(62f, 70f).x, n(62f, 70f).y)
    path.cubicTo(
        n(74f, 68f).x, n(74f, 68f).y, n(78f, 58f).x, n(78f, 58f).y, n(78f, 46f).x, n(78f, 46f).y,
    )
    path.cubicTo(
        n(78f, 28f).x, n(78f, 28f).y, n(66f, 14f).x, n(66f, 14f).y, n(50f, 14f).x, n(50f, 14f).y,
    )
    path.cubicTo(
        n(34f, 14f).x, n(34f, 14f).y, n(24f, 28f).x, n(24f, 28f).y, n(24f, 44f).x, n(24f, 44f).y,
    )
    path.cubicTo(
        n(24f, 54f).x, n(24f, 54f).y, n(27f, 58f).x, n(27f, 58f).y, n(30f, 62f).x, n(30f, 62f).y,
    )
    path.lineTo(n(26f, 68f).x, n(26f, 68f).y)
    path.cubicTo(
        n(24f, 71f).x, n(24f, 71f).y, n(29f, 72f).x, n(29f, 72f).y, n(34f, 72f).x, n(34f, 72f).y,
    )
    path.lineTo(n(34f, 80f).x, n(34f, 80f).y)
    path.cubicTo(
        n(34f, 86f).x, n(34f, 86f).y, n(40f, 88f).x, n(40f, 88f).y, n(46f, 88f).x, n(46f, 88f).y,
    )
    path.close()
    return path
}

/** A teardrop, used for the blood and fluid motifs. */
private fun DrawScope.dropletPath(): Path {
    val path = Path()
    path.moveTo(n(50f, 8f).x, n(50f, 8f).y)
    path.cubicTo(
        n(62f, 26f).x, n(62f, 26f).y, n(84f, 40f).x, n(84f, 40f).y, n(84f, 58f).x, n(84f, 58f).y,
    )
    path.cubicTo(
        n(84f, 77f).x, n(84f, 77f).y, n(69f, 92f).x, n(69f, 92f).y, n(50f, 92f).x, n(50f, 92f).y,
    )
    path.cubicTo(
        n(31f, 92f).x, n(31f, 92f).y, n(16f, 77f).x, n(16f, 77f).y, n(16f, 58f).x, n(16f, 58f).y,
    )
    path.cubicTo(
        n(16f, 40f).x, n(16f, 40f).y, n(38f, 26f).x, n(38f, 26f).y, n(50f, 8f).x, n(50f, 8f).y,
    )
    path.close()
    return path
}

/** Wavy lines for nausea and fluid build-up. */
private fun DrawScope.wavePath(rows: Int, top: Float, spacing: Float, amplitude: Float): Path {
    val path = Path()
    for (row in 0 until rows) {
        val y = top + row * spacing
        path.moveTo(n(26f, y).x, n(26f, y).y)
        path.cubicTo(
            n(32f, y - amplitude).x, n(32f, y - amplitude).y,
            n(44f, y + amplitude).x, n(44f, y + amplitude).y,
            n(50f, y).x, n(50f, y).y,
        )
        path.cubicTo(
            n(56f, y - amplitude).x, n(56f, y - amplitude).y,
            n(68f, y + amplitude).x, n(68f, y + amplitude).y,
            n(74f, y).x, n(74f, y).y,
        )
    }
    return path
}

// MARK: - The motifs

private fun DrawScope.drawMotif(symptomId: String, accent: Color) {
    when (symptomId) {
        "fever" -> fever(accent)
        "headache" -> headache(accent)
        "aches" -> aches(accent)
        "rash" -> rash(accent)
        "nausea" -> nausea(accent)
        "abdominal" -> abdominal(accent)
        "vomiting" -> vomiting(accent)
        "oralIntolerance" -> oralIntolerance(accent)
        "bleeding" -> bleeding(accent)
        "fatigue" -> fatigue(accent)
        "fluid" -> fluid(accent)
        "noUrine" -> noUrine(accent)
        "faint" -> faint(accent)
        "confusion" -> confusion(accent)
        "heavyBleed" -> heavyBleed(accent)
        else -> fallback(accent)
    }
}

/** A thermometer with heat rising off the bulb. */
private fun DrawScope.fever(accent: Color) {
    drawRoundRect(
        accent.copy(alpha = 0.25f),
        topLeft = n(44f, 14f),
        size = Size(s(12f), s(60f)),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(s(6f), s(6f)),
    )
    drawCircle(accent.copy(alpha = 0.25f), radius = s(12f), center = n(50f, 78f))
    // The mercury column and bulb, filled solid.
    drawRoundRect(
        accent,
        topLeft = n(46f, 40f),
        size = Size(s(8f), s(34f)),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(s(4f), s(4f)),
    )
    drawCircle(accent, radius = s(9f), center = n(50f, 78f))
    ring(50f to 78f, 12f, 3.5f, accent)
    radiating(76f to 30f, 3, 4f, 15f, 60f, -75f, accent.copy(alpha = 0.75f), 4f)
}

/** A profile head with the pain focused behind the eye. */
private fun DrawScope.headache(accent: Color) {
    drawPath(headProfilePath(), accent.copy(alpha = 0.22f))
    drawPath(headProfilePath(), accent, style = Stroke(width = s(4.5f)))
    dot(40f to 44f, 5.5f, accent)
    radiating(40f to 44f, 5, 11f, 21f, 200f, 100f, accent, 3.5f)
}

/** A long bone with a joint at each end — the breakbone motif. */
private fun DrawScope.aches(accent: Color) {
    line(26f to 74f, 74f to 26f, accent, 11f)
    listOf(26f to 68f, 38f to 80f, 62f to 20f, 74f to 32f).forEach { (x, y) ->
        dot(x to y, 9f, accent)
    }
    radiating(50f to 50f, 4, 20f, 30f, 240f, 60f, accent.copy(alpha = 0.55f), 3f)
}

/** A torso covered in spots. */
private fun DrawScope.rash(accent: Color) {
    drawPath(torsoPath(), accent.copy(alpha = 0.22f))
    drawPath(torsoPath(), accent, style = Stroke(width = s(4f)))
    listOf(
        34f to 62f, 48f to 58f, 62f to 64f,
        30f to 76f, 44f to 72f, 58f to 78f, 70f to 70f,
        38f to 86f, 54f to 86f,
    ).forEach { (x, y) -> dot(x to y, 4f, accent) }
}

/** Waves rising from the stomach. */
private fun DrawScope.nausea(accent: Color) {
    drawCircle(accent.copy(alpha = 0.18f), radius = s(30f), center = n(50f, 56f))
    drawPath(
        wavePath(rows = 3, top = 40f, spacing = 13f, amplitude = 6f),
        accent,
        style = Stroke(width = s(5f), cap = StrokeCap.Round),
    )
    radiating(50f to 22f, 3, 3f, 12f, 70f, -125f, accent.copy(alpha = 0.6f), 3.5f)
}

/** A torso with the pain concentrated in the belly. */
private fun DrawScope.abdominal(accent: Color) {
    drawPath(torsoPath(), accent.copy(alpha = 0.20f))
    drawPath(torsoPath(), accent, style = Stroke(width = s(4f)))
    dot(50f to 74f, 9f, accent)
    radiating(50f to 74f, 6, 12f, 21f, 300f, 30f, accent, 3.5f)
}

/** A head tipped forward, with the stomach's contents leaving it. */
private fun DrawScope.vomiting(accent: Color) {
    drawCircle(accent.copy(alpha = 0.22f), radius = s(19f), center = n(40f, 32f))
    drawCircle(accent, radius = s(19f), center = n(40f, 32f), style = Stroke(width = s(4f)))
    val path = Path().apply {
        moveTo(n(54f, 42f).x, n(54f, 42f).y)
        cubicTo(
            n(68f, 48f).x, n(68f, 48f).y,
            n(74f, 62f).x, n(74f, 62f).y,
            n(72f, 80f).x, n(72f, 80f).y,
        )
    }
    drawPath(path, accent, style = Stroke(width = s(7f), cap = StrokeCap.Round))
    listOf(60f to 78f, 80f to 74f, 70f to 90f).forEach { (x, y) -> dot(x to y, 4.5f, accent) }
}

/** A droplet of blood, with a second smaller one beside it. */
private fun DrawScope.bleeding(accent: Color) {
    drawPath(dropletPath(), accent.copy(alpha = 0.28f))
    drawPath(dropletPath(), accent, style = Stroke(width = s(5f)))
    dot(50f to 62f, 12f, accent)
}

/** A figure slumped, with the energy drained downward. */
private fun DrawScope.fatigue(accent: Color) {
    drawCircle(accent.copy(alpha = 0.22f), radius = s(15f), center = n(50f, 26f))
    drawCircle(accent, radius = s(15f), center = n(50f, 26f), style = Stroke(width = s(4f)))
    line(50f to 42f, 50f to 68f, accent, 6f)
    // Arms and legs hanging.
    line(50f to 50f, 30f to 66f, accent, 5f)
    line(50f to 50f, 70f to 66f, accent, 5f)
    line(50f to 68f, 36f to 90f, accent, 5f)
    line(50f to 68f, 64f to 90f, accent, 5f)
    radiating(50f to 26f, 3, 19f, 27f, 60f, -120f, accent.copy(alpha = 0.5f), 3f)
}

/** A swollen belly, with fluid pooling. */
private fun DrawScope.fluid(accent: Color) {
    drawCircle(accent.copy(alpha = 0.22f), radius = s(32f), center = n(50f, 54f))
    drawCircle(accent, radius = s(32f), center = n(50f, 54f), style = Stroke(width = s(4f)))
    drawPath(
        wavePath(rows = 2, top = 58f, spacing = 12f, amplitude = 5f),
        accent,
        style = Stroke(width = s(5f), cap = StrokeCap.Round),
    )
    radiating(50f to 16f, 3, 3f, 10f, 60f, -120f, accent.copy(alpha = 0.6f), 3f)
}

/** An empty measure, struck through. */
private fun DrawScope.noUrine(accent: Color) {
    val path = Path().apply {
        moveTo(n(34f, 26f).x, n(34f, 26f).y)
        lineTo(n(66f, 26f).x, n(66f, 26f).y)
        lineTo(n(60f, 84f).x, n(60f, 84f).y)
        lineTo(n(40f, 84f).x, n(40f, 84f).y)
        close()
    }
    drawPath(path, accent.copy(alpha = 0.16f))
    drawPath(path, accent, style = Stroke(width = s(4.5f)))
    // A very low level, and the line saying it should not be this low.
    drawRect(
        accent.copy(alpha = 0.55f),
        topLeft = n(41f, 76f),
        size = Size(s(18f), s(8f)),
    )
    line(24f to 82f, 78f to 20f, accent, 5f)
}

/**
 * A mug, full, struck through: the drink that cannot be taken. A mug with a
 * handle rather than a glass, so it never reads as no-urine's empty glass.
 */
private fun DrawScope.oralIntolerance(accent: Color) {
    val body = Path().apply {
        moveTo(n(28f, 30f).x, n(28f, 30f).y)
        lineTo(n(64f, 30f).x, n(64f, 30f).y)
        lineTo(n(60f, 80f).x, n(60f, 80f).y)
        lineTo(n(32f, 80f).x, n(32f, 80f).y)
        close()
    }
    drawPath(body, accent.copy(alpha = 0.18f))
    drawPath(body, accent, style = Stroke(width = s(4.5f)))
    // The handle.
    drawArc(
        accent,
        startAngle = -90f, sweepAngle = 180f, useCenter = false,
        topLeft = n(56f, 40f), size = Size(s(20f), s(24f)),
        style = Stroke(width = s(4f)),
    )
    // Full to near the brim: there is drink, it is the drinking that fails.
    drawRect(accent.copy(alpha = 0.5f), topLeft = n(31f, 38f), size = Size(s(30f), s(6f)))
    line(22f to 84f, 80f to 22f, accent, 5f)
}

/** A figure falling, with the ground rushing up. */
private fun DrawScope.faint(accent: Color) {
    drawCircle(accent.copy(alpha = 0.22f), radius = s(14f), center = n(36f, 34f))
    drawCircle(accent, radius = s(14f), center = n(36f, 34f), style = Stroke(width = s(4f)))
    line(48f to 44f, 74f to 60f, accent, 6f)
    line(60f to 52f, 52f to 72f, accent, 5f)
    line(60f to 52f, 78f to 74f, accent, 5f)
    // The ground.
    line(16f to 88f, 84f to 88f, accent.copy(alpha = 0.55f), 4f)
    radiating(36f to 34f, 3, 18f, 26f, 70f, -145f, accent.copy(alpha = 0.6f), 3f)
}

/** A head with the thread of thought broken. */
private fun DrawScope.confusion(accent: Color) {
    drawPath(headProfilePath(), accent.copy(alpha = 0.22f))
    drawPath(headProfilePath(), accent, style = Stroke(width = s(4.5f)))
    // A zigzag where a clear line should be.
    val path = Path().apply {
        moveTo(n(34f, 44f).x, n(34f, 44f).y)
        lineTo(n(46f, 34f).x, n(46f, 34f).y)
        lineTo(n(40f, 48f).x, n(40f, 48f).y)
        lineTo(n(54f, 40f).x, n(54f, 40f).y)
        lineTo(n(46f, 56f).x, n(46f, 56f).y)
    }
    drawPath(path, accent, style = Stroke(width = s(5f), cap = StrokeCap.Round))
}

/** Several droplets — bleeding that will not stop. */
private fun DrawScope.heavyBleed(accent: Color) {
    drawPath(dropletPath(), accent.copy(alpha = 0.30f))
    drawPath(dropletPath(), accent, style = Stroke(width = s(5f)))
    listOf(42f to 54f, 58f to 62f, 50f to 76f).forEach { (x, y) -> dot(x to y, 7f, accent) }
}

/** Anything the switch does not know: a plain marker rather than nothing. */
private fun DrawScope.fallback(accent: Color) {
    drawCircle(accent.copy(alpha = 0.25f), radius = s(30f), center = n(50f, 50f))
    dot(50f to 50f, 12f, accent)
}
