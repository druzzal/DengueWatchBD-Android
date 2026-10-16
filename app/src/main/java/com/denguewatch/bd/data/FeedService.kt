package com.denguewatch.bd.data

import android.content.Context
import com.denguewatch.bd.model.FeedDocument
import com.denguewatch.bd.model.FeedSummaryDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** A feed the app refuses rather than half-decodes into wrong numbers. */
class UnsupportedSchemaException(val version: Int) :
    Exception("Feed schema version $version is not supported by this build")

/**
 * Reads the feed from the two places it can come from: the copy cached from a
 * previous download, and the copy shipped in assets.
 *
 * The cached document always wins when it is present and readable, so the app
 * opens on the newest figures it has ever seen rather than on the ones frozen
 * into the build.
 */
class BundledFeedService(private val context: Context) {

    private val cacheFile: File
        get() = File(context.filesDir, FeedConfig.CACHED_FILENAME)

    /** True when [load] is serving a downloaded document rather than the asset. */
    val isServingDownloadedData: Boolean
        get() = cacheFile.exists() && cacheFile.length() > 0

    suspend fun load(): FeedDocument = withContext(Dispatchers.IO) {
        // A cached file that fails to parse is deleted rather than retried
        // forever: a truncated download would otherwise leave the app showing
        // an error every launch with a perfectly good asset sitting unused.
        if (isServingDownloadedData) {
            runCatching { decode(cacheFile.readText()) }
                .onFailure { cacheFile.delete() }
                .getOrNull()
                ?.let { return@withContext it }
        }
        decode(context.assets.open(FeedConfig.BUNDLED_ASSET).bufferedReader().use { it.readText() })
    }

    suspend fun persist(text: String) = withContext(Dispatchers.IO) {
        cacheFile.writeTextAtomically(text)
    }

    private fun decode(text: String): FeedDocument {
        val document = FeedDocument.parse(text)
        if (document.schemaVersion !in FeedConfig.SUPPORTED_SCHEMA_VERSIONS) {
            throw UnsupportedSchemaException(document.schemaVersion)
        }
        return document
    }
}

/**
 * The network half: the cheap probe and the full download.
 *
 * Plain `HttpURLConnection` rather than a client library. Two GETs of static
 * JSON is the entire networking surface of this app, and a dependency that
 * exists to make that easier would be larger than the code it replaces.
 */
class RemoteFeedService {

    suspend fun fetchSummary(): FeedSummaryDocument = withContext(Dispatchers.IO) {
        val document = FeedSummaryDocument.parse(get(FeedConfig.SUMMARY_ENDPOINT))
        if (document.schemaVersion !in FeedConfig.SUPPORTED_SCHEMA_VERSIONS) {
            throw UnsupportedSchemaException(document.schemaVersion)
        }
        document
    }

    /** Returns the raw text as well, so the caller can cache exactly what it read. */
    suspend fun fetchLatest(): Pair<FeedDocument, String> = withContext(Dispatchers.IO) {
        val text = get(FeedConfig.LATEST_ENDPOINT)
        val document = FeedDocument.parse(text)
        if (document.schemaVersion !in FeedConfig.SUPPORTED_SCHEMA_VERSIONS) {
            throw UnsupportedSchemaException(document.schemaVersion)
        }
        document to text
    }

    private fun get(endpoint: String): String {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = FeedConfig.NETWORK_TIMEOUT_MILLIS
            readTimeout = FeedConfig.NETWORK_TIMEOUT_MILLIS
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw Exception("HTTP ${connection.responseCode} from $endpoint")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * The date formats the feed uses, parsed in one place.
 *
 * Everything is read in UTC. The categories are calendar days with no time
 * zone attached, and letting the device's zone shift them would move a day's
 * cases onto the day before for anyone east of UTC — which includes every
 * reader this app has.
 */
object FeedDate {

    private val utc = TimeZone.getTimeZone("UTC")

    /** `01-Jan-26`, the daily chart category format. */
    private val dayFormat = SimpleDateFormat("dd-MMM-yy", Locale.ENGLISH).apply {
        timeZone = utc
        isLenient = false
    }

    /** `2026-09-05`, the ISO date DGHS stamps on the report. */
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).apply {
        timeZone = utc
        isLenient = false
    }

    fun day(label: String): Long? = synchronized(dayFormat) {
        runCatching { dayFormat.parse(label)?.time }.getOrNull()
    }

    fun iso(value: String): Long? = synchronized(isoFormat) {
        runCatching { isoFormat.parse(value)?.time }.getOrNull()
    }
}
