package com.denguewatch.bd.model

import org.json.JSONObject
import java.util.UUID

/**
 * The blood counts a dengue patient is given, and the ranges that matter.
 *
 * The names shown are the ones a Bangladeshi lab slip prints — Platelet, HCT,
 * WBC, Hb — in both languages, so a reader matching the app against their own
 * report does not have to translate each row in their head.
 */
enum class LabMeasure {
    PLATELETS, HAEMATOCRIT, WHITE_CELLS, HAEMOGLOBIN;

    val slug: String
        get() = when (this) {
            PLATELETS -> "platelets"
            HAEMATOCRIT -> "haematocrit"
            WHITE_CELLS -> "whiteCells"
            HAEMOGLOBIN -> "haemoglobin"
        }

    val labelKey: String get() = "lab.$slug"
    val unitKey: String get() = "lab.unit.$slug"

    val enterable: ClosedFloatingPointRange<Double>
        get() = when (this) {
            PLATELETS -> 1.0..1_500.0        // thousands per microlitre
            HAEMATOCRIT -> 10.0..70.0        // percent
            WHITE_CELLS -> 0.1..100.0        // thousands per microlitre
            HAEMOGLOBIN -> 2.0..25.0         // g/dL
        }

    /** A typical adult range, for context only. */
    val typical: ClosedFloatingPointRange<Double>
        get() = when (this) {
            PLATELETS -> 150.0..450.0
            HAEMATOCRIT -> 36.0..54.0
            WHITE_CELLS -> 4.0..11.0
            HAEMOGLOBIN -> 12.0..17.0
        }

    val markedlyAbnormal: ClosedFloatingPointRange<Double>
        get() = when (this) {
            PLATELETS -> 100.0..600.0        // below 100 is the one that matters in dengue
            HAEMATOCRIT -> 30.0..60.0
            WHITE_CELLS -> 2.0..20.0
            HAEMOGLOBIN -> 9.0..20.0
        }

    val decimals: Int get() = if (this == PLATELETS) 0 else 1

    fun status(value: Double): MeasureStatus =
        MeasureStatus.of(value, usual = typical, far = markedlyAbnormal)

    /**
     * Which side of the typical range a reading fell on, in words.
     *
     * The tint already says something is off; this says what. Colour on its own
     * is not a reading a colour-blind person can take, and the number that
     * matters most on this card is a platelet count. Matches iOS, which prints
     * the same three words.
     */
    fun flagKey(value: Double): String = when {
        value < typical.start -> "lab.flag.low"
        value > typical.endInclusive -> "lab.flag.high"
        else -> "lab.flag.ok"
    }
}

/**
 * A dengue antigen or antibody test.
 *
 * Recorded as the lab reported it. The app does not decide what a result
 * means: a positive NS1 with a negative IgM on day two is a conversation with
 * a clinician, not a conclusion an app should reach on someone's behalf.
 */
enum class DengueTest {
    NS1, IGM, IGG;

    val slug: String
        get() = when (this) {
            NS1 -> "ns1"
            IGM -> "igm"
            IGG -> "igg"
        }

    val labelKey: String get() = "lab.test.$slug"

    /**
     * When this test is usually informative, counting the first day of fever
     * as day 1. A fact about the assay, not an interpretation of anybody's
     * result.
     */
    val usefulDaysKey: String get() = "lab.timing.$slug"
}

enum class TestResult {
    NOT_DONE, NEGATIVE, POSITIVE;

    val slug: String
        get() = when (this) {
            NOT_DONE -> "notDone"
            NEGATIVE -> "negative"
            POSITIVE -> "positive"
        }

    val labelKey: String get() = "lab.result.$slug"
}

/**
 * One lab report as the reader entered it.
 *
 * Carries a time as well as a date: two counts from one day are ordinary
 * during dengue — a morning one and an evening one — and a date alone leaves
 * the record unable to say which came first, which is the whole point of
 * watching a platelet count fall.
 */
data class LabReport(
    val id: String = UUID.randomUUID().toString(),
    val dateMillis: Long = System.currentTimeMillis(),
    val platelets: Double? = null,
    val haematocrit: Double? = null,
    val whiteCells: Double? = null,
    val haemoglobin: Double? = null,
    val ns1: TestResult = TestResult.NOT_DONE,
    val igm: TestResult = TestResult.NOT_DONE,
    val igg: TestResult = TestResult.NOT_DONE,
    val note: String = "",
) {
    fun value(measure: LabMeasure): Double? = when (measure) {
        LabMeasure.PLATELETS -> platelets
        LabMeasure.HAEMATOCRIT -> haematocrit
        LabMeasure.WHITE_CELLS -> whiteCells
        LabMeasure.HAEMOGLOBIN -> haemoglobin
    }

    fun result(test: DengueTest): TestResult = when (test) {
        DengueTest.NS1 -> ns1
        DengueTest.IGM -> igm
        DengueTest.IGG -> igg
    }

    val hasAnyValue: Boolean get() = LabMeasure.entries.any { value(it) != null }
    val hasAnyResult: Boolean get() = DengueTest.entries.any { result(it) != TestResult.NOT_DONE }
    val isEmpty: Boolean get() = !hasAnyValue && !hasAnyResult

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("date", dateMillis)
        platelets?.let { put("platelets", it) }
        haematocrit?.let { put("haematocrit", it) }
        whiteCells?.let { put("whiteCells", it) }
        haemoglobin?.let { put("haemoglobin", it) }
        put("ns1", ns1.slug)
        put("igm", igm.slug)
        put("igg", igg.slug)
        put("note", note)
    }

    companion object {
        private fun JSONObject.doubleOrNull(key: String): Double? =
            if (has(key) && !isNull(key)) optDouble(key) else null

        private fun result(raw: String?): TestResult =
            TestResult.entries.firstOrNull { it.slug == raw } ?: TestResult.NOT_DONE

        fun from(json: JSONObject) = LabReport(
            id = json.optStringOrNull("id") ?: UUID.randomUUID().toString(),
            dateMillis = json.optLong("date", System.currentTimeMillis()),
            platelets = json.doubleOrNull("platelets"),
            haematocrit = json.doubleOrNull("haematocrit"),
            whiteCells = json.doubleOrNull("whiteCells"),
            haemoglobin = json.doubleOrNull("haemoglobin"),
            ns1 = result(json.optStringOrNull("ns1")),
            igm = result(json.optStringOrNull("igm")),
            igg = result(json.optStringOrNull("igg")),
            note = json.optString("note", ""),
        )
    }
}
