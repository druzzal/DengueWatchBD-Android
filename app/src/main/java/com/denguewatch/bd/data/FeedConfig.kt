package com.denguewatch.bd.data

/**
 * Where the app gets its figures, and when it decides they are too old to show
 * without comment.
 */
object FeedConfig {

    /**
     * The small document: headline figures and the DGHS report date. Polled to
     * decide whether the large one is worth downloading.
     */
    const val SUMMARY_ENDPOINT =
        "https://druzzal.github.io/dengue-bd-dashboard/data/summary.json"

    /** The full dataset: every chart series and table. */
    const val LATEST_ENDPOINT =
        "https://druzzal.github.io/dengue-bd-dashboard/data/latest.json"

    /**
     * The DGHS page the feed is scraped from. Shown in About so a reader can
     * check the figures at the source. The app never fetches this itself.
     */
    const val SOURCE_URL = "https://dashboard.dghs.gov.bd/pages/heoc_dengue_v1.php"

    /** The feed's own dashboard, for anyone who wants to see the pipeline. */
    const val FEED_HOME_URL = "https://druzzal.github.io/dengue-bd-dashboard/"

    /**
     * The privacy policy, published from its own repository.
     *
     * Play requires a public policy URL for any app requesting location, and it
     * has to be reachable from inside the app as well as from the store listing
     * — a reader deciding whether to grant background location is exactly the
     * person who should be able to find it without leaving for a browser search.
     *
     * Deliberately not hosted alongside the data feed: this URL is compiled into
     * the app and cited on the store listing, so it should not be able to break
     * because the pipeline's repository was renamed or restructured.
     */
    const val PRIVACY_POLICY_URL = "https://druzzal.github.io/denguewatch-bd-privacy/"

    /** The copy shipped in assets, so the app opens with figures on first run. */
    const val BUNDLED_ASSET = "dengue-feed.json"

    /** Where a downloaded document is cached, relative to the app's files dir. */
    const val CACHED_FILENAME = "latest-feed.json"

    /**
     * How long to leave the cheap probe alone when the app returns to the front.
     *
     * This gates `summary.json` — about 350 bytes — and nothing else. The
     * expensive download is already held back by its own gate: the full
     * document is fetched only when the probe reports a report date newer than
     * the one on screen. So the cost of checking often is a few hundred bytes,
     * and the cost of checking rarely is a reader looking at yesterday's
     * figures.
     *
     * This was six hours, which defeated the point of having a cheap probe at
     * all. DGHS publishes once a day but not at a fixed hour — over two weeks
     * the report landed anywhere from 11:05 to 17:36 UTC — so a six-hour window
     * reliably straddled publication. Reproduced on 7 October by planting the
     * previous day's feed in the cache: the app kept serving it across relaunches
     * and picked up the new report the moment the window was cleared.
     *
     * Fifteen minutes is short enough that a new report is on screen about as
     * soon as it exists, and long enough that switching apps back and forth does
     * not talk to the server each time.
     */
    const val MINIMUM_PROBE_INTERVAL_MILLIS = 15L * 60 * 1000

    /**
     * Past this age the app says the figures are behind rather than presenting
     * them as current. DGHS publishes daily, so three quiet days means
     * something is wrong upstream, not that nothing happened.
     */
    const val STALENESS_THRESHOLD_MILLIS = 3L * 24 * 60 * 60 * 1000

    /**
     * Beyond this the figures stop being "a bit behind" and become a poor basis
     * for a decision, so the app says so more firmly.
     */
    const val OUTDATED_THRESHOLD_MILLIS = 7L * 24 * 60 * 60 * 1000

    /**
     * Schema versions this build knows how to read. A feed that moves past this
     * is refused rather than half-decoded into wrong numbers.
     */
    val SUPPORTED_SCHEMA_VERSIONS = 1..1

    const val NETWORK_TIMEOUT_MILLIS = 15_000
}
