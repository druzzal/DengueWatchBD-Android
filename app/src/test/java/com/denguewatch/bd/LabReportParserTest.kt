package com.denguewatch.bd

import com.denguewatch.bd.model.LabReportParser
import com.denguewatch.bd.model.TestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The parser against wording real Bangladeshi reports use.
 *
 * Ported from the iOS test suite, because the reading is the hard part and it
 * has to fail the same way on both platforms. Every value it produces is shown
 * to the reader for checking before it is saved — but a wrong number that
 * looks plausible is still the worst outcome, so the cases about *refusing to
 * guess* matter more than the ones about reading correctly.
 */
class LabReportParserTest {

    // MARK: - Units as Dhaka labs print them

    @Test
    fun `platelets written in full`() {
        // "96,000 /cumm" is the common form on a Dhaka CBC report.
        val report = LabReportParser.draft("Platelet Count      96,000 /cumm")
        assertEquals(96.0, report.platelets!!, 0.01)
    }

    @Test
    fun `platelets already in thousands`() {
        val report = LabReportParser.draft("Platelets  96 x10^3/µL")
        assertEquals(96.0, report.platelets!!, 0.01)
    }

    @Test
    fun `platelets in lakh`() {
        // 1.5 lakh is 150,000, which is 150 in the unit this app stores.
        val report = LabReportParser.draft("Platelet: 1.5 lakh")
        assertEquals(150.0, report.platelets!!, 0.01)
    }

    @Test
    fun `white cells written in full`() {
        val report = LabReportParser.draft("Total WBC Count   3,600 /cumm")
        assertEquals(3.6, report.whiteCells!!, 0.01)
    }

    // MARK: - Labels labs actually use

    @Test
    fun `abbreviations are recognised`() {
        val report = LabReportParser.draft(
            """
            Hb        14.1 g/dL
            HCT       47.5 %
            TLC       3,600 /cumm
            PLT       96,000 /cumm
            """.trimIndent()
        )
        assertEquals(14.1, report.haemoglobin!!, 0.01)
        assertEquals(47.5, report.haematocrit!!, 0.01)
        assertEquals(3.6, report.whiteCells!!, 0.01)
        assertEquals(96.0, report.platelets!!, 0.01)
    }

    @Test
    fun `american and british spellings both work`() {
        assertNotNull(LabReportParser.draft("Hemoglobin 13.2").haemoglobin)
        assertNotNull(LabReportParser.draft("Haemoglobin 13.2").haemoglobin)
    }

    // MARK: - Serology

    @Test
    fun `positive and negative results are read`() {
        val report = LabReportParser.draft(
            """
            Dengue NS1 Antigen    Positive
            Dengue IgM Antibody   Negative
            """.trimIndent()
        )
        assertEquals(TestResult.POSITIVE, report.ns1)
        assertEquals(TestResult.NEGATIVE, report.igm)
    }

    @Test
    fun `reactive and non-reactive are read`() {
        assertEquals(TestResult.POSITIVE, LabReportParser.draft("NS1: Reactive").ns1)
        // The one that must not go wrong: "non-reactive" contains "reactive",
        // so a naive check reads a negative report as positive.
        assertEquals(TestResult.NEGATIVE, LabReportParser.draft("NS1: Non-Reactive").ns1)
        assertEquals(TestResult.NEGATIVE, LabReportParser.draft("NS1: Non Reactive").ns1)
    }

    @Test
    fun `a test not on the page stays not done`() {
        val report = LabReportParser.draft("Dengue NS1 Antigen    Positive")
        assertEquals(TestResult.NOT_DONE, report.igg)
        assertEquals(TestResult.NOT_DONE, report.igm)
    }

    // MARK: - Refusing to guess

    @Test
    fun `a reference range is not mistaken for a result`() {
        // Reports print the normal range beside the value. Picking the first
        // plausible number must not turn "150 - 450" into the reading.
        val report =
            LabReportParser.draft("Platelet Count  96,000 /cumm   (150,000 - 450,000)")
        assertEquals(96.0, report.platelets!!, 0.01)
    }

    @Test
    fun `nonsense lines produce nothing`() {
        val report = LabReportParser.draft(
            """
            Dhaka Medical Laboratory
            Patient: Mr Rahman     Age: 34
            Collected: 13/09/2026
            """.trimIndent()
        )
        assertNull("no measure should be invented from a header", report.platelets)
        assertNull(report.haemoglobin)
        assertNull(report.haematocrit)
        assertNull(report.whiteCells)
    }

    @Test
    fun `an empty scan produces an empty draft`() {
        listOf("", "   \n  \n").forEach { input ->
            val report = LabReportParser.draft(input)
            assertNull(report.platelets)
            assertNull(report.haemoglobin)
            assertEquals(TestResult.NOT_DONE, report.ns1)
        }
    }

    @Test
    fun `a stated x10 power means thousands whatever unit follows`() {
        // The recogniser reads ×10³/µL as plain ASCII, and "/ul" alone means a
        // count written in full. These all used to come back blank.
        listOf(
            "Platelet Count     95     x10^3/uL",
            "Platelet Count 95 x10³/uL",
            "Platelets 95 10^3/ul",
            "PLT 95 x10*3/uL",
            "Platelet count 95 K/uL",
            "Platelets 95 x10^9/L",
        ).forEach { line ->
            assertEquals(line, 95.0, LabReportParser.draft(line).platelets!!, 0.001)
        }
        assertEquals(3.2, LabReportParser.draft("WBC Total Count    3.2    x10^3/uL").whiteCells!!, 0.001)
    }

    @Test
    fun `a full count per microlitre is still read as a full count`() {
        assertEquals(250.0, LabReportParser.draft("Platelet count 250000 /uL").platelets!!, 0.001)
        assertEquals(3.6, LabReportParser.draft("WBC 3600 /ul").whiteCells!!, 0.001)
    }
}
