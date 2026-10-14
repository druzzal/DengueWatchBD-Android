package com.denguewatch.bd

import com.denguewatch.bd.feature.shared.TrendThreshold
import com.denguewatch.bd.i18n.Strings
import com.denguewatch.bd.model.HealthTrend
import com.denguewatch.bd.model.LabMeasure
import com.denguewatch.bd.model.VitalKind
import com.denguewatch.bd.model.minimumMeaningfulChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which way a reader's own readings have moved — and nothing about what the
 * movement means.
 *
 * Ported from the iOS suite so the two apps cannot describe the same three
 * platelet counts differently.
 */
class HealthTrendTest {

    private fun direction(values: List<Double>, minimumChange: Double) =
        HealthTrend.direction(values, minimumChange)

    @Test
    fun `a single reading has no direction`() {
        assertEquals(HealthTrend.NOT_ENOUGH_DATA, direction(listOf(120.0), 20.0))
        assertEquals(HealthTrend.NOT_ENOUGH_DATA, direction(emptyList(), 20.0))
    }

    @Test
    fun `falling platelets read as falling`() {
        assertEquals(HealthTrend.FALLING, direction(listOf(186.0, 140.0, 88.0), 20.0))
    }

    @Test
    fun `rising temperature reads as rising`() {
        assertEquals(HealthTrend.RISING, direction(listOf(37.2, 38.4), 0.3))
    }

    @Test
    fun `scatter below the threshold reads as steady`() {
        // Two tenths of a degree is a thermometer, not a fever changing.
        assertEquals(HealthTrend.STEADY, direction(listOf(38.0, 38.2), 0.3))
    }

    @Test
    fun `the threshold is exclusive so just over it counts`() {
        assertEquals(HealthTrend.RISING, direction(listOf(38.0, 38.31), 0.3))
    }

    @Test
    fun `direction is first to last not highest to lowest`() {
        // A count that dipped and recovered has not fallen.
        assertEquals(HealthTrend.STEADY, direction(listOf(150.0, 90.0, 155.0), 20.0))
    }

    @Test
    fun `thresholds are in each measure's own units`() {
        // A single fraction cannot serve both: 5% of a temperature is a third
        // of a degree, 5% of a platelet count is noise.
        assertEquals(0.3, VitalKind.TEMPERATURE.minimumMeaningfulChange, 0.0001)
        assertEquals(20.0, LabMeasure.PLATELETS.minimumMeaningfulChange, 0.0001)
        assertTrue(
            LabMeasure.PLATELETS.minimumMeaningfulChange >
                VitalKind.TEMPERATURE.minimumMeaningfulChange,
        )
    }

    @Test
    fun `every direction is named in both languages`() {
        HealthTrend.entries.forEach { trend ->
            assertNotNull("missing EN: ${trend.labelKey}", Strings.english[trend.labelKey])
            assertNotNull("missing BN: ${trend.labelKey}", Strings.bangla[trend.labelKey])
        }
        listOf("trend.chart.temperature", "trend.chart.platelets", "trend.readings", "trend.note")
            .forEach { key ->
                assertNotNull("missing EN: $key", Strings.english[key])
                assertNotNull("missing BN: $key", Strings.bangla[key])
            }
    }

    @Test
    fun `the trend note refuses to interpret`() {
        // The sentence that keeps this a chart rather than a diagnosis.
        val note = (Strings.english["trend.note"] ?: "").lowercase()
        assertTrue(note, note.contains("does not say what that means"))
        assertTrue(note, note.contains("doctor"))
    }

    // MARK: - Which side of the line matters

    @Test
    fun `a fever crosses a threshold by rising`() {
        val fever = TrendThreshold(38.0, TrendThreshold.Concern.ABOVE)
        assertTrue(fever.isCrossedBy(38.6))
        assertFalse(fever.isCrossedBy(37.2))
    }

    @Test
    fun `a platelet count crosses a threshold by falling`() {
        val low = TrendThreshold(150.0, TrendThreshold.Concern.BELOW)
        assertTrue(low.isCrossedBy(88.0))
        assertFalse(low.isCrossedBy(220.0))
    }

    @Test
    fun `sitting on the line is not crossing it`() {
        assertFalse(TrendThreshold(38.0, TrendThreshold.Concern.ABOVE).isCrossedBy(38.0))
        assertFalse(TrendThreshold(150.0, TrendThreshold.Concern.BELOW).isCrossedBy(150.0))
    }

    /**
     * The whole reason the direction is a parameter: marking the top of a
     * platelet range would highlight exactly the readings nobody is worried
     * about.
     */
    @Test
    fun `the directions are not interchangeable`() {
        val reading = 88.0
        assertTrue(TrendThreshold(150.0, TrendThreshold.Concern.BELOW).isCrossedBy(reading))
        assertFalse(TrendThreshold(150.0, TrendThreshold.Concern.ABOVE).isCrossedBy(reading))
    }

    @Test
    fun `the platelet threshold is the floor of the typical range`() {
        val floor = LabMeasure.PLATELETS.typical.start
        assertTrue(TrendThreshold(floor, TrendThreshold.Concern.BELOW).isCrossedBy(floor - 1))
    }

    @Test
    fun `the fever threshold is the ceiling of the usual range`() {
        val ceiling = VitalKind.TEMPERATURE.usual.endInclusive
        assertTrue(TrendThreshold(ceiling, TrendThreshold.Concern.ABOVE).isCrossedBy(ceiling + 0.1))
    }

    @Test
    fun `a platelet trend ignores movement inside the assay's noise`() {
        assertEquals(
            HealthTrend.STEADY,
            direction(listOf(150.0, 162.0), LabMeasure.PLATELETS.minimumMeaningfulChange),
        )
    }

    @Test
    fun `one count is not a trend`() {
        assertEquals(
            HealthTrend.NOT_ENOUGH_DATA,
            direction(listOf(88.0), LabMeasure.PLATELETS.minimumMeaningfulChange),
        )
    }
}
