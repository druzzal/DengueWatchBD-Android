package com.denguewatch.bd

import com.denguewatch.bd.data.CaseLogEntry
import com.denguewatch.bd.feature.prevent.MythFact
import com.denguewatch.bd.feature.triage.Symptom
import com.denguewatch.bd.feature.triage.TriageEngine
import com.denguewatch.bd.feature.triage.TriageOutcome
import com.denguewatch.bd.i18n.Strings
import com.denguewatch.bd.model.CarePlanContext
import com.denguewatch.bd.model.FluidDay
import com.denguewatch.bd.model.FluidEntry
import com.denguewatch.bd.model.FluidLog
import com.denguewatch.bd.model.HealthLog
import com.denguewatch.bd.model.WHOCarePlan
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * The changes WHO's 2025 arboviral guideline made to what this app advises:
 * who is seen the same day, the haematocrit pattern it points at, and the fluid
 * log behind protocolized oral fluids. Ported from the iOS suite, so the two
 * apps cannot drift apart on any of it.
 */
class WHO2025Test {

    private lateinit var savedZone: TimeZone

    /** Fixed in Dhaka, so the night window falls in the same place wherever the suite runs. */
    @Before
    fun fixZone() {
        savedZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dhaka"))
    }

    @After
    fun restoreZone() = TimeZone.setDefault(savedZone)

    private fun at(day: Int, hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.OCTOBER, day, hour, minute)
    }.timeInMillis

    private val hour = 3_600_000L

    // MARK: - Triage

    @Test
    fun `being unable to drink is a warning sign`() {
        assertEquals(Symptom.Group.WARNING, TriageEngine.symptom("oralIntolerance")?.group)
        assertEquals(TriageOutcome.SEE_DOCTOR_TODAY,
                     TriageEngine.evaluate(setOf("oralIntolerance"), TriageEngine.Context()))
    }

    @Test
    fun `a pregnant reader with a fever is seen today`() {
        val context = TriageEngine.Context(isPregnant = true)
        assertEquals(TriageOutcome.SEE_DOCTOR_TODAY, TriageEngine.evaluate(setOf("fever"), context))
        assertTrue(TriageEngine.isRaisedByRiskGroup(setOf("fever"), context))
    }

    @Test
    fun `previous dengue alone no longer raises the result`() {
        assertEquals(TriageOutcome.TEST_ADVISED, TriageEngine.evaluate(
            setOf("fever", "headache", "aches"), TriageEngine.Context(hadDengueBefore = true)))
    }

    @Test
    fun `a warning sign is not described as a risk group result`() {
        assertFalse(TriageEngine.isRaisedByRiskGroup(
            setOf("fever", "vomiting"), TriageEngine.Context(isPregnant = true)))
    }

    /** The result screen and the care plan must agree on the same answers. */
    @Test
    fun `the result and the care plan agree on the same-day groups`() {
        listOf(
            TriageEngine.Context(isPregnant = true),
            TriageEngine.Context(isUnderFiveOrOverSixty = true),
            TriageEngine.Context(hasChronicCondition = true),
        ).forEach { context ->
            assertEquals(TriageOutcome.SEE_DOCTOR_TODAY, TriageEngine.evaluate(setOf("fever"), context))
            assertEquals(WHOCarePlan.Group.REFERRAL,
                         WHOCarePlan.evaluate(setOf("fever"), null, context, null).group)
        }
    }

    /**
     * A check saved under the old rules shows what the current rules say, so
     * its card can never disagree with the care plan beside it.
     */
    @Test
    fun `a check is judged by today's rules, not the stored outcome`() {
        val oldRules = CaseLogEntry(
            symptomIds = listOf("fever", "headache", "aches"),
            outcomeOrdinal = TriageOutcome.SEE_DOCTOR_TODAY.ordinal,
            hadDengueBefore = true,
        )
        assertEquals(TriageOutcome.TEST_ADVISED, oldRules.outcome)
        assertEquals(WHOCarePlan.Group.HOME, WHOCarePlan.evaluate(
            oldRules.symptomIds.toSet(), null, oldRules.triageContext, null).group)
    }

    @Test
    fun `the new text exists in both languages`() {
        val keys = listOf("outcome.seeDoctor.riskGroup.summary", "care.plan.haematocritRising",
                          "check.actions", "check.history.title", "check.history.latest",
                          "check.history.footer", "check.delete", "check.delete.confirm",
                          "check.delete.detail", "log.checksOnly", "fluid.section", "fluid.urine.gap",
                          "pdf.fluidDays", "pdf.fluids.chart.intake", "pdf.fluids.aim")
        keys.forEach {
            assertNotNull("missing EN: $it", Strings.english[it])
            assertNotNull("missing BN: $it", Strings.bangla[it])
        }
    }

    // MARK: - Rising haematocrit

    private fun series(values: List<Double>, newestHoursAgo: Long, now: Long) =
        values.mapIndexed { i, v -> (now - newestHoursAgo * hour - (values.size - 1 - i) * 24 * hour) to v }

    @Test
    fun `two meaningful rises in a row are flagged`() {
        val now = at(16, 12)
        assertTrue(CarePlanContext.haematocritIsRising(series(listOf(38.0, 41.0, 45.0), 2, now), now))
    }

    @Test
    fun `one rise is not a progression`() {
        val now = at(16, 12)
        assertFalse(CarePlanContext.haematocritIsRising(series(listOf(38.0, 45.0), 2, now), now))
        assertFalse(CarePlanContext.haematocritIsRising(series(listOf(45.0, 38.0, 45.0), 2, now), now))
    }

    @Test
    fun `rises within the assay's scatter are not flagged`() {
        val now = at(16, 12)
        assertFalse(CarePlanContext.haematocritIsRising(series(listOf(40.0, 41.0, 42.0), 2, now), now))
    }

    @Test
    fun `an old rise says nothing about today`() {
        val now = at(16, 12)
        assertFalse(CarePlanContext.haematocritIsRising(series(listOf(38.0, 41.0, 45.0), 24 * 6, now), now))
    }

    // MARK: - Fluid log

    @Test
    fun `the day totals only that day`() {
        val entries = listOf(
            FluidEntry(dateMillis = at(16, 8), kind = FluidEntry.Kind.DRINK, millilitres = 250),
            FluidEntry(dateMillis = at(16, 9), kind = FluidEntry.Kind.DRINK, millilitres = 200),
            FluidEntry(dateMillis = at(16, 10), kind = FluidEntry.Kind.URINE),
            FluidEntry(dateMillis = at(15, 20), kind = FluidEntry.Kind.DRINK, millilitres = 500),
        )
        assertEquals(FluidDay(450, 2, 1), FluidLog.day(at(16, 12), entries))
    }

    @Test
    fun `the urine aim is WHO's`() {
        assertEquals(4..6, FluidLog.URINATION_AIM)
    }

    private fun urine(at: Long) = listOf(FluidEntry(dateMillis = at, kind = FluidEntry.Kind.URINE))

    @Test
    fun `a long daytime gap is raised`() {
        assertTrue(FluidLog.urineGapIsLong(urine(at(16, 9)), at(16, 16)))
    }

    @Test
    fun `a recent entry raises nothing`() {
        assertFalse(FluidLog.urineGapIsLong(urine(at(16, 14)), at(16, 16)))
    }

    /** Urine at ten at night and a look at the app at eight is a night's sleep, not a ten-hour gap. */
    @Test
    fun `the night is not counted`() {
        assertFalse(FluidLog.urineGapIsLong(urine(at(15, 22)), at(16, 8)))
        assertEquals(2.0, FluidLog.wakingHours(at(15, 22), at(16, 8)), 0.001)
    }

    @Test
    fun `waking hours either side of the night add up`() {
        // 7 pm to 11 pm is four, 7 am to 10 am is three: seven waking hours.
        assertEquals(7.0, FluidLog.wakingHours(at(15, 19), at(16, 10)), 0.001)
        assertTrue(FluidLog.urineGapIsLong(urine(at(15, 19)), at(16, 10)))
    }

    @Test
    fun `the gap does not grow during the night`() {
        assertEquals(0.0, FluidLog.wakingHours(at(16, 0), at(16, 6)), 0.001)
    }

    /** Someone who stopped logging has not stopped passing urine. */
    @Test
    fun `an abandoned log raises nothing`() {
        assertFalse(FluidLog.urineGapIsLong(urine(at(13, 12)), at(16, 12)))
        assertFalse(FluidLog.urineGapIsLong(emptyList(), at(16, 12)))
    }

    @Test
    fun `the summary uses whole days only`() {
        val entries = buildList {
            add(FluidEntry(dateMillis = at(14, 9), kind = FluidEntry.Kind.DRINK, millilitres = 2000))
            add(FluidEntry(dateMillis = at(14, 10), kind = FluidEntry.Kind.URINE))
            add(FluidEntry(dateMillis = at(15, 9), kind = FluidEntry.Kind.DRINK, millilitres = 1000))
            listOf(9, 12, 15, 18).forEach { add(FluidEntry(dateMillis = at(15, it), kind = FluidEntry.Kind.URINE)) }
            // Today: half a day, which must not drag the average down.
            add(FluidEntry(dateMillis = at(16, 8), kind = FluidEntry.Kind.DRINK, millilitres = 100))
        }
        val summary = FluidLog.summary(entries, at(16, 12))
        assertEquals(3, summary.daysLogged)
        assertEquals(1500, summary.averageMillilitres)
        assertEquals(2, summary.daysWithUrineLogged)
        assertEquals(1, summary.daysUrineAimMet)
        assertTrue(summary.includesToday)
    }

    /** A day with drinks but no urine logged is a day it was not written down, not a day of none. */
    @Test
    fun `a day with no urine logged is not counted as short`() {
        val summary = FluidLog.summary(
            listOf(FluidEntry(dateMillis = at(15, 9), kind = FluidEntry.Kind.DRINK, millilitres = 500)),
            at(16, 12),
        )
        assertEquals(0, summary.daysWithUrineLogged)
        assertFalse(summary.includesToday)
    }

    @Test
    fun `only today gives no average yet`() {
        val summary = FluidLog.summary(
            listOf(FluidEntry(dateMillis = at(16, 8), kind = FluidEntry.Kind.DRINK, millilitres = 300)),
            at(16, 12),
        )
        assertNull(summary.averageMillilitres)
    }

    @Test
    fun `fluids make a day in the log, summed rather than listed`() {
        val entry = FluidEntry(dateMillis = at(16, 9), kind = FluidEntry.Kind.DRINK, millilitres = 250)
        val days = HealthLog.days(emptyList(), emptyList(), listOf(entry))
        assertEquals(1, days.size)
        assertEquals(listOf(entry), days.first().fluids)
        assertTrue(days.first().itemsNewestFirst.isEmpty())
        assertFalse(days.first().hasReadings)
    }

    @Test
    fun `a fluid entry survives the round trip to its file format`() {
        val drink = FluidEntry(dateMillis = at(16, 9), kind = FluidEntry.Kind.DRINK, millilitres = 250)
        val urine = FluidEntry(dateMillis = at(16, 10), kind = FluidEntry.Kind.URINE)
        assertEquals(drink, FluidEntry.from(drink.toJson()))
        assertEquals(urine, FluidEntry.from(urine.toJson()))
    }

    // MARK: - Myths

    @Test
    fun `the new myths carry the guidance`() {
        assertTrue(MythFact.all.map { it.id }.containsAll(listOf("transfusion", "steroids")))
        val transfusion = Strings.english["myth.transfusion.fact"].orEmpty().lowercase()
        assertTrue(transfusion, transfusion.contains("no bleeding"))
        val steroids = Strings.english["myth.steroids.fact"].orEmpty().lowercase()
        assertTrue(steroids, steroids.contains("against"))
    }

    /** WHO 2025: water should always be the main source of fluids. */
    @Test
    fun `no advice discourages water`() {
        Strings.english.forEach { (key, value) ->
            assertFalse(key, value.lowercase().contains("plain water alone is not ideal"))
        }
    }
}
