package com.denguewatch.bd

import com.denguewatch.bd.model.AgeBands
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the age-band label says to each reader.
 *
 * A Bengali reader saw `11-15` and `80+` beside `৬.১ হাজার` — every figure on
 * the card in their own digits except the one naming the row. The feed only
 * ever publishes Western numerals, so the label has to be rebuilt rather than
 * passed through.
 */
class AgeBandLabelTest {

    private val english: (Int) -> String = { it.toString() }
    private val bengali: (Int) -> String = { n ->
        val map = mapOf('0' to '০', '1' to '১', '2' to '২', '3' to '৩', '4' to '৪',
            '5' to '৫', '6' to '৬', '7' to '৭', '8' to '৮', '9' to '৯')
        n.toString().map { map[it] ?: it }.joinToString("")
    }

    private fun bn(label: String) = AgeBands.displayLabel(label, bengali)
    private fun en(label: String) = AgeBands.displayLabel(label, english)

    @Test
    fun `a closed band is written in the reader's digits`() {
        assertEquals("১১–১৫", bn("11-15"))
        assertEquals("২৬–৩০", bn("26-30"))
    }

    @Test
    fun `the open-ended top band keeps its plus`() {
        assertEquals("৮০+", bn("80+"))
        assertEquals("80+", en("80+"))
    }

    /** DGHS writes `06-10`; no reader wants a leading zero on an age. */
    @Test
    fun `zero padding is dropped`() {
        assertEquals("6–10", en("06-10"))
        assertEquals("৬–১০", bn("06-10"))
    }

    @Test
    fun `an en dash separates the bounds, not a hyphen`() {
        assertEquals("0–5", en("0-5"))
        assertEquals(-1, en("0-5").indexOf('-'))
    }

    /**
     * Anything the parser cannot read is handed back as published.
     *
     * DGHS's table has carried Excel serials (`42309`), blanks and a
     * `Grand Total` row. Reformatting those would mean inventing an age range
     * the feed never stated.
     */
    @Test
    fun `a label that is not an age band is left alone`() {
        listOf("42309", "", "Grand Total", "unknown", "5-", "-5", "a-b").forEach {
            assertEquals(it, bn(it))
        }
    }

    @Test
    fun `an impossible range is left alone rather than redrawn`() {
        assertEquals("15-11", bn("15-11"))   // reversed
        assertEquals("0-200", bn("0-200"))   // beyond any human age
    }

    /** English keeps Western digits; only the shape is normalised. */
    @Test
    fun `english is unchanged apart from padding and the dash`() {
        assertEquals("11–15", en("11-15"))
        assertEquals("76–80", en("76-80"))
    }

    /** Whatever the label, the formatter decides the digits — never the feed. */
    @Test
    fun `every digit in the output comes from the formatter`() {
        val latin = ('0'..'9').toSet()
        listOf("0-5", "06-10", "11-15", "76-80", "80+").forEach { label ->
            assertEquals(
                "$label kept a Latin digit in Bangla: ${bn(label)}",
                0,
                bn(label).count { it in latin },
            )
        }
    }
}
