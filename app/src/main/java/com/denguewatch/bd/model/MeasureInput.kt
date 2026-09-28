package com.denguewatch.bd.model

/**
 * Checking what someone typed into a reading field.
 *
 * Kept out of the form so the rules can be tested directly, and because they
 * are the rules that decide whether a reading becomes part of somebody's
 * medical record.
 *
 * A field is empty, or good, or refused — never silently dropped. Dropping is
 * the dangerous outcome: a reader who types 395 for 39.5 and is not stopped
 * saves an entry with no temperature in it and every reason to believe they
 * recorded a fever. Refusing is safer than forgetting.
 */
object MeasureInput {

    /** Digits, at most one point, an optional sign. Nothing else. */
    private val NUMBER = Regex("^[+-]?(\\d+(\\.\\d*)?|\\.\\d+)$")

    sealed interface Reading {
        /** Nothing typed. Every field is optional, so this is not a problem. */
        data object Empty : Reading

        /** Good, in the unit the app stores (Celsius for temperature). */
        data class Value(val value: Double) : Reading

        /** Not a number at all: letters, or "39.5.5". */
        data object NotANumber : Reading

        /**
         * A number no thermometer or cuff would produce, with the range that
         * would be accepted — in the unit on screen, so the message matches
         * what the reader is looking at rather than what is on disk.
         */
        data class OutOfRange(val range: ClosedFloatingPointRange<Double>) : Reading

        val isProblem: Boolean
            get() = this is NotANumber || this is OutOfRange

        val storedValue: Double?
            get() = (this as? Value)?.value
    }

    /** Reads one vital-sign field. */
    fun read(text: String, kind: VitalKind, unit: TemperatureUnit): Reading =
        if (kind == VitalKind.TEMPERATURE) {
            read(text, unit.plausible) { unit.toCelsius(it) }
        } else {
            read(text, kind.enterable)
        }

    /**
     * Reads one blood-count field.
     *
     * Same rules as a vital sign, and for the same reason: a platelet count
     * silently dropped is a platelet count the reader believes they recorded.
     */
    fun read(text: String, measure: LabMeasure): Reading = read(text, measure.enterable)

    /**
     * Numerals as the reader writes them, in the digits the parser needs.
     *
     * The app prints its own numbers in Bengali — ৩৯.৫ on every tile and in
     * every chart — so a reader typing on a Bangla keyboard is writing the
     * same digits back. Refusing those as "not a number" would be the app
     * failing to read its own output.
     *
     * Done by asking each character for its numeric value rather than by
     * mapping Bengali specifically, so Arabic-Indic and Devanagari numerals
     * work too without any of them being named here.
     */
    fun westernDigits(text: String): String = buildString {
        for (character in text) {
            val value = Character.digit(character, 10)
            if (character.code > 127 && value in 0..9) append('0' + value) else append(character)
        }
    }

    /**
     * A comma groups digits; it does not separate the decimal.
     *
     * Both bn_BD and en_BD put the decimal on "." and the grouping on ",", so
     * "1,234" is one thousand two hundred and thirty-four. Read as a decimal
     * point that is a platelet count wrong by a factor of a thousand, inside
     * the range, saved without a murmur and shown as "1" — exactly the silent
     * misreading this object exists to prevent.
     *
     * Someone carrying a comma-decimal habit from another country types
     * "38,5", reads 385, and is told the range instead. Being asked again is
     * the right failure; being quietly recorded as 385 is not.
     */
    private fun read(
        text: String,
        range: ClosedFloatingPointRange<Double>,
        convert: (Double) -> Double = { it },
    ): Reading {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Reading.Empty

        val normalised = westernDigits(trimmed).replace(",", "")

        // Shape first, then parse. Java's parser is more permissive than a
        // person is: "98.6f" reads as 98.6 because f is a float suffix, and
        // "38d" would have been accepted as a perfectly good temperature.
        // Hex floats, exponents, "NaN" and "Infinity" all parse too. None of
        // them is something somebody types into a thermometer field.
        if (!NUMBER.matches(normalised)) return Reading.NotANumber

        val typed = normalised.toDoubleOrNull()
        if (typed == null || !typed.isFinite()) return Reading.NotANumber
        if (!range.contains(typed)) return Reading.OutOfRange(range)
        return Reading.Value(convert(typed))
    }
}
