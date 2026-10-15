package dev.reader.app.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.reader.parser.pdf.PdfPageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private val BLANK_LINE_HEIGHT = 8.dp
private const val FALLBACK_RATIO = 0.707f

/**
 * Full-screen picker: tap any text line to mark it as a chapter start. For PDF books pass
 * [pdfFile] to enable a "Pages" toggle that swaps the text list for the rendered page images.
 */
@Composable
fun ChapterLinePicker(
    text: String,
    picked: Set<Int>,
    onToggle: (Int) -> Unit,
    onDone: () -> Unit,
    pdfFile: File? = null,
) {
    val lines = remember(text) { text.lines() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = picked.minOrNull() ?: 0)
    val scope = rememberCoroutineScope()
    var find by remember { mutableStateOf("") }
    var showPages by remember { mutableStateOf(false) }

    fun findNext() {
        if (find.isBlank()) return
        val start = listState.firstVisibleItemIndex + 1
        val next = (lines.indices.drop(start) + lines.indices.take(start))
            .firstOrNull { lines[it].contains(find, ignoreCase = true) } ?: return
        scope.launch { listState.scrollToItem(next) }
    }

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize().testTag("chapter-line-picker")) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pick chapter starts", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tap a line to start a chapter there \u00b7 ${picked.size} picked",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (pdfFile != null) {
                        TextButton(
                            onClick = { showPages = !showPages },
                            modifier = Modifier.testTag("chapter-picker-pdf-mode"),
                        ) { Text(if (showPages) "Text" else "Pages") }
                    }
                    TextButton(onClick = onDone, modifier = Modifier.testTag("chapter-picker-done")) { Text("Done") }
                }

                if (showPages && pdfFile != null) {
                    PdfPagesPreview(pdfFile, Modifier.weight(1f).fillMaxWidth())
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        OutlinedTextField(
                            value = find,
                            onValueChange = { find = it },
                            singleLine = true,
                            placeholder = { Text("Find text") },
                            modifier = Modifier.weight(1f).testTag("chapter-picker-find"),
                        )
                        IconButton(onClick = ::findNext, modifier = Modifier.testTag("chapter-picker-find-next")) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match")
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("chapter-picker-lines")) {
                        itemsIndexed(lines) { index, line ->
                            if (line.isBlank()) {
                                Spacer(Modifier.height(BLANK_LINE_HEIGHT))
                            } else {
                                PickableLine(line, index, index in picked) { onToggle(index) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPagesPreview(file: File, modifier: Modifier = Modifier) {
    var renderer by remember(file) { mutableStateOf<PdfPageRenderer?>(null) }
    LaunchedEffect(file) {
        renderer = withContext(Dispatchers.IO) { runCatching { PdfPageRenderer(file) }.getOrNull() }
    }
    DisposableEffect(file) { onDispose { renderer?.close() } }

    val r = renderer
    if (r == null) {
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LazyColumn(modifier = modifier) {
        items(r.pageCount) { index -> PdfPageItem(r, index) }
    }
}

@Composable
private fun PdfPageItem(renderer: PdfPageRenderer, index: Int) {
    val ratio by produceState<Float?>(null, renderer, index) {
        value = withContext(Dispatchers.IO) {
            runCatching { renderer.aspectRatio(index) }.getOrNull() ?: FALLBACK_RATIO
        }
    }
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().aspectRatio(ratio ?: FALLBACK_RATIO),
    ) {
        if (ratio == null) { CircularProgressIndicator(); return@BoxWithConstraints }
        val density = LocalDensity.current
        val w = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val h = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        val bitmap by produceState<android.graphics.Bitmap?>(null, renderer, index, w, h) {
            value = withContext(Dispatchers.IO) {
                runCatching { renderer.renderPage(index, w, h) }.getOrNull()
            }
        }
        when (val bmp = bitmap) {
            null -> CircularProgressIndicator()
            else -> Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Page ${index + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun PickableLine(line: String, index: Int, picked: Boolean, onClick: () -> Unit) {
    Text(
        line.trim(),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (picked) FontWeight.Bold else null,
        color = if (picked) MaterialTheme.colorScheme.onPrimaryContainer else Color.Unspecified,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (picked) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .testTag("chapter-line-$index")
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

