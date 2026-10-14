package com.denguewatch.bd.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
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

/**
 * Filled, with inner subpaths punched out rather than drawn over.
 *
 * `heart.text.square` is a solid tile with the heart and the line showing the
 * surface behind it. Painting them in the background colour would work until
 * the tile sat on the selected tab's tinted pill, where the wrong colour would
 * show through — an even-odd hole is the surface, whatever it happens to be.
 */
private fun ImageVector.Builder.punched(
    block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
) = path(
    fill = SolidColor(Color.Black),
    pathFillType = PathFillType.EvenOdd,
    pathBuilder = block,
)

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

/** `heart.text.square` — a card with a heart over a line of writing. */
val DwTabIcons_HealthCard: ImageVector by lazy {
    icon("dw_health_card") {
        punched {
            // The tile.
            moveTo(7.5f, 3f)
            lineTo(16.5f, 3f)
            arcToRelative(4.5f, 4.5f, 0f, false, true, 4.5f, 4.5f)
            lineTo(21f, 16.5f)
            arcToRelative(4.5f, 4.5f, 0f, false, true, -4.5f, 4.5f)
            lineTo(7.5f, 21f)
            arcToRelative(4.5f, 4.5f, 0f, false, true, -4.5f, -4.5f)
            lineTo(3f, 7.5f)
            arcToRelative(4.5f, 4.5f, 0f, false, true, 4.5f, -4.5f)
            close()

            // The heart, punched out of it.
            moveTo(12f, 14.1f)
            curveTo(12f, 14.1f, 8.1f, 11.6f, 8.1f, 9.7f)
            curveTo(8.1f, 8.5f, 9f, 7.7f, 10f, 7.7f)
            curveTo(10.85f, 7.7f, 11.6f, 8.25f, 12f, 8.85f)
            curveTo(12.4f, 8.25f, 13.15f, 7.7f, 14f, 7.7f)
            curveTo(15f, 7.7f, 15.9f, 8.5f, 15.9f, 9.7f)
            curveTo(15.9f, 11.6f, 12f, 14.1f, 12f, 14.1f)
            close()

            // The line of writing under it.
            moveTo(8.4f, 16f)
            lineTo(15.6f, 16f)
            arcToRelative(0.7f, 0.7f, 0f, false, true, 0f, 1.4f)
            lineTo(8.4f, 17.4f)
            arcToRelative(0.7f, 0.7f, 0f, false, true, 0f, -1.4f)
            close()
        }
    }
}
