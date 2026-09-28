package com.denguewatch.bd

import com.denguewatch.bd.feature.prevent.PreventionContent
import com.denguewatch.bd.feature.triage.Symptom
import com.denguewatch.bd.feature.triage.TriageEngine
import com.denguewatch.bd.feature.triage.TriageOutcome
import com.denguewatch.bd.i18n.AppLanguage
import com.denguewatch.bd.i18n.NumberStyle
import com.denguewatch.bd.i18n.Strings
import com.denguewatch.bd.i18n.guidanceKey
import com.denguewatch.bd.i18n.labelKey
import com.denguewatch.bd.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The string tables.
 *
 * A missing Bengali key degrades to English at runtime rather than showing a
 * blank, which is the right failure — but it is still a failure, and half an
 * English sentence inside an otherwise Bengali screen is exactly the kind of
 * thing that goes unnoticed for a release. These tests fail instead.
 */
class StringsTest {

    @Test
    fun `every English key has a Bengali counterpart`() {
        assertEquals(
            "keys missing from the Bengali table",
            emptyList<String>(),
            Strings.missingBanglaKeys,
        )
    }

    @Test
    fun `every Bengali key has an English counterpart`() {
        val orphans = Strings.bangla.keys.filter { it !in Strings.english }.sorted()
        assertEquals("keys the English table has lost", emptyList<String>(), orphans)
    }

    @Test
    fun `both tables agree on how many arguments each string takes`() {
        // A Bengali string with a different number of %s slots than its English
        // twin throws at format time, in whichever screen happens to use it.
        val slot = Regex("%[sd]")
        Strings.english.forEach { (key, english) ->
            val bangla = Strings.bangla[key] ?: return@forEach
            assertEquals(
                "argument count differs for '$key'",
                slot.findAll(english).count(),
                slot.findAll(bangla).count(),
            )
        }
    }

    @Test
    fun `no string is left empty`() {
        listOf(Strings.english, Strings.bangla).forEach { table ->
            table.forEach { (key, value) ->
                assertTrue("'$key' is empty", value.isNotBlank())
            }
        }
    }

    // MARK: - Keys the code asks for

    @Test
    fun `every key the risk bands name exists in both tables`() {
        RiskLevel.entries.forEach { level ->
            assertResolves(level.labelKey)
            assertResolves(level.guidanceKey)
            assertResolves("risk.detail.band.${level.ordinal}")
        }
    }

    @Test
    fun `every key the symptom check names exists in both tables`() {
        Symptom.Group.entries.forEach { group ->
            assertResolves(group.titleKey)
            assertResolves(group.footnoteKey)
        }
        TriageEngine.symptoms.forEach { symptom ->
            assertResolves(symptom.titleKey)
            assertResolves(symptom.detailKey)
        }
        TriageOutcome.entries.forEach { outcome ->
            assertResolves(outcome.headlineKey)
            assertResolves(outcome.summaryKey)
            outcome.actionKeys.forEach { assertResolves(it) }
        }
    }

    @Test
    fun `every key the prevention content names exists in both tables`() {
        PreventionContent.topics.forEach { topic ->
            assertResolves(topic.titleKey)
            assertResolves(topic.summaryKey)
            topic.stepKeys.forEach { assertResolves(it) }
        }
        PreventionContent.seasonalPattern.forEach { month ->
            assertResolves(month.labelKey)
            assertResolves(month.noteKey)
        }
    }

    @Test
    fun `every key the tabs name exists in both tables`() {
        Tab.entries.forEach { assertResolves(it.titleKey) }
    }

    private fun assertResolves(key: String) {
        assertTrue("English table is missing '$key'", Strings.english.containsKey(key))
        assertTrue("Bengali table is missing '$key'", Strings.bangla.containsKey(key))
    }

    // MARK: - Formatting

    /**
     * Four keys are built by interpolation at runtime rather than written out,
     * so nothing else in this suite can see them: the About bullets, the
     * first-run intro paragraphs, and the risk band explanations. A loop widened
     * without a matching string would show a reader the raw key — `about.bullet6`
     * — instead of a sentence, and only on the screen nobody opens in testing.
     */
    @Test
    fun `every interpolated key the screens build exists in both tables`() {
        val built = buildList {
            // AboutScreen: (1..5).forEach { "about.bullet$it" }
            (1..5).forEach { add("about.bullet$it") }
            // AboutScreen first-run intro: (1..4).forEach { "intro.p$it.title" }
            (1..4).forEach {
                add("intro.p$it.title")
                add("intro.p$it.detail")
            }
            // AreaSheets: "risk.detail.band.${level.ordinal}" for every band.
            RiskLevel.entries.forEach { add("risk.detail.band.${it.ordinal}") }
        }

        built.forEach { key ->
            assertNotNull("English is missing interpolated key '$key'", Strings.english[key])
            assertNotNull("Bengali is missing interpolated key '$key'", Strings.bangla[key])
        }
    }

    /**
     * A string that takes arguments must never be looked up without them.
     *
     * `t(key)` and `t(key, args)` are different overloads, and the single-argument
     * one does no substitution — so calling it on "About %s residents" renders the
     * placeholder to the reader verbatim. That is exactly what the area sheet did:
     * it used the sentence form as the label half of a label/value row, and the
     * map showed "About %s residents" next to the figure.
     *
     * Nothing else here could see it. The parity tests check that a key exists and
     * that both languages agree on how many arguments it takes; neither looks at
     * how a call site invokes it. This walks the source instead.
     */
    @Test
    fun `no call site looks up a formatted string without arguments`() {
        val takesArguments = Strings.english.filterValues { it.contains("%s") }.keys

        // `.t("key")` with nothing after the closing quote but the paren.
        val bareLookup = Regex("""\.t\(\s*"([^"]+)"\s*\)""")
        val offenders = mutableListOf<String>()

        val sourceRoot = File("src/main/java/com/denguewatch/bd")
        sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            // The tables themselves contain the literals, not lookups of them.
            .filterNot { it.path.contains("/i18n/Strings") }
            .forEach { file ->
                bareLookup.findAll(file.readText()).forEach { match ->
                    val key = match.groupValues[1]
                    if (key in takesArguments) {
                        offenders += "${'$'}{file.name}: t(\"${'$'}key\") — \"${'$'}{Strings.english[key]}\""
                    }
                }
            }

        assertTrue(
            "formatted strings looked up without arguments:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test
    fun `Bengali numbers use Bengali digits`() {
        val bn = NumberStyle(AppLanguage.BANGLA)
        assertEquals("১,২৩৪", bn.num(1234))
        assertEquals("৫.৭", bn.decimal(5.67))
        assertEquals("+১৮%", bn.percentChange(0.18))
        assertEquals("−১৮%", bn.percentChange(-0.18))
    }

    @Test
    fun `English numbers use Latin digits`() {
        val en = NumberStyle(AppLanguage.ENGLISH)
        assertEquals("1,234", en.num(1234))
        assertEquals("5.7", en.decimal(5.67))
        assertEquals("+18%", en.percentChange(0.18))
    }

    @Test
    fun `compact numbers read the way each language reads them`() {
        val en = NumberStyle(AppLanguage.ENGLISH)
        val bn = NumberStyle(AppLanguage.BANGLA)

        // "101k" is fine in English and is not how anyone reads a number in Bengali.
        assertEquals("101k", en.compact(101_000))
        assertEquals("১.০ লাখ", bn.compact(101_000))
        assertEquals("৪১ হাজার", bn.compact(41_032))
        assertEquals("১.২ কোটি", bn.compact(12_000_000))
    }

    @Test
    fun `an absent change reads as a dash rather than as zero`() {
        assertEquals("—", NumberStyle(AppLanguage.ENGLISH).percentChange(null))
        assertEquals("—", NumberStyle(AppLanguage.BANGLA).percentChange(null))
    }
}
