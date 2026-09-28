package com.denguewatch.bd.model

import org.json.JSONArray
import org.json.JSONObject

// MARK: - Wire format
//
// The published feed at druzzal.github.io/dengue-bd-dashboard is two documents
// of deliberately different sizes:
//
//   summary.json  ~350 bytes  headline figures and the DGHS report date
//   latest.json   ~40 KB      every chart series and table behind them
//
// The app asks the small one whether anything changed and only pays for the
// large one when the answer is yes.
//
// Parsing goes through org.json rather than a generated decoder: the feed's
// points and cells are genuinely polymorphic — a point is a bare number or a
// `{name, y}` object, a cell is a string, a number or null — and hand-written
// readers express that more honestly than annotations plus custom serializers.

/**
 * Headline figures. Present in both documents, identically shaped.
 *
 * Kept whole rather than trimmed to what the UI currently draws: this type is
 * the contract for `summary.json`, which carries exactly these nine fields and
 * nothing else.
 */
data class FeedHeadline(
    val epiWeek: String? = null,
    val weekCases: Int? = null,
    val weekDeaths: Int? = null,
    val ytdCases: Int? = null,
    val ytdDeaths: Int? = null,
    val last24Cases: Int? = null,
    val last24Deaths: Int? = null,
    val dischargedLast24: Int? = null,
    val dischargedYtd: Int? = null,
) {
    companion object {
        fun from(json: JSONObject?): FeedHeadline {
            if (json == null) return FeedHeadline()
            return FeedHeadline(
                epiWeek = json.optStringOrNull("epi_week"),
                weekCases = json.optIntOrNull("week_cases"),
                weekDeaths = json.optIntOrNull("week_deaths"),
                ytdCases = json.optIntOrNull("ytd_cases"),
                ytdDeaths = json.optIntOrNull("ytd_deaths"),
                last24Cases = json.optIntOrNull("last24_cases"),
                last24Deaths = json.optIntOrNull("last24_deaths"),
                dischargedLast24 = json.optIntOrNull("discharged_last24"),
                dischargedYtd = json.optIntOrNull("discharged_ytd"),
            )
        }
    }
}

/** `summary.json` — the cheap freshness probe. */
data class FeedSummaryDocument(
    val schemaVersion: Int,
    val fetchedAt: String?,
    /**
     * The report date DGHS itself stamps. This, not [fetchedAt], decides
     * whether the full document is worth downloading.
     */
    val lastUpdated: String?,
    val year: Int?,
    val summary: FeedHeadline,
) {
    companion object {
        fun parse(text: String): FeedSummaryDocument {
            val root = JSONObject(text)
            return FeedSummaryDocument(
                schemaVersion = root.optInt("schema_version", 1),
                fetchedAt = root.optStringOrNull("fetched_at"),
                lastUpdated = root.optStringOrNull("last_updated"),
                year = root.optIntOrNull("year"),
                summary = FeedHeadline.from(root.optJSONObject("summary")),
            )
        }
    }
}

/** `latest.json` — the full dataset. */
data class FeedDocument(
    val schemaVersion: Int,
    val fetchedAt: String?,
    val meta: Meta,
    val summary: FeedHeadline,
    val charts: Map<String, FeedChart>,
    val tables: List<FeedTable>,
) {
    data class Meta(
        val sourceUrl: String? = null,
        val sourceName: String? = null,
        val lastUpdatedLabel: String? = null,
        val lastUpdated: String? = null,
        val year: Int? = null,
    )

    fun chart(id: FeedChartID): FeedChart? = charts[id.key]

    companion object {
        fun parse(text: String): FeedDocument {
            val root = JSONObject(text)
            val metaJson = root.optJSONObject("meta")
            val meta = Meta(
                sourceUrl = metaJson?.optStringOrNull("source_url"),
                sourceName = metaJson?.optStringOrNull("source_name"),
                lastUpdatedLabel = metaJson?.optStringOrNull("last_updated_label"),
                lastUpdated = metaJson?.optStringOrNull("last_updated"),
                year = metaJson?.optIntOrNull("year"),
            )

            val charts = mutableMapOf<String, FeedChart>()
            root.optJSONObject("charts")?.let { chartsJson ->
                for (key in chartsJson.keys()) {
                    chartsJson.optJSONObject(key)?.let { charts[key] = FeedChart.from(it) }
                }
            }

            val tables = root.optJSONArray("tables").mapObjects { FeedTable.from(it) }

            return FeedDocument(
                schemaVersion = root.optInt("schema_version", 1),
                fetchedAt = root.optStringOrNull("fetched_at"),
                meta = meta,
                summary = FeedHeadline.from(root.optJSONObject("summary")),
                charts = charts,
                tables = tables,
            )
        }
    }
}

/**
 * The chart keys this app reads. Named rather than stringly-typed so a feed
 * that drops one fails somewhere the compiler and tests can point at.
 */
enum class FeedChartID(val key: String) {
    DAILY_CASES("confirmed_case"),
    DAILY_DEATHS("death_case"),
    AREA_SEASON_CASES("div_city_cor_case_in_year"),
    AREA_SEASON_DEATHS("div_city_cor_death_in_year"),
    DIVISION_CASES_BY_WEEK("affected_in_division_by_week"),
    CASES_BY_YEAR("year_case"),
    CASES_BY_GENDER("dengue_affected_by_gender"),
}

data class FeedChart(
    val title: String?,
    val categories: List<String>,
    val series: List<FeedSeries>,
) {
    /** The first series' values. Most charts in the feed carry exactly one. */
    val primaryValues: List<Int> get() = series.firstOrNull()?.values ?: emptyList()

    /**
     * Value for one category, matched by name. Feed category spellings are
     * whatever DGHS typed, so callers pass the exact string.
     */
    fun value(category: String): Int? {
        val index = categories.indexOf(category)
        if (index < 0) return null
        return series.firstOrNull()?.values?.getOrNull(index)
    }

    fun series(named: String): FeedSeries? = series.firstOrNull { it.name == named }

    companion object {
        fun from(json: JSONObject) = FeedChart(
            title = json.optStringOrNull("title"),
            categories = json.optJSONArray("categories").mapStrings(),
            series = json.optJSONArray("series").mapObjects { FeedSeries.from(it) },
        )
    }
}

data class FeedSeries(
    val name: String?,
    val type: String?,
    val data: List<FeedPoint>,
) {
    /**
     * Missing points read as zero: in this feed a gap means "none reported",
     * and a list of nullables would push that decision into every call site.
     */
    val values: List<Int> get() = data.map { it.value ?: 0 }

    /** Only named points (pie slices) carry labels. */
    val labelled: List<Pair<String, Int>>
        get() = data.mapNotNull { point ->
            val name = point.name ?: return@mapNotNull null
            val value = point.value ?: return@mapNotNull null
            name to value
        }

    companion object {
        fun from(json: JSONObject) = FeedSeries(
            name = json.optStringOrNull("name"),
            type = json.optStringOrNull("type"),
            data = json.optJSONArray("data").mapAny { FeedPoint.from(it) },
        )
    }
}

/**
 * One point, in either shape Highcharts emits: a bare number (column and line
 * charts) or `{"name": "Male", "y": 25367}` (pie charts). Nulls survive as null.
 */
data class FeedPoint(val name: String?, val value: Int?) {
    companion object {
        fun from(raw: Any?): FeedPoint = when (raw) {
            null, JSONObject.NULL -> FeedPoint(null, null)
            is Int -> FeedPoint(null, raw)
            is Long -> FeedPoint(null, raw.toInt())
            is Double -> FeedPoint(null, Math.round(raw).toInt())
            is Number -> FeedPoint(null, Math.round(raw.toDouble()).toInt())
            is JSONObject -> FeedPoint(raw.optStringOrNull("name"), raw.optIntOrNull("y"))
            else -> FeedPoint(null, null)
        }
    }
}

/** An age-by-sex table. Row keys are the header strings the source page uses. */
data class FeedTable(
    val title: String?,
    val headers: List<String>,
    val rows: List<Map<String, FeedCell>>,
) {
    companion object {
        fun from(json: JSONObject) = FeedTable(
            title = json.optStringOrNull("title"),
            headers = json.optJSONArray("headers").mapStrings(),
            rows = json.optJSONArray("rows").mapObjects { row ->
                buildMap {
                    for (key in row.keys()) put(key, FeedCell.from(row.opt(key)))
                }
            },
        )
    }
}

/** A table cell: a label, a number, or empty. */
data class FeedCell(val text: String?, val number: Int?) {
    companion object {
        fun from(raw: Any?): FeedCell = when (raw) {
            null, JSONObject.NULL -> FeedCell(null, null)
            is Int -> FeedCell(null, raw)
            is Long -> FeedCell(null, raw.toInt())
            is Double -> FeedCell(null, Math.round(raw).toInt())
            is Number -> FeedCell(null, Math.round(raw.toDouble()).toInt())
            is String -> raw.toIntOrNull()?.let { FeedCell(null, it) } ?: FeedCell(raw, null)
            else -> FeedCell(null, null)
        }
    }
}

// MARK: - org.json conveniences
//
// `optString` returns "" for a missing key and the literal "null" for a JSON
// null, both of which read as present downstream. These wrappers give back the
// absence instead.

internal fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val value = optString(key, "")
    return value.ifEmpty { null }
}

internal fun JSONObject.optIntOrNull(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return when (val value = opt(key)) {
        is Int -> value
        is Long -> value.toInt()
        is Double -> Math.round(value).toInt()
        is Number -> Math.round(value.toDouble()).toInt()
        is String -> value.toIntOrNull() ?: value.toDoubleOrNull()?.let { Math.round(it).toInt() }
        else -> null
    }
}

internal fun JSONArray?.mapStrings(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        if (isNull(index)) null else optString(index, "").ifEmpty { null }
    }
}

internal fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optJSONObject(index)?.let(transform) }
}

internal fun <T> JSONArray?.mapAny(transform: (Any?) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).map { index -> transform(opt(index)) }
}
