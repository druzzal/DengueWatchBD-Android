package com.denguewatch.bd

import com.denguewatch.bd.i18n.Strings
import com.denguewatch.bd.model.Area
import com.denguewatch.bd.model.Division
import com.denguewatch.bd.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A shared card travels without the app, so a wrong number on one cannot be
 * corrected later. These tests are about what the card is allowed to claim.
 *
 * `AreaShareCard.content` takes a `Localization`, which needs an Android
 * `Context`, so the assembled `Content` is checked on the emulator rather than
 * here. What this covers is everything that decides *what the card says*: the
 * risk band it reports, whether a weekly change exists to state at all, and
 * whether the copy it needs is present in both languages. The iOS suite's
 * rendering assertions have no counterpart here — on Android the card is
 * rasterised from a composition, which a JVM test cannot run.
 */
class AreaShareCardTest {

    private fun area(
        weeklyCases: List<Int> = listOf(100, 130),
        populationThousands: Int = 1_000,
        seasonCases: Int = 5_432,
    ) = Area(
        code = "TEST",
        name = "Testville",
        division = Division.DHAKA,
        latitude = 23.8,
        longitude = 90.4,
        populationThousands = populationThousands,
        seasonCases = seasonCases,
        seasonDeaths = 7,
        weeklyCases = weeklyCases,
        weeklyIsApportioned = false,
        geofenceRadiusMeters = 12_000.0,
    )

    // MARK: - What the card is allowed to claim

    @Test
    fun `the risk label matches the area's own band`() {
        // 230 cases in 1,000k over two weeks is 23 per 100k — High.
        assertEquals(RiskLevel.HIGH, area(weeklyCases = listOf(100, 130)).risk)
    }

    @Test
    fun `a weekly change is omitted when there is no previous week`() {
        // One week of data makes the ratio meaningless; the line disappears
        // rather than showing a zero or a dash that reads like a real figure.
        assertNull(area(weeklyCases = listOf(120)).weeklyChange)
    }

    @Test
    fun `a weekly change is available when it can be computed`() {
        assertNotNull(area(weeklyCases = listOf(100, 130)).weeklyChange)
    }

    @Test
    fun `rise and fall are distinguished`() {
        assertTrue((area(weeklyCases = listOf(100, 130)).weeklyChange ?: 0.0) >= 0)
        assertTrue((area(weeklyCases = listOf(130, 100)).weeklyChange ?: 0.0) < 0)
    }

    // MARK: - Attribution

    /**
     * The attribution is language-specific: English uses the acronym, Bengali
     * spells the directorate out. Asserting one against both would only pass by
     * accident, depending on the default language.
     */
    @Test
    fun `the source line credits the directorate in each language`() {
        assertTrue(Strings.english["share.source"]!!.contains("DGHS"))
        assertTrue(Strings.bangla["share.source"]!!.contains("স্বাস্থ্য অধিদপ্তর"))
        // The report date follows the source.
        listOf(Strings.english, Strings.bangla).forEach {
            assertTrue(it["share.source"]!!.contains("·"))
            assertTrue(it["share.source"]!!.contains("%s"))
        }
    }

    @Test
    fun `a missing date still credits the source without inventing one`() {
        listOf(Strings.english, Strings.bangla).forEach { table ->
            val line = table["share.sourceNoDate"]!!
            assertFalse(line.isEmpty())
            assertFalse("no date means no date separator: $line", line.contains("·"))
            assertFalse("and no empty slot to fill: $line", line.contains("%s"))
        }
    }

    @Test
    fun `every key the card uses exists in both languages`() {
        listOf(
            "share.button", "share.appName", "share.tagline",
            "share.unit.incidence", "share.unit.season", "share.change",
            "share.source", "share.sourceNoDate",
        ).forEach { key ->
            assertNotNull("missing EN: $key", Strings.english[key])
            assertNotNull("missing BN: $key", Strings.bangla[key])
        }
    }

    /**
     * The card is a fixed canvas, not a view that adapts. If these drift the
     * output stops being the 4:5 a feed shows without cropping.
     */
    @Test
    fun `the card keeps the four by five shape a feed will not crop`() {
        val width = com.denguewatch.bd.feature.share.ShareCard.WIDTH
        val height = com.denguewatch.bd.feature.share.ShareCard.HEIGHT
        assertEquals(1080, width)
        assertEquals(1350, height)
        assertEquals(4.0 / 5.0, width.toDouble() / height, 0.0001)
    }
}
