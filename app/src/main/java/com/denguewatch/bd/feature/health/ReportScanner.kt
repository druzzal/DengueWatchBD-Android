package com.denguewatch.bd.feature.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.denguewatch.bd.model.ReportLayout
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * The ways of reading a lab report, and the recognition behind them.
 *
 * Everything happens on the device. The image is handed to ML Kit's bundled
 * text recogniser, the text comes back, and any capture is deleted — nothing
 * is uploaded, and nothing is written where another app could read it.
 *
 * Only the reading lives here. What the words mean is
 * [com.denguewatch.bd.model.LabReportParser]'s job, which is pure and tested;
 * this half needs a camera and a device and so cannot be.
 */
class ReportScanner internal constructor(
    private val onScan: () -> Unit,
    private val onPick: () -> Unit,
    private val onPickFile: () -> Unit,
) {
    /** Photograph a report now. */
    fun scan() = onScan()

    /** Choose a photo of one already taken. */
    fun pickImage() = onPick()

    /**
     * Choose a file — an image, or the PDF a diagnostic centre emailed.
     *
     * The screen's own note has always promised "a photo or PDF in Files", as
     * iOS offers, but only the photo picker was wired up: a report that arrived
     * as a PDF could not be read at all.
     */
    fun pickFile() = onPickFile()
}

@Composable
fun rememberReportScanner(
    onStarted: () -> Unit,
    onText: (String) -> Unit,
    onFailed: () -> Unit,
): ReportScanner {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val started by rememberUpdatedState(onStarted)
    val text by rememberUpdatedState(onText)
    val failed by rememberUpdatedState(onFailed)

    // Where a capture lands: the app's own cache, never shared storage. The
    // file is deleted as soon as it has been read.
    val captureFile = remember {
        File(context.cacheDir, "reports").apply { mkdirs() }
            .let { File(it, "report-capture.jpg") }
    }
    val captureUri = remember {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", captureFile)
    }

    fun read(uri: Uri, deleteAfter: Boolean) {
        started()
        scope.launch {
            val fragments = try {
                if (context.contentResolver.getType(uri) == PDF_TYPE) {
                    pdfFragments(context, uri)
                } else {
                    recognise(InputImage.fromFilePath(context, uri), offset = 0)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Usually the model has not finished loading, or the file is not
                // one the platform can open. Saying so beats a silent no-op, and
                // the fields below still take the numbers by hand.
                null
            } finally {
                if (deleteAfter) captureFile.delete()
            }
            if (fragments == null) failed() else text(ReportLayout.rows(fragments))
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (taken) read(captureUri, deleteAfter = true) else captureFile.delete()
    }

    // The photo picker needs no permission of its own: the user chooses the one
    // image, and the app never sees the rest of the gallery.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { read(it, deleteAfter = false) } }

    // The system file picker, which likewise grants access to the one file.
    val files = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { read(it, deleteAfter = false) } }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) camera.launch(captureUri) else failed() }

    return remember(captureUri) {
        ReportScanner(
            onScan = {
                val allowed = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (allowed) camera.launch(captureUri)
                else cameraPermission.launch(Manifest.permission.CAMERA)
            },
            onPick = {
                picker.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            },
            onPickFile = { files.launch(arrayOf("image/*", PDF_TYPE)) },
        )
    }
}

private const val PDF_TYPE = "application/pdf"

/**
 * Enough for any blood report, and a cap on the work: a 40-page PDF attached
 * by mistake should not keep the phone busy for a minute.
 */
private const val MAX_PDF_PAGES = 5

/** About 200 dpi on an A4 page — sharp enough for the recogniser, light on memory. */
private const val PDF_RENDER_WIDTH = 1650

/**
 * Each page of a PDF drawn to a bitmap and read like a photo.
 *
 * Pages are stacked by offsetting each page's rows below the last, so
 * [ReportLayout] keeps every row on its own page instead of merging the top
 * line of page two into the top line of page one.
 */
private suspend fun pdfFragments(context: Context, uri: Uri): List<ReportLayout.Fragment> =
    withContext(Dispatchers.IO) {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("No file descriptor")
        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val fragments = mutableListOf<ReportLayout.Fragment>()
                var offset = 0
                for (index in 0 until minOf(renderer.pageCount, MAX_PDF_PAGES)) {
                    renderer.openPage(index).use { page ->
                        val scale = PDF_RENDER_WIDTH.toFloat() / page.width
                        val bitmap = Bitmap.createBitmap(
                            PDF_RENDER_WIDTH,
                            (page.height * scale).toInt().coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888,
                        )
                        // PDF pages are transparent where nothing is drawn; the
                        // recogniser wants dark text on white.
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        fragments += recognise(InputImage.fromBitmap(bitmap, 0), offset)
                        offset += bitmap.height
                        bitmap.recycle()
                    }
                }
                fragments
            }
        }
    }

/** The recognised lines of one image, shifted down by [offset]. */
private suspend fun recognise(image: InputImage, offset: Int): List<ReportLayout.Fragment> {
    val result = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        .process(image)
        .await()
    // Not result.text: ML Kit orders that by block, which on a two-column
    // report puts every label before every value. ReportLayout puts the rows
    // back first.
    return result.textBlocks
        .flatMap { it.lines }
        .mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            ReportLayout.Fragment(
                text = line.text,
                top = box.top + offset,
                bottom = box.bottom + offset,
                left = box.left,
            )
        }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}

/** Whether the device has a camera at all; tablets and emulators may not. */
fun hasCamera(context: Context): Boolean =
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
