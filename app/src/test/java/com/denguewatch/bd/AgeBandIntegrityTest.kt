package com.denguewatch.bd

import com.denguewatch.bd.data.FeedConfig
import com.denguewatch.bd.model.AgeBands
import com.denguewatch.bd.model.FeedDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the "Who is affected" card draws, reconciled against what DGHS publishes.
 *
 * The card's job is to account for every case in the season table. It once did
 * not: `AgeBand.total` was derived as `male + female`, so the 244 cases DGHS
 * counts without recording a sex were dropped from sixteen of the seventeen
 * bands. Every bar was short, the card's own totals agreed with each other, and
 * only the source disagreed — which is the kind of error no unit test built from
 * the app's own numbers can find.
 *
 * So this test reads the shipped feed and asks the arithmetic question directly.
 */
class AgeBandIntegrityTest {

    private val document: FeedDocument by lazy {
        val file = File("src/main/assets/${FeedConfig.BUNDLED_ASSET}")
        assertTrue("bundled feed not found at ${file.absolutePath}", file.exists())
        FeedDocument.parse(file.readText())
    }

    private val seasonTable by lazy {
        document.tables.first {
            (it.title ?: "").contains("affected cases from 1 January", ignoreCase = true)
        }
    }

    private fun cell(row: Map<String, com.denguewatch.bd.model.FeedCell>, name: String) =
        row[name]?.number ?: 0

    private val bodyRows by lazy {
        seasonTable.rows.filter {
            (it["Age Group"]?.text ?: "").trim().lowercase() != "grand total"
        }
    }

    private val bands by lazy {
        AgeBands.normalise(
            bodyRows.mapNotNull { row ->
                val label = row["Age Group"]?.text ?: return@mapNotNull null
                val male = cell(row, "Male")
                val female = cell(row, "Female")
                com.denguewatch.bd.model.AgeBand(
                    label = label,
                    male = male,
                    female = female,
                    total = row["Total"]?.number ?: (male + female),
                )
            },
        )
    }

    @Test
    fun `the published totals add up to the table's own Grand Total`() {
        val grandTotal = seasonTable.rows
            .first { (it["Age Group"]?.text ?: "").trim().lowercase() == "grand total" }
            .let { cell(it, "Total") }
        assertEquals(grandTotal, bodyRows.sumOf { cell(it, "Total") })
    }

    @Test
    fun `cases DGHS records without a sex are kept, not discarded`() {
        val published = bodyRows.sumOf { cell(it, "Total") }
        val sexes = bodyRows.sumOf { cell(it, "Male") + cell(it, "Female") }
        // The feed this ships with carries such cases; if a future feed does
        // not, the assertion below still holds and this one is simply quiet.
        val unrecorded = published - sexes
        assertTrue("expected the feed's own figures to be self-consistent", unrecorded >= 0)
        assertEquals(unrecorded, bands.sumOf { it.unrecordedSex } + droppedByOverlap(unrecorded))
    }

    /**
     * Cases in rows deliberately dropped for overlapping a finer band. There is
     * no honest band to put them in; the point is that they are the only ones
     * missing.
     */
    private fun droppedByOverlap(unrecorded: Int): Int {
        val keptSpans = bands.mapNotNull { AgeBands.span(it.label) }
        return bodyRows.filter { row ->
            val span = AgeBands.span(row["Age Group"]?.text ?: "") ?: return@filter false
            keptSpans.none { it == span } 
        }.sumOf { cell(it, "Total") - cell(it, "Male") - cell(it, "Female") }
            .coerceAtMost(unrecorded)
    }

    @Test
    fun `almost every published case reaches the chart`() {
        val published = bodyRows.sumOf { cell(it, "Total") }
        val drawn = bands.sumOf { it.total }
        val dropped = published - drawn
        assertTrue("nothing should be invented: drew $drawn of $published", dropped >= 0)
        assertTrue(
            "$dropped of $published cases never reach the chart",
            dropped.toDouble() / published < 0.01,
        )
    }

    @Test
    fun `each band draws the whole of its own total`() {
        bands.forEach { band ->
            assertEquals(
                "${band.label} draws fewer people than it counts",
                band.total,
                band.male + band.female + band.unrecordedSex,
            )
        }
    }
}
