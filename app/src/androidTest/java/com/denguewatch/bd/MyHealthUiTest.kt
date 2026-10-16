package com.denguewatch.bd

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.denguewatch.bd.data.CaseLogEntry
import com.denguewatch.bd.feature.triage.TriageOutcome
import com.denguewatch.bd.model.VitalsEntry
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The flows a reader taps through in My health, driven on the real app on an
 * emulator or a phone. Ported from the iOS UI tests.
 *
 * The unit tests cover what each store and rule does; these cover that the
 * buttons reach them — a screen that opens, a confirmation that deletes, a
 * total that updates. Each test starts the app empty, in English, past the
 * first-run disclaimer, and seeds only what it needs, by writing the same
 * files and preferences the app reads — so the app carries no test mode.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MyHealthUiTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun close() {
        scenario?.close()
        reset()
    }

    // MARK: - Fluids

    @Test
    fun addingADrinkUpdatesTodaysTotal() {
        launchOnHealth()
        scrollTo(hasText("Add a drink"))
        compose.onNodeWithText("Add a drink").tap()
        compose.onNodeWithText("250 ml").tap()
        compose.onNodeWithText("Save").tap()

        scrollTo(hasTestTag("fluidIntakeTile"))
        compose.onNodeWithTag("fluidIntakeTile").assertTextContains("250", substring = true)
        compose.onNodeWithTag("fluidIntakeTile").assertTextContains("Drinks: 1", substring = true)
    }

    @Test
    fun passedUrineIsOneTapAndCanBeUndone() {
        launchOnHealth()
        scrollTo(hasTestTag("fluidUrineTile"))
        compose.onNodeWithTag("fluidUrineTile").assertTextContains("0", substring = true)

        // The button, not the tile that shares its words.
        compose.onAllNodes(hasText("Passed urine") and hasClickAction()).onFirst().tap()
        compose.onNodeWithTag("fluidUrineTile").assertTextContains("1", substring = true)

        scrollTo(hasText("Today's entries", substring = true))
        compose.onNodeWithText("Today's entries", substring = true).tap()
        scrollTo(hasContentDescription("Delete"))
        compose.onNodeWithContentDescription("Delete").tap()
        scrollTo(hasTestTag("fluidUrineTile"))
        compose.onNodeWithTag("fluidUrineTile").assertTextContains("0", substring = true)
    }

    // MARK: - Symptom check

    @Test
    fun aSymptomCheckGivesItsAdviceAndIsKept() {
        launchOnHealth()
        compose.onNodeWithText("Check symptoms").tap()
        compose.onNodeWithText("Yes, I have a fever").tap()
        compose.onNodeWithText("Continue").tap()        // other symptoms: none ticked
        compose.onNodeWithText("None of these").tap()   // warning signs
        compose.onNodeWithText("Continue").tap()        // fever timeline
        compose.onNodeWithText("See what to do").tap()  // risk factors: none

        compose.waitUntilAtLeastOneExists(hasText("Get a dengue test"), 5_000)

        // Back to My health, where the kept check now drives the card.
        compose.onNodeWithContentDescription("Done").tap()
        compose.waitUntilAtLeastOneExists(hasText("Get a dengue test"), 5_000)
        compose.onNodeWithText("Check again").assertExists()
    }

    // MARK: - Deleting checks

    @Test
    fun deletingTheLatestCheckFallsBackToTheOneBefore() {
        launchOnHealth(::seedChecks)
        compose.waitUntilAtLeastOneExists(hasText("Go to a hospital now"), 5_000)

        compose.onNodeWithContentDescription("Symptom check options").tap()
        compose.onNodeWithText("Delete this check").tap()
        confirm("Delete")

        // The plain fever from two days ago is the latest check now.
        compose.waitUntilAtLeastOneExists(hasText("Get a dengue test"), 5_000)
        compose.onAllNodes(hasText("Go to a hospital now")).assertCountEquals(0)

        compose.onNodeWithContentDescription("Symptom check options").tap()
        compose.onNodeWithText("Delete this check").tap()
        confirm("Delete")

        // With no checks left, the card offers a first one.
        compose.waitUntilAtLeastOneExists(hasText("Check symptoms"), 5_000)
    }

    @Test
    fun aPastCheckIsDeletedFromTheList() {
        launchOnHealth(::seedChecks)
        compose.onNodeWithContentDescription("Symptom check options").tap()
        compose.onNodeWithText("Past checks").tap()

        compose.waitUntilAtLeastOneExists(hasTestTag("checkRow"), 5_000)
        compose.onAllNodesWithTag("checkRow").assertCountEquals(2)

        // The second row is the older check.
        compose.onAllNodesWithContentDescription("Symptom check options")[1].tap()
        compose.onNodeWithText("Delete").tap()
        confirm("Delete")

        compose.onAllNodesWithTag("checkRow").assertCountEquals(1)
        compose.onNodeWithContentDescription("Done").tap()
        // The latest check was the one kept.
        compose.waitUntilAtLeastOneExists(hasText("Go to a hospital now"), 5_000)
    }

    // MARK: - The log

    @Test
    fun exportingTheLogWritesThePdfAndHandsItOn() {
        launchOnHealth(::seedVitals)
        compose.onNodeWithContentDescription("My log").tap()
        compose.onNodeWithContentDescription("Export as PDF").tap()

        // The share sheet is the system's, outside this app's UI; what this app
        // owns is the finished PDF it hands to it.
        val folder = File(context.cacheDir, "reports")
        val deadline = System.currentTimeMillis() + 15_000
        var pdf: File? = null
        while (pdf == null && System.currentTimeMillis() < deadline) {
            pdf = folder.listFiles()?.firstOrNull {
                it.name.startsWith("DengueWatch-health-log") && it.length() > 0
            }
            if (pdf == null) Thread.sleep(200)
        }
        assertTrue("the finished PDF was written for the share sheet", pdf != null)
    }

    // MARK: - Helpers

    /** Resets, writes what the test seeds, then opens My health. */
    private fun launchOnHealth(seed: () -> Unit = {}) {
        reset()
        seed()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntilAtLeastOneExists(hasText("My health") and hasClickAction(), 10_000)
        compose.onAllNodes(hasText("My health") and hasClickAction()).onFirst().tap()
        compose.waitUntilAtLeastOneExists(hasText("Symptom check"), 5_000)
    }

    /** Empty records, English, past the first-run disclaimer. */
    private fun reset() {
        listOf("case-log.json", "vitals.json", "lab-reports.json", "fluids.json")
            .forEach { File(context.filesDir, it).delete() }
        File(context.cacheDir, "reports").deleteRecursively()
        context.getSharedPreferences("dengue_watch_prefs", Context.MODE_PRIVATE).edit()
            .clear()
            .putBoolean("hasSeenDisclaimer", true)
            .putString("appLanguage", "en")
            .commit()
    }

    /** An emergency check an hour ago over a plain fever two days ago. */
    private fun seedChecks() {
        val now = System.currentTimeMillis()
        val checks = listOf(
            CaseLogEntry(dateMillis = now - 3_600_000L, symptomIds = listOf("fever", "faint"),
                         outcomeOrdinal = TriageOutcome.EMERGENCY.ordinal, feverDaysAgo = 2),
            CaseLogEntry(dateMillis = now - 2 * 86_400_000L, symptomIds = listOf("fever"),
                         outcomeOrdinal = TriageOutcome.TEST_ADVISED.ordinal, feverDaysAgo = 0),
        )
        File(context.filesDir, "case-log.json")
            .writeText(JSONArray().apply { checks.forEach { put(it.toJson()) } }.toString())
    }

    private fun seedVitals() {
        val entry = VitalsEntry(dateMillis = System.currentTimeMillis() - 1_800_000L, temperature = 38.6,
                                pulse = 96.0, systolic = 112.0, diastolic = 74.0, oxygenSaturation = 98.0)
        File(context.filesDir, "vitals.json").writeText(JSONArray().put(entry.toJson()).toString())
    }

    /**
     * Activates the node through its click action rather than a synthesised
     * touch at its centre. Same handler, but it does not depend on the node
     * being uncovered: My health's floating tab bar sits over the bottom of the
     * list, and a touch aimed at a row half under it lands on the bar instead.
     */
    private fun SemanticsNodeInteraction.tap() {
        performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun scrollTo(matcher: SemanticsMatcher) {
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(matcher)
    }

    /** The confirming button of an open dialog: the last node with that text. */
    private fun confirm(title: String) {
        compose.waitUntilAtLeastOneExists(hasText(title) and hasClickAction(), 5_000)
        val buttons = compose.onAllNodes(hasText(title) and hasClickAction())
        val count = buttons.fetchSemanticsNodes().size
        buttons[count - 1].tap()
    }
}
