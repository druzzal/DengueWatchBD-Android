package com.denguewatch.bd.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.ui.graphics.vector.ImageVector
import com.denguewatch.bd.model.RiskLevel

/**
 * The app's icon vocabulary.
 *
 * Named for the job rather than for the glyph, so the whole set can be
 * re-pointed at a different family without touching a screen. Every one has a
 * counterpart in the iOS build; where Material has no equivalent of an SF
 * Symbol, the nearest icon with the same *meaning* is used rather than the
 * nearest one with the same shape.
 */
object DwIcons {
    // The WHO care plan's three levels, drawn rather than lettered: the 2025
    // guideline has no A / B / C. A shape as well as a colour, so the level
    // still reads in greyscale.
    val careHome: ImageVector = Icons.Filled.Home
    val careHospital: ImageVector = Icons.Filled.LocalHospital
    val careEmergency: ImageVector = Icons.Filled.Dangerous

    // The fluid log: a drink, and urine passed.
    val drink: ImageVector = Icons.Filled.LocalCafe
    val urine: ImageVector = Icons.Filled.WaterDrop
    val add: ImageVector = Icons.Filled.AddCircle

    /** Past symptom checks. */
    val history: ImageVector = Icons.Filled.History

    // Risk scale. Four distinct silhouettes, so the shape carries the band as
    // well as the colour does — that pairing is the whole reason risk is never
    // shown as colour alone.
    val riskLow: ImageVector = Icons.Filled.VerifiedUser
    val riskModerate: ImageVector = Icons.Filled.Error
    val riskHigh: ImageVector = Icons.Filled.Warning
    val riskSevere: ImageVector = Icons.Filled.Dangerous

    // Tabs
    // Tab glyphs. Three are drawn in DwTabIcons because Material has no
    // stethoscope, no half-filled shield, and nothing matching iOS's
    // chart.line.uptrend.xyaxis without sparkles attached.
    val home: ImageVector = DwTabIcons_Trend
    val map: ImageVector = Icons.Outlined.Map
    val check: ImageVector = DwTabIcons_Stethoscope

    /** The My health tab. iOS uses `heart.text.square` here. */
    val health: ImageVector = DwTabIcons_HealthCard
    val care: ImageVector = Icons.Filled.MedicalServices
    val prevent: ImageVector = DwTabIcons_Shield

    // Navigation and chrome
    val chevronRight: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight
    val chevronDown: ImageVector = Icons.Filled.KeyboardArrowDown
    val back: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    val close: ImageVector = Icons.Filled.Close
    val info: ImageVector = Icons.Outlined.Info

    /** Leaves the app — used on rows that open a browser. */
    val external: ImageVector = Icons.AutoMirrored.Filled.OpenInNew
    val list: ImageVector = Icons.AutoMirrored.Filled.List
    val search: ImageVector = Icons.Filled.Search
    val filter: ImageVector = Icons.Filled.FilterList
    val check2: ImageVector = Icons.Filled.Check

    // The two answers to "do you have a fever?". Material has no high/low pair
    // to match iOS's thermometer.high and thermometer.low, so the weight of the
    // same glyph carries it: solid for yes, outlined for no.
    val temperature: ImageVector = Icons.Filled.DeviceThermostat
    val temperatureLow: ImageVector = Icons.Outlined.DeviceThermostat
    /** The unticked half of a checkable row. */
    val circleOutline: ImageVector = Icons.Outlined.Circle
    val refresh: ImageVector = Icons.Filled.Refresh
    val save: ImageVector = Icons.Filled.Save
    val log: ImageVector = Icons.Filled.ContentPaste

    // My log's own controls.
    val delete: ImageVector = Icons.Filled.Delete
    val sort: ImageVector = Icons.Filled.SwapVert
    val more: ImageVector = Icons.Filled.MoreVert
    // The health log as a document: the platform's own share glyph in the bar,
    // where iOS has square.and.arrow.up, and a PDF mark for one day's export.
    val share: ImageVector = Icons.Filled.Share
    val pdf: ImageVector = Icons.Filled.PictureAsPdf

    /** Marks the note saying a record never leaves the phone. */
    val lock: ImageVector = Icons.Filled.Lock

    /** Photograph a lab report. */
    val scan: ImageVector = Icons.Filled.DocumentScanner

    /** Choose a photo of a lab report that already exists. */
    val upload: ImageVector = Icons.Filled.UploadFile

    // Trend
    val trendUp: ImageVector = Icons.AutoMirrored.Filled.TrendingUp
    val trendDown: ImageVector = Icons.AutoMirrored.Filled.TrendingDown
    val trendFlat: ImageVector = Icons.AutoMirrored.Filled.TrendingFlat

    // Provenance
    val official: ImageVector = Icons.Filled.Verified
    val guidance: ImageVector = Icons.AutoMirrored.Filled.MenuBook

    // Sync status
    val syncing: ImageVector = Icons.Filled.Sync
    val upToDate: ImageVector = Icons.Filled.CheckCircle

    /**
     * A plain tick, for an item in a list of things to do. Distinct from
     * [check], which is the stethoscope the symptom checker is named by — one
     * marks a step, the other opens a screen.
     */
    val tick: ImageVector = Icons.Filled.CheckCircle
    val offline: ImageVector = Icons.Filled.WifiOff
    val bundled: ImageVector = Icons.Filled.Inventory2
    val unreachable: ImageVector = Icons.Filled.CloudOff

    // Care and location
    val phone: ImageVector = Icons.Filled.Phone
    val directions: ImageVector = Icons.Filled.Directions
    val hospital: ImageVector = Icons.Filled.LocalHospital
    val myLocation: ImageVector = Icons.Filled.MyLocation
    val noLocation: ImageVector = Icons.Filled.LocationOff
    val alerts: ImageVector = Icons.Filled.NotificationsActive

    // Prevention topics
    val breeding: ImageVector = Icons.Filled.WaterDrop
    val bites: ImageVector = Icons.Filled.Shield
    val patientCare: ImageVector = Icons.Filled.Hotel
    val community: ImageVector = Icons.Filled.Apartment
    val testing: ImageVector = Icons.Filled.LocalHospital

    /**
     * The small symbol beside a prevention task.
     *
     * Decorative — the task's own words carry the meaning, and these are hidden
     * from screen readers. They exist so the five rows can be told apart at a
     * glance by someone who ticks the same list every day.
     */
    fun forTask(id: String): ImageVector = when (id) {
        "standingWater" -> breeding
        "plantTrays" -> Icons.Filled.Eco
        "containers" -> Icons.Filled.Delete
        "storedWater" -> Icons.Filled.Inventory2
        else -> bites
    }

    fun forTopic(id: String): ImageVector = when (id) {
        "breeding" -> breeding
        "bites" -> bites
        "care" -> patientCare
        "community" -> community
        else -> testing
    }
}

/** The icon half of the icon + label pairing risk colour always requires. */
val RiskLevel.icon: ImageVector
    get() = when (this) {
        RiskLevel.LOW -> DwIcons.riskLow
        RiskLevel.MODERATE -> DwIcons.riskModerate
        RiskLevel.HIGH -> DwIcons.riskHigh
        RiskLevel.SEVERE -> DwIcons.riskSevere
    }
