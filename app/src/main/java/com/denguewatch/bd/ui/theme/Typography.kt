package com.denguewatch.bd.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.denguewatch.bd.i18n.AppLanguage
import com.denguewatch.bd.i18n.LocalLanguage

/**
 * The app's type scale.
 *
 * Sizes are in `sp`, so the whole interface scales with the reader's font-size
 * setting instead of being pinned to fixed pixels. Weight and tracking are
 * applied on top.
 *
 * Bengali and Latin share the scale but not the vertical rhythm — Bengali
 * carries the matra headline and deeper descenders, so it gets extra leading at
 * the same size. That is why views ask for [typo] rather than building a
 * [TextStyle] directly.
 */
enum class Typo {
    /** The one number a screen is about — a case count, a risk word. */
    HERO,
    TITLE,
    SECTION_TITLE,
    HEADLINE,
    BODY,
    CALLOUT,
    SUBHEADLINE,
    CAPTION,
    MICRO,
    /** Large figures inside a stat card. */
    STAT_VALUE,
    STAT_UNIT;

    /**
     * The iOS text styles at the default Dynamic Type size, which is what the
     * iOS build resolves each of these to: largeTitle 34, title 28, title3 20,
     * headline 17, body 17, callout 16, subheadline 15, footnote 13, caption 12.
     * Title, stat value, body and callout had been set a point or more smaller,
     * so a tile's figure read 24 here against 28 on the iPhone.
     */
    val size get() = when (this) {
        HERO -> 34.sp
        TITLE -> 28.sp
        SECTION_TITLE -> 20.sp
        HEADLINE -> 17.sp
        BODY -> 17.sp
        CALLOUT -> 16.sp
        SUBHEADLINE -> 15.sp
        CAPTION -> 13.sp
        MICRO -> 12.sp
        STAT_VALUE -> 28.sp
        STAT_UNIT -> 13.sp
    }

    val weight get() = when (this) {
        HERO -> FontWeight.Bold
        TITLE, STAT_VALUE, SECTION_TITLE, HEADLINE -> FontWeight.SemiBold
        SUBHEADLINE, STAT_UNIT -> FontWeight.Medium
        BODY, CALLOUT, CAPTION, MICRO -> FontWeight.Normal
    }

    /**
     * Tighter tracking on large text, looser on small — the usual optical fix.
     * iOS's values in points (−0.8, −0.4, −0.2, +0.2) as a share of each size.
     */
    val tracking get() = when (this) {
        HERO -> (-0.8 / 34).em
        TITLE, STAT_VALUE -> (-0.4 / 28).em
        SECTION_TITLE -> (-0.2 / 20).em
        HEADLINE -> (-0.2 / 17).em
        MICRO -> (0.2 / 12).em
        else -> 0.em
    }

    /** Line height as a multiple of the size, before the Bengali allowance. */
    private val leadingRatio get() = when (this) {
        HERO -> 1.15f
        TITLE, STAT_VALUE -> 1.2f
        SECTION_TITLE, HEADLINE -> 1.3f
        else -> 1.4f
    }

    /**
     * Extra leading Bengali needs so the matra line clears the row above.
     *
     * Worked out in raw sp rather than by adding TextUnits, which do not
     * compose with `+`.
     */
    fun lineHeight(language: AppLanguage): TextUnit {
        val base = size.value * leadingRatio
        if (language != AppLanguage.BANGLA) return base.sp
        val bengaliAllowance = when (this) {
            HERO, TITLE, STAT_VALUE -> 4f
            SECTION_TITLE, HEADLINE, BODY, SUBHEADLINE, CALLOUT -> 3f
            else -> 2f
        }
        return (base + bengaliAllowance).sp
    }
}

/**
 * How much the type scale is multiplied by on this screen.
 *
 * The sizes in [Typo] are the iOS ones, which are drawn for a standard 390pt
 * iPhone. A narrower phone takes them down in proportion, so a 360dp screen is
 * not left fitting iPhone-sized type into less room; a tablet takes them up a
 * little, so a 10-inch screen does not read like a phone held at arm's length.
 * Both ends are capped — text shrunk below 85% stops being comfortable to
 * read, and past 115% the extra width is better spent as margin (see
 * [Layout.readableWidth]) than as ever-larger letters.
 *
 * Keyed on the screen's shorter side, so turning a phone sideways does not
 * make every word jump in size. It multiplies the reader's own font-size
 * setting rather than replacing it: `sp` already carries that.
 */
object TypeScale {
    /** The width the iOS sizes are drawn for. */
    const val REFERENCE_WIDTH_DP = 390f

    /** Where the upward scaling reaches its cap: a large tablet. */
    const val LARGE_WIDTH_DP = 840f

    const val MIN = 0.85f
    const val MAX = 1.15f

    fun forSmallestWidth(smallestWidthDp: Int): Float = when {
        smallestWidthDp <= 0 -> 1f
        smallestWidthDp < REFERENCE_WIDTH_DP -> (smallestWidthDp / REFERENCE_WIDTH_DP).coerceAtLeast(MIN)
        else -> (1f + (smallestWidthDp - REFERENCE_WIDTH_DP) / (LARGE_WIDTH_DP - REFERENCE_WIDTH_DP) * (MAX - 1f))
            .coerceAtMost(MAX)
    }
}

/** The [TypeScale] for the current screen, provided by the theme. */
val LocalTypeScale = compositionLocalOf { 1f }

/**
 * Apply an app type style. Prefer this over building a [TextStyle] by hand so
 * Bengali gets its own leading and everything scales with the system font size.
 */
@Composable
@ReadOnlyComposable
fun typo(style: Typo, weight: FontWeight? = null): TextStyle {
    val language = LocalLanguage.current
    val scale = LocalTypeScale.current
    return LocalTextStyle.current.copy(
        fontSize = (style.size.value * scale).sp,
        fontWeight = weight ?: style.weight,
        // Latin only. The tracking values here are an optical correction for
        // Latin letterforms, and the negative ones measure Bengali short: the
        // reported width comes back narrower than the glyphs actually drawn, so
        // a word gets clipped at the right edge rather than laid out to fit.
        // That is how "মাঝারি" rendered as "মাঝা" on the hero card. Bengali
        // needs no tracking correction anyway — it is shaped, not spaced.
        letterSpacing = if (language == AppLanguage.BANGLA) 0.em else style.tracking,
        lineHeight = (style.lineHeight(language).value * scale).sp,
        fontFamily = FontFamily.Default,
    )
}

/**
 * Big figures line up in columns only if their digits do.
 *
 * Android's default font does not expose a tabular-figures feature the way the
 * system font does on iOS, so the request goes through the font feature
 * setting and falls back harmlessly when unsupported. [TextMotion.Static] keeps
 * animated counters from shimmering as they re-layout.
 */
@Composable
@ReadOnlyComposable
fun TextStyle.tabularFigures(): TextStyle =
    copy(fontFeatureSettings = "tnum", textMotion = TextMotion.Static)
