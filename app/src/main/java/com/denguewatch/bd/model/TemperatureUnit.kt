package com.denguewatch.bd.model

/**
 * Celsius or Fahrenheit, as the reader prefers.
 *
 * Home thermometers in Bangladesh are commonly Fahrenheit while clinical notes
 * are Celsius, so neither is a safe silent default — the reader is asked, and
 * everything is stored in Celsius whichever they pick.
 */
enum class TemperatureUnit {
    CELSIUS, FAHRENHEIT;

    val slug: String get() = if (this == CELSIUS) "celsius" else "fahrenheit"
    val labelKey: String get() = "temp.unit.$slug"
    val symbol: String get() = if (this == CELSIUS) "°C" else "°F"

    /**
     * What a reading could plausibly be, in this unit.
     *
     * Checked in the unit on screen rather than after conversion: 39.5 is an
     * ordinary fever in Celsius and far too cold to be a person in Fahrenheit,
     * so checking against the wrong one accepts a reading it should refuse.
     */
    val plausible: ClosedFloatingPointRange<Double>
        get() = if (this == CELSIUS) 30.0..45.0 else 86.0..113.0

    fun toCelsius(value: Double): Double =
        if (this == CELSIUS) value else (value - 32.0) * 5.0 / 9.0

    fun fromCelsius(celsius: Double): Double =
        if (this == CELSIUS) celsius else celsius * 9.0 / 5.0 + 32.0
}
