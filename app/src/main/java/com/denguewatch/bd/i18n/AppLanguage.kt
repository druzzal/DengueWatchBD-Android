package com.denguewatch.bd.i18n

import androidx.compose.runtime.compositionLocalOf
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class AppLanguage(val code: String) {
    ENGLISH("en"),
    BANGLA("bn");

    /** The language's name written in that language — never translated. */
    val nativeName: String
        get() = when (this) {
            ENGLISH -> "English"
            BANGLA -> "বাংলা"
        }

    /** Short badge for the compact toggle. */
    val shortLabel: String
        get() = when (this) {
            ENGLISH -> "EN"
            BANGLA -> "বাং"
        }

    val locale: Locale
        get() = when (this) {
            ENGLISH -> Locale.Builder().setLanguage("en").setRegion("BD").build()
            BANGLA -> Locale.Builder().setLanguage("bn").setRegion("BD").build()
        }

    /** Bengali readers expect Bengali digits; English readers expect Latin. */
    val usesBengaliDigits: Boolean get() = this == BANGLA

    companion object {
        fun from(code: String?): AppLanguage? = entries.firstOrNull { it.code == code }
    }
}

/** The language in force, so every `Text` can reach it without threading it through. */
val LocalLanguage = compositionLocalOf { AppLanguage.ENGLISH }

/**
 * Locale-aware formatting, kept separate from the state holder because it is
 * pure: chart axis painters and other non-composable code still need Bengali
 * digits and dates.
 */
data class NumberStyle(val language: AppLanguage) {

    private val locale: Locale get() = language.locale

    private fun digits(text: String): String {
        if (!language.usesBengaliDigits) return text
        val map = mapOf(
            '0' to '০', '1' to '১', '2' to '২', '3' to '৩', '4' to '৪',
            '5' to '৫', '6' to '৬', '7' to '৭', '8' to '৮', '9' to '৯',
        )
        return text.map { map[it] ?: it }.joinToString("")
    }

    /**
     * Grouped the way Bangladesh groups numbers — 1,05,316, not 105,316 — as
     * iOS prints them for en_BD and bn_BD alike.
     */
    fun num(value: Int): String =
        digits((if (value < 0) "-" else "") + lakhGrouped(abs(value.toLong()).toString()))

    fun decimal(value: Double, places: Int = 1): String {
        val formatter = NumberFormat.getNumberInstance(Locale.ENGLISH).apply {
            minimumFractionDigits = places
            maximumFractionDigits = places
            isGroupingUsed = false
        }
        val plain = formatter.format(value)
        val sign = if (plain.startsWith("-")) "-" else ""
        val whole = plain.removePrefix("-").substringBefore('.')
        val fraction = plain.substringAfter('.', "")
        return digits(sign + lakhGrouped(whole) + if (fraction.isEmpty()) "" else ".$fraction")
    }

    /**
     * The last three digits, then pairs: the South Asian grouping. Written out
     * because java.text cannot express a secondary grouping size, and
     * android.icu, which can, is not there for the JVM tests.
     */
    private fun lakhGrouped(whole: String): String {
        if (whole.length <= 3) return whole
        val pairs = whole.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        return pairs + "," + whole.takeLast(3)
    }

    /**
     * Bengali uses হাজার / লাখ rather than k / M, because "১০১k" is not how
     * anyone reads a number in Bengali.
     *
     * Each unit takes over from the value that would round up to it, as on
     * iOS, so 99,600 reads "১.০ লাখ" rather than "১০০ হাজার", and 999,600
     * "1.0M" rather than "1000k".
     */
    fun compact(value: Int): String = when (language) {
        AppLanguage.ENGLISH -> when {
            value >= 999_500 -> String.format(Locale.ENGLISH, "%.1fM", value / 1_000_000.0)
            value >= 9_950 -> String.format(Locale.ENGLISH, "%.0fk", value / 1_000.0)
            value >= 1_000 -> String.format(Locale.ENGLISH, "%.1fk", value / 1_000.0)
            else -> num(value)
        }
        AppLanguage.BANGLA -> when {
            value >= 9_995_000 ->
                digits(String.format(Locale.ENGLISH, "%.1f", value / 10_000_000.0)) + " কোটি"
            value >= 99_500 ->
                digits(String.format(Locale.ENGLISH, "%.1f", value / 100_000.0)) + " লাখ"
            // Below ten thousand a whole-thousand label is too coarse to be
            // true. This band rounded to the nearest thousand while English
            // kept a decimal, so a chart axis tick of 2,500 was labelled
            // "৩ হাজার" — three thousand — and the ticks 2,500 / 1,875 / 1,250
            // collapsed onto ৩ / ২ / ১ হাজার, which also made an uneven axis
            // look evenly stepped. The two languages now round the same way.
            value >= 9_950 ->
                digits(String.format(Locale.ENGLISH, "%.0f", value / 1_000.0)) + " হাজার"
            value >= 1_000 ->
                digits(String.format(Locale.ENGLISH, "%.1f", value / 1_000.0)) + " হাজার"
            else -> num(value)
        }
    }

    /**
     * Several names as one phrase — "A, B, and C", "A, B এবং C" — the way
     * iOS's `ListFormatter` writes them for each language (CLDR's standard
     * list patterns). A plain comma list read as unfinished in a sentence.
     */
    fun list(items: List<String>): String {
        val and = if (language == AppLanguage.BANGLA) " এবং " else " and "
        return when (items.size) {
            0 -> ""
            1 -> items[0]
            2 -> items[0] + and + items[1]
            else -> items.dropLast(1).joinToString(", ") +
                (if (language == AppLanguage.BANGLA) and else ",$and") + items.last()
        }
    }

    fun percentChange(value: Double?): String {
        if (value == null) return "—"
        val sign = if (value >= 0) "+" else "−"
        return sign + digits((abs(value * 100)).roundToInt().toString()) + "%"
    }

    fun fullDate(millis: Long): String =
        digits(SimpleDateFormat("d MMMM yyyy", locale).format(Date(millis)))

    fun dayMonth(millis: Long): String =
        digits(SimpleDateFormat("d MMM", locale).format(Date(millis)))

    /**
     * The date under "Today in Bangladesh", which Bangla sets month first.
     *
     * CLDR orders both bn_BD and en_BD day-first, so the Bangla form is an
     * explicit pattern — set that way on the author's instruction in the iOS
     * build rather than derived. English keeps the locale's own order.
     */
    fun reportedDate(millis: Long): String =
        if (language == AppLanguage.BANGLA) {
            digits(SimpleDateFormat("MMMM d", locale).format(Date(millis)))
        } else {
            dayMonth(millis)
        }

    /**
     * The date as the iOS picker shows it in the recording screens:
     * "14 Oct, 2026". The year is there because a reading can be dated up to a
     * year back, and "14 Oct" alone does not say which one.
     */
    fun pickerDate(millis: Long): String =
        digits(shortMonthFormat("d MMM, yyyy").format(Date(millis)))

    /**
     * A formatter whose Bangla short months are the ones iOS prints — "অক্টো",
     * not Android's "অক্টোবর" — so a reading's date reads the same on both, and
     * fits beside its label instead of pushing it onto a second line.
     */
    private fun shortMonthFormat(pattern: String): SimpleDateFormat =
        SimpleDateFormat(pattern, locale).apply {
            if (language == AppLanguage.BANGLA) {
                dateFormatSymbols = dateFormatSymbols.apply { shortMonths = BANGLA_SHORT_MONTHS }
            }
        }

    /**
     * Clock time alone, for a record that already states the day.
     *
     * Twelve-hour, as iOS shows it in both languages: "5:02 PM", "৫:০২ PM".
     * CLDR gives bn_BD the Latin AM/PM as well, so the marker is formatted in
     * English rather than left to whichever ICU the phone happens to carry.
     */
    fun timeOfDay(millis: Long): String =
        digits(SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(millis)))

    /**
     * Weekday-or-"today" plus the clock time, ported from iOS.
     *
     * Health readings need the actual moment, not "2 hours ago": a temperature
     * taken at 3am rather than after lunch is the difference between a fever
     * chart that means something and one that does not. Recent days are named
     * rather than dated, because "Today, 2:20 PM" is read faster than
     * "13 Sep, 2:20 PM" when it is in fact today.
     */
    fun dayAndTime(
        millis: Long,
        todayLabel: String,
        yesterdayLabel: String,
        now: Long = System.currentTimeMillis(),
    ): String {
        val time = timeOfDay(millis)
        when (daysBetween(millis, now)) {
            0 -> return "$todayLabel, $time"
            1 -> return "$yesterdayLabel, $time"
        }
        // Whole days elapsed, as iOS counts it here, not calendar days.
        val pattern = if ((now - millis) / 86_400_000L < 7) "EEEE" else "EEE, d MMM"
        return digits(shortMonthFormat(pattern).format(Date(millis))) + ", " + time
    }

    /**
     * A relative description ("2 days ago"). Written out rather than delegated
     * to `DateUtils`, which has no Bengali strings on most devices and would
     * silently drop back to English inside an otherwise Bengali screen.
     */
    fun relative(millis: Long, now: Long = System.currentTimeMillis()): String {
        val delta = now - millis
        val future = delta < 0
        val seconds = abs(delta) / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        val bn = language == AppLanguage.BANGLA
        return when {
            seconds < 60 -> if (bn) "এইমাত্র" else "just now"
            minutes < 60 -> unit(minutes.toInt(), if (bn) "মিনিট" else "minute", future)
            hours < 24 -> unit(hours.toInt(), if (bn) "ঘণ্টা" else "hour", future)
            days < 7 -> unit(days.toInt(), if (bn) "দিন" else "day", future)
            days < 31 -> unit((days / 7).toInt(), if (bn) "সপ্তাহ" else "week", future)
            days < 365 -> unit((days / 30).toInt(), if (bn) "মাস" else "month", future)
            else -> unit((days / 365).toInt(), if (bn) "বছর" else "year", future)
        }
    }

    private fun unit(count: Int, noun: String, future: Boolean): String =
        if (language == AppLanguage.BANGLA) {
            val body = "${digits(count.toString())} $noun"
            if (future) "$body পরে" else "$body আগে"
        } else {
            val body = "$count $noun" + if (count == 1) "" else "s"
            if (future) "in $body" else "$body ago"
        }

    /** Days between two instants, counted by calendar day rather than by 24h blocks. */
    fun daysBetween(fromMillis: Long, toMillis: Long): Int {
        val from = Calendar.getInstance().apply { timeInMillis = fromMillis }.startOfDay()
        val to = Calendar.getInstance().apply { timeInMillis = toMillis }.startOfDay()
        return ((to - from) / 86_400_000L).toInt()
    }

    private companion object {
        /** CLDR's bn abbreviations as Apple ships them. */
        val BANGLA_SHORT_MONTHS = arrayOf(
            "জানু", "ফেব", "মার্চ", "এপ্রি", "মে", "জুন",
            "জুল", "আগ", "সেপ", "অক্টো", "নভে", "ডিসে",
        )
    }

    private fun Calendar.startOfDay(): Long {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        return timeInMillis
    }
}
