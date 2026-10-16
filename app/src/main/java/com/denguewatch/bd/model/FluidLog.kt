package com.denguewatch.bd.model

import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

/**
 * One thing written down about fluids: a drink and how much of it, or a time
 * the reader passed urine.
 *
 * WHO's first recommendation for non-severe dengue is protocolized oral fluids:
 * measure each drink, write it down, total it at the end of the day, and aim to
 * pass urine at least four to six times a day. Source: WHO, *Guidelines for
 * clinical management of arboviral diseases* (2025), section 4.1. This is that
 * record and only that record.
 *
 * There is deliberately no target volume. WHO sets the volume by age and by
 * conditions such as heart or kidney disease, which is a clinician's
 * calculation; a single number on this screen would be the app prescribing.
 * The urine count is the target WHO gives patients themselves, so that one is
 * shown. Ported from the iOS build.
 */
data class FluidEntry(
    val id: String = UUID.randomUUID().toString(),
    val dateMillis: Long = System.currentTimeMillis(),
    val kind: Kind,
    /** For drinks. Null for urine: WHO asks for the count, and nobody measures it at home. */
    val millilitres: Int? = null,
) {
    enum class Kind { DRINK, URINE }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("date", dateMillis)
        put("kind", kind.name.lowercase())
        millilitres?.let { put("millilitres", it) }
    }

    companion object {
        fun from(json: JSONObject): FluidEntry? {
            val kind = when (json.optString("kind")) {
                "drink" -> Kind.DRINK
                "urine" -> Kind.URINE
                else -> return null
            }
            return FluidEntry(
                id = json.optStringOrNull("id") ?: UUID.randomUUID().toString(),
                dateMillis = json.optLong("date", System.currentTimeMillis()),
                kind = kind,
                millilitres = if (json.has("millilitres") && !json.isNull("millilitres")) {
                    json.optInt("millilitres")
                } else null,
            )
        }
    }
}

/** One day's fluid record, totalled the way WHO asks for it. */
data class FluidDay(
    val drunkMillilitres: Int,
    val drinks: Int,
    val urinations: Int,
) {
    val isEmpty: Boolean get() = drinks == 0 && urinations == 0
}

/** A whole fluid log, summed for the front of the report. */
data class FluidSummary(
    /** Every day with anything logged, today included. */
    val daysLogged: Int,
    /** Mean intake over whole days with at least one drink logged. Null with no whole day yet. */
    val averageMillilitres: Int?,
    /** Whole days with at least one urination logged. */
    val daysWithUrineLogged: Int,
    /** Of those, the days that reached the bottom of WHO's aim. */
    val daysUrineAimMet: Int,
    /** Whether today is in the log, and so left out of the figures above. */
    val includesToday: Boolean,
)

object FluidLog {

    /** "Adequate oral fluid intake should result in urination at least four to six times per day." */
    val URINATION_AIM = 4..6

    /**
     * The app's own warning sign: little or no urine for six hours or more.
     * The same number as the symptom check, so the two never disagree.
     */
    const val URINE_GAP_HOURS = 6.0

    /**
     * The night the urine gap does not count, as hours of the day: from 11 pm
     * to 7 am. Someone who passed urine at ten and slept until seven has not
     * gone nine hours without, in the sense the warning sign means, and telling
     * them so first thing every morning teaches them to ignore it.
     */
    const val NIGHT_START_HOUR = 23
    const val NIGHT_END_HOUR = 7

    /** How long a silent log still counts as being kept. */
    const val ACTIVE_LOG_HOURS = 24.0

    /** Choices offered when adding a drink, in millilitres. */
    val PRESET_MILLILITRES = listOf(100, 150, 200, 250, 300, 500)

    /** What a single drink could plausibly be. Wide: this only catches typing slips. */
    val PLAUSIBLE_MILLILITRES = 50..2000

    private const val HOUR = 3_600_000L

    /** The day's totals. Entries on other days are ignored. */
    fun day(dateMillis: Long, entries: List<FluidEntry>): FluidDay {
        val start = HealthLog.startOfDay(dateMillis)
        val todays = entries.filter { HealthLog.startOfDay(it.dateMillis) == start }
        val drinks = todays.filter { it.kind == FluidEntry.Kind.DRINK }
        return FluidDay(
            drunkMillilitres = drinks.sumOf { it.millilitres ?: 0 },
            drinks = drinks.size,
            urinations = todays.count { it.kind == FluidEntry.Kind.URINE },
        )
    }

    /**
     * Each logged day's totals, oldest first. Days with nothing logged are
     * absent rather than zero: an empty day is a day nobody wrote anything
     * down, not a day nobody drank.
     */
    fun daily(entries: List<FluidEntry>): List<Pair<Long, FluidDay>> =
        entries.map { HealthLog.startOfDay(it.dateMillis) }.distinct().sorted()
            .map { start -> start to day(start, entries) }

    /**
     * The figures a doctor scans before reading the days one by one.
     *
     * Only whole days count towards the average and the urine aim. The day the
     * report is made is still going on: three urinations by noon is on course,
     * not short of four.
     */
    fun summary(entries: List<FluidEntry>, now: Long = System.currentTimeMillis()): FluidSummary {
        val days = daily(entries)
        val today = HealthLog.startOfDay(now)
        val whole = days.filter { it.first < today }.map { it.second }
        val drinking = whole.filter { it.drinks > 0 }
        // A day with no urine logged is a day it was not written down, so it is
        // left out of the count rather than counted as short.
        val urineDays = whole.filter { it.urinations > 0 }
        return FluidSummary(
            daysLogged = days.size,
            averageMillilitres = drinking.takeIf { it.isNotEmpty() }
                ?.let { Math.round(it.sumOf { d -> d.drunkMillilitres }.toDouble() / it.size).toInt() },
            daysWithUrineLogged = urineDays.size,
            daysUrineAimMet = urineDays.count { it.urinations >= URINATION_AIM.first },
            includesToday = days.any { it.first == today },
        )
    }

    /**
     * Whether the log shows no urine for longer than the warning sign allows,
     * counting waking hours only.
     *
     * Only while the reader is keeping the log: someone who logged once last
     * week has not gone a week without passing urine, they have stopped writing
     * it down. Nothing logged at all raises nothing.
     */
    fun urineGapIsLong(entries: List<FluidEntry>, now: Long = System.currentTimeMillis()): Boolean {
        val last = entries.filter { it.kind == FluidEntry.Kind.URINE }.maxOfOrNull { it.dateMillis }
            ?: return false
        if (now - last > ACTIVE_LOG_HOURS * HOUR) return false
        return wakingHours(last, now) >= URINE_GAP_HOURS
    }

    /** Hours between two moments, less any time inside the night window. */
    fun wakingHours(startMillis: Long, endMillis: Long): Double {
        if (endMillis <= startMillis) return 0.0
        var asleep = 0L
        // Every night that could overlap: the one that began the evening
        // before the start, through the one that begins on the end's day.
        val day = Calendar.getInstance().apply {
            timeInMillis = HealthLog.startOfDay(startMillis)
            add(Calendar.DAY_OF_YEAR, -1)
        }
        while (day.timeInMillis <= endMillis) {
            val nightStart = (day.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, NIGHT_START_HOUR)
            }.timeInMillis
            day.add(Calendar.DAY_OF_YEAR, 1)
            val nightEnd = (day.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, NIGHT_END_HOUR)
            }.timeInMillis
            val overlap = minOf(endMillis, nightEnd) - maxOf(startMillis, nightStart)
            if (overlap > 0) asleep += overlap
        }
        return (endMillis - startMillis - asleep).toDouble() / HOUR
    }
}
