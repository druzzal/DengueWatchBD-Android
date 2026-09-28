package com.denguewatch.bd.feature.shared

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.Radius
import com.denguewatch.bd.ui.theme.Space
import com.denguewatch.bd.ui.theme.Typo
import com.denguewatch.bd.ui.theme.cardSurface
import com.denguewatch.bd.ui.theme.typo

// MARK: - Skeletons

/**
 * A slow diagonal sheen. Used only while real content is on its way, never as
 * decoration.
 */
@Composable
fun Modifier.shimmering(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerPhase",
    )
    return drawWithContent {
        drawContent()
        val sweep = size.width * 1.6f
        val start = phase * sweep
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.28f),
                    Color.Transparent,
                ),
                start = Offset(start, 0f),
                end = Offset(start + sweep, size.height),
            ),
        )
    }
}

/** A neutral block standing in for text or a figure that has not loaded. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp = 12.dp,
    radius: Dp = 6.dp,
) {
    Box(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(Dw.colors.mutedInk.copy(alpha = 0.16f))
    )
}

/** Placeholder for the hero risk card. */
@Composable
fun RiskCardSkeleton(modifier: Modifier = Modifier) {
    val loading = LocalLocalization.current.t("map.state.loading")
    Column(
        modifier
            .fillMaxWidth()
            .cardSurface(shape = RoundedCornerShape(Radius.hero))
            .padding(Space.card + 4.dp)
            .shimmering()
            .semantics { contentDescription = loading },
        verticalArrangement = Arrangement.spacedBy(Space.row),
    ) {
        SkeletonBlock(width = 96.dp, height = 11.dp)
        SkeletonBlock(width = 168.dp, height = 34.dp, radius = 9.dp)
        SkeletonBlock(width = 132.dp, height = 11.dp)
        SkeletonBlock(height = 6.dp, radius = 3.dp)
    }
}

/** Placeholder grid for the activity metrics. */
@Composable
fun StatGridSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().shimmering().clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(Space.row),
    ) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.row)) {
                repeat(2) {
                    Column(
                        Modifier
                            .weight(1f)
                            .cardSurface()
                            .padding(Space.card),
                        verticalArrangement = Arrangement.spacedBy(Space.tight),
                    ) {
                        SkeletonBlock(width = 74.dp, height = 26.dp, radius = 7.dp)
                        SkeletonBlock(width = 92.dp, height = 10.dp)
                        SkeletonBlock(height = 22.dp, radius = 5.dp)
                    }
                }
            }
        }
    }
}

/** Placeholder for a chart card. */
@Composable
fun ChartSkeleton(modifier: Modifier = Modifier, height: Dp = 180.dp) {
    Column(
        modifier
            .fillMaxWidth()
            .cardSurface()
            .padding(Space.card)
            .shimmering()
            .clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(Space.row),
    ) {
        SkeletonBlock(width = 128.dp, height = 13.dp)
        SkeletonBlock(height = height, radius = 10.dp)
    }
}

/** Placeholder rows for lists. */
@Composable
fun RowsSkeleton(modifier: Modifier = Modifier, count: Int = 4) {
    Column(
        modifier
            .fillMaxWidth()
            .cardSurface()
            .padding(horizontal = Space.card)
            .shimmering()
            .clearAndSetSemantics { },
    ) {
        repeat(count) { index ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = Space.row),
                horizontalArrangement = Arrangement.spacedBy(Space.row),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonBlock(width = 34.dp, height = 34.dp, radius = 10.dp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBlock(width = 150.dp, height = 11.dp)
                    SkeletonBlock(width = 92.dp, height = 9.dp)
                }
            }
            if (index < count - 1) HairlineDivider()
        }
    }
}

// MARK: - Empty and error

/** A designed empty state: icon, explanation, and something to do about it. */
@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionTitle: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = Dw.colors
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = Space.section, horizontal = Space.card),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.row),
    ) {
        Box(
            Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(colors.mutedInk.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.mutedInk, modifier = Modifier.size(34.dp))
        }

        Text(title, style = typo(Typo.HEADLINE), textAlign = TextAlign.Center)
        if (message != null) {
            Text(
                message,
                style = typo(Typo.CALLOUT),
                color = colors.secondaryText,
                textAlign = TextAlign.Center,
            )
        }

        if (actionTitle != null && onAction != null) {
            Spacer(Modifier.height(Space.hair))
            SecondaryActionButton(actionTitle, onClick = onAction)
        }
    }
}

/**
 * A friendly failure. The underlying error is never shown to the user; it is
 * summarised for them instead.
 */
@Composable
fun ErrorStateView(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    val loc = LocalLocalization.current
    val colors = Dw.colors

    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = Space.section, horizontal = Space.card),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.row),
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(colors.mutedInk.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                DwIcons.unreachable,
                contentDescription = null,
                tint = colors.mutedInk,
                modifier = Modifier.size(32.dp),
            )
        }

        Text(title, style = typo(Typo.HEADLINE), textAlign = TextAlign.Center)
        if (message != null) {
            Text(
                message,
                style = typo(Typo.CALLOUT),
                color = colors.secondaryText,
                textAlign = TextAlign.Center,
            )
        }

        if (onRetry != null) {
            Spacer(Modifier.height(Space.hair))
            PrimaryActionButton(loc.t("common.tryAgain"), onClick = onRetry)
        }
    }
}
