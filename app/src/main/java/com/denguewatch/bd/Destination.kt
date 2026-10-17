package com.denguewatch.bd

import androidx.compose.runtime.saveable.Saver
import com.denguewatch.bd.feature.triage.TriageEngine
import com.denguewatch.bd.i18n.Localization
import com.denguewatch.bd.i18n.displayName
import com.denguewatch.bd.model.HealthLog

/**
 * Which screen is in front.
 *
 * A sealed hierarchy rather than a navigation graph with string routes: there
 * are five tabs and four pushed screens, every one of which carries a typed
 * argument or none at all, and a route table would turn those arguments back
 * into strings for no gain.
 */
sealed interface Destination {
    /**
     * Where Back leads. Every screen is opened from the tabs except a log day,
     * which is opened from the log and goes back there — sending it to the
     * tabs skipped the list the reader had just picked it from — and the area
     * picker, which belongs to the alerts screen.
     */
    val parent: Destination get() = Tabs

    data object Tabs : Destination
    data class AreaDetail(val areaCode: String) : Destination
    data object SymptomCheck : Destination
    data object RecordVitals : Destination
    data object AddLabReport : Destination
    data object AddFluid : Destination
    data object CheckHistory : Destination
    data object CaseLog : Destination
    data class LogDay(val dayMillis: Long) : Destination {
        override val parent: Destination get() = CaseLog
    }
    data object TriageResult : Destination
    data object About : Destination
    data object AlertSettings : Destination
    data object AreaPicker : Destination {
        override val parent: Destination get() = AlertSettings
    }
}

/**
 * The top bar's title for this screen: the tab's name, the area's, or the
 * day's own name for a day of the log.
 */
fun Destination.title(state: AppState, loc: Localization): String = when (val current = this) {
    is Destination.Tabs -> when (state.selectedTab) {
        Tab.HOME -> loc.t("dash.title")
        Tab.MAP -> loc.t("map.title")
        Tab.HEALTH -> loc.t("tab.health")
        Tab.CARE -> loc.t("care.title")
        Tab.PREVENT -> loc.t("prevent.title")
    }
    is Destination.AreaDetail ->
        state.store.area(current.areaCode)?.displayName(loc.language) ?: ""
    Destination.SymptomCheck -> loc.t("check.title")
    Destination.RecordVitals -> loc.t("vital.record")
    Destination.AddLabReport -> loc.t("lab.add")
    Destination.AddFluid -> loc.t("fluid.add.title")
    Destination.CheckHistory -> loc.t("check.history.title")
    Destination.CaseLog -> loc.t("log.title")
    // The day's own name, so the reader knows which record they opened.
    is Destination.LogDay -> {
        val days = state.healthLogDays
        val numbers = HealthLog.numbers(days)
        state.logNames.name(current.dayMillis)
            ?: numbers[current.dayMillis]?.let { loc.t("log.defaultName", loc.num(it)) }
            ?: loc.dayMonth(current.dayMillis)
    }
    Destination.TriageResult -> loc.t("result.title")
    Destination.About -> loc.t("about.title")
    Destination.AlertSettings -> loc.t("alerts.title")
    Destination.AreaPicker -> loc.t("area.picker.title")
}

/**
 * Writes a destination as one short string, so the screen the reader was on
 * survives Android stopping the app in the background — which it does to make
 * room for the camera, exactly while a lab report is being photographed.
 *
 * Keys are spelled out rather than taken from class names, which R8 renames.
 * Anything unreadable restores to the tabs.
 */
val DestinationSaver: Saver<Destination, String> = Saver(
    save = { destination ->
        when (destination) {
            Destination.Tabs -> "tabs"
            is Destination.AreaDetail -> "area:" + destination.areaCode
            Destination.SymptomCheck -> "symptomCheck"
            Destination.RecordVitals -> "recordVitals"
            Destination.AddLabReport -> "addLabReport"
            Destination.AddFluid -> "addFluid"
            Destination.CheckHistory -> "checkHistory"
            Destination.CaseLog -> "caseLog"
            is Destination.LogDay -> "logDay:" + destination.dayMillis
            Destination.TriageResult -> "triageResult"
            Destination.About -> "about"
            Destination.AlertSettings -> "alertSettings"
            Destination.AreaPicker -> "areaPicker"
        }
    },
    restore = { key -> restoreDestination(key) },
)

internal fun restoreDestination(key: String): Destination = when {
    key.startsWith("area:") -> Destination.AreaDetail(key.removePrefix("area:"))
    key.startsWith("logDay:") ->
        key.removePrefix("logDay:").toLongOrNull()?.let(Destination::LogDay) ?: Destination.Tabs
    else -> when (key) {
        "symptomCheck" -> Destination.SymptomCheck
        "recordVitals" -> Destination.RecordVitals
        "addLabReport" -> Destination.AddLabReport
        "addFluid" -> Destination.AddFluid
        "checkHistory" -> Destination.CheckHistory
        "caseLog" -> Destination.CaseLog
        "triageResult" -> Destination.TriageResult
        "about" -> Destination.About
        "alertSettings" -> Destination.AlertSettings
        "areaPicker" -> Destination.AreaPicker
        else -> Destination.Tabs
    }
}

/** The symptoms ticked so far in a check, kept across the same stop. */
val SymptomSetSaver: Saver<Set<String>, ArrayList<String>> = Saver(
    save = { ArrayList(it.sorted()) },
    restore = { it.toSet() },
)

/** The risk questions answered so far. The day of fever is saved on its own. */
val TriageContextSaver: Saver<TriageEngine.Context, BooleanArray> = Saver(
    save = {
        booleanArrayOf(it.isPregnant, it.isUnderFive, it.hasChronicCondition, it.hadDengueBefore, it.isOverSixty)
    },
    restore = {
        TriageEngine.Context(
            isPregnant = it.getOrElse(0) { false },
            isUnderFive = it.getOrElse(1) { false },
            hasChronicCondition = it.getOrElse(2) { false },
            hadDengueBefore = it.getOrElse(3) { false },
            isOverSixty = it.getOrElse(4) { false },
        )
    },
)
