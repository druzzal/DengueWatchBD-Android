package com.denguewatch.bd.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The three tab glyphs Material has no equivalent for.
 *
 * The other two tabs map cleanly onto the icon set — `map` and `cross.case`
 * are, near enough, `Outlined.Map` and `Filled.MedicalServices`. These three do
 * not, and the substitutes were saying something different from the iOS app:
 *
 *   * Home was `Outlined.Insights`, a sparkline with sparkles beside it. The
 *     sparkles read as "AI" on Android, which is not what a surveillance figure
 *     is offering.
 *   * Check was `Outlined.Checklist`, which describes the form rather than the
 *     subject — iOS uses a stethoscope, which says "clinical" before a word is
 *     read.
 *   * Prevent was `Outlined.HealthAndSafety`, a shield with a medical cross in
 *     it. iOS uses a half-filled shield, and the cross made it a second medical
 *     icon sitting next to Care's bag.
 *
 * Drawn at the 24dp Material grid so they sit on the same optical weight as the
 * two stock icons beside them, with a 1.8dp stroke to match their line weight.
 */
private const val STROKE = 1.8f

private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()

private fun ImageVector.Builder.stroked(
    block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
) = path(
    fill = null,
    stroke = SolidColor(Color.Black),
    strokeLineWidth = STROKE,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
    pathBuilder = block,
)

private fun ImageVector.Builder.filled(
    block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
) = path(fill = SolidColor(Color.Black), pathBuilder = block)

/** `chart.line.uptrend.xyaxis` — axes, a rising series, an arrow head. */
val DwTabIcons_Trend: ImageVector by lazy {
    icon("dw_trend") {
        // The two axes, as one open corner.
        stroked {
            moveTo(3.5f, 3f)
            lineTo(3.5f, 20.5f)
            lineTo(21f, 20.5f)
        }
        // The series, rising left to right with one dip — an epidemic curve
        // shape rather than a straight line.
        stroked {
            moveTo(6.5f, 16.5f)
            lineTo(10.5f, 11.5f)
            lineTo(13.5f, 14f)
            lineTo(19f, 7f)
        }
        // Arrow head on the end of the series.
        stroked {
            moveTo(15.2f, 7f)
            lineTo(19f, 7f)
            lineTo(19f, 10.8f)
        }
    }
}

/** `stethoscope` — binaural tubing, a stem, and the chest piece. */
val DwTabIcons_Stethoscope: ImageVector by lazy {
    icon("dw_stethoscope") {
        // The binaural: down from both ear tips, curving together.
        stroked {
            moveTo(6.5f, 2.5f)
            lineTo(6.5f, 8.5f)
            curveTo(6.5f, 11.54f, 8.96f, 14f, 12f, 14f)
            curveTo(15.04f, 14f, 17.5f, 11.54f, 17.5f, 8.5f)
            lineTo(17.5f, 2.5f)
        }
        // The stem, running down and across to the head.
        stroked {
            moveTo(12f, 14f)
            lineTo(12f, 16.5f)
            curveTo(12f, 18.43f, 13.57f, 20f, 15.5f, 20f)
            lineTo(16.2f, 20f)
        }
        // The chest piece.
        filled {
            moveTo(18.6f, 20f)
            arcToRelative(2.4f, 2.4f, 0f, true, true, -4.8f, 0f)
            arcToRelative(2.4f, 2.4f, 0f, true, true, 4.8f, 0f)
            close()
        }
    }
}

/** `shield.lefthalf.filled` — the outline, with the leading half solid. */
val DwTabIcons_Shield: ImageVector by lazy {
    icon("dw_shield") {
        stroked {
            moveTo(12f, 2.5f)
            lineTo(20f, 5.5f)
            lineTo(20f, 11.5f)
            curveTo(20f, 16.7f, 16.6f, 20.7f, 12f, 21.9f)
            curveTo(7.4f, 20.7f, 4f, 16.7f, 4f, 11.5f)
            lineTo(4f, 5.5f)
            close()
        }
        // The filled half. Drawn to the shield's own centre line, so the two
        // halves meet exactly rather than leaving a hairline of background.
        filled {
            moveTo(12f, 2.5f)
            lineTo(4f, 5.5f)
            lineTo(4f, 11.5f)
            curveTo(4f, 16.7f, 7.4f, 20.7f, 12f, 21.9f)
            close()
        }
    }
}
