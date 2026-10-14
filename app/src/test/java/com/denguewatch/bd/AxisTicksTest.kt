package com.denguewatch.bd

import com.denguewatch.bd.feature.shared.axisTicks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a reader can place a bar against.
 *
 * The Trend card drew "2.5k · 1.9k · 1.3k · 625 · 0" because the ceiling was
 * rounded and then cut into four: 2,500 is a fine maximum, a quarter of it is
 * not a number anyone reads off an axis. The step is what has to be round.
 */
class AxisTicksTest {

    /** The case from the screenshot: a fortnight peaking around 2,600. */
    @Test
    fun `the trend card gets round thousands`() {
        assertEquals(listOf(3000, 2000, 1000, 0), axisTicks(2600.0))
    }

    @Test
    fun `every tick is a multiple of the step`() {
        listOf(7.0, 95.0, 260.0, 2600.0, 17_791.0, 99_275.0).forEach { max ->
            val ticks = axisTicks(max)
            val step = ticks.first() / (ticks.size - 1)
            ticks.forEachIndexed { i, t ->
                assertEquals("$max: tick $i", (ticks.size - 1 - i) * step, t)
            }
        }
    }

    @Test
    fun `the axis always reaches the data`() {
        listOf(1.0, 2.0, 11.0, 99.0, 2600.0, 99_275.0).forEach { max ->
            assertTrue("$max is above its own axis", axisTicks(max).first() >= max)
        }
    }

    @Test
    fun `ticks run highest first and end at zero`() {
        val ticks = axisTicks(2600.0)
        assertEquals(ticks.sortedDescending(), ticks)
        assertEquals(0, ticks.last())
    }

    /**
     * Early in a season the deaths chart peaks at one or two. A half-step
     * across that range rounds to the same integer twice.
     */
    @Test
    fun `small ranges never repeat a label`() {
        listOf(1.0, 2.0, 3.0, 4.0, 5.0).forEach { max ->
            val ticks = axisTicks(max)
            assertEquals("$max repeated a label: $ticks", ticks.distinct(), ticks)
        }
    }

    /**
     * The deaths chart, peaking at ten. A step of 2.5 put ticks on 7.5 and 2.5,
     * which rounded to 8 and 3 — "10 · 8 · 5 · 3 · 0" on a chart counting people.
     */
    @Test
    fun `a whole-number range never gets a fractional step`() {
        assertEquals(listOf(10, 5, 0), axisTicks(10.0))
        listOf(6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0).forEach { max ->
            val ticks = axisTicks(max)
            val step = ticks.first() / (ticks.size - 1)
            ticks.forEachIndexed { i, t ->
                assertEquals("$max: tick $i is not on the step", (ticks.size - 1 - i) * step, t)
            }
        }
    }

    @Test
    fun `an empty chart still has an axis`() {
        assertEquals(listOf(0), axisTicks(0.0))
        assertEquals(listOf(0), axisTicks(-5.0))
    }

    /** The count is a target, not a promise: a round step matters more. */
    @Test
    fun `the tick count stays close to what was asked`() {
        listOf(7.0, 95.0, 2600.0, 99_275.0).forEach { max ->
            val n = axisTicks(max, count = 4).size
            assertTrue("$max produced $n labels", n in 3..6)
        }
    }
}
