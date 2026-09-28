package com.denguewatch.bd.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.denguewatch.bd.model.AgeBand
import com.denguewatch.bd.model.Area
import com.denguewatch.bd.model.DailyPoint
import com.denguewatch.bd.model.FeedChart
import com.denguewatch.bd.model.FeedChartID
import com.denguewatch.bd.model.FeedDocument
import com.denguewatch.bd.model.FeedHeadline
import com.denguewatch.bd.model.Division
import com.denguewatch.bd.model.Geography
import com.denguewatch.bd.model.RiskLevel
import com.denguewatch.bd.model.Series
import com.denguewatch.bd.model.SexSplit
import com.denguewatch.bd.model.YearSummary
import kotlin.coroutines.cancellation.CancellationException

/**
 * Loads the published feed and exposes the figures the UI reads.
 *
 * Everything here is derived from one [FeedDocument]. The feed reports national
 * figures daily and geography at division level, so that is exactly what this
 * store offers — no interpolation, and no pretending to a finer grain than DGHS
 * publishes.
 */
class DengueStore(private val bundled: BundledFeedService) {

    sealed interface LoadState {
        data object Idle : LoadState
        data object Loading : LoadState
        data object Loaded : LoadState
        data class Failed(val message: String) : LoadState
    }

    enum class Source { BUNDLED, NETWORK }

    var state: LoadState by mutableStateOf(LoadState.Idle)
        private set
    var source: Source by mutableStateOf(Source.BUNDLED)
        private set

    var meta: FeedDocument.Meta? by mutableStateOf(null)
        private set
    var headline: FeedHeadline? by mutableStateOf(null)
        private set
    var national: List<DailyPoint> by mutableStateOf(emptyList())
        private set
    var areas: List<Area> by mutableStateOf(emptyList())
        private set
    var history: List<YearSummary> by mutableStateOf(emptyList())
        private set
    var epiWeeks: List<String> by mutableStateOf(emptyList())
        private set
    var ageBandsCases: List<AgeBand> by mutableStateOf(emptyList())
        private set
    var sexSplitCases: SexSplit? by mutableStateOf(null)
        private set

    // MARK: - Loading

    suspend fun load() {
        if (state is LoadState.Loading || state is LoadState.Loaded) return
        state = LoadState.Loading
        try {
            apply(bundled.load())
            source = if (bundled.isServingDownloadedData) Source.NETWORK else Source.BUNDLED
            state = LoadState.Loaded
        } catch (cancellation: CancellationException) {
            // Cancellation is not a load failure. Catching Exception without
            // this would swallow it, leaving the screen reading "unavailable"
            // because the reader navigated away mid-load, and would stop the
            // cancellation propagating to the scope that asked for it.
            state = LoadState.Idle
            throw cancellation
        } catch (error: Exception) {
            state = LoadState.Failed(error.message ?: "Unknown error")
        }
    }

    suspend fun reload() {
        state = LoadState.Idle
        load()
    }

    /**
     * Refresh in place, for pull-to-refresh.
     *
     * Deliberately does not pass through [LoadState.Loading]: dropping back
     * would swap the whole dashboard for skeletons mid-gesture and blank
     * figures the reader is looking at.
     */
    suspend fun refresh() {
        try {
            apply(bundled.load())
            state = LoadState.Loaded
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // A failed refresh is no reason to blank a screen that already has
            // yesterday's figures — only surface it when there is nothing to show.
            if (areas.isEmpty()) state = LoadState.Failed(error.message ?: "Unknown error")
        }
    }

    /**
     * Replace the in-memory dataset. Called by the sync layer when a newer
     * document lands.
     */
    fun apply(document: FeedDocument, source: Source) {
        apply(document)
        this.source = source
        state = LoadState.Loaded
    }

    // MARK: - Mapping the feed onto the domain

    private fun apply(document: FeedDocument) {
        meta = document.meta
        headline = document.summary

        national = dailySeries(document)
        epiWeeks = document.chart(FeedChartID.DIVISION_CASES_BY_WEEK)?.categories ?: emptyList()
        areas = areas(document, epiWeeks.size)
        history = history(document)
        ageBandsCases = ageBands(document, "affected cases from 1 January")
        sexSplitCases = sexSplit(document.chart(FeedChartID.CASES_BY_GENDER))
    }

    /**
     * Daily national cases, with deaths joined on by date.
     *
     * The two series are not parallel in the feed: cases are published for
     * every day of the season, deaths only for days a death occurred. Zipping
     * them by index would file deaths under the wrong dates entirely, so they
     * are matched on the parsed date instead.
     */
    private fun dailySeries(document: FeedDocument): List<DailyPoint> {
        val cases = document.chart(FeedChartID.DAILY_CASES) ?: return emptyList()
        val caseValues = cases.primaryValues

        val deathsByDate = mutableMapOf<Long, Int>()
        document.chart(FeedChartID.DAILY_DEATHS)?.let { deaths ->
            val values = deaths.primaryValues
            deaths.categories.forEachIndexed { index, label ->
                val date = FeedDate.day(label) ?: return@forEachIndexed
                deathsByDate[date] = (deathsByDate[date] ?: 0) + (values.getOrNull(index) ?: 0)
            }
        }

        return cases.categories.mapIndexedNotNull { index, label ->
            val date = FeedDate.day(label) ?: return@mapIndexedNotNull null
            DailyPoint(
                dateMillis = date,
                cases = caseValues.getOrNull(index) ?: 0,
                deaths = deathsByDate[date] ?: 0,
            )
        }
    }

    private fun areas(document: FeedDocument, weekCount: Int): List<Area> {
        val seasonCases = document.chart(FeedChartID.AREA_SEASON_CASES)
        val seasonDeaths = document.chart(FeedChartID.AREA_SEASON_DEATHS)
        val weekly = document.chart(FeedChartID.DIVISION_CASES_BY_WEEK)

        fun cases(definition: Geography.Definition): Int =
            seasonCases?.value(definition.feedCategory) ?: 0

        // The area list is built from a fixed table of ten places, so without
        // this it is never empty: a feed that dropped or renamed the breakdown
        // would render ten areas reporting zero cases next to a national total
        // in the tens of thousands, and the map's "no area data" state could
        // never appear. Recognising none of our categories means no data, not
        // a quiet season.
        if (seasonCases == null ||
            Geography.definitions.none { seasonCases.value(it.feedCategory) != null }
        ) {
            return emptyList()
        }

        // Dhaka's three feed rows share one weekly series, so each week is split
        // between them by their share of the division's season cases. Split once
        // for all three, and exactly: apportioning them independently let
        // rounding drift, so the parts no longer summed to the division's own
        // weekly figure — which is precisely what the area page promises.
        val dhaka = Geography.definitions.filter { it.code in Geography.apportionedCodes }
        val dhakaWeights = dhaka.map { cases(it) }
        val dhakaSeries = mutableMapOf<String, MutableList<Int>>()
        dhaka.firstOrNull()
            ?.let { weekly?.series(it.weeklySeriesName)?.values }
            ?.forEach { week ->
                val parts = Series.apportion(week, dhakaWeights)
                dhaka.forEachIndexed { index, definition ->
                    dhakaSeries.getOrPut(definition.code) { mutableListOf() }.add(parts[index])
                }
            }

        return Geography.definitions.map { definition ->
            val apportioned = definition.code in Geography.apportionedCodes
            var series = if (apportioned) {
                dhakaSeries[definition.code]?.toList() ?: emptyList()
            } else {
                weekly?.series(definition.weeklySeriesName)?.values ?: emptyList()
            }
            if (weekCount > 0 && series.size > weekCount) series = series.take(weekCount)

            Area(
                code = definition.code,
                name = definition.name,
                division = definition.division,
                latitude = definition.latitude,
                longitude = definition.longitude,
                populationThousands = definition.populationThousands,
                seasonCases = cases(definition),
                seasonDeaths = seasonDeaths?.value(definition.feedCategory) ?: 0,
                weeklyCases = series,
                weeklyIsApportioned = apportioned,
                geofenceRadiusMeters = definition.geofenceRadiusMeters,
            )
        }
    }

    private fun history(document: FeedDocument): List<YearSummary> {
        val chart = document.chart(FeedChartID.CASES_BY_YEAR) ?: return emptyList()
        val values = chart.primaryValues
        val currentYear = document.meta.year
        val seasonDeaths = document.summary.ytdDeaths ?: 0
        return chart.categories.mapIndexedNotNull { index, label ->
            val year = label.toIntOrNull() ?: return@mapIndexedNotNull null
            // The feed publishes yearly case totals but not yearly deaths, so
            // only the current season's death count is known here.
            YearSummary(
                year = year,
                cases = values.getOrNull(index) ?: 0,
                deaths = if (year == currentYear) seasonDeaths else null,
            )
        }.sortedBy { it.year }
    }

    /**
     * Age bands come from the tables rather than the age charts: the charts
     * ship empty categories and negative male values (they are drawn as
     * population pyramids), so the labels only exist in the tables.
     */
    private fun ageBands(document: FeedDocument, titleFragment: String): List<AgeBand> {
        val table = document.tables.firstOrNull {
            (it.title ?: "").contains(titleFragment, ignoreCase = true)
        } ?: return emptyList()

        return table.rows.mapNotNull { row ->
            val label = row["Age Group"]?.text ?: return@mapNotNull null
            if (!isAgeBand(label)) return@mapNotNull null
            AgeBand(
                label = label,
                male = row["Male"]?.number ?: 0,
                female = row["Female"]?.number ?: 0,
            )
        }
    }

    /**
     * DGHS's age tables occasionally carry a row where a date was typed into
     * the age column ("42309" — an Excel serial) or left blank. Those are
     * dropped rather than rendered as an age band.
     */
    private fun isAgeBand(label: String): Boolean {
        if (label == "80+") return true
        val parts = label.split("-")
        if (parts.size != 2) return false
        val low = parts[0].toIntOrNull() ?: return false
        val high = parts[1].toIntOrNull() ?: return false
        return low <= high && high <= 120
    }

    private fun sexSplit(chart: FeedChart?): SexSplit? {
        val points = chart?.series?.firstOrNull()?.labelled ?: return null
        if (points.isEmpty()) return null
        val male = points.firstOrNull {
            it.first.contains("male", ignoreCase = true) &&
                !it.first.contains("female", ignoreCase = true)
        }?.second
        val female = points.firstOrNull {
            it.first.contains("female", ignoreCase = true)
        }?.second
        if (male == null || female == null) return null
        return SexSplit(male, female)
    }

    // MARK: - Freshness

    /** The newest DGHS report date the feed carries. */
    val lastUpdated: Long?
        get() {
            val iso = meta?.lastUpdated ?: return national.lastOrNull()?.dateMillis
            return FeedDate.iso(iso) ?: national.lastOrNull()?.dateMillis
        }

    val dataAgeInDays: Int?
        get() {
            val updated = lastUpdated ?: return null
            return ((System.currentTimeMillis() - updated) / 86_400_000L).toInt()
        }

    enum class DataFreshness { FRESH, STALE, OUTDATED }

    val freshness: DataFreshness
        get() {
            val updated = lastUpdated ?: return DataFreshness.FRESH
            val age = System.currentTimeMillis() - updated
            return when {
                age > FeedConfig.OUTDATED_THRESHOLD_MILLIS -> DataFreshness.OUTDATED
                age > FeedConfig.STALENESS_THRESHOLD_MILLIS -> DataFreshness.STALE
                else -> DataFreshness.FRESH
            }
        }

    /**
     * True when the figures are old enough that showing them without comment
     * would misrepresent them as current.
     */
    val isStale: Boolean get() = freshness != DataFreshness.FRESH

    // MARK: - National roll-ups

    val latest: DailyPoint? get() = national.lastOrNull()

    /**
     * Season totals come from the headline block, which is what DGHS puts at
     * the top of the press release. It agrees with the daily series to the
     * case, but the headline is the figure of record.
     */
    val seasonCases: Int get() = headline?.ytdCases ?: national.sumOf { it.cases }
    val seasonDeaths: Int get() = headline?.ytdDeaths ?: national.sumOf { it.deaths }

    val cases24h: Int get() = headline?.last24Cases ?: national.lastOrNull()?.cases ?: 0
    val deaths24h: Int get() = headline?.last24Deaths ?: national.lastOrNull()?.deaths ?: 0

    val last7Cases: Int get() = national.takeLast(7).sumOf { it.cases }
    val previous7Cases: Int get() = national.dropLast(7).takeLast(7).sumOf { it.cases }

    val weeklyCaseChange: Double? get() = Series.change(previous7Cases, last7Cases)

    fun nationalRecent(days: Int): List<DailyPoint> = national.takeLast(days)

    /** National risk band, over the same two-week window as the areas. */
    val nationalRisk: RiskLevel
        get() {
            val population = areas.sumOf { it.populationThousands }
            if (population <= 0) return RiskLevel.LOW
            val cases = areas.sumOf { it.recentCases }
            return RiskLevel.forIncidence(cases.toDouble() / population * 100)
        }

    val nationalIncidencePer100k: Double
        get() {
            val population = areas.sumOf { it.populationThousands }
            if (population <= 0) return 0.0
            return areas.sumOf { it.recentCases }.toDouble() / population * 100
        }

    val nationalRecentCases: Int get() = areas.sumOf { it.recentCases }

    // MARK: - Area roll-ups

    fun area(code: String?): Area? = code?.let { c -> areas.firstOrNull { it.code == c } }

    /**
     * The reporting area a coordinate falls in.
     *
     * Two steps, because neither alone is right. Containment rejects
     * coordinates outside Bangladesh — a distance test cannot, since the area
     * centres are nearer to Shillong and Kolkata than to St Martin's Island.
     * Nearest centre then picks the area, which is an approximation that
     * degrades gracefully near an internal boundary.
     *
     * [maxDistanceMeters] is only a backstop against a wildly wrong fix; 250 km
     * clears the 198 km worst case inside the country.
     */
    fun nearestArea(
        latitude: Double,
        longitude: Double,
        maxDistanceMeters: Double = 250_000.0,
    ): Area? {
        if (!Geography.containsBangladesh(latitude, longitude)) return null

        // Dhaka's three areas share one metro, so a centroid cannot tell them
        // apart: the city corporations are small polygons inside the region
        // they are carved out of. Ask the boundaries directly, and let anywhere
        // else in the division fall through to the nearest centre.
        Geography.cityCorporationCode(latitude, longitude)
            ?.let { code -> area(code)?.let { return it } }

        val ranked = areas
            .filter { it.code !in Geography.apportionedCodes || it.code == "DHAKA_OUT_CC" }
            .map { it to Geography.distanceMeters(it.latitude, it.longitude, latitude, longitude) }
            .minByOrNull { it.second }
            ?: return null

        return if (ranked.second <= maxDistanceMeters) ranked.first else null
    }

    val areasByCases: List<Area> get() = areas.sortedByDescending { it.seasonCases }
    val areasByRisk: List<Area> get() = areas.sortedByDescending { it.incidencePer100k }

    val hotspots: List<Area> get() = areasByRisk.filter { it.risk >= RiskLevel.HIGH }

    /**
     * The hotspots to arm geofences against, or `null` when there is no area
     * data at all.
     *
     * An empty hotspot list and a missing breakdown look identical from the
     * outside but mean opposite things: the first says disarm, nothing is high
     * right now; the second says we cannot tell, so leave the geofences alone.
     */
    /** The largest season total, for scaling the bars in the area list. */
    val peakAreaCases: Int get() = maxOf(areas.maxOfOrNull { it.seasonCases } ?: 1, 1)

    /**
     * Division-level roll-up, folding the Dhaka city corporations back into
     * their division.
     *
     * The band is recomputed from the summed recent cases over the summed
     * population — not averaged from the members' bands. Averaging bands would
     * let Dhaka's three rows, which share one division, pull the division's
     * reading toward whichever of them happens to be smallest.
     */
    data class DivisionSummary(
        val division: Division,
        val cases: Int,
        val deaths: Int,
        val recentCases: Int,
        val populationThousands: Int,
    ) {
        val incidencePer100k: Double
            get() = if (populationThousands > 0) {
                recentCases.toDouble() / populationThousands * 100
            } else {
                0.0
            }

        val risk: RiskLevel get() = RiskLevel.forIncidence(incidencePer100k)
    }

    val divisionSummaries: List<DivisionSummary>
        get() = Division.entries.map { division ->
            val members = areas.filter { it.division == division }
            DivisionSummary(
                division = division,
                cases = members.sumOf { it.seasonCases },
                deaths = members.sumOf { it.seasonDeaths },
                recentCases = members.sumOf { it.recentCases },
                populationThousands = members.sumOf { it.populationThousands },
            )
        }.sortedByDescending { it.cases }

    val hotspotsToMonitor: List<Area>? get() = if (areas.isEmpty()) null else hotspots

    /** Areas at or above a band the reader chose, worst first. */
    fun areasAtOrAbove(threshold: RiskLevel): List<Area> =
        areasByRisk.filter { it.risk.ordinal >= threshold.ordinal }

    /**
     * The areas to watch for entry, or null when the feed carries no area
     * breakdown at all.
     *
     * Keyed on the reader's own threshold rather than on [hotspots]. The entry
     * warning used to arm against High and above regardless of what the reader
     * had chosen, which made the setting a lie in one direction — someone who
     * asked to hear about Moderate areas was never told — and left the feature
     * doing nothing at all in a season where no division is above Moderate.
     */
    fun areasToMonitor(threshold: RiskLevel): List<Area>? =
        if (areas.isEmpty()) null else areasAtOrAbove(threshold)
}
