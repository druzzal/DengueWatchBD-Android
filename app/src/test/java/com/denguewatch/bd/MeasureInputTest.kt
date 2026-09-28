package com.denguewatch.bd

import com.denguewatch.bd.model.LabMeasure
import com.denguewatch.bd.model.MeasureInput
import com.denguewatch.bd.model.MeasureInput.Reading
import com.denguewatch.bd.model.TemperatureUnit
import com.denguewatch.bd.model.VitalKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What gets accepted into a reading field.
 *
 * The rule these tests hold down is that a field is either empty, or good, or
 * refused — never silently dropped. Dropping is the dangerous outcome: a
 * reader who types 395 for 39.5 and is not stopped saves an entry with no
 * temperature in it and every reason to believe they recorded a fever.
 */
class MeasureInputTest {

    private fun read(text: String, kind: VitalKind, unit: TemperatureUnit = TemperatureUnit.CELSIUS) =
        MeasureInput.read(text, kind, unit)

    private fun read(text: String, measure: LabMeasure) = MeasureInput.read(text, measure)

    // Nothing typed

    @Test fun `an empty field is not a problem`() {
        assertEquals(Reading.Empty, read("", VitalKind.PULSE))
        assertFalse(read("", VitalKind.PULSE).isProblem)
    }

    @Test fun `whitespace alone is empty`() {
        assertEquals(Reading.Empty, read("   ", VitalKind.PULSE))
    }

    // Not numbers

    @Test fun `letters are refused`() {
        assertEquals(Reading.NotANumber, read("ninety", VitalKind.PULSE))
        assertEquals(Reading.NotANumber, read("98.6f", VitalKind.TEMPERATURE))
    }

    @Test fun `a typo with two decimal points is refused`() {
        assertEquals(Reading.NotANumber, read("39.5.5", VitalKind.TEMPERATURE))
    }

    @Test fun `infinity and NaN are refused`() {
        // Kotlin parses both of these happily. Neither is a reading.
        assertEquals(Reading.NotANumber, read("Infinity", VitalKind.PULSE))
        assertEquals(Reading.NotANumber, read("NaN", VitalKind.PULSE))
    }

    // Numbers nobody could measure

    @Test fun `a wildly high pulse is refused`() {
        assertTrue(read("900", VitalKind.PULSE) is Reading.OutOfRange)
    }

    @Test fun `a negative reading is refused`() {
        assertTrue(read("-5", VitalKind.PULSE) is Reading.OutOfRange)
    }

    @Test fun `saturation above one hundred is refused`() {
        assertTrue(read("101", VitalKind.OXYGEN_SATURATION) is Reading.OutOfRange)
    }

    /** The classic decimal-point slip, and the reason this exists. */
    @Test fun `the missing decimal point is refused rather than dropped`() {
        assertTrue(read("395", VitalKind.TEMPERATURE) is Reading.OutOfRange)
    }

    /** The same slip on a blood count: a lab prints 96,000 for 96 thousand. */
    @Test fun `a platelet count typed in full is refused`() {
        assertTrue(read("96000", LabMeasure.PLATELETS) is Reading.OutOfRange)
    }

    // Alarming but real

    /**
     * The range is deliberately wide. A frightening reading is exactly the one
     * worth keeping, so validation must not become a second opinion.
     */
    @Test fun `alarming but possible readings are accepted`() {
        assertEquals(41.5, read("41.5", VitalKind.TEMPERATURE).storedValue)
        assertEquals(210.0, read("210", VitalKind.PULSE).storedValue)
        assertEquals(55.0, read("55", VitalKind.OXYGEN_SATURATION).storedValue)
        assertEquals(12.0, read("12", LabMeasure.PLATELETS).storedValue)
    }

    @Test fun `the edges of the range are accepted`() {
        assertEquals(25.0, read("25", VitalKind.PULSE).storedValue)
        assertEquals(220.0, read("220", VitalKind.PULSE).storedValue)
    }

    // Temperature units

    @Test fun `fahrenheit is checked in fahrenheit and stored in celsius`() {
        val celsius = read("98.6", VitalKind.TEMPERATURE, TemperatureUnit.FAHRENHEIT).storedValue
        assertEquals(37.0, celsius!!, 0.05)
    }

    /**
     * 39.5 is an ordinary fever in Celsius and far too cold to be a person in
     * Fahrenheit. Checking against the wrong unit accepts one and refuses the
     * other.
     */
    @Test fun `the range follows the unit on screen`() {
        assertEquals(39.5, read("39.5", VitalKind.TEMPERATURE, TemperatureUnit.CELSIUS).storedValue)
        assertTrue(read("39.5", VitalKind.TEMPERATURE, TemperatureUnit.FAHRENHEIT) is Reading.OutOfRange)
    }

    @Test fun `the range quoted back is in the unit typed`() {
        val reading = read("500", VitalKind.TEMPERATURE, TemperatureUnit.FAHRENHEIT)
        assertEquals(TemperatureUnit.FAHRENHEIT.plausible, (reading as Reading.OutOfRange).range)
    }

    // Bengali numerals

    /**
     * The app prints ৩৯.৫ on every tile, so a reader on a Bangla keyboard is
     * typing the app's own digits back at it.
     */
    @Test fun `bengali numerals are read`() {
        assertEquals(39.5, read("৩৯.৫", VitalKind.TEMPERATURE).storedValue)
        assertEquals(112.0, read("১১২", VitalKind.PULSE).storedValue)
        assertEquals(96.0, read("৯৬", LabMeasure.PLATELETS).storedValue)
    }

    @Test fun `bengali numerals are range checked like any other`() {
        assertTrue(read("৯০০", VitalKind.PULSE) is Reading.OutOfRange)
        assertTrue(read("৯৬০০০", LabMeasure.PLATELETS) is Reading.OutOfRange)
    }

    @Test fun `bengali and western digits mixed in one number`() {
        assertEquals(39.5, read("৩9.৫", VitalKind.TEMPERATURE).storedValue)
    }

    @Test fun `letters in bengali script are still not numbers`() {
        assertEquals(Reading.NotANumber, read("জ্বর", VitalKind.TEMPERATURE))
    }

    // Separators

    /**
     * A comma groups digits in Bangladesh; it does not separate the decimal.
     * Read as a decimal point this was a platelet count wrong by a thousand,
     * inside the range, and saved without a murmur.
     */
    @Test fun `a comma groups digits rather than separating the decimal`() {
        assertEquals(1234.0, read("1,234", LabMeasure.PLATELETS).storedValue)
        assertEquals(1234.0, read("১,২৩৪", LabMeasure.PLATELETS).storedValue)
    }

    @Test fun `a comma decimal habit is refused rather than guessed`() {
        assertTrue(read("38,5", VitalKind.TEMPERATURE) is Reading.OutOfRange)
    }

    @Test fun `surrounding space is ignored`() {
        assertEquals(72.0, read("  72  ", VitalKind.PULSE).storedValue)
    }

    // The shape the form relies on

    @Test fun `only good readings carry a value`() {
        assertNull(read("abc", VitalKind.PULSE).storedValue)
        assertNull(read("900", VitalKind.PULSE).storedValue)
        assertNull(read("", VitalKind.PULSE).storedValue)
        assertEquals(72.0, read("72", VitalKind.PULSE).storedValue)
    }

    @Test fun `every kind refuses something`() {
        for (kind in VitalKind.entries) {
            assertTrue("$kind accepted letters", read("banana", kind).isProblem)
            assertTrue("$kind accepted 99999", read("99999", kind).isProblem)
        }
        for (measure in LabMeasure.entries) {
            assertTrue("$measure accepted letters", read("banana", measure).isProblem)
            assertTrue("$measure accepted 99999", read("99999", measure).isProblem)
            assertTrue("$measure accepted a negative", read("-1", measure).isProblem)
        }
    }
}
