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

    fun num(value: Int): String =
        digits(NumberFormat.getIntegerInstance(Locale.ENGLISH).format(value))

    fun decimal(value: Double, places: Int = 1): String {
        val formatter = NumberFormat.getNumberInstance(Locale.ENGLISH).apply {
            minimumFractionDigits = places
            maximumFractionDigits = places
        }
        return digits(formatter.format(value))
    }

    /**
     * Bengali uses হাজার / লাখ rather than k / M, because "১০১k" is not how
     * anyone reads a number in Bengali.
     */
    fun compact(value: Int): String = when (language) {
        AppLanguage.ENGLISH -> when {
            value >= 1_000_000 -> String.format(Locale.ENGLISH, "%.1fM", value / 1_000_000.0)
            value >= 10_000 -> String.format(Locale.ENGLISH, "%.0fk", value / 1_000.0)
            value >= 1_000 -> String.format(Locale.ENGLISH, "%.1fk", value / 1_000.0)
            else -> num(value)
        }
        AppLanguage.BANGLA -> when {
            value >= 10_000_000 ->
                digits(String.format(Locale.ENGLISH, "%.1f", value / 10_000_000.0)) + " কোটি"
            value >= 100_000 ->
                digits(String.format(Locale.ENGLISH, "%.1f", value / 100_000.0)) + " লাখ"
            value >= 1_000 ->
                digits(String.format(Locale.ENGLISH, "%.0f", value / 1_000.0)) + " হাজার"
            else -> num(value)
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

    fun dateTime(millis: Long): String =
        digits(SimpleDateFormat("d MMM, HH:mm", locale).format(Date(millis)))

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

    private fun Calendar.startOfDay(): Long {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        return timeInMillis
    }
}
