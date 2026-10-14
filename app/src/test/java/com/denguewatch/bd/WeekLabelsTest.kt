package com.denguewatch.bd

import com.denguewatch.bd.feature.shared.weekLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which epidemiological weeks get a label.
 *
 * Ten evenly spaced indices gave W01, W05, W10, W14 — steps of four and five
 * alternating, so the gridposts were not the same distance apart and the axis
 * could not be read as months. iOS has always chosen by the week's own number.
 */
class WeekLabelsTest {

    private val season = (1..41).map { "W%02d".format(it) }

    @Test
    fun `every fourth week is labelled`() {
        assertEquals(
            listOf("W04", "W08", "W12", "W16", "W20", "W24", "W28", "W32", "W36", "W40"),
            weekLabels(season).map { it.second },
        )
    }

    /** The gap between gridposts is what makes the axis readable as months. */
    @Test
    fun `the gridposts are evenly spaced`() {
        val gaps = weekLabels(season).map { it.first }.zipWithNext { a, b -> b - a }
        assertEquals("gaps: $gaps", 1, gaps.distinct().size)
        assertEquals(4, gaps.first())
    }

    @Test
    fun `the index points at the week it labels`() {
        weekLabels(season).forEach { (index, week) ->
            assertEquals(week, season[index])
        }
    }

    @Test
    fun `a week that is not a number is skipped rather than guessed at`() {
        val odd = listOf("W04", "", "Grand Total", "Wxx", "W08")
        assertEquals(listOf("W04", "W08"), weekLabels(odd).map { it.second })
    }

    @Test
    fun `a short season still gets its gridposts`() {
        assertEquals(listOf("W04", "W08"), weekLabels((1..9).map { "W%02d".format(it) }).map { it.second })
    }

    @Test
    fun `an empty season asks for no labels`() {
        assertTrue(weekLabels(emptyList()).isEmpty())
    }

    @Test
    fun `a nonsense stride cannot divide by zero`() {
        assertTrue(weekLabels(season, stride = 0).isEmpty())
    }
}
