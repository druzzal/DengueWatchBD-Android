package com.denguewatch.bd

import com.denguewatch.bd.model.AgeBand
import com.denguewatch.bd.model.AgeBands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The age pyramid, built from a table typed by hand.
 *
 * Every case here is taken from the real DGHS season table, not invented: it is
 * the one the app was drawing when a reader would have counted nineteen age
 * groups where there are seventeen, with `6-10` sitting between `56-60` and
 * `61-65`.
 */
class AgeBandsTest {

    /**
     * A row of the published table whose Total column agrees with its sexes.
     * `AgeBand` carries DGHS's own Total, which does not always agree — see
     * [AgeBandIntegrityTest] for the rows where it does not.
     */
    private fun band(label: String, male: Int, female: Int) =
        AgeBand(label, male, female, total = male + female)

    /**
     * The season table as published, including every stray row.
     * Totals are the real ones.
     */
    private val published = listOf(
        band("", 7, 0),
        band("0-10", 1, 0),
        band("0-5", 2512, 1972),
        band("06-10", 2552, 1993),
        band("11-15", 3582, 2029),
        band("16-20", 8191, 4062),
        band("21-25", 8670, 4312),
        band("21-30", 1, 0),
        band("26-30", 8286, 4502),
        band("31-35", 5324, 3593),
        band("36-40", 4683, 3220),
        band("41-45", 3471, 2408),
        band("42309", 1, 1),
        band("46-50", 2748, 2020),
        band("46301", 0, 1),
        band("51-55", 1965, 1267),
        band("56-60", 1639, 1143),
        band("6-10", 1, 0),
        band("61-65", 1085, 710),
        band("66-70", 710, 401),
        band("71-75", 326, 169),
        band("76-80", 204, 118),
        band("80+", 153, 79),
        band("Grand Total", 56112, 34000),
    )

    @Test
    fun `the real table yields one bar per age group, in age order`() {
        val bands = AgeBands.normalise(published)
        assertEquals(
            listOf(
                "0-5", "06-10", "11-15", "16-20", "21-25", "26-30", "31-35",
                "36-40", "41-45", "46-50", "51-55", "56-60", "61-65", "66-70",
                "71-75", "76-80", "80+",
            ),
            bands.map { it.label },
        )
    }

    @Test
    fun `a band written two ways is counted once, not twice`() {
        // "6-10" and "06-10" are the same five years. Before this the chart
        // drew both, one of them far down the list between 56-60 and 61-65.
        val bands = AgeBands.normalise(published)
        val sixToTen = bands.filter { AgeBands.span(it.label) == 6..10 }
        assertEquals("the band must appear exactly once", 1, sixToTen.size)
        // Nothing is dropped in the merge: 2,552 + 1 male, 1,993 + 0 female.
        assertEquals(2553, sixToTen.single().male)
        assertEquals(1993, sixToTen.single().female)
    }

    @Test
    fun `a coarser band laid over finer ones is dropped`() {
        // "0-10" covers 0-5 and 06-10; "21-30" covers 21-25 and 26-30. Keeping
        // them would count those ages twice in a chart read by eye.
        val labels = AgeBands.normalise(published).map { it.label }
        assertTrue("0-10" !in labels)
        assertTrue("21-30" !in labels)
    }

    @Test
    fun `the open top band survives sharing its boundary year`() {
        // 76-80 and 80+ both include the year 80 — that is how DGHS writes the
        // grid, so a single shared year must not read as an overlap. An earlier
        // rule that counted it as one deleted the oldest age group entirely.
        val labels = AgeBands.normalise(published).map { it.label }
        assertTrue("76-80" in labels)
        assertTrue("80+" in labels)
    }

    @Test
    fun `rows that are not age groups are left out`() {
        val labels = AgeBands.normalise(published).map { it.label }
        // An Excel serial where a date was typed into the age column, a blank,
        // and the table's own total row.
        listOf("42309", "46301", "", "Grand Total").forEach {
            assertTrue("$it is not an age band", it !in labels)
        }
    }

    @Test
    fun `no two bands drawn together cover the same year twice`() {
        // The property the chart depends on: one bar per age, so a reader can
        // add the bars up and get the table's total.
        val spans = AgeBands.normalise(published).mapNotNull { AgeBands.span(it.label) }
        spans.forEachIndexed { i, a ->
            spans.drop(i + 1).forEach { b ->
                val shared = minOf(a.last, b.last) - maxOf(a.first, b.first)
                assertTrue("$a and $b overlap by $shared years", shared < 1)
            }
        }
    }

    @Test
    fun `merging keeps every case, and only the two strays are left behind`() {
        // 90,102 cases sit in a row that names an age range. Two of them are in
        // "0-10" and "21-30", the coarse rows dropped for overlapping a finer
        // band — there is no honest way to say which five-year group they
        // belong to, and inventing one would be worse than losing two cases out
        // of ninety thousand. Everything else survives, the duplicate "6-10"
        // included: the merge adds, it does not discard.
        val kept = AgeBands.normalise(published).sumOf { it.total }
        val namedAnAgeRange = published
            .filter { AgeBands.span(it.label) != null }
            .sumOf { it.total }

        assertEquals(90_102, namedAnAgeRange)
        assertEquals(namedAnAgeRange - 2, kept)
        assertEquals(90_100, kept)
    }

    @Test
    fun `labels that are not ranges have no span`() {
        listOf("", "Grand Total", "42309", "unknown", "5", "90-", "-5", "10-5", "0-130")
            .forEach { assertNull("'$it' should not parse", AgeBands.span(it)) }
    }

    @Test
    fun `a zero-padded label spans the same years as its plain twin`() {
        assertEquals(AgeBands.span("6-10"), AgeBands.span("06-10"))
        assertEquals(6..10, AgeBands.span("06-10"))
    }

    @Test
    fun `an empty table stays empty`() {
        assertEquals(emptyList<AgeBand>(), AgeBands.normalise(emptyList()))
    }

    @Test
    fun `the result does not depend on the order the rows arrived in`() {
        val forwards = AgeBands.normalise(published)
        val backwards = AgeBands.normalise(published.reversed())
        assertEquals(forwards.map { it.label }, backwards.map { it.label })
        assertEquals(forwards.map { it.total }, backwards.map { it.total })
    }

    @Test
    fun `both halves of the pyramid share the longest side`() {
        val bands = listOf(
            AgeBand("16-20", male = 9_000, female = 5_000, total = 14_200),
            AgeBand("21-25", male = 8_500, female = 6_000, total = 15_000),
        )
        // 9,000 men in 16-20 is the longest side; 6,000 women plus 500
        // unrecorded is the longest right side, and shorter.
        assertEquals(9_000, AgeBands.sidePeak(bands))

        val womenLonger = listOf(AgeBand("0-5", male = 100, female = 300, total = 450))
        assertEquals(350, AgeBands.sidePeak(womenLonger))
        assertEquals(1, AgeBands.sidePeak(emptyList()))
    }
}
