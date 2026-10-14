package com.denguewatch.bd

import com.denguewatch.bd.data.FeedConfig
import com.denguewatch.bd.data.FeedSync
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the app decides to ask DGHS whether there is a newer report.
 *
 * This policy once skipped the check for six hours after any successful sync.
 * The check costs about 350 bytes — `summary.json` — and the expensive download
 * has its own gate behind it, so six hours bought almost nothing and cost a
 * reader the thing the app is for.
 *
 * Reproduced on 7 October 2026 by planting the previous day's feed in the
 * cache: the app kept serving it across relaunches and picked up the new report
 * the moment the window was cleared. Over the preceding fortnight the DGHS
 * report landed anywhere between 11:05 and 17:36 UTC, so a six-hour window did
 * not merely risk straddling publication — it was wider than the spread.
 */
class FeedSyncPolicyTest {

    private val now = 1_791_000_000_000L

    private fun probes(
        afterMillis: Long,
        force: Boolean = false,
        hasCachedPayload: Boolean = true,
    ) = FeedSync.shouldProbe(
        force = force,
        lastSuccess = now - afterMillis,
        hasCachedPayload = hasCachedPayload,
        now = now,
    )

    @Test
    fun `the probe is skipped immediately after a successful check`() {
        assertFalse(probes(0))
        assertFalse(probes(60_000))
    }

    @Test
    fun `the probe runs once the floor has passed`() {
        assertTrue(probes(FeedConfig.MINIMUM_PROBE_INTERVAL_MILLIS))
        assertTrue(probes(FeedConfig.MINIMUM_PROBE_INTERVAL_MILLIS + 1))
    }

    /** The regression this exists for. */
    @Test
    fun `an app opened an hour later asks again`() {
        assertTrue(probes(60 * 60 * 1000L))
    }

    /**
     * The floor has to be shorter than the spread of DGHS publication times, or
     * it can sit across the moment the report lands — which is what six hours
     * did.
     */
    @Test
    fun `the floor is shorter than the spread of publication times`() {
        val observedSpread = (6.5 * 60 * 60 * 1000).toLong()   // 11:05 to 17:36 UTC
        assertTrue(FeedConfig.MINIMUM_PROBE_INTERVAL_MILLIS < observedSpread / 4)
    }

    @Test
    fun `the floor is long enough to survive app switching`() {
        assertTrue(FeedConfig.MINIMUM_PROBE_INTERVAL_MILLIS >= 5 * 60 * 1000L)
    }

    @Test
    fun `pull to refresh is never throttled`() {
        assertTrue(probes(0, force = true))
    }

    @Test
    fun `a check always runs when there is no cached payload to fall back on`() {
        assertTrue(probes(0, hasCachedPayload = false))
    }

    @Test
    fun `the first ever launch checks`() {
        assertTrue(
            FeedSync.shouldProbe(force = false, lastSuccess = null, hasCachedPayload = true, now = now),
        )
    }

    /**
     * A clock that has gone backwards leaves a "last success" in the future.
     * That must not lock the app out of checking until the clock catches up.
     */
    @Test
    fun `a future timestamp does not lock out the check`() {
        assertTrue(probes(-(24 * 60 * 60 * 1000L)))
    }
}
