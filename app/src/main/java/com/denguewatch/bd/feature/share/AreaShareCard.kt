package com.denguewatch.bd.feature.share

import com.denguewatch.bd.i18n.Localization
import com.denguewatch.bd.i18n.displayName
import com.denguewatch.bd.i18n.labelKey
import com.denguewatch.bd.model.Area

/**
 * Turns one area's real figures into a shareable card.
 *
 * Every value comes from the [Area] the reader is looking at. Nothing is
 * rounded up for effect, and a figure the feed does not carry is left off the
 * card rather than filled in — a card is harder to correct than a screen,
 * because it travels without the app.
 */
object AreaShareCard {

    /**
     * Fields resolved from live data, kept apart from the view so what the card
     * says can be checked without rendering anything.
     */
    data class Content(
        val areaName: String,
        val riskLabel: String,
        val incidence: String,
        val incidenceUnit: String,
        val weeklyChange: String?,
        val weeklyChangeIsUp: Boolean,
        val seasonCases: String,
        val seasonCasesLabel: String,
        val sourceLine: String,
    )

    fun content(area: Area, lastUpdatedMillis: Long?, loc: Localization): Content {
        // Only stated when the previous week exists; otherwise the ratio is
        // meaningless and the line is omitted entirely.
        //
        // The signed value, not its magnitude. `percentChange` always prefixes
        // a sign, so passing `abs(value)` printed "+33%" beside a falling
        // arrow — the card said the opposite of what the area had done, on the
        // one artefact that travels without the app to correct it.
        val change = area.weeklyChange?.let { loc.t("share.change", loc.percentChange(it)) }

        val source = lastUpdatedMillis
            ?.let { loc.t("share.source", loc.fullDate(it)) }
            ?: loc.t("share.sourceNoDate")

        return Content(
            areaName = area.displayName(loc.language),
            riskLabel = loc.t(area.risk.labelKey),
            incidence = loc.decimal(area.incidencePer100k),
            incidenceUnit = loc.t("share.unit.incidence"),
            weeklyChange = change,
            weeklyChangeIsUp = (area.weeklyChange ?: 0.0) >= 0,
            seasonCases = loc.compact(area.seasonCases),
            seasonCasesLabel = loc.t("share.unit.season"),
            sourceLine = source,
        )
    }
}
