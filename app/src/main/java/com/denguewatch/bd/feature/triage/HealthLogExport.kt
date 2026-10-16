package com.denguewatch.bd.feature.triage

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.denguewatch.bd.AppState
import com.denguewatch.bd.i18n.Localization
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.model.HealthLog
import com.denguewatch.bd.model.HealthLogDay
import com.denguewatch.bd.model.TemperatureUnit
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hands the health log — the whole of it, or one day — to the share sheet as a
 * finished PDF.
 *
 * The document is laid out off the main thread and only then offered, so the
 * share sheet opens with the file, its name and its preview already there,
 * rather than freezing the screen while a month of entries is drawn.
 */
object HealthLogExport {

    /** What one export needs from the stores, read on the main thread. */
    class Request(
        val days: List<HealthLogDay>,
        val numbers: Map<Long, Int>,
        val names: Map<Long, String>,
        val unit: TemperatureUnit,
    )

    /**
     * The whole log, or only [dayMillis]. Days keep the number they have in
     * the whole log either way, so the third day of an illness is "Day 3" on
     * its own page as well.
     */
    fun request(state: AppState, dayMillis: Long? = null): Request? {
        val all = state.healthLogDays
        val ordered = if (state.preferences.logOldestFirst) all.reversed() else all
        val chosen = if (dayMillis == null) ordered else ordered.filter { it.dateMillis == dayMillis }
        if (chosen.isEmpty()) return null
        return Request(
            days = chosen,
            numbers = HealthLog.numbers(all),
            names = chosen.mapNotNull { day -> state.logNames.name(day.dateMillis)?.let { day.dateMillis to it } }
                .toMap(),
            unit = state.preferences.temperatureUnit,
        )
    }

    suspend fun share(context: Context, loc: Localization, request: Request) {
        val file = withContext(Dispatchers.Default) {
            HealthLogPdf.write(context, request.days, loc, request.unit, request.names, request.numbers)
        }
        // The file lives in the app's own cache, which is the only place its
        // FileProvider will share from.
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    // So the share sheet shows the file it is sending, not an
                    // unlabelled attachment.
                    clipData = ClipData.newRawUri(file.name, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                loc.t(if (request.days.size == 1) "log.exportDay" else "log.export"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun launch(scope: CoroutineScope, context: Context, loc: Localization, request: Request?,
               onDone: () -> Unit = {}) {
        if (request == null) { onDone(); return }
        scope.launch {
            try {
                share(context, loc, request)
            } finally {
                onDone()
            }
        }
    }
}

/**
 * The share action in the top bar: the whole log from My log, one day from a
 * day's own screen. Shows that it is working while the pages are drawn, rather
 * than looking like the tap was missed.
 */
@Composable
fun ExportLogButton(state: AppState, dayMillis: Long?, modifier: Modifier = Modifier) {
    val loc = LocalLocalization.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    IconButton(
        onClick = {
            if (busy) return@IconButton
            busy = true
            HealthLogExport.launch(scope, context, loc, HealthLogExport.request(state, dayMillis)) {
                busy = false
            }
        },
        enabled = !busy,
        modifier = modifier,
    ) {
        if (busy) {
            CircularProgressIndicator(
                color = Dw.colors.accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Icon(
                DwIcons.share,
                contentDescription = loc.t(if (dayMillis == null) "log.export" else "log.exportDay"),
            )
        }
    }
}
