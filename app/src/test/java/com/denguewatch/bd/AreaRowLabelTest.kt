package com.denguewatch.bd

import com.denguewatch.bd.i18n.AppLanguage
import com.denguewatch.bd.i18n.PlaceNames
import com.denguewatch.bd.i18n.divisionLabel
import com.denguewatch.bd.model.Geography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The division label beside an area's name in the map list.
 *
 * It exists to disambiguate ("Khulna" the area, in "Khulna" the division), and
 * it earns its place only when it adds something. When it does not it takes the
 * width the name needed: "Dhaka (outside city)" was printed beside a second
 * "Dhaka" squeezed so thin it broke across two lines as "Dhak / a".
 *
 * Mirrors the iOS tests of the same rule.
 */
class AreaRowLabelTest {

    @Test
    fun `the label is hidden when the name already carries the division`() {
        // The case from the screenshot.
        assertNull(divisionLabel("Dhaka (outside city)", "Dhaka"))
        assertNull(divisionLabel("Dhaka North City", "Dhaka"))
        assertNull(divisionLabel("Dhaka South City", "Dhaka"))
        assertNull(divisionLabel("ঢাকা (সিটির বাইরে)", "ঢাকা"))
        assertNull(divisionLabel("ঢাকা উত্তর সিটি", "ঢাকা"))
        assertNull(divisionLabel("ঢাকা দক্ষিণ সিটি", "ঢাকা"))
    }

    @Test
    fun `the label is hidden when the names match exactly`() {
        assertNull(divisionLabel("Mymensingh", "Mymensingh"))
        assertNull(divisionLabel("ময়মনসিংহ", "ময়মনসিংহ"))
    }

    @Test
    fun `the label stays where it adds something`() {
        // Or the rule has been traded for a different bug.
        assertEquals("Chattogram", divisionLabel("Cox's Bazar", "Chattogram"))
        assertEquals("Dhaka", divisionLabel("Gazipur", "Dhaka"))
    }

    @Test
    fun `no real area prints its division twice`() {
        AppLanguage.entries.forEach { language ->
            Geography.definitions.forEach { definition ->
                val area = PlaceNames.area(definition.code, definition.name, language)
                val division = PlaceNames.division(definition.division, language)
                if (area.contains(division, ignoreCase = true)) {
                    assertNull(
                        "$area should not repeat $division",
                        divisionLabel(area, division),
                    )
                }
            }
        }
    }

    @Test
    fun `every Dhaka row drops the label in both languages`() {
        // The three areas that share a division are the ones the rule is for,
        // and the ones a regression would show up on first.
        AppLanguage.entries.forEach { language ->
            Geography.definitions
                .filter { it.code in Geography.apportionedCodes }
                .forEach { definition ->
                    val area = PlaceNames.area(definition.code, definition.name, language)
                    val division = PlaceNames.division(definition.division, language)
                    assertNull("$language: $area kept the label $division", divisionLabel(area, division))
                }
        }
    }

    @Test
    fun `an area outside its division's name keeps the label in both languages`() {
        // The other half of the rule, checked per language so a Bengali name
        // table that lost an entry cannot pass by falling back to English.
        AppLanguage.entries.forEach { language ->
            val sylhet = PlaceNames.division(
                Geography.definitions.single { it.code == "SYLHET" }.division,
                language,
            )
            assertEquals(sylhet, divisionLabel("Moulvibazar", sylhet))
        }
    }
}
