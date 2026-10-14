package com.denguewatch.bd

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Nothing the app shows may be cut off.
 *
 * Two different mechanisms can do it, and only one of them is visible in a
 * screenshot. `TextOverflow.Ellipsis` at least marks the cut with a "…";
 * a bare `maxLines` uses Compose's default `TextOverflow.Clip`, which ends the
 * text mid-word with no mark at all. That is how "Chattogram" became
 * "Chattogra" and "মাঝারি" became "মাঝা" — both read as words rather than as
 * truncations, so they survived several passes of looking straight at them.
 *
 * The fix at each site is to wrap, or to shrink via [feature.shared.ShrinkToFitText]
 * where one line is structurally required. This test walks the source so a
 * future edit cannot quietly reintroduce either mechanism.
 */
class NoTruncationTest {

    private val sources: Sequence<File>
        get() = File("src/main/java/com/denguewatch/bd")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }

    @Test
    fun `no text is ellipsized`() {
        val offenders = sources
            .filter { it.readText().contains("TextOverflow.Ellipsis") }
            .map { it.name }
            .toList()

        assertTrue(
            "these draw an ellipsis instead of showing the text in full: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `no display text is capped to a line count`() {
        // Two exemptions, both of which cap something other than displayed text:
        //
        //   * ShrinkToFitText is the sanctioned fix — it pins maxLines to 1 so
        //     Compose's auto-sizing has a single line to size against, and the
        //     text shrinks rather than losing a character.
        //   * A text field's maxLines bounds how far the *input box* grows;
        //     what the user typed scrolls and is never discarded. True of
        //     BasicTextField as much as OutlinedTextField — the note fields
        //     moved from one to the other to match the iOS form, and naming
        //     only the latter here turned that into a spurious failure.
        val exempt = Regex("""maxLines\s*=\s*\d+""")
        val offenders = mutableListOf<String>()

        sources.forEach { file ->
            val text = file.readText()
            text.lineSequence().forEachIndexed { index, line ->
                if (!exempt.containsMatchIn(line)) return@forEachIndexed
                val isAutoSize = file.name == "Components.kt" &&
                    text.contains("fun ShrinkToFitText")
                val isTextFieldGrowth = text.contains("OutlinedTextField") ||
                    text.contains("BasicTextField")
                if (!isAutoSize && !isTextFieldGrowth) {
                    offenders += "${file.name}:${index + 1}: ${line.trim()}"
                }
            }
        }

        assertTrue(
            "these cap displayed text to a line count, which clips it:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}
