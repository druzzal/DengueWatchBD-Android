package com.denguewatch.bd

import androidx.compose.runtime.saveable.SaverScope
import com.denguewatch.bd.feature.triage.TriageEngine
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What survives Android stopping the app: the screen the reader was on, and
 * the answers given so far in a symptom check.
 */
class DestinationSaverTest {

    private val scope = object : SaverScope {
        override fun canBeSaved(value: Any) = true
    }

    @Test
    fun `every screen comes back as itself`() {
        listOf(
            Destination.Tabs,
            Destination.AreaDetail("DNCC"),
            Destination.SymptomCheck,
            Destination.RecordVitals,
            Destination.AddLabReport,
            Destination.AddFluid,
            Destination.CheckHistory,
            Destination.CaseLog,
            Destination.LogDay(1_760_000_000_000),
            Destination.TriageResult,
            Destination.About,
            Destination.AlertSettings,
            Destination.AreaPicker,
        ).forEach { destination ->
            val saved = with(DestinationSaver) { scope.save(destination) }!!
            assertEquals(destination, DestinationSaver.restore(saved))
        }
    }

    @Test
    fun `anything unreadable goes back to the tabs`() {
        assertEquals(Destination.Tabs, restoreDestination("somewhere"))
        assertEquals(Destination.Tabs, restoreDestination("logDay:yesterday"))
    }

    @Test
    fun `a check's answers come back as given`() {
        val context = TriageEngine.Context(isPregnant = true, hadDengueBefore = true)
        val savedContext = with(TriageContextSaver) { scope.save(context) }!!
        assertEquals(context, TriageContextSaver.restore(savedContext))

        val symptoms = setOf("fever", "abdominal")
        val savedSymptoms = with(SymptomSetSaver) { scope.save(symptoms) }!!
        assertEquals(symptoms, SymptomSetSaver.restore(savedSymptoms))
    }
}
