package com.denguewatch.bd.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * The spacing, radius and motion scale everything else is built from.
 *
 * Values are named by role rather than by size, so a screen asks for
 * [Space.section] instead of 24 and the rhythm stays consistent when the scale
 * is tuned.
 */
object Space {
    /** Inside a chip or between an icon and its label. */
    val hair = 4.dp
    val tight = 8.dp
    /** Between related rows inside a card. */
    val row = 12.dp
    /** Default padding inside a card. */
    val card = 16.dp
    /** Between stacked cards. */
    val stack = 14.dp
    /** Between major sections of a screen. */
    val section = 28.dp
    /** Screen edge inset. */
    val screen = 20.dp
}

object Radius {
    val chip = 999.dp
    val control = 12.dp
    val card = 18.dp
    /** The hero risk card sits a step above everything else. */
    val hero = 24.dp
}

object Shapes {
    val chip = RoundedCornerShape(Radius.chip)
    val control = RoundedCornerShape(Radius.control)
    val card = RoundedCornerShape(Radius.card)
    val hero = RoundedCornerShape(Radius.hero)
}

object Motion {
    /** State changes the user caused — taps, toggles, selection. */
    fun <T> interactive(): FiniteAnimationSpec<T> =
        tween(260, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))

    /** Content arriving or transitioning. */
    fun <T> content(): FiniteAnimationSpec<T> =
        tween(350, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f))

    /** The slow pulse on the live status dot, in milliseconds. */
    const val PULSE_MILLIS = 1800
}

/**
 * Minimum comfortable hit target. Material's guidance is 48dp, which is a
 * little larger than the 44pt the iOS build uses.
 */
object Hit {
    val minimum = 48.dp
}

object Layout {
    /**
     * Cap on the content column. Beyond roughly this width a line of body text
     * becomes tiring to read and cards turn into mostly-empty rectangles, so
     * the extra space on a tablet becomes margin rather than stretch.
     */
    val readableWidth = 820.dp

    /** Past this the layout is treated as regular width rather than compact. */
    val regularWidthBreakpoint = 600.dp
}
