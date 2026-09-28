package com.denguewatch.bd.model

import org.json.JSONObject
import java.util.UUID

/**
 * The readings a person can take at home.
 *
 * Ranges are deliberately wide. `enterable` is what a thermometer or a cuff
 * could plausibly produce, not what is healthy — a frightening reading is
 * exactly the one worth keeping, and validation that argues with an alarming
 * number is validation that loses data when it matters most. `usual` is only
 * for marking a reading as worth asking about.
 */
enum class VitalKind {
    TEMPERATURE, PULSE, SYSTOLIC, DIASTOLIC, OXYGEN_SATURATION;

    val slug: String
        get() = when (this) {
            TEMPERATURE -> "temperature"
            PULSE -> "pulse"
            SYSTOLIC -> "systolic"
            DIASTOLIC -> "diastolic"
            OXYGEN_SATURATION -> "oxygenSaturation"
        }

    val labelKey: String get() = "vital.$slug"
    val unitKey: String get() = "vital.unit.$slug"

    /** Celsius for temperature; conversion happens as the reading is typed. */
    val enterable: ClosedFloatingPointRange<Double>
        get() = when (this) {
            TEMPERATURE -> 30.0..45.0
            PULSE -> 25.0..220.0
            SYSTOLIC -> 50.0..260.0
            DIASTOLIC -> 30.0..160.0
            OXYGEN_SATURATION -> 50.0..100.0
        }

    /** Where most healthy adults sit at rest. Marks a reading, concludes nothing. */
    val usual: ClosedFloatingPointRange<Double>
        get() = when (this) {
            TEMPERATURE -> 36.1..37.8
            PULSE -> 60.0..100.0
            SYSTOLIC -> 90.0..130.0
            DIASTOLIC -> 60.0..85.0
            OXYGEN_SATURATION -> 95.0..100.0
        }

    /**
     * Beyond this a reading has stopped being "a bit off".
     *
     * Set per measure rather than as a percentage of the usual range: a
     * proportional rule works for a pulse and is meaningless for a Celsius
     * temperature, whose zero is arbitrary.
     */
    val markedlyAbnormal: ClosedFloatingPointRange<Double>
        get() = when (this) {
            TEMPERATURE -> 35.5..39.0
            PULSE -> 50.0..120.0
            SYSTOLIC -> 85.0..160.0
            DIASTOLIC -> 55.0..100.0
            OXYGEN_SATURATION -> 92.0..100.0
        }

    val decimals: Int get() = if (this == TEMPERATURE) 1 else 0

    fun status(value: Double): MeasureStatus =
        MeasureStatus.of(value, usual = usual, far = markedlyAbnormal)
}

/**
 * One set of readings, taken at a moment the reader chooses.
 *
 * Every field is optional: someone with a thermometer and no cuff should be
 * able to keep a useful record, and demanding a full set means they record
 * nothing at all.
 */
data class VitalsEntry(
    val id: String = UUID.randomUUID().toString(),
    val dateMillis: Long = System.currentTimeMillis(),
    /** Always Celsius on disk, whatever unit it was typed in. */
    val temperature: Double? = null,
    val pulse: Double? = null,
    val systolic: Double? = null,
    val diastolic: Double? = null,
    val oxygenSaturation: Double? = null,
    val note: String = "",
) {
    fun value(kind: VitalKind): Double? = when (kind) {
        VitalKind.TEMPERATURE -> temperature
        VitalKind.PULSE -> pulse
        VitalKind.SYSTOLIC -> systolic
        VitalKind.DIASTOLIC -> diastolic
        VitalKind.OXYGEN_SATURATION -> oxygenSaturation
    }

    val isEmpty: Boolean get() = VitalKind.entries.all { value(it) == null }

    /** Readings outside their usual range, in a stable order for the UI. */
    val outsideUsual: List<VitalKind>
        get() = VitalKind.entries.filter { kind ->
            value(kind)?.let { !kind.usual.contains(it) } ?: false
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("date", dateMillis)
        temperature?.let { put("temperature", it) }
        pulse?.let { put("pulse", it) }
        systolic?.let { put("systolic", it) }
        diastolic?.let { put("diastolic", it) }
        oxygenSaturation?.let { put("oxygenSaturation", it) }
        put("note", note)
    }

    companion object {
        private fun JSONObject.doubleOrNull(key: String): Double? =
            if (has(key) && !isNull(key)) optDouble(key) else null

        fun from(json: JSONObject) = VitalsEntry(
            id = json.optStringOrNull("id") ?: UUID.randomUUID().toString(),
            dateMillis = json.optLong("date", System.currentTimeMillis()),
            temperature = json.doubleOrNull("temperature"),
            pulse = json.doubleOrNull("pulse"),
            systolic = json.doubleOrNull("systolic"),
            diastolic = json.doubleOrNull("diastolic"),
            oxygenSaturation = json.doubleOrNull("oxygenSaturation"),
            note = json.optString("note", ""),
        )
    }
}
