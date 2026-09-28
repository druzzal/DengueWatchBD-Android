package com.denguewatch.bd.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle

val LocalDwColors = staticCompositionLocalOf { DwColors.Light }

/**
 * The app theme.
 *
 * Material's own scheme is derived from our palette rather than the other way
 * round: the palette is the source of truth (it is the one that carries the
 * risk scale and the contrast measurements), and Material only needs enough of
 * it that a stock component — a switch, a text field, a ripple — lands in the
 * same world as everything drawn by hand.
 *
 * Dynamic colour is deliberately not used. A wallpaper-derived scheme would
 * recolour a public-health risk scale, which is the one thing on screen that
 * must mean the same to every reader.
 */
@Composable
fun DengueWatchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DwColors.Dark else DwColors.Light

    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = androidx.compose.ui.graphics.Color.White,
            secondary = colors.admitted,
            background = colors.plane,
            onBackground = colors.primaryText,
            surface = colors.card,
            onSurface = colors.primaryText,
            surfaceVariant = colors.card,
            onSurfaceVariant = colors.secondaryText,
            outline = colors.hairline,
            outlineVariant = colors.hairline,
            error = colors.riskTint(com.denguewatch.bd.model.RiskLevel.SEVERE),
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = androidx.compose.ui.graphics.Color.White,
            secondary = colors.admitted,
            background = colors.plane,
            onBackground = colors.primaryText,
            surface = colors.card,
            onSurface = colors.primaryText,
            surfaceVariant = colors.card,
            onSurfaceVariant = colors.secondaryText,
            outline = colors.hairline,
            outlineVariant = colors.hairline,
            error = colors.riskTint(com.denguewatch.bd.model.RiskLevel.SEVERE),
        )
    }

    // Bengali needs the extra leading to be honoured at the top of the first
    // line too, or the matra clips against whatever sits above the text.
    val baseStyle = TextStyle(
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None,
        ),
    )

    CompositionLocalProvider(
        LocalDwColors provides colors,
        LocalTextStyle provides baseStyle.copy(color = colors.primaryText),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(),
            content = content,
        )
    }
}
