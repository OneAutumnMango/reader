package dev.reader.app.ui.reader

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import dev.reader.core.model.BookFormat
import dev.reader.core.reader.ReaderSettings
import dev.reader.core.reader.TapAction
import dev.reader.parser.comic.ComicArchiveParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private val PLACEHOLDER_HEIGHT = 480.dp

/** Comic-book page viewer: each page is an image decoded from the cbz/cbr archive. */
@Composable
fun ComicReaderScreen(
    file: File,
    format: BookFormat,
    pages: List<String>,
    settings: ReaderSettings,
    modifier: Modifier = Modifier,
    showTapZoneLabels: Boolean = false,
    initialPositionFraction: Float = 0f,
    onTapAction: (TapAction) -> Unit = {},
    onPageChanged: (index: Int, pageCount: Int) -> Unit = { _, _ -> },
) {
    KeepScreenAwakeEffect(settings.keepScreenAwake)

    Box(modifier = modifier.fillMaxSize()) {
        TapZoneOverlay(settings.tapZones, showTapZoneLabels, onTapAction, Modifier.fillMaxSize()) {
            ImagePager(
                pageCount = pages.size,
                readingMode = settings.readingMode,
                initialPage = (initialPositionFraction * pages.size).roundToInt(),
                zoomEnabled = settings.zoomEnabled,
                pageGap = settings.pageGap,
                onPageChanged = { onPageChanged(it, pages.size) },
            ) { index, continuous ->
                ComicPageImage(file, format, pages[index], continuous)
            }
        }
    }
}

@Composable
private fun ComicPageImage(file: File, format: BookFormat, pageName: String, continuous: Boolean) {
    val bitmap by produceState<ImageBitmap?>(null, file, pageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = ComicArchiveParser.readPage(file, format, pageName)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
    val loaded = bitmap
    when {
        loaded != null && continuous -> Image(
            bitmap = loaded,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().aspectRatio(loaded.width.toFloat() / loaded.height),
        )
        loaded != null -> Image(bitmap = loaded, contentDescription = null, modifier = Modifier.fillMaxSize())
        else -> Box(
            modifier = if (continuous) Modifier.fillMaxWidth().height(PLACEHOLDER_HEIGHT) else Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
    }
}
