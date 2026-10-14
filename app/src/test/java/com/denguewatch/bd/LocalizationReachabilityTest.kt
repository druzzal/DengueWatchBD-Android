package com.denguewatch.bd

import com.denguewatch.bd.Tab
import com.denguewatch.bd.feature.care.EmergencyNumber
import com.denguewatch.bd.feature.care.Hospital
import com.denguewatch.bd.feature.map.MapMetric
import com.denguewatch.bd.feature.prevent.MythFact
import com.denguewatch.bd.feature.prevent.PreventionContent
import com.denguewatch.bd.feature.dashboard.TrendRange
import com.denguewatch.bd.feature.triage.CheckerStep
import com.denguewatch.bd.feature.triage.Symptom
import com.denguewatch.bd.feature.triage.TriageEngine
import com.denguewatch.bd.feature.triage.TriageOutcome
import com.denguewatch.bd.i18n.Strings
import com.denguewatch.bd.i18n.guidanceKey
import com.denguewatch.bd.i18n.headlineKey
import com.denguewatch.bd.i18n.labelKey
import com.denguewatch.bd.model.HealthTrend
import com.denguewatch.bd.model.LabMeasure
import com.denguewatch.bd.model.DengueTest
import com.denguewatch.bd.model.MeasureStatus
import com.denguewatch.bd.model.PreventionTask
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.model.TemperatureUnit
import com.denguewatch.bd.model.TestResult
import com.denguewatch.bd.model.VitalKind
import com.denguewatch.bd.model.WHOCarePlan
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keys the code builds at runtime, and keys nothing can reach.
 *
 * Most of this app does not ask for a string by literal: a symptom's label is
 * `"symptom.$id.title"`, a lab measure's unit is `"lab.unit.$slug"`. A scanner
 * sees the template, not the keys it produces, so renaming a case — or an entry
 * in the table — ships a row reading `symptom.rash.title` with every test green.
 *
 * This walks the real values instead and asks the real table. Ported from the
 * iOS suite, which caught 35 dead keys the first time it ran.
 */
class LocalizationReachabilityTest {

    /** Every key any of the enumerable content types can produce. */
    private fun generatedKeys(): Set<String> {
        val keys = mutableSetOf<String>()

        Tab.entries.forEach { keys += it.titleKey }
        MythFact.all.forEach { keys += it.mythKey; keys += it.factKey }
        PreventionContent.topics.forEach {
            keys += it.titleKey; keys += it.summaryKey; keys += it.stepKeys
        }
        PreventionContent.seasonalPattern.forEach { keys += it.labelKey; keys += it.noteKey }
        PreventionTask.entries.forEach { keys += it.titleKey }
        EmergencyNumber.all.forEach { keys += it.nameKey; keys += it.detailKey }
        CheckerStep.entries.forEach { keys += it.titleKey; keys += it.subtitleKey }
        Symptom.Group.entries.forEach { keys += it.titleKey; keys += it.footnoteKey }
        TriageEngine.symptoms.forEach { keys += it.titleKey; keys += it.detailKey }
        TriageOutcome.entries.forEach {
            keys += it.headlineKey; keys += it.summaryKey; keys += it.actionKeys
        }
        TriageEngine.FeverPhase.entries.forEach { keys += it.nameKey; keys += it.key }
        TrendRange.entries.forEach { keys += it.labelKey }
        MapMetric.entries.forEach { keys += it.labelKey; keys += it.explanationKey }
        WHOCarePlan.Group.entries.forEach {
            keys += it.titleKey; keys += it.detailKey; keys += it.adviceKeys
        }
        WHOCarePlan.Reason.entries.forEach { keys += it.labelKey }
        HealthTrend.entries.forEach { keys += it.labelKey }
        MeasureStatus.entries.forEach { keys += it.labelKey }
        VitalKind.entries.forEach { keys += it.labelKey; keys += it.unitKey }
        TemperatureUnit.entries.forEach { keys += it.labelKey }
        LabMeasure.entries.forEach { keys += it.labelKey; keys += it.unitKey }
        DengueTest.entries.forEach { keys += it.labelKey }
        TestResult.entries.forEach { keys += it.labelKey }
        RiskLevel.entries.forEach {
            keys += it.labelKey; keys += it.guidanceKey; keys += it.headlineKey
            keys += "risk.detail.band.${it.ordinal}"
        }
        // Built from a loop index and from a prefix passed at the call site:
        // neither the template nor the values appear as a key in the source.
        (1..5).forEach { keys += "about.bullet$it" }
        (1..4).forEach { keys += "intro.p$it.title"; keys += "intro.p$it.detail" }
        return keys
    }

    /** Every key the content types build must be in both tables. */
    @Test
    fun `every generated key is translated`() {
        val missingEnglish = generatedKeys().filter { Strings.english[it] == null }.sorted()
        val missingBangla = generatedKeys().filter { Strings.bangla[it] == null }.sorted()
        assertTrue("built at runtime, missing from English: $missingEnglish", missingEnglish.isEmpty())
        assertTrue("built at runtime, missing from Bangla: $missingBangla", missingBangla.isEmpty())
    }

    /**
     * No key is translated twice over into nothing.
     *
     * An orphan is a string somebody wrote, a translator read, and no reader
     * will ever see. It is not a crash, but it is work spent and a trap: the
     * next person to touch the feature edits the dead copy.
     */
    @Test
    fun `no key is unreachable`() {
        val reachable = literalKeysInSource() + generatedKeys()
        val orphans = Strings.english.keys.filter { it !in reachable }.sorted()
        val unexpected = orphans.filterNot { it in KNOWN_ORPHANS }
        assertTrue(
            "defined but nothing asks for them, and not on the known list: $unexpected",
            unexpected.isEmpty(),
        )
    }

    /**
     * The known list must not rot: a key that has since been wired up should
     * come off it, or the list stops meaning anything.
     */
    @Test
    fun `the known orphan list has no stale entries`() {
        val reachable = literalKeysInSource() + generatedKeys()
        val nowUsed = KNOWN_ORPHANS.filter { it in reachable }.sorted()
        assertTrue("these are reachable now — take them off the list: $nowUsed", nowUsed.isEmpty())
        val gone = KNOWN_ORPHANS.filter { Strings.english[it] == null }.sorted()
        assertTrue("these no longer exist — take them off the list: $gone", gone.isEmpty())
    }

    /**
     * Every string literal in the app that looks like a key, outside the tables
     * themselves. Deliberately generous: a false "reachable" hides an orphan,
     * which is cheaper than a false failure on an icon name.
     */
    private fun literalKeysInSource(): Set<String> {
        val pattern = Regex("\"([a-z][A-Za-z0-9]*(?:\\.[A-Za-z0-9]+)+)\"")
        val root = File("src/main/java/com/denguewatch/bd")
        val found = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.parentFile.name == "i18n" && it.name.startsWith("Strings") }
            .flatMap { file -> pattern.findAll(file.readText()).map { it.groupValues[1] } }
            .toSet()
        assertFalse("the source tree was not found at ${root.absolutePath}", found.isEmpty())
        return found
    }

    /** The two tables stay in step. */
    @Test
    fun `every english key has a bangla translation and the reverse`() {
        assertNotNull(Strings.english)
        assertTrue(
            "missing from Bangla: ${Strings.missingBanglaKeys}",
            Strings.missingBanglaKeys.isEmpty(),
        )
        val extra = Strings.bangla.keys.filter { Strings.english[it] == null }.sorted()
        assertTrue("in Bangla with no English original: $extra", extra.isEmpty())
    }
}

private val KNOWN_ORPHANS = setOf(
    // ---------------------------------------------------------------------
    // Copy waiting on a feature iOS has and this app does not yet.
    //
    // These are translated and correct; deleting them would throw away work
    // that the port still needs. They are listed so a *new* orphan fails the
    // build rather than joining them unnoticed.
    // ---------------------------------------------------------------------
    // When each serology test is actually informative.
    "lab.timing.igg", "lab.timing.igm", "lab.timing.ns1",
    // The location permission explainer sheet.
    "locperm.allow", "locperm.body", "locperm.notNow", "locperm.reason1",
    "locperm.reason2", "locperm.reason3", "locperm.title",
    // States the nearby-hospital card does not have yet; it ships the title,
    // subtitle and search action only.
    "care.nearby.distance", "care.nearby.empty", "care.nearby.enable",
    "care.nearby.permission", "care.nearby.searching",
    // The fever timeline's spoken description and its critical-phase note.
    "phase.a11y.day", "phase.a11y.unknown", "phase.critical.note",
    // Map states and geofence status lines.
    "geo.status.foreground", "geo.status.off", "map.legend.risk",
    "map.you.outside", "map.you.wholeCountry",
    // The unit picker's own label.
    "temp.unit.label",
    // Odds and ends with no home yet.
    "common.ok", "common.saved", "common.today", "common.yesterday",
    "health.title", "intro.language",

    // ---------------------------------------------------------------------
    // Dead copy: features that were removed here and on iOS, where the iOS
    // reachability test deleted the matching keys. Kept on this list rather
    // than deleted so the two tables stay comparable while the port finishes;
    // they can go once the parity diff is closed.
    // ---------------------------------------------------------------------
    "health.latest.title", "health.today.done", "health.today.subtitle",
    "health.today.symptoms", "health.today.title", "health.today.todo",
    "health.today.vitals",
    "check.fever.section", "check.intro", "check.risk.section", "check.seeResult",
    "guidance.care", "guidance.check", "guidance.prevent", "guidance.title",
    "result.addToLog", "result.keptOnPhone", "result.saveEntry", "result.temperature",
    "log.pdf.noSymptoms", "log.pdf.symptoms",
    "tab.check", "tab.overview", "step.skip", "geo.prompt.enabled",
    "temp.outOfRange", "vital.outsideUsual", "vital.usualRange",
    "lab.outsideTypical", "lab.typicalRange", "who.plan.subtitle",
)
