package com.denguewatch.bd.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Keeps the dengue figures current.
 *
 * A refresh is triggered by three things: app launch, the app returning to the
 * foreground, and the network coming back after being unavailable. That last
 * one is the point — a reader reconnects and the numbers update without them
 * having to pull anything.
 *
 * Each refresh asks `summary.json` (~350 bytes) what DGHS's latest report date
 * is. Only when that date is newer than what is already on screen does it
 * download the full `latest.json`.
 */
class FeedSync(
    context: Context,
    private val bundled: BundledFeedService,
    private val remote: RemoteFeedService = RemoteFeedService(),
) {

    sealed interface Status {
        data object Idle : Status
        data object Syncing : Status
        /** New figures landed. */
        data class Updated(val at: Long) : Status
        /** Checked, and DGHS has published nothing newer. */
        data class UpToDate(val at: Long) : Status
        data object Offline : Status
        data class Failed(val message: String) : Status
        /** No endpoint reachable, so the bundled copy is all there is. */
        data object BundledOnly : Status
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("dengue_watch_prefs", Context.MODE_PRIVATE)
    private val connectivity =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    var status: Status by mutableStateOf(Status.Idle)
        private set
    var isOnline: Boolean by mutableStateOf(true)
        private set

    var lastSuccessfulSync: Long? by mutableStateOf(
        prefs.getLong(LAST_SYNC_KEY, 0L).takeIf { it > 0L }
    )
        private set

    private var store: DengueStore? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    /**
     * Point the sync at a store without registering a network callback.
     *
     * For the background worker, which lives for one run: it wants the fetch
     * and the freshness comparison, but a connectivity callback it can never
     * unregister would outlive it.
     */
    fun attach(store: DengueStore) {
        this.store = store
    }

    fun start(store: DengueStore, scope: CoroutineScope) {
        this.store = store
        if (callback != null) return

        isOnline = currentlyOnline()

        // Only the offline → online edge triggers a fetch, so a flapping
        // connection doesn't hammer the server.
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val wasOffline = !isOnline
                isOnline = true
                if (wasOffline) scope.launch { sync(force = false) }
            }

            override fun onLost(network: Network) {
                if (!currentlyOnline()) {
                    isOnline = false
                    status = Status.Offline
                }
            }
        }
        callback = networkCallback

        runCatching {
            connectivity.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                networkCallback,
            )
        }
    }

    fun stop() {
        callback?.let { runCatching { connectivity.unregisterNetworkCallback(it) } }
        callback = null
    }

    private fun currentlyOnline(): Boolean {
        val active = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(active) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** [force] skips the minimum-interval check — used by pull-to-refresh. */
    suspend fun sync(force: Boolean) {
        if (!isOnline) {
            status = Status.Offline
            return
        }

        val last = lastSuccessfulSync
        if (!shouldProbe(
                force = force,
                lastSuccess = last,
                hasCachedPayload = bundled.isServingDownloadedData,
            ) && last != null
        ) {
            status = Status.UpToDate(last)
            return
        }

        status = Status.Syncing
        try {
            val summary = remote.fetchSummary()
            val known = store?.meta?.lastUpdated

            // The probe's whole purpose: when DGHS has published nothing newer
            // than what is already on screen, the 40 KB document is not worth
            // a byte of the reader's data.
            val unchanged = known != null &&
                summary.lastUpdated != null &&
                summary.lastUpdated <= known &&
                bundled.isServingDownloadedData

            if (unchanged) {
                markSynced()
                status = Status.UpToDate(lastSuccessfulSync ?: System.currentTimeMillis())
                return
            }

            val (document, text) = remote.fetchLatest()
            bundled.persist(text)
            store?.apply(document, DengueStore.Source.NETWORK)
            markSynced()
            status = Status.Updated(lastSuccessfulSync ?: System.currentTimeMillis())
        } catch (cancellation: CancellationException) {
            // Leaving the screen mid-sync is not a sync failure, and swallowing
            // the cancellation here would also stop it reaching the scope.
            status = Status.Idle
            throw cancellation
        } catch (error: UnsupportedSchemaException) {
            // A feed the app cannot read is not a network failure. Say so
            // plainly rather than implying the connection is at fault.
            status = Status.BundledOnly
        } catch (error: Exception) {
            status = Status.Failed(error.message ?: "Sync failed")
        }
    }

    private fun markSynced() {
        val now = System.currentTimeMillis()
        lastSuccessfulSync = now
        prefs.edit().putLong(LAST_SYNC_KEY, now).apply()
    }

    companion object {
        private const val LAST_SYNC_KEY = "feed.lastSuccessfulSync"

        /**
         * Whether this call should talk to the server at all.
         *
         * Pure, and separate from [sync], so the policy can be tested without
         * standing up a network: this decision is the difference between a
         * reader seeing today's figures and yesterday's.
         *
         * What it gates is the 350-byte probe. The full document has its own
         * gate — it is downloaded only when the probe reports a report date
         * newer than the one on screen — so saying yes here is nearly free.
         *
         * Two questions, not one. Elapsed time alone would skip a check even
         * when the cache had gone missing, leaving the app quietly serving the
         * bundled seed despite a working connection. A skip is only safe when
         * there is actually a cached payload to fall back on.
         */
        fun shouldProbe(
            force: Boolean,
            lastSuccess: Long?,
            hasCachedPayload: Boolean,
            now: Long = System.currentTimeMillis(),
        ): Boolean {
            if (force) return true
            if (lastSuccess == null || !hasCachedPayload) return true
            val elapsed = now - lastSuccess
            // A stored time in the future means the clock moved backwards — a
            // manual change, a timezone correction on a phone that had drifted.
            // Comparing against it would hold the check off until the clock
            // caught up, which could be a day, and the reader would have no way
            // to tell why the figures had stopped moving.
            if (elapsed < 0) return true
            return elapsed >= FeedConfig.MINIMUM_PROBE_INTERVAL_MILLIS
        }
    }
}
