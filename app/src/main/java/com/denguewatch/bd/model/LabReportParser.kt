package com.denguewatch.bd.model

/**
 * Reads lab values out of the text recognised on a photographed report.
 *
 * Everything this produces is a *suggestion*. It is shown in the normal entry
 * form for the reader to check and correct before anything is saved, because a
 * misread platelet count is worse than no platelet count: one is a number
 * somebody can see is wrong, the other is a number they will believe.
 *
 * Pure text in, draft out — no camera, no recogniser, no view. The hard part is
 * the reading, and this way it can be tested against real report wording.
 * Ported from the iOS build.
 */
object LabReportParser {

    /** How a value was written, so it can be normalised to what the app stores. */
    private enum class Scale {
        /** Already in ×10³/µL, e.g. "96" or "96 K/µL". */
        THOUSANDS,

        /** Written out in full, e.g. "96,000 /cumm" — divide by 1,000. */
        ABSOLUTE,

        /** South Asian convention: "1.5 lakh" is 150 ×10³/µL. */
        LAKH,
    }

    /**
     * Words a Bangladeshi lab report is likely to use for each measure.
     * Lowercased, matched as substrings so "platelet count (plt)" still hits.
     */
    private val synonyms: Map<LabMeasure, List<String>> = mapOf(
        LabMeasure.PLATELETS to listOf("platelet", "platelets", "plt"),
        LabMeasure.HAEMATOCRIT to listOf("haematocrit", "hematocrit", "hct", "pcv"),
        LabMeasure.WHITE_CELLS to
            listOf("white blood cell", "white cell", "wbc", "total wbc", "tlc", "leucocyte"),
        LabMeasure.HAEMOGLOBIN to listOf("haemoglobin", "hemoglobin", "hgb", "hb"),
    )

    /**
     * Includes what a recogniser is likely to *mis*read.
     *
     * A capital I and a lowercase l are the same shape in many report fonts,
     * so "IgM" comes back as "lgM" often enough to matter. Accepting both
     * costs nothing — no other word on a lab report is "lgm" — and the
     * alternative is silently missing the serology on a readable report.
     */
    private val testSynonyms: Map<DengueTest, List<String>> = mapOf(
        DengueTest.NS1 to listOf("ns1", "ns-1", "dengue ns1", "ns1 antigen"),
        DengueTest.IGM to listOf("igm", "ig m", "dengue igm", "lgm", "1gm"),
        DengueTest.IGG to listOf("igg", "ig g", "dengue igg", "lgg", "1gg"),
    )

    private val NUMBER = Regex("""(\d+(?:[.,]\d+)?)""")

    /**
     * How a report says its count is already in thousands per microlitre,
     * lowercased, including what the recogniser makes of the superscript and
     * the micro sign: ×10³/µL comes back as x10^3/ul, x103/ul, 10*3/ul and the
     * like. ×10⁹/L is the same quantity in SI units.
     */
    private val THOUSANDS_MARKERS = listOf(
        "x10^3", "x10³", "×10^3", "×10³", "x 10^3", "× 10^3", "10^3/", "10³/", "10*3", "x103/", "e3/",
        "x10^9", "x10⁹", "×10^9", "×10⁹", "10^9/l", "10⁹/l", "10*9", "x109/l",
        "k/ul", "k/µl", "k/μl", "thou/",
    )

    /**
     * Builds a draft report from recognised text.
     *
     * Lines are considered one at a time: lab reports are tabular, and a value
     * on the same line as its label is far more reliable than one found by
     * searching the whole page.
     */
    fun draft(text: String): LabReport {
        var report = LabReport()

        text.split('\n', '\r').forEach { rawLine ->
            val line = rawLine.lowercase().trim()
            if (line.isEmpty()) return@forEach

            synonyms.forEach inner@{ (measure, words) ->
                if (report.value(measure) != null) return@inner
                if (words.none { line.contains(it) }) return@inner
                val value = firstNumber(line, measure) ?: return@inner
                report = report.with(measure, value)
            }

            testSynonyms.forEach inner@{ (test, words) ->
                if (report.result(test) != TestResult.NOT_DONE) return@inner
                if (words.none { line.contains(it) }) return@inner
                // "non-reactive" contains "reactive", so the negatives are
                // tested first; otherwise a negative report reads as positive,
                // which is the worst way this could be wrong.
                val negative = line.contains("negative") ||
                    line.contains("non reactive") || line.contains("non-reactive")
                report = when {
                    negative -> report.with(test, TestResult.NEGATIVE)
                    line.contains("positive") || line.contains("reactive") ->
                        report.with(test, TestResult.POSITIVE)
                    else -> report
                }
            }
        }
        return report
    }

    /**
     * Finds a number on the line and converts it to the stored unit.
     *
     * Returns null when the result would be outside what a person could have —
     * a reference range printed beside the value ("150 - 450") or a stray date
     * must not become somebody's platelet count.
     */
    private fun firstNumber(line: String, measure: LabMeasure): Double? {
        val scale = scaleOf(line, measure)
        NUMBER.findAll(line).forEach { match ->
            val raw = match.groupValues[1].replace(",", "")
            val number = raw.toDoubleOrNull() ?: return@forEach
            val normalised = normalise(number, scale)
            if (normalised in measure.enterable) return normalised
        }
        return null
    }

    private fun scaleOf(line: String, measure: LabMeasure): Scale {
        if (measure != LabMeasure.PLATELETS && measure != LabMeasure.WHITE_CELLS) {
            return Scale.THOUSANDS
        }
        if (line.contains("lakh") || line.contains("lac")) return Scale.LAKH
        // A stated multiplier wins over the unit after it. "95 x10^3/uL" — how
        // most CBC slips print it, and how the recogniser reads "×10³/µL" —
        // ends in "/ul", which on its own means a count written in full; read
        // that way, 95 became 0.095, was refused as impossible, and the
        // platelet count was silently left blank. ×10⁹/L is the same scale.
        if (THOUSANDS_MARKERS.any { line.contains(it) }) return Scale.THOUSANDS
        // "/cumm", "/cmm" and bare six-figure counts are absolute.
        val absoluteUnit = line.contains("cumm") || line.contains("cmm") || line.contains("/ul")
        if (absoluteUnit || (!line.contains("µl") && line.contains("000"))) return Scale.ABSOLUTE
        return Scale.THOUSANDS
    }

    private fun normalise(value: Double, scale: Scale): Double = when (scale) {
        Scale.THOUSANDS -> value
        Scale.ABSOLUTE -> value / 1_000
        Scale.LAKH -> value * 100
    }

    private fun LabReport.with(measure: LabMeasure, value: Double): LabReport = when (measure) {
        LabMeasure.PLATELETS -> copy(platelets = value)
        LabMeasure.HAEMATOCRIT -> copy(haematocrit = value)
        LabMeasure.WHITE_CELLS -> copy(whiteCells = value)
        LabMeasure.HAEMOGLOBIN -> copy(haemoglobin = value)
    }

    private fun LabReport.with(test: DengueTest, result: TestResult): LabReport = when (test) {
        DengueTest.NS1 -> copy(ns1 = result)
        DengueTest.IGM -> copy(igm = result)
        DengueTest.IGG -> copy(igg = result)
    }
}
