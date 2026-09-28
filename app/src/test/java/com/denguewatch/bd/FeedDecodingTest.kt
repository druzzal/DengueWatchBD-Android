package com.denguewatch.bd

import com.denguewatch.bd.model.FeedChartID
import com.denguewatch.bd.model.FeedDocument
import com.denguewatch.bd.model.FeedSummaryDocument
import com.denguewatch.bd.model.Geography
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.model.Series
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decoding the published feed.
 *
 * The traps here are real ones the feed actually contains, not hypotheticals:
 * two different shapes for a chart point, a death series whose dates do not
 * line up with the case series, and age tables carrying rows where a date got
 * typed into the age column.
 */
class FeedDecodingTest {

    /** Structurally complete but small. Mirrors the real feed's quirks exactly. */
    private val documentJson = """
    {"schema_version":1,"fetched_at":"2026-09-05T19:04:58Z",
     "meta":{"source_url":"https://example.org","source_name":"DGHS",
             "last_updated_label":"05-Sep-2026","last_updated":"2026-09-05","year":2026},
     "summary":{"epi_week":"W35","week_cases":7446,"week_deaths":23,
                "ytd_cases":300,"ytd_deaths":6,"last24_cases":988,"last24_deaths":2,
                "discharged_last24":986,"discharged_ytd":38179},
     "charts":{
       "confirmed_case":{"title":"by date","categories":["01-Jan-26","02-Jan-26","03-Jan-26"],
         "series":[{"name":"Affected","type":"column","data":[10,null,20]}]},
       "death_case":{"title":"deaths","categories":["03-Jan-26"],
         "series":[{"name":"Death","type":"column","data":[4]}]},
       "div_city_cor_case_in_year":{"title":"cases","categories":["Barishal","Dhaka (Out of CC)","DNCC","DSCC"],
         "series":[{"name":"Admitted","type":"column","data":[100,60,80,60]}]},
       "div_city_cor_death_in_year":{"title":"deaths","categories":["Barishal","Dhaka (Out of CC)","DNCC","DSCC"],
         "series":[{"name":"Death","type":"column","data":[2,1,2,1]}]},
       "affected_in_division_by_week":{"title":"weekly","categories":["W01","W02"],
         "series":[{"name":"Barisal","data":[40,60]},{"name":"Dhaka","data":[100,101]}]},
       "year_case":{"title":"years","categories":["2025","2026"],
         "series":[{"name":"Affected","type":"column","data":[102861,300]}]},
       "dengue_affected_by_gender":{"title":"sex","categories":[],
         "series":[{"name":"Brands","data":[{"name":"Male","y":180},{"name":"Female","y":120}]}]}
     },
     "tables":[
       {"title":"Age group distribution of affected cases from 1 January to till date in 2026",
        "headers":["Age Group","Male","Female","Total"],
        "rows":[{"Age Group":"0-5","Male":30,"Female":20,"Total":50},
                {"Age Group":"42309","Male":1,"Female":1,"Total":2},
                {"Age Group":"","Male":1,"Female":0,"Total":1},
                {"Age Group":"80+","Male":2,"Female":1,"Total":3},
                {"Age Group":"Grand Total","Male":34,"Female":22,"Total":56}]}
     ]}
    """.trimIndent()

    private fun decoded() = FeedDocument.parse(documentJson)

    // MARK: - Wire format

    @Test
    fun `decodes the snake_case headline`() {
        val summary = FeedSummaryDocument.parse(
            """
            {"schema_version":1,"fetched_at":"2026-09-05T19:04:58Z","last_updated":"2026-09-05",
             "year":2026,
             "summary":{"epi_week":"W35","week_cases":7446,"week_deaths":23,
                        "ytd_cases":41032,"ytd_deaths":113,"last24_cases":988,
                        "last24_deaths":2,"discharged_last24":986,"discharged_ytd":38179}}
            """.trimIndent()
        )

        assertEquals(1, summary.schemaVersion)
        assertEquals("2026-09-05", summary.lastUpdated)
        assertEquals(2026, summary.year)
        assertEquals("W35", summary.summary.epiWeek)
        assertEquals(41032, summary.summary.ytdCases)
        assertEquals(113, summary.summary.ytdDeaths)
        // The one key that is not uniformly snake_cased. Getting this wrong
        // silently zeroes the freshest figure on the dashboard.
        assertEquals(988, summary.summary.last24Cases)
        assertEquals(2, summary.summary.last24Deaths)
        assertEquals(986, summary.summary.dischargedLast24)
        assertEquals(38179, summary.summary.dischargedYtd)
    }

    @Test
    fun `reads a bare number and a named point from the same feed`() {
        val document = decoded()

        // Column chart: bare numbers, with a null meaning "none reported".
        val daily = document.chart(FeedChartID.DAILY_CASES)!!
        assertEquals(listOf(10, 0, 20), daily.primaryValues)

        // Pie chart: {"name": …, "y": …} objects.
        val gender = document.chart(FeedChartID.CASES_BY_GENDER)!!
        assertEquals(
            listOf("Male" to 180, "Female" to 120),
            gender.series.first().labelled,
        )
    }

    @Test
    fun `looks a value up by the exact DGHS category spelling`() {
        val chart = decoded().chart(FeedChartID.AREA_SEASON_CASES)!!
        assertEquals(100, chart.value("Barishal"))
        assertEquals(60, chart.value("Dhaka (Out of CC)"))
        // A spelling the feed does not use resolves to nothing rather than zero.
        assertNull(chart.value("Barisal"))
    }

    @Test
    fun `keeps the weekly series apart by name`() {
        val chart = decoded().chart(FeedChartID.DIVISION_CASES_BY_WEEK)!!
        // The weekly series spells Barishal the old way; the season chart does not.
        assertEquals(listOf(40, 60), chart.series("Barisal")!!.values)
        assertEquals(listOf(100, 101), chart.series("Dhaka")!!.values)
    }

    @Test
    fun `refuses a schema version this build cannot read`() {
        val document = FeedDocument.parse(
            """{"schema_version":99,"meta":{},"summary":{},"charts":{},"tables":[]}"""
        )
        assertEquals(99, document.schemaVersion)
        assertFalse(document.schemaVersion in com.denguewatch.bd.data.FeedConfig.SUPPORTED_SCHEMA_VERSIONS)
    }

    @Test
    fun `survives a feed that has dropped a chart`() {
        val document = FeedDocument.parse(
            """{"schema_version":1,"meta":{},"summary":{},"charts":{},"tables":[]}"""
        )
        assertNull(document.chart(FeedChartID.DAILY_CASES))
        assertTrue(document.tables.isEmpty())
    }

    // MARK: - Apportioning
    //
    // Dhaka's three feed rows share one weekly series. Rounding each share on
    // its own drifts, so the parts stop summing to the division's own figure.

    @Test
    fun `apportioned parts always sum back to the original`() {
        val weights = listOf(60, 80, 60)
        listOf(0, 1, 7, 100, 101, 999, 7446).forEach { total ->
            val parts = Series.apportion(total, weights)
            assertEquals(
                "apportioning $total drifted",
                total,
                parts.sum(),
            )
            assertTrue(parts.all { it >= 0 })
        }
    }

    @Test
    fun `apportioning is stable when shares tie`() {
        // Equal weights and a remainder: the leftover unit goes to the earlier
        // index, the same way on every run.
        assertEquals(listOf(1, 0, 0), Series.apportion(1, listOf(10, 10, 10)))
        assertEquals(listOf(1, 1, 0), Series.apportion(2, listOf(10, 10, 10)))
    }

    @Test
    fun `apportioning a total across no weight at all yields zeros`() {
        assertEquals(listOf(0, 0, 0), Series.apportion(50, listOf(0, 0, 0)))
        assertTrue(Series.apportion(50, emptyList()).isEmpty())
    }

    // MARK: - Risk bands

    @Test
    fun `risk bands sit on the documented thresholds`() {
        assertEquals(RiskLevel.LOW, RiskLevel.forIncidence(0.0))
        assertEquals(RiskLevel.LOW, RiskLevel.forIncidence(4.99))
        assertEquals(RiskLevel.MODERATE, RiskLevel.forIncidence(5.0))
        assertEquals(RiskLevel.MODERATE, RiskLevel.forIncidence(19.99))
        assertEquals(RiskLevel.HIGH, RiskLevel.forIncidence(20.0))
        assertEquals(RiskLevel.HIGH, RiskLevel.forIncidence(59.99))
        assertEquals(RiskLevel.SEVERE, RiskLevel.forIncidence(60.0))
        assertEquals(RiskLevel.SEVERE, RiskLevel.forIncidence(1000.0))
    }

    // MARK: - Moving average

    @Test
    fun `the moving average ramps from what it has rather than from zero`() {
        val average = Series.movingAverage(listOf(10, 20, 30, 40), window = 3)
        assertEquals(10.0, average[0], 1e-9)
        assertEquals(15.0, average[1], 1e-9)
        assertEquals(20.0, average[2], 1e-9)
        assertEquals(30.0, average[3], 1e-9)
    }

    // MARK: - Geography
    //
    // Containment is tested before distance because the area centres sit closer
    // to several Indian border cities than to parts of Bangladesh itself.

    @Test
    fun `containment accepts places inside the country`() {
        // Dhaka, Chattogram, Sylhet, Khulna.
        assertTrue(Geography.containsBangladesh(23.81, 90.41))
        assertTrue(Geography.containsBangladesh(22.36, 91.78))
        assertTrue(Geography.containsBangladesh(24.89, 91.87))
        assertTrue(Geography.containsBangladesh(22.81, 89.56))
    }

    @Test
    fun `containment rejects the near-border cities a distance test would accept`() {
        // Shillong is 75 km from the Sylhet centre; Kolkata 121 km from Khulna's.
        assertFalse(Geography.containsBangladesh(25.57, 91.88))
        assertFalse(Geography.containsBangladesh(22.57, 88.36))
    }

    @Test
    fun `the city corporations are separated from the division around them`() {
        // Inside DNCC and DSCC.
        assertEquals("DNCC", Geography.cityCorporationCode(23.80, 90.40))
        assertEquals("DSCC", Geography.cityCorporationCode(23.71, 90.42))
        // Savar and Gazipur are in the division, not in a city corporation —
        // the case a nearest-centroid test got wrong.
        assertNull(Geography.cityCorporationCode(23.85, 90.26))
        assertNull(Geography.cityCorporationCode(23.99, 90.42))
    }

    @Test
    fun `every reporting area has a definition and a positive population`() {
        assertEquals(10, Geography.definitions.size)
        Geography.definitions.forEach { definition ->
            assertTrue(definition.populationThousands > 0)
            assertTrue(definition.geofenceRadiusMeters > 0)
            assertNotNull(Geography.definition(definition.code))
        }
    }
}
