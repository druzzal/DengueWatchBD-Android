package com.denguewatch.bd.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.denguewatch.bd.model.RiskLevel

/**
 * Two separate jobs, deliberately kept apart:
 *
 *  * Risk — an ordered public-health scale, green through red. Semantic, and
 *    the one thing a user must read correctly under stress.
 *  * Series — chart identity for cases / admissions / deaths, which carry no
 *    severity meaning and must not borrow the risk colours.
 *
 * Risk has two tokens per level. [riskTint] is for fills, marks and map
 * symbols; [riskInk] is a darker step for text and icons that needs to clear
 * 4.5:1. Amber in particular cannot do both jobs with one value — as a fill it
 * is 1.9:1 on white, which is fine behind dark ink and unreadable as text.
 *
 * Every value is chosen twice, once per scheme. Dark mode is a selected
 * palette, never an automatic flip of the light one.
 *
 * Colour never carries risk alone — every presentation pairs it with an icon
 * and the band's name.
 */
data class DwColors(
    val dark: Boolean,

    // Chart series identity.
    val cases: Color,
    val casesMuted: Color,
    val admitted: Color,
    val deaths: Color,

    // Chart chrome.
    val grid: Color,
    val axis: Color,
    val mutedInk: Color,

    // Surfaces.
    val card: Color,
    val plane: Color,
    val hairline: Color,

    // Ink.
    val primaryText: Color,
    val secondaryText: Color,
    val tertiaryText: Color,

    val upIsBad: Color,
    val downIsGood: Color,

    /**
     * The app's theme colour: cyan, and dark enough to be legible as text.
     *
     * Measured, not picked by eye. #0088b0 is the cyan you reach for first and
     * it reaches only 4.08:1 on white, under the 4.5:1 small-text bar. This
     * step measures 4.93:1 on white and 4.52:1 on the plane, so a link or a
     * button label set in it is readable everywhere it appears.
     *
     * Deliberately not the same value as [cases]. That blue is the chart's
     * identity for case counts and means something specific wherever it
     * appears; the accent is chrome. They were one colour here until now, so
     * every tinted control was claiming to be about cases.
     */
    val accent: Color,
) {

    /**
     * Card lift. Light mode gets a real (if quiet) shadow so cards read as
     * surfaces above the plane; dark mode gets none, because a drop shadow on
     * near-black reads as grime rather than elevation — there the hairline and
     * the lighter card fill carry the separation instead.
     */
    val cardShadow: Color
        get() = if (dark) Color.Transparent else Color(0x0E0B0B14)

    /** Fills, marks, map symbols. */
    fun riskTint(level: RiskLevel): Color = if (dark) when (level) {
        RiskLevel.LOW -> Color(0xFF34A853)
        RiskLevel.MODERATE -> Color(0xFFF2B705)
        RiskLevel.HIGH -> Color(0xFFF97316)
        RiskLevel.SEVERE -> Color(0xFFE11D48)
    } else when (level) {
        RiskLevel.LOW -> Color(0xFF157F3B)
        RiskLevel.MODERATE -> Color(0xFFF0B400)
        RiskLevel.HIGH -> Color(0xFFE2600A)
        RiskLevel.SEVERE -> Color(0xFF9E1B13)
    }

    /** Text and icons. Darker than the tint so it clears 4.5:1 on the card. */
    fun riskInk(level: RiskLevel): Color = if (dark) when (level) {
        RiskLevel.LOW -> Color(0xFF4ADE80)
        RiskLevel.MODERATE -> Color(0xFFFBBF24)
        RiskLevel.HIGH -> Color(0xFFFB923C)
        RiskLevel.SEVERE -> Color(0xFFF87171)
    } else when (level) {
        RiskLevel.LOW -> Color(0xFF0F6B30)
        RiskLevel.MODERATE -> Color(0xFF8A6100)
        RiskLevel.HIGH -> Color(0xFF9C3F00)
        RiskLevel.SEVERE -> Color(0xFF8F1710)
    }

    /** A wash for tinted backgrounds behind risk ink. */
    fun riskSoft(level: RiskLevel): Color = riskTint(level).copy(alpha = 0.14f)

    companion object {
        val Light = DwColors(
            dark = false,
            accent = Color(0xFF007A9C),
            cases = Color(0xFF2A78D6),
            casesMuted = Color(0xFF86B6EF),
            admitted = Color(0xFF1BAF7A),
            deaths = Color(0xFFE34948),
            grid = Color(0xFFE1E0D9),
            axis = Color(0xFFC3C2B7),
            mutedInk = Color(0xFF898781),
            card = Color(0xFFFFFFFF),
            plane = Color(0xFFF5F5F7),
            hairline = Color(0xFFE6E6EB),
            primaryText = Color(0xFF11111A),
            secondaryText = Color(0xFF6B6B76),
            tertiaryText = Color(0xFF9E9EA8),
            upIsBad = Color(0xFFB3261E),
            downIsGood = Color(0xFF0F6B30),
        )

        val Dark = DwColors(
            dark = true,
            accent = Color(0xFF3FC4EA),
            cases = Color(0xFF3987E5),
            casesMuted = Color(0xFF256ABF),
            admitted = Color(0xFF199E70),
            deaths = Color(0xFFE66767),
            grid = Color(0xFF2C2C2A),
            axis = Color(0xFF383835),
            mutedInk = Color(0xFF8E8E93),
            card = Color(0xFF1C1C1E),
            plane = Color(0xFF000000),
            hairline = Color(0xFF2C2C2E),
            primaryText = Color(0xFFF2F2F7),
            secondaryText = Color(0xFF98989F),
            tertiaryText = Color(0xFF6C6C72),
            upIsBad = Color(0xFFF87171),
            downIsGood = Color(0xFF4ADE80),
        )
    }
}

/** Shorthand for the palette, so screens read `Dw.colors.accent`. */
object Dw {
    val colors: DwColors
        @Composable @ReadOnlyComposable get() = LocalDwColors.current
}

@Composable
fun riskTint(level: RiskLevel): Color = Dw.colors.riskTint(level)

@Composable
fun riskInk(level: RiskLevel): Color = Dw.colors.riskInk(level)

@Composable
fun riskSoft(level: RiskLevel): Color = Dw.colors.riskSoft(level)
