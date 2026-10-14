package com.denguewatch.bd

import com.denguewatch.bd.i18n.AppLanguage
import com.denguewatch.bd.i18n.NumberStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Numbers as iOS prints them for en_BD and bn_BD: grouped in lakhs, and with
 * the short forms switching unit where rounding would otherwise reach it.
 */
class NumberGroupingTest {

    private val english = NumberStyle(AppLanguage.ENGLISH)
    private val bangla = NumberStyle(AppLanguage.BANGLA)

    @Test
    fun `whole numbers are grouped in lakhs`() {
        assertEquals("1,05,316", english.num(105_316))
        assertEquals("১,০৫,৩১৬", bangla.num(105_316))
        assertEquals("13,100", english.num(13_100))
        assertEquals("1,920", english.num(1_920))
        assertEquals("321", english.num(321))
        assertEquals("1,00,00,000", english.num(10_000_000))
        assertEquals("-1,05,316", english.num(-105_316))
    }

    @Test
    fun `decimals keep the same grouping`() {
        assertEquals("66.2", english.decimal(66.2))
        assertEquals("1,05,316.5", english.decimal(105_316.5))
        assertEquals("৩৮.৬", bangla.decimal(38.6))
    }

    @Test
    fun `a short form never rounds up into the next unit's number`() {
        assertEquals("10k", english.compact(9_960))
        assertEquals("1.0M", english.compact(999_600))
        assertEquals("১.০ লাখ", bangla.compact(99_600))
        assertEquals("১০ হাজার", bangla.compact(9_960))
    }
}
