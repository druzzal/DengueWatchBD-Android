package com.denguewatch.bd.i18n

import com.denguewatch.bd.model.Area
import com.denguewatch.bd.model.Division
import com.denguewatch.bd.model.RiskLevel

/**
 * Bengali names for the reporting areas and the eight divisions.
 *
 * These live here rather than in the feed because they are a property of the
 * place, not of any particular day's figures — the feed is keyed by area code
 * and needs no Bengali column.
 */
object PlaceNames {

    fun area(code: String, fallback: String, language: AppLanguage): String {
        if (language != AppLanguage.BANGLA) return fallback
        return areasBangla[code] ?: fallback
    }

    fun division(division: Division, language: AppLanguage): String {
        if (language != AppLanguage.BANGLA) return division.label
        return divisionsBangla[division] ?: division.label
    }

    private val divisionsBangla = mapOf(
        Division.BARISHAL to "বরিশাল",
        Division.CHATTOGRAM to "চট্টগ্রাম",
        Division.DHAKA to "ঢাকা",
        Division.KHULNA to "খুলনা",
        Division.MYMENSINGH to "ময়মনসিংহ",
        Division.RAJSHAHI to "রাজশাহী",
        Division.RANGPUR to "রংপুর",
        Division.SYLHET to "সিলেট",
    )

    /** Keyed by `Geography.Definition.code`. */
    private val areasBangla = mapOf(
        "BARISHAL" to "বরিশাল",
        "CHATTOGRAM" to "চট্টগ্রাম",
        "DHAKA_OUT_CC" to "ঢাকা (সিটির বাইরে)",
        "DNCC" to "ঢাকা উত্তর সিটি",
        "DSCC" to "ঢাকা দক্ষিণ সিটি",
        "KHULNA" to "খুলনা",
        "MYMENSINGH" to "ময়মনসিংহ",
        "RAJSHAHI" to "রাজশাহী",
        "RANGPUR" to "রংপুর",
        "SYLHET" to "সিলেট",
    )
}

fun Area.displayName(language: AppLanguage): String =
    PlaceNames.area(code, name, language)

fun Division.displayName(language: AppLanguage): String =
    PlaceNames.division(this, language)

/**
 * The division to print beside an area's name, or null to print none.
 *
 * The label exists to disambiguate — "Khulna" the area sits in "Khulna" the
 * division, and "Cox's Bazar" needs telling that it is in Chattogram. It earns
 * its place only when it adds something, and when it does not it takes width
 * the name needed: "Dhaka (outside city)" was being shown beside a second
 * "Dhaka" squeezed so thin it broke across two lines as "Dhak / a".
 *
 * Containment rather than equality is the whole point. All three Dhaka areas
 * carry their division inside their own name — "Dhaka (outside city)", "Dhaka
 * North City", "Dhaka South City" — so an equality test calls them different
 * and prints the division anyway. Ported from the iOS rule.
 */
fun divisionLabel(areaName: String, divisionName: String): String? =
    if (areaName.contains(divisionName, ignoreCase = true)) null else divisionName

// MARK: - Risk copy keys
//
// The band carries no strings of its own; it names the keys the tables answer.

val RiskLevel.labelKey: String
    get() = when (this) {
        RiskLevel.LOW -> "risk.low"
        RiskLevel.MODERATE -> "risk.moderate"
        RiskLevel.HIGH -> "risk.high"
        RiskLevel.SEVERE -> "risk.severe"
    }

val RiskLevel.guidanceKey: String
    get() = when (this) {
        RiskLevel.LOW -> "risk.guidance.low"
        RiskLevel.MODERATE -> "risk.guidance.moderate"
        RiskLevel.HIGH -> "risk.guidance.high"
        RiskLevel.SEVERE -> "risk.guidance.severe"
    }

val RiskLevel.headlineKey: String
    get() = when (this) {
        RiskLevel.LOW -> "risk.headline.low"
        RiskLevel.MODERATE -> "risk.headline.moderate"
        RiskLevel.HIGH -> "risk.headline.high"
        RiskLevel.SEVERE -> "risk.headline.severe"
    }
