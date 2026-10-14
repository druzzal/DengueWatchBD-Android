package com.denguewatch.bd

import com.denguewatch.bd.feature.triage.CheckerStep
import com.denguewatch.bd.feature.triage.Symptom
import com.denguewatch.bd.feature.triage.TriageEngine
import com.denguewatch.bd.feature.triage.TriageOutcome
import com.denguewatch.bd.i18n.bnCheck
import com.denguewatch.bd.i18n.enCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The steps decide what is asked and in what order. They must never decide what
 * the answers mean — that stays in [TriageEngine]. Mirrors the iOS tests, so
 * the two flows cannot drift apart silently.
 */
class CheckerStepTest {

    @Test
    fun `the order puts warning signs after ordinary symptoms`() {
        // Someone answering in order should meet the routine questions first
        // and reach the ones that change the advice with attention left.
        assertEquals(
            listOf(
                CheckerStep.FEVER,
                CheckerStep.SYMPTOMS,
                CheckerStep.WARNING_SIGNS,
                CheckerStep.TIMELINE,
                CheckerStep.RISK_FACTORS,
            ),
            CheckerStep.entries,
        )
    }

    @Test
    fun `the timeline step is skipped without a fever`() {
        // Asking which day of a fever someone is on when they have said they
        // have none is nonsense, and an unanswerable step gets guessed at.
        val withoutFever = CheckerStep.sequence(hasFever = false)
        assertFalse(CheckerStep.TIMELINE in withoutFever)
        assertEquals(4, withoutFever.size)
    }

    @Test
    fun `the timeline step appears once fever is confirmed`() {
        val withFever = CheckerStep.sequence(hasFever = true)
        assertTrue(CheckerStep.TIMELINE in withFever)
        assertEquals(5, withFever.size)
    }

    @Test
    fun `skipping a step keeps the rest in order`() {
        val sequence = CheckerStep.sequence(hasFever = false)
        assertEquals("skipping must not reorder what remains", sequence.sortedBy { it.ordinal }, sequence)
    }

    @Test
    fun `every step has a title and subtitle in both languages`() {
        CheckerStep.entries.forEach { step ->
            listOf(step.titleKey, step.subtitleKey).forEach { key ->
                assertNotNull("missing EN: $key", enCheck[key])
                assertNotNull("missing BN: $key", bnCheck[key])
            }
        }
    }

    @Test
    fun `the navigation strings exist in both languages`() {
        listOf(
            "step.next", "step.back", "step.seeResult", "step.progress",
            "step.fever.yes", "step.fever.no", "step.warning.none",
            "step.timeline.unknown",
        ).forEach { key ->
            assertNotNull("missing EN: $key", enCheck[key])
            assertNotNull("missing BN: $key", bnCheck[key])
        }
    }

    @Test
    fun `the progress string takes both of its numbers in both languages`() {
        // "Step 2 of 5" — a table that dropped one slot would read "Step 2 of".
        listOf(enCheck, bnCheck).forEach { table ->
            assertEquals(2, Regex("%s").findAll(table.getValue("step.progress")).count())
        }
    }

    @Test
    fun `splitting the form did not change what the answers mean`() {
        // Same inputs, same outcome as before the flow was stepped. The steps
        // only gather; the engine still decides.
        val none = TriageEngine.Context()
        assertEquals(TriageOutcome.SELF_CARE, TriageEngine.evaluate(emptySet(), none))
        assertEquals(TriageOutcome.TEST_ADVISED, TriageEngine.evaluate(setOf("fever"), none))
        assertEquals(TriageOutcome.SEE_DOCTOR_TODAY, TriageEngine.evaluate(setOf("vomiting"), none))
        assertEquals(TriageOutcome.EMERGENCY, TriageEngine.evaluate(setOf("faint"), none))
    }

    @Test
    fun `the warning step covers every symptom that can change the outcome`() {
        // If a warning or severe sign were left off that screen, the flow could
        // never reach the outcome it triggers.
        val onWarningScreen = (
            TriageEngine.symptoms(Symptom.Group.WARNING) +
                TriageEngine.symptoms(Symptom.Group.SEVERE)
            ).map { it.id }.toSet()

        TriageEngine.symptoms.filter { it.group != Symptom.Group.CORE }.forEach {
            assertTrue("${it.id} is unreachable in the flow", it.id in onWarningScreen)
        }
    }

    @Test
    fun `every core symptom is reachable across the fever and symptom steps`() {
        // The symptom step deliberately drops "fever", which has a screen of
        // its own. Nothing else may fall through that gap.
        val onFeverScreen = setOf("fever")
        val onSymptomScreen = TriageEngine.symptoms(Symptom.Group.CORE)
            .map { it.id }
            .filterNot { it in onFeverScreen }
            .toSet()

        TriageEngine.symptoms(Symptom.Group.CORE).forEach {
            assertTrue(
                "${it.id} is unreachable in the flow",
                it.id in onFeverScreen || it.id in onSymptomScreen,
            )
        }
    }
}
