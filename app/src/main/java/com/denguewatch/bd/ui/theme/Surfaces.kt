package com.denguewatch.bd.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The standard card surface: soft fill, hairline edge, and a two-part shadow
 * that seats the card on the plane behind it.
 *
 * Two shadows rather than one: a tight contact shadow that seats the card on
 * the plane, and a wider, softer one that gives it height. A single blur can do
 * one job or the other, never both.
 *
 * In dark mode both collapse to nothing, because a drop shadow on near-black
 * reads as grime rather than elevation — there the hairline and the lighter
 * card fill carry the separation instead.
 */
@Composable
fun Modifier.cardSurface(
    shape: Shape = Shapes.card,
    fill: Color = Dw.colors.card,
    border: Color = Dw.colors.hairline,
): Modifier {
    val colors = Dw.colors
    return if (colors.dark) {
        this.background(fill, shape).border(0.5.dp, border, shape)
    } else {
        this
            .shadow(1.dp, shape, ambientColor = colors.cardShadow, spotColor = colors.cardShadow)
            .shadow(10.dp, shape, ambientColor = colors.cardShadow, spotColor = colors.cardShadow)
            .background(fill, shape)
            .border(0.5.dp, border, shape)
    }
}

/**
 * Press feedback for a whole card acting as a button.
 *
 * Pair it with the same [interactionSource] the click modifier is given, so the
 * scale follows the real press rather than a second, guessed one.
 */
@Composable
fun Modifier.pressable(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = Motion.interactive(),
        label = "pressScale",
    )
    val opacity by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = Motion.interactive(),
        label = "pressAlpha",
    )
    return this.scale(scale).alpha(opacity)
}

@Composable
fun rememberPressSource() = remember { MutableInteractionSource() }

/**
 * Constrain content to a comfortable reading column and centre it.
 *
 * Applied to every scrolling screen. Without it the phone layout simply expands
 * on a tablet: hospital rows a thousand points wide with the name at one edge
 * and its button at the other, symptom rows trailing into empty space, single
 * figures adrift in half-metre cards.
 *
 * A phone is unaffected — it never reaches the cap.
 */
fun Modifier.readableColumn(width: Dp = Layout.readableWidth): Modifier =
    this.widthIn(max = width)

/** A titled card for chart and list content. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    padding: Dp = Space.card,
    shape: Shape = Shapes.card,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .cardSurface(shape = shape)
            .padding(padding),
        contentAlignment = Alignment.TopStart,
        content = content,
    )
}

/** A shape with only the top corners rounded, for cards that top a stack. */
fun topRounded(radius: Dp) = RoundedCornerShape(topStart = radius, topEnd = radius)
