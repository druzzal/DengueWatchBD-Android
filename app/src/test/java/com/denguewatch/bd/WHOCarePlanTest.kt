package com.denguewatch.bd

import com.denguewatch.bd.feature.triage.TriageEngine
import com.denguewatch.bd.i18n.Strings
import com.denguewatch.bd.model.VitalsEntry
import com.denguewatch.bd.model.WHOCarePlan
import com.denguewatch.bd.model.WHOCarePlan.Group
import com.denguewatch.bd.model.WHOCarePlan.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The WHO management groups. These rules decide whether someone is told to stay
 * home or go to an emergency department, so the tests are about the direction
 * of every influence as much as the outcome.
 *
 * Ported from the iOS suite case for case: the two apps must not disagree about
 * which group a person is in.
 */
class WHOCarePlanTest {

    private fun vitals(
        systolic: Double? = null,
        diastolic: Double? = null,
        pulse: Double? = null,
        oxygen: Double? = null,
        temperature: Double? = null,
    ) = VitalsEntry(
        temperature = temperature,
        pulse = pulse,
        systolic = systolic,
        diastolic = diastolic,
        oxygenSaturation = oxygen,
    )

    private fun plan(
        symptoms: Set<String> = emptySet(),
        vitals: VitalsEntry? = null,
        context: TriageEngine.Context = TriageEngine.Context(),
        feverDaysAgo: Int? = null,
    ) = WHOCarePlan.evaluate(symptoms, vitals, context, feverDaysAgo)

    // MARK: - The groups

    @Test
    fun `plain fever is managed at home`() {
        assertEquals(Group.HOME, plan(setOf("fever", "headache")).group)
    }

    @Test
    fun `a warning sign means hospital assessment`() {
        assertEquals(Group.REFERRAL, plan(setOf("fever", "abdominal")).group)
    }

    @Test
    fun `a severe sign is an emergency`() {
        assertEquals(Group.EMERGENCY, plan(setOf("fever", "faint")).group)
    }

    @Test
    fun `a higher risk condition alone means assessment`() {
        val plan = plan(
            setOf("fever"),
            context = TriageEngine.Context(isPregnant = true),
        )
        assertEquals(Group.REFERRAL, plan.group)
        assertTrue(Reason.CO_MORBIDITY in plan.reasons)
    }

    // MARK: - Measured signs

    @Test
    fun `a narrow pulse pressure is treated as shock`() {
        val plan = plan(setOf("fever"), vitals(systolic = 100.0, diastolic = 85.0))
        assertEquals(Group.EMERGENCY, plan.group)
        assertTrue(Reason.NARROW_PULSE_PRESSURE in plan.reasons)
    }

    @Test
    fun `low blood pressure is an emergency`() {
        assertEquals(Group.EMERGENCY, plan(setOf("fever"), vitals(systolic = 85.0)).group)
    }

    @Test
    fun `a fast pulse or low oxygen raises to assessment`() {
        assertEquals(Group.REFERRAL, plan(setOf("fever"), vitals(pulse = 130.0)).group)
        assertEquals(Group.REFERRAL, plan(setOf("fever"), vitals(oxygen = 92.0)).group)
    }

    /** Readings only ever raise the group. */
    @Test
    fun `normal readings do not lower the group`() {
        val plan = plan(
            setOf("fever", "faint"),
            vitals(systolic = 118.0, diastolic = 76.0, pulse = 78.0, oxygen = 98.0),
        )
        assertEquals(Group.EMERGENCY, plan.group)
    }

    /**
     * A blood pressure never taken is not a normal blood pressure. A missing
     * number must never quietly move someone into a calmer group.
     */
    @Test
    fun `a missing reading is not a normal reading`() {
        assertEquals(plan(setOf("fever", "abdominal")).group,
            plan(setOf("fever", "abdominal"), vitals()).group)
    }

    @Test
    fun `half a blood pressure raises no pulse pressure concern`() {
        val plan = plan(setOf("fever"), vitals(systolic = 100.0))
        assertFalse(Reason.NARROW_PULSE_PRESSURE in plan.reasons)
    }

    // MARK: - Phase

    @Test
    fun `the critical phase is named as a reason`() {
        val plan = plan(setOf("fever"), feverDaysAgo = 4)
        assertTrue(Reason.CRITICAL_PHASE in plan.reasons)
        assertEquals(TriageEngine.FeverPhase.CRITICAL, plan.phase)
    }

    @Test
    fun `fever day counts from one not zero`() {
        assertEquals(1, plan(setOf("fever"), feverDaysAgo = 0).feverDay)
        assertEquals(5, plan(setOf("fever"), feverDaysAgo = 4).feverDay)
    }

    /** Being in the critical phase is a reason to watch, not to escalate. */
    @Test
    fun `the critical phase alone does not escalate`() {
        assertEquals(Group.HOME, plan(setOf("fever"), feverDaysAgo = 4).group)
    }

    // MARK: - Freshness

    @Test
    fun `a plan only speaks for today for a few days`() {
        assertTrue(WHOCarePlan.describesToday(0))
        assertTrue(WHOCarePlan.describesToday(WHOCarePlan.FRESH_FOR_DAYS))
        assertFalse(WHOCarePlan.describesToday(WHOCarePlan.FRESH_FOR_DAYS + 1))
    }

    @Test
    fun `a future dated check is not treated as current`() {
        assertFalse(WHOCarePlan.describesToday(-1))
    }

    @Test
    fun `the timeline stops once the illness is over`() {
        assertTrue(WHOCarePlan.showsTimeline(0))
        assertTrue(WHOCarePlan.showsTimeline(WHOCarePlan.ILLNESS_WINDOW_DAYS - 1))
        assertFalse(WHOCarePlan.showsTimeline(WHOCarePlan.ILLNESS_WINDOW_DAYS))
    }

    @Test
    fun `the timeline rejects negative days`() {
        assertFalse(WHOCarePlan.showsTimeline(-1))
    }

    // MARK: - Reasons

    /** A plan with no reason at all would read as a verdict out of nowhere. */
    @Test
    fun `the reasons are never empty`() {
        assertEquals(listOf(Reason.NONE_OF_THESE), plan(setOf("fever")).reasons)
    }

    @Test
    fun `the most serious reason comes first`() {
        val plan = plan(
            setOf("fever", "faint", "abdominal"),
            context = TriageEngine.Context(isPregnant = true),
        )
        assertEquals(Reason.SEVERE_SIGN, plan.reasons.first())
    }

    @Test
    fun `measured reasons are separable from reported ones`() {
        val plan = plan(setOf("fever", "abdominal"), vitals(pulse = 130.0))
        assertEquals(listOf(Reason.FAST_PULSE), plan.measuredReasons)
        assertTrue(Reason.WARNING_SIGN in plan.reasons)
        assertFalse(Reason.WARNING_SIGN in plan.measuredReasons)
    }

    // MARK: - Content

    @Test
    fun `every group and reason has text in both languages`() {
        for (group in Group.entries) {
            for (key in listOf(group.titleKey, group.detailKey) + group.adviceKeys) {
                assertNotNull("missing EN: $key", Strings.english[key])
                assertNotNull("missing BN: $key", Strings.bangla[key])
            }
        }
        for (reason in Reason.entries) {
            assertNotNull("missing EN: ${reason.labelKey}", Strings.english[reason.labelKey])
            assertNotNull("missing BN: ${reason.labelKey}", Strings.bangla[reason.labelKey])
        }
    }

    /**
     * The advice must not contradict the app's standing line on painkillers,
     * and must not quietly become dosing instructions.
     */
    @Test
    fun `the advice names the drugs to avoid and gives no doses`() {
        val joined = (1..4)
            .mapNotNull { Strings.english["who.group.home.advice$it"] }
            .joinToString(" ")
            .lowercase()
        assertTrue(joined, joined.contains("aspirin"))
        assertTrue(joined, joined.contains("ibuprofen"))
        for (dose in listOf("mg", "500", "tablet", "twice a day")) {
            assertFalse("no dosing advice: $joined", joined.contains(dose))
        }
    }

    /** The card must keep saying what it is not. */
    @Test
    fun `the source line disclaims diagnosis`() {
        val source = (Strings.english["who.plan.source"] ?: "").lowercase()
        assertTrue(source, source.contains("who"))
        assertTrue(source, source.contains("does not decide whether you have dengue"))
    }
}
