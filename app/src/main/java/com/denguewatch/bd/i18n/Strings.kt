package com.denguewatch.bd.i18n

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * The two string tables.
 *
 * Keys are grouped into chunks purely for readability; they are merged into one
 * lookup per language on first use.
 */
object Strings {

    val english: Map<String, String> by lazy {
        buildMap {
            putAll(enCommon)
            listOf(enDashboard, enMap, enCheck, enPrevent, enCare, enAbout, enRedesign, enHealth)
                .forEach { putAll(it) }
        }
    }

    val bangla: Map<String, String> by lazy {
        buildMap {
            putAll(bnCommon)
            listOf(bnDashboard, bnMap, bnCheck, bnPrevent, bnCare, bnAbout, bnRedesign, bnHealth)
                .forEach { putAll(it) }
        }
    }

    fun table(language: AppLanguage): Map<String, String> = when (language) {
        AppLanguage.ENGLISH -> english
        AppLanguage.BANGLA -> bangla
    }

    /** Debug-only parity check: every English key should exist in Bengali. */
    val missingBanglaKeys: List<String>
        get() = english.keys.filter { bangla[it] == null }.sorted()
}

/**
 * Holds the chosen language and hands out the formatter that depends on it.
 * Screens read strings and numbers through this, so switching language redraws
 * everything at once.
 */
class Localization(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("dengue_watch_prefs", Context.MODE_PRIVATE)

    var language: AppLanguage by mutableStateOf(loadInitialLanguage())
        private set

    private fun loadInitialLanguage(): AppLanguage {
        AppLanguage.from(prefs.getString(STORAGE_KEY, null))?.let { return it }
        // First run follows the device, so a phone set to Bengali opens in Bengali.
        return if (Locale.getDefault().language == "bn") AppLanguage.BANGLA else AppLanguage.ENGLISH
    }

    fun set(value: AppLanguage) {
        if (value == language) return
        language = value
        prefs.edit().putString(STORAGE_KEY, value.code).apply()
    }

    fun toggle() {
        set(if (language == AppLanguage.ENGLISH) AppLanguage.BANGLA else AppLanguage.ENGLISH)
    }

    /** Hand this to anything that needs to format outside composition. */
    val style: NumberStyle get() = NumberStyle(language)

    // MARK: - Strings

    /**
     * Look up a key in the current language.
     *
     * A missing key falls back to English, then to the key itself, so a gap in
     * the Bengali table degrades to readable English rather than a blank label.
     */
    fun t(key: String): String =
        Strings.table(language)[key] ?: Strings.english[key] ?: key

    /** Look up a key and substitute positional arguments (`%s`). */
    fun t(key: String, vararg arguments: Any?): String =
        String.format(language.locale, t(key), *arguments)

    // MARK: - Numbers and dates (delegated, so there is one implementation)

    fun num(value: Int) = style.num(value)
    fun decimal(value: Double, places: Int = 1) = style.decimal(value, places)
    fun compact(value: Int) = style.compact(value)
    fun percentChange(value: Double?) = style.percentChange(value)
    fun fullDate(millis: Long) = style.fullDate(millis)
    fun dayMonth(millis: Long) = style.dayMonth(millis)
    fun reportedDate(millis: Long) = style.reportedDate(millis)
    fun pickerDate(millis: Long) = style.pickerDate(millis)
    fun timeOfDay(millis: Long) = style.timeOfDay(millis)
    fun dayAndTime(millis: Long) =
        style.dayAndTime(millis, t("common.today"), t("common.yesterday"))
    fun relative(millis: Long) = style.relative(millis)

    /**
     * How old a DGHS report is, in calendar days: "today", "yesterday",
     * "3 days ago".
     *
     * The feed dates a report by day, with no time attached, so "Updated 22
     * hours ago" claimed a precision the figures do not have — and read
     * differently from iOS, which counted the same date from a different
     * midnight. Both now say it in days.
     */
    fun reportAge(reportDayMillis: Long, today: java.time.LocalDate = java.time.LocalDate.now()): String {
        return when (val days = reportAgeInDays(reportDayMillis, today)) {
            0 -> t("common.today").lowercase(language.locale)
            1 -> t("common.yesterday").lowercase(language.locale)
            else -> style.relative(System.currentTimeMillis() - days * 86_400_000L)
        }
    }

    companion object {
        private const val STORAGE_KEY = "appLanguage"

        /**
         * Whole calendar days from a report's date to [today]. The feed's dates
         * are read at UTC midnight (see FeedDate), so the date is taken back
         * out in UTC — never shifted by the phone's zone. A future date counts
         * as today.
         */
        fun reportAgeInDays(reportDayMillis: Long, today: java.time.LocalDate): Int {
            val reportDay = java.time.Instant.ofEpochMilli(reportDayMillis)
                .atZone(java.time.ZoneOffset.UTC).toLocalDate()
            return java.time.temporal.ChronoUnit.DAYS.between(reportDay, today).toInt().coerceAtLeast(0)
        }
    }
}

val LocalLocalization = compositionLocalOf<Localization> {
    error("No Localization provided")
}

/** Shorthand so a composable reads `t("dash.title")`. */
@Composable
@ReadOnlyComposable
fun t(key: String): String = LocalLocalization.current.t(key)

@Composable
@ReadOnlyComposable
fun t(key: String, vararg arguments: Any?): String =
    LocalLocalization.current.t(key, *arguments)
