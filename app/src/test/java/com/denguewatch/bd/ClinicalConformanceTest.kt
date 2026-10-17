package com.denguewatch.bd

import com.denguewatch.bd.feature.triage.TriageEngine
import com.denguewatch.bd.model.CarePlanContext
import com.denguewatch.bd.model.DengueTest
import com.denguewatch.bd.model.FluidEntry
import com.denguewatch.bd.model.FluidLog
import com.denguewatch.bd.model.HealthTrend
import com.denguewatch.bd.model.LabMeasure
import com.denguewatch.bd.model.LabReportParser
import com.denguewatch.bd.model.MeasureInput
import com.denguewatch.bd.model.MeasureSide
import com.denguewatch.bd.model.TemperatureUnit
import com.denguewatch.bd.model.VitalKind
import com.denguewatch.bd.model.VitalsEntry
import com.denguewatch.bd.model.WHOCarePlan
import com.denguewatch.bd.model.side
import java.io.File
import java.time.OffsetDateTime
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Runs the shared clinical cases in `clinical-rules.json`.
 *
 * The iOS build runs the same file, byte for byte, through its own
 * implementation. The two apps carry every clinical rule twice — triage, the
 * WHO plan, reading ranges, what a typed number means, what a scanned report
 * says — and nothing else checks that the two copies still agree. A rule
 * changed on one platform and not the other fails here, or there.
 */
class ClinicalConformanceTest {

    private val text: String =
        requireNotNull(javaClass.classLoader?.getResource("clinical-rules.json")) {
            "clinical-rules.json is missing from the test resources"
        }.readText()
    private val cases = JSONObject(text)
    private val now = millis(cases.getString("now"))

    private lateinit var savedZone: TimeZone

    /** The fluid rules count nights in the phone's own zone; the cases are Dhaka's. */
    @Before
    fun useDhakaTime() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dhaka"))
    }

    @After
    fun restoreTime() {
        TimeZone.setDefault(savedZone)
    }

    // MARK: - Reading the file

    private fun list(key: String): List<JSONObject> {
        val array = cases.getJSONArray(key)
        check(array.length() > 0) { "no $key cases" }
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    private fun millis(iso: String): Long = OffsetDateTime.parse(iso).toInstant().toEpochMilli()

    private fun strings(array: JSONArray?): List<String> =
        if (array == null) emptyList() else (0 until array.length()).map { array.getString(it) }

    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) getDouble(key) else null

    private fun JSONObject.intOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) getInt(key) else null

    private fun JSONObject.hoursAgo(): Long = now - (getDouble("hoursAgo") * 3_600_000).toLong()

    /** `SEE_DOCTOR_TODAY` → `seeDoctorToday`, the spelling iOS's cases use. */
    private fun camel(name: String): String =
        name.lowercase().split("_").mapIndexed { index, part ->
            if (index == 0) part else part.replaceFirstChar { it.uppercase() }
        }.joinToString("")

    private fun context(json: JSONObject?) = TriageEngine.Context(
        isPregnant = json?.optBoolean("isPregnant") ?: false,
        isUnderFive = json?.optBoolean("isUnderFive") ?: false,
        isOverSixty = json?.optBoolean("isOverSixty") ?: false,
        isUnderFiveOrOverSixty = json?.optBoolean("isUnderFiveOrOverSixty") ?: false,
        hasChronicCondition = json?.optBoolean("hasChronicCondition") ?: false,
        hadDengueBefore = json?.optBoolean("hadDengueBefore") ?: false,
    )

    private fun vitalKind(slug: String) = VitalKind.entries.first { it.slug == slug }
    private fun labMeasure(slug: String) = LabMeasure.entries.first { it.slug == slug }

    // MARK: - Keeping the two copies one

    @Test
    fun `the Android copy matches the iOS copy`() {
        // Only where both repositories sit side by side, as they do on the
        // machine that builds both apps. Elsewhere there is nothing to compare.
        val ios = File("../../DengueWatchBD/DengueWatchBDTests/clinical-rules.json")
        assumeTrue(ios.exists())
        assertEquals(
            "clinical-rules.json differs between the iOS and Android repositories; copy one over the other",
            ios.readText(),
            text,
        )
    }

    // MARK: - Cases

    @Test
    fun triage() {
        list("triage").forEach { item ->
            val selected = strings(item.getJSONArray("selected")).toSet()
            val context = context(item.optJSONObject("context"))
            val label = "$selected ${item.optJSONObject("context")}"
            assertEquals(label, item.getString("outcome"), camel(TriageEngine.evaluate(selected, context).name))
            assertEquals(
                label,
                item.getBoolean("raisedByRiskGroup"),
                TriageEngine.isRaisedByRiskGroup(selected, context),
            )
        }
    }

    @Test
    fun feverPhase() {
        list("feverPhase").forEach { item ->
            val days = item.getInt("feverDaysAgo")
            assertEquals("day $days", item.getString("phase"), TriageEngine.FeverPhase.of(days).name.lowercase())
        }
    }

    @Test
    fun carePlan() {
        list("carePlan").forEach { item ->
            val vitals = item.optJSONObject("vitals")?.let {
                VitalsEntry(
                    temperature = it.doubleOrNull("temperature"),
                    pulse = it.doubleOrNull("pulse"),
                    systolic = it.doubleOrNull("systolic"),
                    diastolic = it.doubleOrNull("diastolic"),
                    oxygenSaturation = it.doubleOrNull("oxygenSaturation"),
                )
            }
            val plan = WHOCarePlan.evaluate(
                symptoms = strings(item.getJSONArray("symptoms")).toSet(),
                vitals = vitals,
                context = context(item.optJSONObject("context")),
                feverDaysAgo = item.intOrNull("feverDaysAgo"),
            )
            val label = "${item.getJSONArray("symptoms")} ${item.opt("vitals")} ${item.opt("context")}"
            assertEquals(label, item.getString("group"), plan.group.name.lowercase())
            assertEquals(label, strings(item.getJSONArray("reasons")), plan.reasons.map { camel(it.name) })
            assertEquals(label, item.optString("phase").takeIf { !item.isNull("phase") }, plan.phase?.name?.lowercase())
            assertEquals(label, item.intOrNull("feverDay"), plan.feverDay)
        }
    }

    @Test
    fun vitalStatus() {
        list("vitalStatus").forEach { item ->
            val kind = vitalKind(item.getString("kind"))
            val value = item.getDouble("value")
            assertEquals("$kind $value", item.getString("status"), kind.status(value).slug)
            assertEquals("$kind $value", item.getString("side"), kind.side(value).name.lowercase())
        }
    }

    @Test
    fun labStatus() {
        list("labStatus").forEach { item ->
            val measure = labMeasure(item.getString("measure"))
            val value = item.getDouble("value")
            assertEquals("$measure $value", item.getString("status"), measure.status(value).slug)
            assertEquals("$measure $value", item.getString("side"), measure.side(value).name.lowercase())
        }
    }

    @Test
    fun bloodPressure() {
        list("bloodPressure").forEach { item ->
            val systolic = item.getDouble("systolic")
            val diastolic = item.getDouble("diastolic")
            val (side, status) = MeasureSide.bloodPressure(systolic, diastolic)
            assertEquals("$systolic/$diastolic", item.getString("side"), side.name.lowercase())
            assertEquals("$systolic/$diastolic", item.getString("status"), status.slug)
        }
    }

    @Test
    fun measureInput() {
        list("measureInput").forEach { item ->
            val text = item.getString("text")
            val reading = if (item.has("measure")) {
                MeasureInput.read(text, labMeasure(item.getString("measure")))
            } else {
                val unit = TemperatureUnit.entries.firstOrNull { it.slug == item.optString("unit") }
                    ?: TemperatureUnit.CELSIUS
                MeasureInput.read(text, vitalKind(item.getString("kind")), unit)
            }
            val label = "\"$text\""
            when (item.getString("result")) {
                "empty" -> assertEquals(label, MeasureInput.Reading.Empty, reading)
                "notANumber" -> assertEquals(label, MeasureInput.Reading.NotANumber, reading)
                "value" -> {
                    val value = (reading as? MeasureInput.Reading.Value)?.value
                        ?: return fail("$label: expected a value, got $reading")
                    assertEquals(label, item.getDouble("value"), value, 0.0001)
                }
                "outOfRange" -> {
                    val range = (reading as? MeasureInput.Reading.OutOfRange)?.range
                        ?: return fail("$label: expected out of range, got $reading")
                    assertEquals(label, item.getDouble("lower"), range.start, 0.0)
                    assertEquals(label, item.getDouble("upper"), range.endInclusive, 0.0)
                }
                else -> fail("$label: unknown result ${item.getString("result")}")
            }
        }
    }

    @Test
    fun labParser() {
        list("labParser").forEach { item ->
            val text = item.getString("text")
            val report = LabReportParser.draft(text)
            LabMeasure.entries.forEach { measure ->
                val expected = item.doubleOrNull(measure.slug)
                val actual = report.value(measure)
                if (expected == null) {
                    assertNull("$measure in $text", actual)
                } else {
                    assertEquals("$measure in $text", expected, actual ?: Double.NaN, 0.001)
                }
            }
            DengueTest.entries.forEach { test ->
                assertEquals("$test in $text", item.optString(test.slug, "notDone"), report.result(test).slug)
            }
        }
    }

    @Test
    fun trend() {
        list("trend").forEach { item ->
            val array = item.getJSONArray("values")
            val values = (0 until array.length()).map { array.getDouble(it) }
            val trend = HealthTrend.direction(values, item.getDouble("minimumChange"))
            assertEquals("$values", item.getString("trend"), camel(trend.name))
        }
    }

    @Test
    fun haematocritRising() {
        list("haematocritRising").forEach { item ->
            val array = item.getJSONArray("series")
            val series = (0 until array.length()).map { index ->
                array.getJSONObject(index).let { it.hoursAgo() to it.getDouble("value") }
            }
            assertEquals("$array", item.getBoolean("rising"), CarePlanContext.haematocritIsRising(series, now))
        }
    }

    @Test
    fun notableReadings() {
        list("notableReadings").forEach { item ->
            val vitalsArray = item.getJSONArray("vitals")
            val vitals = (0 until vitalsArray.length()).map { index ->
                vitalsArray.getJSONObject(index).let {
                    Triple(vitalKind(it.getString("kind")), it.getDouble("value"), it.hoursAgo())
                }
            }
            val labsArray = item.getJSONArray("labs")
            val labs = (0 until labsArray.length()).map { index ->
                labsArray.getJSONObject(index).let {
                    Triple(labMeasure(it.getString("measure")), it.getDouble("value"), it.hoursAgo())
                }
            }
            val context = CarePlanContext.from(vitals, labs, now)
            assertEquals(strings(item.getJSONArray("notableVitals")), context.notableVitals.map { it.slug })
            assertEquals(strings(item.getJSONArray("notableLabs")), context.notableLabs.map { it.slug })
        }
    }

    @Test
    fun recentReadings() {
        list("recentReadings").forEach { item ->
            val array = item.getJSONArray("entries")
            val entries = (0 until array.length()).map { index ->
                array.getJSONObject(index).let {
                    VitalsEntry(
                        dateMillis = it.hoursAgo(),
                        temperature = it.doubleOrNull("temperature"),
                        pulse = it.doubleOrNull("pulse"),
                        systolic = it.doubleOrNull("systolic"),
                        diastolic = it.doubleOrNull("diastolic"),
                        oxygenSaturation = it.doubleOrNull("oxygenSaturation"),
                    )
                }
            }
            val label = array.toString()
            val actual = WHOCarePlan.recentReadings(entries, now)
            val expected = item.optJSONObject("readings")
            if (expected == null) {
                assertNull(label, actual)
                return@forEach
            }
            val readings = requireNotNull(actual) { label }
            VitalKind.entries.forEach { kind ->
                assertEquals("${kind.slug} in $label", expected.doubleOrNull(kind.slug), readings.value(kind))
            }
        }
    }

    @Test
    fun wakingHours() {
        list("wakingHours").forEach { item ->
            assertEquals(
                "${item.getString("start")} → ${item.getString("end")}",
                item.getDouble("hours"),
                FluidLog.wakingHours(millis(item.getString("start")), millis(item.getString("end"))),
                0.001,
            )
        }
    }

    @Test
    fun urineGap() {
        list("urineGap").forEach { item ->
            val urine = strings(item.getJSONArray("urine")).map {
                FluidEntry(dateMillis = millis(it), kind = FluidEntry.Kind.URINE)
            }
            val drinks = strings(item.optJSONArray("drinks")).map {
                FluidEntry(dateMillis = millis(it), kind = FluidEntry.Kind.DRINK, millilitres = 200)
            }
            assertEquals(
                "${item.getJSONArray("urine")} at ${item.getString("now")}",
                item.getBoolean("long"),
                FluidLog.urineGapIsLong(urine + drinks, millis(item.getString("now"))),
            )
        }
    }
}
