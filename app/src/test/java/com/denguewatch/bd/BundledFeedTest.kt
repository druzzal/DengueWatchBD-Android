package com.denguewatch.bd

import com.denguewatch.bd.data.FeedConfig
import com.denguewatch.bd.model.FeedChartID
import com.denguewatch.bd.model.FeedDocument
import com.denguewatch.bd.model.Geography
import com.denguewatch.bd.model.Series
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The parser against the real document, rather than a fixture.
 *
 * `FeedDecodingTest` covers the awkward shapes with a small hand-written
 * payload. This one runs the same code over the ~40 KB feed that actually ships
 * in `assets/`, so a future feed that renames a chart, drops a reporting area or
 * re-words the age table's title fails here rather than on a reader's phone —
 * where it would surface as an empty map or a national total sitting above ten
 * areas all reporting zero.
 */
class BundledFeedTest {

    private val document: FeedDocument by lazy {
        // Unit tests run with the module directory as the working directory.
        val file = File("src/main/assets/${FeedConfig.BUNDLED_ASSET}")
        assertTrue("bundled feed not found at ${file.absolutePath}", file.exists())
        FeedDocument.parse(file.readText())
    }

    @Test
    fun `the bundled feed is a schema this build accepts`() {
        assertTrue(
            "bundled feed is schema ${document.schemaVersion}",
            document.schemaVersion in FeedConfig.SUPPORTED_SCHEMA_VERSIONS,
        )
        assertNotNull(document.meta.lastUpdated)
        assertNotNull(document.meta.year)
    }

    @Test
    fun `every chart the app names is present and populated`() {
        FeedChartID.entries.forEach { id ->
            val chart = document.chart(id)
            assertNotNull("feed is missing chart '${id.key}'", chart)
            assertTrue(
                "chart '${id.key}' carries no series",
                chart!!.series.isNotEmpty(),
            )
        }
    }

    @Test
    fun `every reporting area resolves against the season charts`() {
        val cases = document.chart(FeedChartID.AREA_SEASON_CASES)!!
        val deaths = document.chart(FeedChartID.AREA_SEASON_DEATHS)!!

        Geography.definitions.forEach { definition ->
            assertNotNull(
                "season cases missing category '${definition.feedCategory}'",
                cases.value(definition.feedCategory),
            )
            assertNotNull(
                "season deaths missing category '${definition.feedCategory}'",
                deaths.value(definition.feedCategory),
            )
        }
    }

    @Test
    fun `every weekly series the app names is present`() {
        val weekly = document.chart(FeedChartID.DIVISION_CASES_BY_WEEK)!!
        // The city corporations share Dhaka's series, so the distinct set is
        // the eight divisions rather than the ten reporting areas.
        Geography.definitions.map { it.weeklySeriesName }.distinct().forEach { name ->
            assertNotNull("weekly chart missing series '$name'", weekly.series(name))
        }
    }

    @Test
    fun `the age table the store looks up by title is present`() {
        val matches = document.tables.filter {
            (it.title ?: "").contains("affected cases from 1 January", ignoreCase = true)
        }
        assertEquals("expected exactly one season age table", 1, matches.size)

        val bands = matches.first().rows.mapNotNull { it["Age Group"]?.text }
        assertTrue("age table carries no bands", bands.isNotEmpty())
        // The rows the store filters out are really in there.
        assertTrue("expected a 'Grand Total' row to be filtered", "Grand Total" in bands)
    }

    @Test
    fun `the headline agrees with the daily series it sits above`() {
        val ytd = document.summary.ytdCases
        assertNotNull(ytd)

        val daily = document.chart(FeedChartID.DAILY_CASES)!!.primaryValues.sum()
        // These are two independent figures DGHS publishes; they should agree
        // closely. A large gap means the app is adding up something different
        // from what it prints at the top of the screen.
        val drift = kotlin.math.abs(ytd!! - daily).toDouble() / ytd
        assertTrue(
            "headline ytd=$ytd but the daily series sums to $daily",
            drift < 0.02,
        )
    }

    @Test
    fun `apportioning Dhaka's weekly series preserves the division total`() {
        val weekly = document.chart(FeedChartID.DIVISION_CASES_BY_WEEK)!!
        val cases = document.chart(FeedChartID.AREA_SEASON_CASES)!!

        val dhaka = Geography.definitions.filter { it.code in Geography.apportionedCodes }
        val weights = dhaka.map { cases.value(it.feedCategory) ?: 0 }
        val division = weekly.series("Dhaka")!!.values

        // The promise the area pages make: the three Dhaka rows always sum back
        // to the division's own weekly figure, every week, with no drift.
        division.forEach { week ->
            assertEquals(week, Series.apportion(week, weights).sum())
        }
    }

    @Test
    fun `the season is long enough to drive the risk bands`() {
        val weekly = document.chart(FeedChartID.DIVISION_CASES_BY_WEEK)!!
        // Risk is a two-week attack rate, so anything less than two EPI weeks
        // would silently read as a partial window.
        assertTrue(
            "only ${weekly.categories.size} EPI weeks published",
            weekly.categories.size >= 2,
        )
    }
}
