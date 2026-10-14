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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.reader.app.data.ReaderGraph
import dev.reader.app.ui.describeImportError
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.parser.text.ChapterBreak
import dev.reader.parser.text.ChapterSplitter
import dev.reader.parser.text.ChapterSuggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val PREVIEW_MAX_HEIGHT = 280.dp

enum class ChapterDetectMode { AUTO, STARTS_WITH, REGEX }

/** Whether "Detect chapters" makes sense for [book]. */
fun Book.supportsChapterDetection(): Boolean =
    !isLightNovel && (format == BookFormat.TXT || format == BookFormat.MARKDOWN)

/** Loads [book]'s text and re-splits it with whatever pattern the user confirms. */
@Composable
fun ChapterDetectHost(graph: ReaderGraph, book: Book, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val text by produceState<String?>(null, book.sourcePath) {
        value = withContext(Dispatchers.IO) { runCatching { File(book.sourcePath).readText() }.getOrDefault("") }
    }
    ChapterDetectDialog(
        text = text,
        markdown = book.format == BookFormat.MARKDOWN,
        currentPattern = book.chapterPattern,
        currentExcludedLines = book.chapterExcludedLines,
        onApply = { pattern, excludedLines ->
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching { graph.importer.applyChapterPattern(book, pattern, excludedLines) }
                        .onFailure { describeImportError(it) }
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
) {
    var mode by remember {
        mutableStateOf(if (currentPattern == null) ChapterDetectMode.AUTO else ChapterDetectMode.REGEX)
    }
    var input by remember { mutableStateOf(currentPattern.orEmpty()) }
    val suggestions = remember(text, markdown) { text?.let { ChapterSplitter.suggestions(it, markdown) }.orEmpty() }
    var suggestionIndex by remember { mutableStateOf(0) }
    val pattern = when (mode) {
        ChapterDetectMode.AUTO -> suggestions.getOrNull(suggestionIndex)?.pattern
        ChapterDetectMode.STARTS_WITH -> input.takeIf { it.isNotEmpty() }?.let(ChapterSplitter::prefixPattern)
        ChapterDetectMode.REGEX -> input.takeIf { it.isNotBlank() }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Detect chapters") },
        text = {
            Column {
                ModeRow("Auto-detect", ChapterDetectMode.AUTO, mode) { mode = it }
                ModeRow("Lines starting with", ChapterDetectMode.STARTS_WITH, mode) { mode = it }
                ModeRow("Regular expression", ChapterDetectMode.REGEX, mode) { mode = it }
                if (mode == ChapterDetectMode.AUTO && suggestions.size > 1) {
                    SuggestionChips(suggestions, suggestionIndex) { suggestionIndex = it }
                }
                if (mode != ChapterDetectMode.AUTO) {
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
        },
        confirmButton = {
            val keptCount = breaks.getOrNull().orEmpty().count { it.line !in excludedLines }
            TextButton(
                onClick = { onApply(pattern, excludedLines) },
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

@Composable
private fun ModeRow(
    label: String,
    value: ChapterDetectMode,
    selected: ChapterDetectMode,
    onSelect: (ChapterDetectMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chapter-mode-${value.name}")
            .clickable { onSelect(value) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = value == selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
