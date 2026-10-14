package dev.reader.app.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import dev.reader.core.reader.ReaderSettings
import dev.reader.core.reader.TapAction
import dev.reader.parser.pdf.PdfPageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private const val A4_ASPECT_RATIO = 0.707f

/** Fixed-page PDF viewer: each page is rendered to a bitmap preserving the original layout. */
@Composable
fun PdfReaderScreen(
    file: File,
    settings: ReaderSettings,
    modifier: Modifier = Modifier,
    showTapZoneLabels: Boolean = false,
    initialPositionFraction: Float = 0f,
    onTapAction: (TapAction) -> Unit = {},
    onPageChanged: (index: Int, pageCount: Int) -> Unit = { _, _ -> },
) {
    var renderer by remember(file) { mutableStateOf<PdfPageRenderer?>(null) }
    var openFailed by remember(file) { mutableStateOf(false) }

    DisposableEffect(file) {
        val opened = runCatching { PdfPageRenderer(file) }
        renderer = opened.getOrNull()
        openFailed = opened.isFailure
        onDispose { opened.getOrNull()?.close() }
    }

    KeepScreenAwakeEffect(settings.keepScreenAwake)

    Box(modifier = modifier.fillMaxSize()) {
        val pageRenderer = renderer
        TapZoneOverlay(settings.tapZones, showTapZoneLabels, onTapAction, Modifier.fillMaxSize()) {
            when {
                pageRenderer != null -> ImagePager(
                    pageCount = pageRenderer.pageCount,
                    readingMode = settings.readingMode,
                    initialPage = (initialPositionFraction * pageRenderer.pageCount).roundToInt(),
                    zoomEnabled = settings.zoomEnabled,
                    onPageChanged = { onPageChanged(it, pageRenderer.pageCount) },
                ) { index, continuous ->
                    PdfPageImage(pageRenderer, index, continuous)
                }
                openFailed -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Couldn't open this PDF. It may be encrypted or corrupted.")
                }
                else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun PdfPageImage(renderer: PdfPageRenderer, index: Int, continuous: Boolean) {
    val ratio by produceState<Float?>(null, renderer, index) {
        value = withContext(Dispatchers.IO) { runCatching { renderer.aspectRatio(index) }.getOrNull() }
            ?: A4_ASPECT_RATIO
    }
    val pageModifier = if (continuous) {
        Modifier.fillMaxWidth().aspectRatio(ratio ?: A4_ASPECT_RATIO)
    } else {
        Modifier.fillMaxSize()
    }

    BoxWithConstraints(modifier = pageModifier, contentAlignment = Alignment.Center) {
        val pageRatio = ratio
        if (pageRatio == null) {
            CircularProgressIndicator()
            return@BoxWithConstraints
        }
        val density = LocalDensity.current
        val boxWidth = with(density) { maxWidth.toPx() }
        val boxHeight = with(density) { maxHeight.toPx() }
        // Fit the page inside the box without stretching it.
        val widthPx = minOf(boxWidth, boxHeight * pageRatio).roundToInt().coerceAtLeast(1)
        val heightPx = (widthPx / pageRatio).roundToInt().coerceAtLeast(1)
        val bitmap by produceState<Result<ImageBitmap>?>(null, renderer, index, widthPx, heightPx) {
            value = withContext(Dispatchers.IO) {
                runCatching { renderer.renderPage(index, widthPx, heightPx).asImageBitmap() }
            }
        }
        when (val result = bitmap) {
            null -> CircularProgressIndicator()
            else -> result.fold(
                onSuccess = { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize()) },
                onFailure = { Text("Couldn't render this page.") },
            )
        }
    }
}
