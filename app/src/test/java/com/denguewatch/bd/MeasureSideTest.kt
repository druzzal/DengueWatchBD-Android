package com.denguewatch.bd

import com.denguewatch.bd.model.LabMeasure
import com.denguewatch.bd.model.MeasureSide
import com.denguewatch.bd.model.VitalKind
import com.denguewatch.bd.model.side
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which way a reading strayed — the same rule, and the same answers, as iOS. */
class MeasureSideTest {

    @Test
    fun `the usual range is inclusive at both ends`() {
        assertEquals(MeasureSide.NORMAL, MeasureSide.of(36.1, 36.1..37.8))
        assertEquals(MeasureSide.NORMAL, MeasureSide.of(37.8, 36.1..37.8))
        assertEquals(MeasureSide.LOW, MeasureSide.of(36.0, 36.1..37.8))
        assertEquals(MeasureSide.HIGH, MeasureSide.of(37.9, 36.1..37.8))
    }

    @Test
    fun `vital signs say which way`() {
        assertEquals("a fever", MeasureSide.HIGH, VitalKind.TEMPERATURE.side(39.2))
        assertEquals(MeasureSide.LOW, VitalKind.TEMPERATURE.side(35.8))
        assertEquals(MeasureSide.NORMAL, VitalKind.TEMPERATURE.side(37.0))
        assertEquals(MeasureSide.HIGH, VitalKind.PULSE.side(124.0))
        assertEquals(MeasureSide.LOW, VitalKind.OXYGEN_SATURATION.side(93.0))
    }

    @Test
    fun `lab values say which way`() {
        assertEquals("the number dengue turns on", MeasureSide.LOW, LabMeasure.PLATELETS.side(40.0))
        assertEquals(MeasureSide.NORMAL, LabMeasure.PLATELETS.side(250.0))
        assertEquals(MeasureSide.HIGH, LabMeasure.HAEMATOCRIT.side(56.0))
    }

    @Test
    fun `blood pressure takes the half that strayed further`() {
        assertEquals(MeasureSide.NORMAL, MeasureSide.bloodPressure(118.0, 76.0).first)
        assertEquals(MeasureSide.HIGH, MeasureSide.bloodPressure(150.0, 80.0).first)
        // Systolic a little high, diastolic far low: the diastolic is the story.
        assertEquals(MeasureSide.LOW, MeasureSide.bloodPressure(140.0, 50.0).first)
        // Both equally out: systolic wins the tie.
        assertEquals(MeasureSide.HIGH, MeasureSide.bloodPressure(140.0, 58.0).first)
    }

    @Test
    fun `the three words are the lab table's own`() {
        assertEquals("lab.flag.low", MeasureSide.LOW.labelKey)
        assertEquals("lab.flag.ok", MeasureSide.NORMAL.labelKey)
        assertEquals("lab.flag.high", MeasureSide.HIGH.labelKey)
    }
}
