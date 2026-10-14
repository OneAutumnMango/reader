package dev.reader.app.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import dev.reader.app.data.AppLog
import dev.reader.core.richtext.Block

@Composable
internal fun ReaderImage(image: Block.Image, modifier: Modifier = Modifier) {
    var failed by remember(image.path) { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.Center) {
        AsyncImage(
            model = image.path,
            contentDescription = image.alt.ifBlank { "Book illustration" },
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            onError = {
                failed = true
                AppLog.warn("Couldn't display book illustration", "ReaderImage", it.result.throwable)
            },
            onSuccess = { failed = false },
        )
        if (failed) Text(image.alt.ifBlank { "Image unavailable" })
    }
}
