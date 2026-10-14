package com.denguewatch.bd.feature.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.content.FileProvider
import com.denguewatch.bd.feature.shared.SecondaryActionButton
import com.denguewatch.bd.i18n.LocalLocalization
import com.denguewatch.bd.i18n.labelKey
import com.denguewatch.bd.model.Area
import com.denguewatch.bd.ui.theme.Dw
import com.denguewatch.bd.ui.theme.DwIcons
import com.denguewatch.bd.ui.theme.LocalTypeScale
import com.denguewatch.bd.ui.theme.Space
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * The share action for one area.
 *
 * The card is composed off-screen into a [androidx.compose.ui.graphics.layer.GraphicsLayer]
 * and rasterised on demand, rather than kept alive in state: it is a one-off
 * artefact, and holding a 1080×1350 bitmap for a screen the reader may never
 * share from is wasteful.
 *
 * It is drawn at a density of 1, so the dp sizes in [ShareCardView] are pixels
 * and the output is exactly the size that view declares — independent of the
 * sender's screen density or font-size setting.
 */
@Composable
fun ShareAreaButton(
    area: Area,
    lastUpdatedMillis: Long?,
    modifier: Modifier = Modifier,
    /**
     * Draw as a bar icon rather than a full-width row.
     *
     * iOS puts this in the navigation bar, so on Android it sat as a second
     * stacked row under "Set as my area" and read as another line of the card
     * rather than as the action it is. In the bar it is the same action in the
     * same place on both platforms.
     */
    asIcon: Boolean = false,
) {
    val loc = LocalLocalization.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val content = AreaShareCard.content(area, lastUpdatedMillis, loc)
    val tint = Dw.colors.riskTint(area.risk)

    Box(modifier) {
        // The card is composed, measured at its own full size and recorded into
        // the layer — but reports a size of zero to its parent, so it occupies
        // no room on the screen it is shared from. Measuring it to zero instead
        // would lay it out to nothing and record a blank bitmap.
        //
        // `drawWithContent` records without calling `drawContent` outside the
        // layer, so the card is never painted: the reader sees the system share
        // sheet, not the card flashing on screen first.
        Box(
            Modifier
                .layout { measurable, _ ->
                    val placeable = measurable.measure(Constraints())
                    layout(0, 0) { placeable.place(0, 0) }
                }
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                },
        ) {
            // A fixed-size image: neither the reader's font size nor the
            // screen-size type scale may change what gets shared.
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = 1f),
                LocalTypeScale provides 1f,
            ) {
                ShareCardView(
                    content = content,
                    riskTint = tint,
                    appName = loc.t("share.appName"),
                    tagline = loc.t("share.tagline"),
                )
            }
        }

        val onShare: () -> Unit = {
            scope.launch {
                val bitmap = layer.toImageBitmap().asAndroidBitmap()
                share(context, bitmap, content.areaName)
            }
        }
        if (asIcon) {
            IconButton(onClick = onShare) {
                Icon(DwIcons.external, contentDescription = loc.t("share.button"))
            }
        } else {
            SecondaryActionButton(
                title = loc.t("share.button"),
                icon = DwIcons.external,
                onClick = onShare,
            )
        }
    }
}

/**
 * Hands the PNG to the system share sheet.
 *
 * Written into `cacheDir/shared` and served through the app's FileProvider: a
 * `file://` URI would be rejected outright by any app that receives it.
 */
private fun share(context: Context, bitmap: Bitmap, areaName: String) {
    val directory = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(directory, "denguewatch-area.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, areaName)
        // As ClipData as well as the extra: that is what lets the system share
        // sheet read the card and show it, rather than "Sharing image" over an
        // empty box. The receiving app was granted access either way.
        clipData = ClipData.newRawUri(areaName, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, areaName))
}
