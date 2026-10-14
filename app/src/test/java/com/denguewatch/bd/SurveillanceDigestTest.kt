package com.denguewatch.bd

import com.denguewatch.bd.i18n.Strings
import com.denguewatch.bd.model.SurveillanceDigest
import com.denguewatch.bd.model.SurveillanceDigest.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the home screen is allowed to say about the week.
 *
 * Ported from the iOS suite: both apps summarise the same DGHS week, and a
 * sentence that says "rose" on one phone and "held steady" on the other would
 * be worse than no sentence at all.
 */
class SurveillanceDigestTest {

    private fun items(
        change: Double? = null,
        hotspots: Int = 0,
        hasBreakdown: Boolean = true,
        rising: List<Pair<String, Double>> = emptyList(),
    ) = SurveillanceDigest.items(change, hotspots, hasBreakdown, rising)

    private fun kinds(items: List<SurveillanceDigest.Item>) = items.map { it.kind }

    // MARK: - The national line

    @Test
    fun `a clear rise is reported as a rise`() {
        val item = items(change = 0.3).first()
        assertEquals(Kind.NATIONAL_RISE, item.kind)
        assertEquals(listOf("30"), item.arguments)
    }

    @Test
    fun `a clear fall is reported as a fall`() {
        val item = items(change = -0.3).first()
        assertEquals(Kind.NATIONAL_FALL, item.kind)
        // The sign lives in the sentence, not in the number.
        assertEquals(listOf("30"), item.arguments)
    }

    @Test
    fun `small moves read as steady rather than a trend`() {
        // DGHS figures wobble with reporting-day effects. Calling a 2% wobble a
        // rise would cry wolf every other week.
        assertEquals(Kind.NATIONAL_STEADY, items(change = 0.02).first().kind)
        assertEquals(Kind.NATIONAL_STEADY, items(change = -0.02).first().kind)
        assertEquals(Kind.NATIONAL_STEADY, items(change = 0.0).first().kind)
    }

    @Test
    fun `the band is exclusive so just over it counts`() {
        assertEquals(
            Kind.NATIONAL_STEADY,
            items(change = SurveillanceDigest.STEADY_BAND).first().kind,
        )
        assertEquals(
            Kind.NATIONAL_RISE,
            items(change = SurveillanceDigest.STEADY_BAND + 0.001).first().kind,
        )
    }

    @Test
    fun `an absent change produces no national statement`() {
        // A season with no previous week has no ratio to report, and inventing
        // one would be a claim the feed cannot support.
        assertFalse(
            kinds(items(change = null)).any {
                it in setOf(Kind.NATIONAL_RISE, Kind.NATIONAL_FALL, Kind.NATIONAL_STEADY)
            },
        )
    }

    // MARK: - Areas

    @Test
    fun `missing area figures are not reported as zero high risk areas`() {
        val result = items(change = 0.3, hotspots = 0, hasBreakdown = false)
        assertTrue(Kind.NO_BREAKDOWN in kinds(result))
        assertFalse(Kind.HOTSPOTS in kinds(result))
    }

    @Test
    fun `hotspot count is reported when the breakdown exists`() {
        assertEquals(
            listOf("3"),
            items(hotspots = 3).first { it.kind == Kind.HOTSPOTS }.arguments,
        )
    }

    @Test
    fun `a single hotspot uses the singular sentence`() {
        assertEquals(
            "digest.hotspots.one",
            items(hotspots = 1).first { it.kind == Kind.HOTSPOTS }.key,
        )
    }

    @Test
    fun `zero hotspots is still stated when the breakdown arrived`() {
        // "No area is high risk" is real news, and different from "we cannot tell".
        assertTrue(Kind.HOTSPOTS in kinds(items(hotspots = 0)))
    }

    @Test
    fun `only notable rises are named`() {
        val result = items(rising = listOf("Dhaka" to 0.05, "Khulna" to 0.08))
        assertFalse(Kind.STEEPEST_RISES in kinds(result))
    }

    @Test
    fun `at most two areas are named`() {
        val result = items(rising = listOf("A" to 0.9, "B" to 0.8, "C" to 0.7))
            .first { it.kind == Kind.STEEPEST_RISES }
        assertEquals(listOf("A", "B"), result.arguments)
    }

    @Test
    fun `one notable area uses the singular sentence`() {
        val result = items(rising = listOf("Barishal" to 0.6, "Sylhet" to 0.01))
            .first { it.kind == Kind.STEEPEST_RISES }
        assertEquals("digest.rises.one", result.key)
        assertEquals(listOf("Barishal"), result.arguments)
    }

    @Test
    fun `order is preserved from the caller`() {
        val result = items(rising = listOf("First" to 0.9, "Second" to 0.5))
            .first { it.kind == Kind.STEEPEST_RISES }
        assertEquals(listOf("First", "Second"), result.arguments)
    }

    @Test
    fun `no data at all still says something honest`() {
        assertEquals(listOf(Kind.NO_BREAKDOWN), kinds(items(hasBreakdown = false)))
    }

    // MARK: - Content

    @Test
    fun `every key used is present in both languages`() {
        val all = items(change = 0.3, hotspots = 2, rising = listOf("A" to 0.5, "B" to 0.4)) +
            items(change = -0.3, hotspots = 1) +
            items(change = 0.0) +
            items(hasBreakdown = false)
        all.forEach { item ->
            assertNotNull("missing EN: ${item.key}", Strings.english[item.key])
            assertNotNull("missing BN: ${item.key}", Strings.bangla[item.key])
        }
    }

    /**
     * The build cannot catch this: "rose %s% nationwide" compiles fine and
     * renders with the percent sign eaten by the formatter. Only a rendered
     * string shows it.
     */
    @Test
    fun `formatted sentences keep their literal percent sign`() {
        listOf(Strings.english, Strings.bangla).forEach { table ->
            listOf("digest.national.rise", "digest.national.fall").forEach { key ->
                val rendered = String.format(table[key] ?: "", "25")
                assertTrue("$key lost its percent sign: $rendered", rendered.contains("25%"))
                assertFalse("$key mangled: $rendered", rendered.contains("25 %"))
            }
        }
    }

    /**
     * Figures go through the reader's own formatter.
     *
     * They did not, and a Bengali reader saw "4টি এলাকা উচ্চ ঝুঁকিতে" — a Latin
     * 4 in a Bengali sentence, the only number on that screen not in their own
     * digits. Found by reading the card, not by a test.
     */
    @Test
    fun `figures are rendered by the caller's formatter`() {
        val bengali = mapOf('0' to '০', '1' to '১', '2' to '২', '3' to '৩', '4' to '৪',
            '5' to '৫', '6' to '৬', '7' to '৭', '8' to '৮', '9' to '৯')
        val render: (Int) -> String =
            { n -> n.toString().map { bengali[it] ?: it }.joinToString("") }

        val hotspots = SurveillanceDigest.items(null, 4, true, emptyList(), render)
            .first { it.kind == Kind.HOTSPOTS }
        assertEquals(listOf("৪"), hotspots.arguments)

        val rise = SurveillanceDigest.items(0.3, 0, true, emptyList(), render)
            .first { it.kind == Kind.NATIONAL_RISE }
        assertEquals(listOf("৩০"), rise.arguments)
    }

    /** The top-areas card ships with the digest; its copy must exist too. */
    @Test
    fun `the top areas card has its text in both languages`() {
        listOf("top.title", "top.subtitle", "top.rate", "top.viewMap").forEach { key ->
            assertNotNull("missing EN: $key", Strings.english[key])
            assertNotNull("missing BN: $key", Strings.bangla[key])
        }
    }
}
