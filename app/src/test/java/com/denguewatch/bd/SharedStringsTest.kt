package com.denguewatch.bd

import com.denguewatch.bd.i18n.Strings
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Checks this app's string tables against `shared-strings.json`.
 *
 * The iOS app runs the same file. A key both apps carry must have the same
 * English and Bangla wording in both; a key only one carries must be written
 * down as such. So a feature added on one platform only — the way this app
 * once lacked "where you are now" on the home card — or a phrase reworded on
 * one side, fails here until the other catches up or the difference is
 * recorded on purpose.
 */
class SharedStringsTest {

    private val text: String =
        requireNotNull(javaClass.classLoader?.getResource("shared-strings.json")) {
            "shared-strings.json is missing from the test resources"
        }.readText()
    private val shared = JSONObject(text)

    private fun list(key: String): Set<String> =
        shared.getJSONArray(key).let { array -> (0 until array.length()).map { array.getString(it) }.toSet() }

    private fun wording(language: String): Map<String, String> =
        shared.getJSONObject(language).let { json -> json.keys().asSequence().associateWith { json.getString(it) } }

    /** `%s` and `%1$s` here are `%@` and `%1$@` in the shared file, which uses iOS's spelling. */
    private fun canonical(value: String) =
        value.replace(Regex("%(\\d+\\$)?s")) { "%" + it.groupValues[1] + "@" }

    private val ours: Set<String> get() = Strings.english.keys + Strings.bangla.keys

    @Test
    fun `the Android copy matches the iOS copy`() {
        val ios = File("../../DengueWatchBD/DengueWatchBDTests/shared-strings.json")
        assumeTrue(ios.exists())
        assertEquals(
            "shared-strings.json differs between the iOS and Android repositories; copy one over the other",
            ios.readText(),
            text,
        )
    }

    @Test
    fun `shared wording matches`() {
        listOf("en" to Strings.english, "bn" to Strings.bangla).forEach { (language, table) ->
            val expected = wording(language)
            assertTrue(expected.isNotEmpty())
            expected.forEach { (key, value) ->
                assertEquals("$language $key", value, table[key]?.let(::canonical))
            }
        }
    }

    @Test
    fun `every key is shared or recorded as Android only`() {
        val known = wording("en").keys + list("platformWording") + list("androidOnly")
        assertEquals(
            "keys only Android has; add them to iOS, or to androidOnly in shared-strings.json",
            emptyList<String>(),
            (ours - known).sorted(),
        )
        assertEquals("keys the shared file expects that Android lacks", emptyList<String>(), (known - ours).sorted())
    }

    @Test
    fun `iOS only keys are not here`() {
        assertEquals(
            "now on both platforms; move them into the shared wording",
            emptyList<String>(),
            (list("iosOnly") intersect ours).sorted(),
        )
    }

    @Test
    fun `platform wording is in both languages`() {
        list("platformWording").forEach { key ->
            assertNotNull("en $key", Strings.english[key])
            assertNotNull("bn $key", Strings.bangla[key])
        }
    }
}
