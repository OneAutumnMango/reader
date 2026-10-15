package dev.reader.app.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.reader.app.ui.reader.Zoomable
import dev.reader.app.ui.reader.Zoomable
import dev.reader.parser.pdf.PdfLineBounds
import dev.reader.parser.pdf.PdfPageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs


private val BLANK_LINE_HEIGHT = 8.dp
private const val FALLBACK_RATIO = 0.707f
private const val MAX_ZOOM = 5f

/**
 * Full-screen picker: tap any text line to mark it as a chapter start. For PDF books pass
 * [pdfFile] to enable a "Pages" toggle — each page renders with pinch-to-zoom and the text lines
 * belonging to that page are listed below so any one can be individually picked.
 */
@Composable
fun ChapterLinePicker(
    text: String,
    picked: Set<Int>,
    onToggle: (Int) -> Unit,
    onDone: () -> Unit,
    pdfFile: File? = null,
    pdfPageLineBounds: Map<Int, List<PdfLineBounds>>? = null,
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
                    PdfPagesPreview(
                        file = pdfFile,
                        pageLineBounds = pdfPageLineBounds,
                        picked = picked,
                        onToggle = onToggle,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
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
private fun PdfPagesPreview(
    file: File,
    modifier: Modifier = Modifier,
    pageLineBounds: Map<Int, List<PdfLineBounds>>? = null,
    picked: Set<Int> = emptySet(),
    onToggle: (Int) -> Unit = {},
) {
    var renderer by remember(file) { mutableStateOf<PdfPageRenderer?>(null) }
    LaunchedEffect(file) {
        renderer = withContext(Dispatchers.IO) { runCatching { PdfPageRenderer(file) }.getOrNull() }
    }
    DisposableEffect(file) { onDispose { renderer?.close() } }

    val r = renderer ?: run {
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    // Pre-cache all aspect ratios so each page Box has the right size immediately.
    val ratios = remember(r) {
        mutableStateMapOf<Int, Float>().also { map ->
            // populated lazily as each page is composed
        }
    }
    Zoomable(enabled = true, modifier = modifier) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(r.pageCount) { pageIndex ->
                PdfPickerPage(
                    renderer = r,
                    pageIndex = pageIndex,
                    lineBounds = pageLineBounds?.get(pageIndex) ?: emptyList(),
                    picked = picked,
                    onToggle = onToggle,
                    onRatioCached = { ratios[pageIndex] = it },
                    cachedRatio = ratios[pageIndex],
                )
            }
        }
    }
}

/**
 * One PDF page inside the parent [Zoomable].
 */
@Composable
private fun PdfPickerPage(
    renderer: PdfPageRenderer,
    pageIndex: Int,
    lineBounds: List<PdfLineBounds>,
    picked: Set<Int>,
    onToggle: (Int) -> Unit,
    cachedRatio: Float? = null,
    onRatioCached: (Float) -> Unit = {},
) {
    val pickedColor = MaterialTheme.colorScheme.primary
    val ratio by produceState(cachedRatio, renderer, pageIndex) {
        if (value == null) value = withContext(Dispatchers.IO) {
            runCatching { renderer.aspectRatio(pageIndex) }.getOrNull() ?: FALLBACK_RATIO
        }
        value?.let(onRatioCached)
    }
    var w by remember { mutableStateOf(1) }
    var h by remember { mutableStateOf(1) }
    val bitmap by produceState<android.graphics.Bitmap?>(null, renderer, pageIndex, w, h) {
        if (w > 1 && h > 1) value = withContext(Dispatchers.IO) {
            runCatching { renderer.renderPage(pageIndex, w, h) }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio ?: FALLBACK_RATIO)
            .onSizeChanged { s -> w = s.width.coerceAtLeast(1); h = s.height.coerceAtLeast(1) }
            .detectLineTap(lineBounds, onToggle),
    ) {
        if (ratio == null) { CircularProgressIndicator(); return@Box }
        val bmp = bitmap ?: run { CircularProgressIndicator(); return@Box }
        Image(bitmap = bmp.asImageBitmap(), contentDescription = "Page ${pageIndex + 1}", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        if (lineBounds.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                lineBounds.forEach { b ->
                    if (b.lineIndex in picked) {
                        drawRect(
                            color = pickedColor.copy(alpha = 0.35f),
                            topLeft = Offset(b.left * size.width, b.top * size.height),
                            size = Size((b.right - b.left) * size.width, (b.bottom - b.top) * size.height),
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider()
}

/** Mirrors [detectTapGestures] but works inside [Zoomable]: ignores consumed moves and cancels on multi-touch. */
private fun Modifier.detectLineTap(lineBounds: List<PdfLineBounds>, onToggle: (Int) -> Unit): Modifier =
    if (lineBounds.isEmpty()) this
    else pointerInput(lineBounds) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var up: PointerInputChange? = null
            while (up == null) {
                val event = awaitPointerEvent()
                val ptr = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!ptr.pressed) up = ptr
                else if (event.changes.count { it.pressed } > 1 || (ptr.positionChanged() && ptr.isConsumed)) break
            }
            if (up != null && (up.position - down.position).getDistance() < viewConfiguration.touchSlop) {
                val xf = (down.position.x / size.width).coerceIn(0f, 1f)
                val yf = (down.position.y / size.height).coerceIn(0f, 1f)
                val hit = lineBounds.firstOrNull { b -> yf in b.top..b.bottom }
                    ?: lineBounds.minBy { b -> abs((b.top + b.bottom) / 2f - yf) }
                onToggle(hit.lineIndex)
            }
        }
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

