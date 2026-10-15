package dev.reader.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.reader.app.data.AppLog
import dev.reader.app.data.ReaderGraph
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.parser.docx.DocxParser
import dev.reader.parser.pdf.PdfTextParser
import dev.reader.parser.text.ChapterBreak
import dev.reader.parser.text.ChapterSplitter
import dev.reader.parser.text.ChapterSuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val PREVIEW_MAX_HEIGHT = 280.dp

enum class ChapterDetectMode { AUTO, STARTS_WITH, REGEX, PICK_LINES }

/** Whether "Detect chapters" makes sense for [book]. */
fun Book.supportsChapterDetection(): Boolean =
    !isLightNovel && (format == BookFormat.TXT || format == BookFormat.MARKDOWN || format == BookFormat.PDF || format == BookFormat.DOCX)

/** Loads [book]'s text and re-splits it with whatever pattern the user confirms. */
@Composable
fun ChapterDetectHost(graph: ReaderGraph, book: Book, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val text by produceState<String?>(null, book.sourcePath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(book.sourcePath)
                when (book.format) {
                    BookFormat.PDF -> PdfTextParser(context).text(file)
                    BookFormat.DOCX -> DocxParser.text(file)
                    else -> file.readText()
                }
            }
                .onFailure { AppLog.error("Couldn't read book for chapter detection", "ChapterDetection", it) }
                .getOrDefault("")
        }
    }
    ChapterDetectDialog(
        text = text,
        markdown = book.format == BookFormat.MARKDOWN,
        currentPattern = book.chapterPattern,
        currentExcludedLines = book.chapterExcludedLines,
        pdfFile = if (book.format == BookFormat.PDF) File(book.sourcePath) else null,
        onApply = { pattern, excludedLines ->
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching { graph.importer.applyChapterPattern(book, pattern, excludedLines, context) }
                        .onSuccess { AppLog.info("Applied chapter detection settings", "ChapterDetection") }
                        .onFailure { AppLog.error("Couldn't apply chapter detection settings", "ChapterDetection", it) }
                }
                onDismiss()
            }
        },
        onDismiss = onDismiss,
    )
}

/**
 * Lets the user pick how a txt/markdown book is split: auto-detected headings, lines starting with
 * some text (e.g. `# ` or `Chapter`), or a regex — previewing every break before applying, and
 * letting them manually drop any detected break they don't actually want to be a chapter.
 * [onApply]'s pattern is null for "no chapters, keep it as one".
 */
@Composable
fun ChapterDetectDialog(
    text: String?,
    markdown: Boolean,
    currentPattern: String?,
    onApply: (String?, Set<Int>) -> Unit,
    onDismiss: () -> Unit,
    currentExcludedLines: Set<Int> = emptySet(),
    pdfFile: File? = null,
) {
    var mode by remember {
        mutableStateOf(
            when {
                currentPattern == null -> ChapterDetectMode.AUTO
                ChapterSplitter.pickedLines(currentPattern) != null -> ChapterDetectMode.PICK_LINES
                else -> ChapterDetectMode.REGEX
            },
        )
    }
    var input by remember {
        mutableStateOf(currentPattern?.takeIf { ChapterSplitter.pickedLines(it) == null }.orEmpty())
    }
    var picked by remember { mutableStateOf(currentPattern?.let(ChapterSplitter::pickedLines).orEmpty()) }
    val suggestions = remember(text, markdown) { text?.let { ChapterSplitter.suggestions(it, markdown) }.orEmpty() }
    var suggestionIndex by remember { mutableStateOf(0) }
    val pattern = when (mode) {
        ChapterDetectMode.AUTO -> suggestions.getOrNull(suggestionIndex)?.pattern
        ChapterDetectMode.STARTS_WITH -> input.takeIf { it.isNotEmpty() }?.let(ChapterSplitter::prefixPattern)
        ChapterDetectMode.REGEX -> input.takeIf { it.isNotBlank() }
        ChapterDetectMode.PICK_LINES -> picked.takeIf { it.isNotEmpty() }?.let(ChapterSplitter::linesPattern)
    }
    val breaks: Result<List<ChapterBreak>> = remember(text, pattern) {
        if (text == null || pattern == null) {
            Result.success(emptyList())
        } else {
            runCatching { ChapterSplitter.breaks(text, pattern) }
        }
    }
    // Manual removals only make sense for the breaks they were chosen against.
    var excludedLines by remember(pattern) {
        mutableStateOf(if (pattern == currentPattern) currentExcludedLines else emptySet())
    }
    var showPicker by remember { mutableStateOf(false) }

    // A saved auto-detected split reopens as Auto-detect with that suggestion chosen, not as a raw regex.
    LaunchedEffect(suggestions) {
        val index = suggestions.indexOfFirst { it.pattern == currentPattern }
        if (index >= 0 && mode == ChapterDetectMode.REGEX && input == currentPattern) {
            mode = ChapterDetectMode.AUTO
            suggestionIndex = index
        }
    }

    fun selectMode(next: ChapterDetectMode) {
        if (next == ChapterDetectMode.PICK_LINES && picked.isEmpty()) {
            picked = breaks.getOrNull().orEmpty().map { it.line }.filterNot { it in excludedLines }.toSet()
        }
        mode = next
    }

    if (showPicker && text != null) {
        ChapterLinePicker(
            text = text,
            picked = picked,
            onToggle = { line -> picked = if (line in picked) picked - line else picked + line },
            onDone = { showPicker = false },
            pdfFile = pdfFile,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Detect chapters") },
        text = {
            Column {
                ModeDropdown(mode, ::selectMode)
                if (mode == ChapterDetectMode.AUTO && suggestions.size > 1) {
                    SuggestionChips(suggestions, suggestionIndex) { suggestionIndex = it }
                }
                if (mode == ChapterDetectMode.STARTS_WITH || mode == ChapterDetectMode.REGEX) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        label = {
                            Text(if (mode == ChapterDetectMode.STARTS_WITH) "e.g. \"# \" or \"Chapter\"" else "Regex")
                        },
                        modifier = Modifier.fillMaxWidth().testTag("chapter-pattern-field"),
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                if (mode == ChapterDetectMode.PICK_LINES) {
                    PickedChapters(
                        text = text,
                        breaks = breaks.getOrNull().orEmpty(),
                        onUnpick = { line -> picked = picked - line },
                        onOpenPicker = { showPicker = true },
                    )
                } else {
                    ChapterPreview(
                        text = text,
                        mode = mode,
                        breaks = breaks,
                        excludedLines = excludedLines,
                        onToggle = { line ->
                            excludedLines = if (line in excludedLines) excludedLines - line else excludedLines + line
                        },
                    )
                }
            }
        },
        confirmButton = {
            val keptCount = breaks.getOrNull().orEmpty().count { it.line !in excludedLines }
            TextButton(
                onClick = {
                    onApply(pattern, if (mode == ChapterDetectMode.PICK_LINES) emptySet() else excludedLines)
                },
                enabled = keptCount > 0,
                modifier = Modifier.testTag("chapter-detect-apply"),
            ) { Text("Apply") }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = { onApply(null, emptySet()) },
                    modifier = Modifier.testTag("chapter-detect-none"),
                ) {
                    Text("No chapters")
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun ChapterPreview(
    text: String?,
    mode: ChapterDetectMode,
    breaks: Result<List<ChapterBreak>>,
    excludedLines: Set<Int>,
    onToggle: (Int) -> Unit,
) {
    val found = breaks.getOrNull()
    when {
        text == null -> CircularProgressIndicator()
        found == null -> Text("That isn't a valid regular expression.", color = MaterialTheme.colorScheme.error)
        found.isEmpty() -> Text(
            if (mode == ChapterDetectMode.AUTO) {
                "Couldn't spot any chapter headings. Try \"Lines starting with\" instead."
            } else {
                "No lines match yet."
            },
        )
        else -> {
            val keptCount = found.count { it.line !in excludedLines }
            Text(
                "$keptCount of ${found.size} ${if (found.size == 1) "chapter" else "chapters"} kept",
                style = MaterialTheme.typography.labelLarge,
            )
            LazyColumn(modifier = Modifier.heightIn(max = PREVIEW_MAX_HEIGHT).testTag("chapter-preview")) {
                items(found, key = { it.line }) { chapterBreak ->
                    ChapterBreakRow(chapterBreak, chapterBreak.line !in excludedLines) { onToggle(chapterBreak.line) }
                }
            }
        }
    }
}

@Composable
private fun PickedChapters(
    text: String?,
    breaks: List<ChapterBreak>,
    onUnpick: (Int) -> Unit,
    onOpenPicker: () -> Unit,
) {
    if (text == null) {
        CircularProgressIndicator()
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (breaks.isEmpty()) "No lines picked yet." else "${breaks.size} picked",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpenPicker, modifier = Modifier.testTag("chapter-open-picker")) {
            Text("Pick in book")
        }
    }
    LazyColumn(modifier = Modifier.heightIn(max = PREVIEW_MAX_HEIGHT).testTag("chapter-preview")) {
        items(breaks, key = { it.line }) { chapterBreak ->
            ChapterBreakRow(chapterBreak, kept = true) { onUnpick(chapterBreak.line) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeDropdown(mode: ChapterDetectMode, onSelect: (ChapterDetectMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = mode.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Method") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag("chapter-mode"),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ChapterDetectMode.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    modifier = Modifier.testTag("chapter-mode-${option.name}"),
                )
            }
        }
    }
}

internal val ChapterDetectMode.label: String
    get() = when (this) {
        ChapterDetectMode.AUTO -> "Auto-detect"
        ChapterDetectMode.STARTS_WITH -> "Lines starting with"
        ChapterDetectMode.REGEX -> "Regular expression"
        ChapterDetectMode.PICK_LINES -> "Pick lines"
    }

@Composable
private fun ChapterBreakRow(chapterBreak: ChapterBreak, kept: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chapter-break-${chapterBreak.line}")
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = kept, onCheckedChange = null)
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                chapterBreak.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (kept) null else TextDecoration.LineThrough,
                color = if (kept) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Line ${chapterBreak.line + 1} \u00b7 ${chapterBreak.preview}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SuggestionChips(suggestions: List<ChapterSuggestion>, selected: Int, onSelect: (Int) -> Unit) {
    Text(
        "Other ways this book could be split:",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 8.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestions.forEachIndexed { index, suggestion ->
            FilterChip(
                selected = index == selected,
                onClick = { onSelect(index) },
                label = { Text("${suggestion.name} (${suggestion.count})") },
                modifier = Modifier.testTag("chapter-suggestion-$index"),
            )
        }
    }
}
