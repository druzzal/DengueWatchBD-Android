package com.denguewatch.bd.model

import java.util.Calendar

/**
 * One day of the reader's measurements: the readings they took, the lab
 * reports they were given and the fluids they logged, together.
 *
 * Two separate stores, which is right — a blood pressure and a blood count are
 * taken at different moments. But a person looking back at an illness, or
 * handing the record to a doctor, thinks in days: "Tuesday my temperature was
 * 39.2 and my platelets were 96". Keeping them apart on screen makes the reader
 * do that join in their head.
 *
 * Symptom checks are deliberately not here. They are not measurements — they
 * are what the reader felt, answered against WHO criteria — and they have their
 * own place in My health, where the latest one drives the care plan. A log
 * handed to a doctor is a list of numbers. Ported from the iOS build.
 */
data class HealthLogDay(
    /** Midnight at the start of the day, in the reader's own calendar. */
    val dateMillis: Long,
    /** Newest first, matching the stores. */
    val vitals: List<VitalsEntry>,
    val labs: List<LabReport>,
    /**
     * Oldest first. Kept out of [itemsNewestFirst]: a day's fluids are read as
     * totals, not as a dozen cups interleaved with a blood count.
     */
    val fluids: List<FluidEntry> = emptyList(),
) {
    val isEmpty: Boolean get() = vitals.isEmpty() && labs.isEmpty() && fluids.isEmpty()

    /**
     * Whether the day holds any reading that can be normal or not.
     *
     * Values, not entries: a lab report holding only an NS1 result has no range
     * to sit in, and counting it painted the day green and had TalkBack call it
     * "in the usual range" — beside a positive test. As on iOS.
     */
    val hasReadings: Boolean
        get() = vitals.any { !it.isEmpty } || labs.any { it.hasAnyValue }

    /** Everything recorded that day, newest first, so a day reads as one list. */
    val itemsNewestFirst: List<Item>
        get() = (vitals.map(Item::Vitals) + labs.map(Item::Lab))
            .sortedByDescending { it.dateMillis }

    sealed interface Item {
        val id: String
        val dateMillis: Long

        data class Vitals(val entry: VitalsEntry) : Item {
            override val id get() = entry.id
            override val dateMillis get() = entry.dateMillis
        }

        data class Lab(val report: LabReport) : Item {
            override val id get() = report.id
            override val dateMillis get() = report.dateMillis
        }
    }

    /**
     * The furthest any reading strayed that day.
     *
     * What colours the row, so a bad day is visible without opening it. Taken
     * from the measurements rather than from a symptom check's verdict: this
     * marks the day a platelet count fell, not the day the reader answered a
     * questionnaire.
     */
    val worstStatus: MeasureStatus
        get() {
            var worst = MeasureStatus.NORMAL
            vitals.forEach { entry ->
                VitalKind.entries.forEach { kind ->
                    val value = entry.value(kind) ?: return@forEach
                    val status = kind.status(value)
                    if (status.risk.ordinal > worst.risk.ordinal) worst = status
                }
            }
            labs.forEach { report ->
                LabMeasure.entries.forEach { measure ->
                    val value = report.value(measure) ?: return@forEach
                    val status = measure.status(value)
                    if (status.risk.ordinal > worst.risk.ordinal) worst = status
                }
            }
            return worst
        }

    /** The day's highest temperature, in Celsius, or null if none was taken. */
    val peakTemperature: Double? get() = vitals.mapNotNull { it.temperature }.maxOrNull()

    /** The most recent complete blood pressure that day, or null. */
    val latestBloodPressure: Pair<Int, Int>?
        // firstNotNullOfOrNull rather than a predicate followed by `!!`: the
        // two readings are checked and used in the same step, so there is no
        // window in which a later edit could loosen the predicate and leave a
        // force-unwrap behind it.
        get() = vitals.firstNotNullOfOrNull { entry ->
            val systolic = entry.systolic ?: return@firstNotNullOfOrNull null
            val diastolic = entry.diastolic ?: return@firstNotNullOfOrNull null
            systolic.toInt() to diastolic.toInt()
        }

    /** The day's lowest platelet count, which is the one worth showing. */
    val lowestPlatelets: Double? get() = labs.mapNotNull { it.platelets }.minOrNull()
}

object HealthLog {

    /**
     * Groups the measurement stores by calendar day, newest day first.
     *
     * Days with nothing in them are absent rather than empty: a gap in the
     * record is a day the reader did not write anything down, and inventing a
     * row for it would suggest they recorded "nothing wrong".
     */
    fun days(
        vitals: List<VitalsEntry>,
        labs: List<LabReport> = emptyList(),
        fluids: List<FluidEntry> = emptyList(),
    ): List<HealthLogDay> {
        val vitalsByDay = vitals.groupBy { startOfDay(it.dateMillis) }
        val labsByDay = labs.groupBy { startOfDay(it.dateMillis) }
        val fluidsByDay = fluids.groupBy { startOfDay(it.dateMillis) }

        return (vitalsByDay.keys + labsByDay.keys + fluidsByDay.keys)
            .sortedDescending()
            .map { day ->
                HealthLogDay(
                    dateMillis = day,
                    vitals = vitalsByDay[day].orEmpty().sortedByDescending { it.dateMillis },
                    labs = labsByDay[day].orEmpty().sortedByDescending { it.dateMillis },
                    fluids = fluidsByDay[day].orEmpty().sortedBy { it.dateMillis },
                )
            }
    }

    /**
     * Each day's number, counted from the oldest.
     *
     * Counting from the oldest is what keeps a number attached to a day:
     * numbering the list as displayed would renumber everything each time a
     * reading arrived, so "Log 3" would mean a different day every day.
     */
    fun numbers(days: List<HealthLogDay>): Map<Long, Int> =
        days.map { it.dateMillis }.sorted()
            .withIndex()
            .associate { (index, day) -> day to index + 1 }

    /** Midnight at the start of the day containing [millis], in the local zone. */
    fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
