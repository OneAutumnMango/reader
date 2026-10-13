package dev.reader.app.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import dev.reader.core.reader.TapAction
import dev.reader.core.reader.TapZoneConfig
import dev.reader.parser.pdf.PdfPageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Fixed-page PDF viewer: each page is rendered to a bitmap preserving the original layout. */
@Composable
fun PdfReaderScreen(
    file: File,
    tapZones: TapZoneConfig,
    showTapZoneLabels: Boolean = false,
    onTapAction: (TapAction) -> Unit = {},
    onPageChanged: (index: Int, pageCount: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    var renderer by remember(file) { mutableStateOf<PdfPageRenderer?>(null) }

    DisposableEffect(file) {
        val opened = runCatching { PdfPageRenderer(file) }.getOrNull()
        renderer = opened
        onDispose { opened?.close() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val pageRenderer = renderer
        TapZoneOverlay(tapZones, showTapZoneLabels, onTapAction, Modifier.fillMaxSize()) {
            if (pageRenderer != null) {
                ImagePager(
                    pageCount = pageRenderer.pageCount,
                    onPageChanged = { onPageChanged(it, pageRenderer.pageCount) },
                ) { index ->
                    PdfPageImage(pageRenderer, index, Modifier.fillMaxSize())
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun PdfPageImage(renderer: PdfPageRenderer, index: Int, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }
        val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, index, widthPx, heightPx) {
            value = withContext(Dispatchers.IO) {
                runCatching { renderer.renderPage(index, widthPx, heightPx).asImageBitmap() }.getOrNull()
            }
        }
        val loaded = bitmap
        if (loaded != null) {
            Image(bitmap = loaded, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}
