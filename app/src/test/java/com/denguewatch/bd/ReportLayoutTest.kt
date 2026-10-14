package com.denguewatch.bd

import com.denguewatch.bd.feature.health.formatForField
import com.denguewatch.bd.model.LabMeasure
import com.denguewatch.bd.model.MeasureInput
import com.denguewatch.bd.model.LabReportParser
import com.denguewatch.bd.model.ReportLayout
import com.denguewatch.bd.model.ReportLayout.Fragment
import com.denguewatch.bd.model.TestResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rebuilding a report's rows from where its text sits.
 *
 * The case that matters is taken from what ML Kit actually returned for a
 * two-column CBC report on a device: every label, then every value. Before
 * this grouping existed the parser read nothing at all from it, and the app
 * told the reader their perfectly good photograph could not be read.
 */
class ReportLayoutTest {

    /** A label on the left and its value on the right, sharing a row. */
    private fun row(y: Int, label: String, value: String) = listOf(
        Fragment(text = label, top = y, bottom = y + 30, left = 60),
        Fragment(text = value, top = y + 2, bottom = y + 32, left = 700),
    )

    @Test
    fun `a two-column report is put back into rows`() {
        val fragments =
            row(100, "Haemoglobin", "14.1 g/dL") +
                row(150, "Haematocrit (PCV)", "47.5 %") +
                row(200, "Total WBC Count", "3,600 /cumm") +
                row(250, "Platelet Count", "96,000 /cumm")

        val rows = ReportLayout.rows(fragments).lines()

        assertEquals(4, rows.size)
        assertEquals("Haemoglobin  14.1 g/dL", rows[0])
        assertEquals("Platelet Count  96,000 /cumm", rows[3])
    }

    @Test
    fun `the parser reads a report it could not read before`() {
        // The end-to-end point of the grouping: same fragments, real values.
        val fragments =
            row(100, "Haemoglobin", "14.1 g/dL") +
                row(150, "Haematocrit (PCV)", "47.5 %") +
                row(200, "Total WBC Count", "3,600 /cumm") +
                row(250, "Platelet Count", "96,000 /cumm") +
                row(320, "Dengue NS1 Antigen", "Positive") +
                // As the recogniser actually read it: a capital I as an l.
                row(370, "Dengue lgM Antibody", "Negative")

        val report = LabReportParser.draft(ReportLayout.rows(fragments))

        assertEquals(14.1, report.haemoglobin!!, 0.01)
        assertEquals(47.5, report.haematocrit!!, 0.01)
        assertEquals(3.6, report.whiteCells!!, 0.01)
        assertEquals(96.0, report.platelets!!, 0.01)
        assertEquals(TestResult.POSITIVE, report.ns1)
        assertEquals(TestResult.NEGATIVE, report.igm)
    }

    @Test
    fun `fragments out of order still group by position`() {
        // ML Kit emits blocks, not rows: the order it hands them over says
        // nothing about where they sit.
        val fragments = listOf(
            Fragment("96,000 /cumm", top = 252, bottom = 282, left = 700),
            Fragment("Haemoglobin", top = 100, bottom = 130, left = 60),
            Fragment("Platelet Count", top = 250, bottom = 280, left = 60),
            Fragment("14.1 g/dL", top = 102, bottom = 132, left = 700),
        )
        val rows = ReportLayout.rows(fragments).lines()
        assertEquals("Haemoglobin  14.1 g/dL", rows[0])
        assertEquals("Platelet Count  96,000 /cumm", rows[1])
    }

    @Test
    fun `rows far apart are not merged`() {
        val fragments = listOf(
            Fragment("Platelet Count", top = 100, bottom = 130, left = 60),
            Fragment("96,000 /cumm", top = 400, bottom = 430, left = 700),
        )
        assertEquals(2, ReportLayout.rows(fragments).lines().size)
    }

    @Test
    fun `nothing recognised gives nothing back`() {
        assertTrue(ReportLayout.rows(emptyList()).isEmpty())
    }
}

/**
 * Rendering a recognised value back into the entry field.
 *
 * Its own test because the bug it covers shipped and crashed the app: the
 * format string was built by interpolation inside a generated file and came
 * out literal, which `Formatter` rejected. Nothing caught it because this runs
 * only after a scan actually succeeds.
 */
class FormatForFieldTest {

    @Test
    fun `platelets are whole, being counted in thousands`() {
        assertEquals("96", formatForField(96.0, LabMeasure.PLATELETS))
        assertEquals("150", formatForField(149.6, LabMeasure.PLATELETS))
    }

    @Test
    fun `every other measure keeps one place`() {
        assertEquals("47.5", formatForField(47.5, LabMeasure.HAEMATOCRIT))
        assertEquals("14.1", formatForField(14.1, LabMeasure.HAEMOGLOBIN))
        assertEquals("3.6", formatForField(3.6, LabMeasure.WHITE_CELLS))
    }

    @Test
    fun `what it writes is what the reader can type back`() {
        // The field is read by MeasureInput, so the two have to agree.
        LabMeasure.entries.forEach { measure ->
            // Mid-range, because a value outside what the measure accepts is
            // supposed to read back as nothing — that is MeasureInput doing
            // its job, not a formatting failure.
            val sample = (measure.enterable.start + measure.enterable.endInclusive) / 2
            val rendered = formatForField(sample, measure)
            val readBack = MeasureInput.read(rendered, measure).storedValue
            assertEquals("round trip failed for $measure", sample, readBack!!, 0.51)
        }
    }
}
