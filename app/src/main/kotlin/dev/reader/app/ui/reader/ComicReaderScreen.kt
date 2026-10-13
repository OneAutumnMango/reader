package dev.reader.app.ui.reader

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import dev.reader.core.model.BookFormat
import dev.reader.core.reader.TapAction
import dev.reader.core.reader.TapZoneConfig
import dev.reader.parser.comic.ComicArchiveParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Swipeable comic-book page viewer: each page is an image decoded from the cbz/cbr archive. */
@Composable
fun ComicReaderScreen(
    file: File,
    format: BookFormat,
    pages: List<String>,
    tapZones: TapZoneConfig,
    showTapZoneLabels: Boolean = false,
    onTapAction: (TapAction) -> Unit = {},
    onPageChanged: (index: Int, pageCount: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        TapZoneOverlay(tapZones, showTapZoneLabels, onTapAction, Modifier.fillMaxSize()) {
            ImagePager(
                pageCount = pages.size,
                onPageChanged = { onPageChanged(it, pages.size) },
            ) { index ->
                ComicPageImage(file, format, pages[index], Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun ComicPageImage(file: File, format: BookFormat, pageName: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, file, pageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = ComicArchiveParser.readPage(file, format, pageName)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(bitmap = loaded, contentDescription = null, modifier = modifier)
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}
