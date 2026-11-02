package dev.reader.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.reader.app.ui.MenuSection
import dev.reader.app.ui.MenuSubheader
import dev.reader.core.model.Book
import dev.reader.core.search.hasSearchableText
import dev.reader.data.repo.BookIndexSize
import dev.reader.data.store.SearchIndexJob
import dev.reader.data.store.SearchSettings

private const val BYTES_PER_STEP = 1024L
private const val DECIMAL = 10L
private val DIALOG_LIST_HEIGHT = 320.dp

/** "1.4 MB"-style sizes, formatted without locale surprises. */
fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB")
    var scaled = bytes * DECIMAL
    var unit = 0
    while (scaled >= BYTES_PER_STEP * DECIMAL && unit < units.lastIndex) {
        scaled /= BYTES_PER_STEP
        unit++
    }
    return if (unit == 0) "$bytes B" else "${scaled / DECIMAL}.${scaled % DECIMAL} ${units[unit]}"
}

/**
 * Search settings: the defaults for library search, and the optional text index behind in-text
 * search (whether to keep it, when to build it, which books to leave out, and what it costs).
 * [sizes] is null while the index size is still being worked out.
 */
@Composable
fun SearchSettingsScreen(
    settings: SearchSettings,
    onSettingsChange: ((SearchSettings) -> SearchSettings) -> Unit,
    books: List<Book>,
    sizes: List<BookIndexSize>?,
    onReindex: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onClearIndex: () -> Unit,
    onExcludedChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    unindexedChapters: Int? = null,
    onRetryUnindexed: () -> Unit = {},
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MenuSection("Search") {
            ToggleRow("Search inside books by default", settings.inTextByDefault, "search-in-text-default") {
                onSettingsChange { s -> s.copy(inTextByDefault = it) }
            }
            MenuSubheader("Default search options")
            ToggleRow("Match case", settings.defaultMatchCase, "search-default-match-case") {
                onSettingsChange { s -> s.copy(defaultMatchCase = it) }
            }
            ToggleRow("Regular expression", settings.defaultRegex, "search-default-regex") {
                onSettingsChange { s -> s.copy(defaultRegex = it) }
            }
            ToggleRow(
                "Match any whitespace",
                settings.defaultAnyWhitespace,
                "search-default-whitespace",
                enabled = !settings.defaultRegex,
            ) { onSettingsChange { s -> s.copy(defaultAnyWhitespace = it) } }
        }

        MenuSection("Search index") {
            Text(
                "Keeps a copy of each book's text so searching inside books is fast. " +
                    "Without it, every search has to read every book.",
                style = MaterialTheme.typography.bodySmall,
            )
            ToggleRow("Index book text", settings.indexEnabled, "search-index-enabled") {
                onSettingsChange { s -> s.copy(indexEnabled = it) }
            }
            if (settings.indexEnabled) {
                IndexOptions(
                    settings,
                    onSettingsChange,
                    books,
                    sizes,
                    IndexActions(onReindex, onPause, onResume, onRetryUnindexed, unindexedChapters),
                    onExcludedChange,
                )
            }
            if (!sizes.isNullOrEmpty()) {
                OutlinedButton(
                    onClick = onClearIndex,
                    modifier = Modifier.fillMaxWidth().testTag("search-index-clear"),
                ) { Text("Delete index data (${formatBytes(sizes.sumOf { it.bytes })})") }
            }
        }
    }
}

private class IndexActions(
    val onReindex: () -> Unit,
    val onPause: () -> Unit,
    val onResume: () -> Unit,
    val onRetryUnindexed: () -> Unit,
    val unindexedChapters: Int?,
)

@Composable
private fun IndexOptions(
    settings: SearchSettings,
    onSettingsChange: ((SearchSettings) -> SearchSettings) -> Unit,
    books: List<Book>,
    sizes: List<BookIndexSize>?,
    actions: IndexActions,
    onExcludedChange: (Set<String>) -> Unit,
) {
    ToggleRow("Index on import", settings.indexOnImport, "search-index-on-import") {
        onSettingsChange { s -> s.copy(indexOnImport = it) }
    }
    ReindexControls(settings.indexJob, actions)
    ExcludedBooks(books, settings.indexExcludedBooks, onExcludedChange)
    IndexSize(books, sizes)
}

@Composable
private fun ReindexControls(job: SearchIndexJob, actions: IndexActions) {
    MenuSubheader("Reindex")
    when {
        job.running -> {
            Text("Indexing ${job.done} of ${job.total} books…", modifier = Modifier.testTag("reindex-status"))
            LinearProgressIndicator(
                progress = { job.done.toFloat() / job.total.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().testTag("reindex-progress"),
            )
            OutlinedButton(onClick = actions.onPause, modifier = Modifier.fillMaxWidth().testTag("reindex-pause")) {
                Text("Pause")
            }
        }
        job.isPaused -> {
            Text("Paused at ${job.done} of ${job.total} books", modifier = Modifier.testTag("reindex-status"))
            LinearProgressIndicator(
                progress = { job.done.toFloat() / job.total.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().testTag("reindex-progress"),
            )
            Button(
                onClick = actions.onResume,
                modifier = Modifier.fillMaxWidth().testTag("reindex-resume"),
            ) { Text("Resume") }
        }
        else -> {
            OutlinedButton(
                onClick = actions.onReindex,
                modifier = Modifier.fillMaxWidth().testTag("reindex-library"),
            ) { Text("Reindex library") }
            val missing = actions.unindexedChapters
            OutlinedButton(
                onClick = actions.onRetryUnindexed,
                enabled = (missing ?: 0) > 0,
                modifier = Modifier.fillMaxWidth().testTag("retry-unindexed"),
            ) {
                Text(
                    when (missing) {
                        null -> "Retry unindexed chapters"
                        0 -> "Every chapter is indexed"
                        else -> "Retry $missing unindexed chapters"
                    },
                )
            }
            if (job.finished) {
                val skipped = job.unreadableChapters.takeIf { it > 0 }?.let { " $it chapters couldn't be read." }
                val verb = if (job.onlyMissing) "Retried" else "Indexed"
                Text("$verb ${job.total} books.${skipped.orEmpty()}", modifier = Modifier.testTag("reindex-status"))
            }
        }
    }
    Text(
        "Reindexing runs in the background and keeps going if you leave the app.",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun ExcludedBooks(books: List<Book>, excluded: Set<String>, onExcludedChange: (Set<String>) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    MenuSubheader("Leave out of the index")
    Text(
        if (excluded.isEmpty()) "Every book is indexed." else "${excluded.size} books are left out.",
        modifier = Modifier.testTag("excluded-summary"),
    )
    OutlinedButton(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth().testTag("choose-excluded")) {
        Text("Choose books")
    }
    if (choosing) {
        ExcludeBooksDialog(books, excluded, onConfirm = {
            choosing = false
            onExcludedChange(it)
        }, onDismiss = { choosing = false })
    }
}

@Composable
private fun ExcludeBooksDialog(
    books: List<Book>,
    excluded: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(excluded) }
    var filter by remember { mutableStateOf("") }
    val shown = remember(books, filter) {
        books.filter { it.hasSearchableText() && it.title.contains(filter, ignoreCase = true) }
            .sortedBy { it.title.lowercase() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Leave out of the index") },
        text = {
            Column {
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    singleLine = true,
                    label = { Text("Filter") },
                    modifier = Modifier.fillMaxWidth().testTag("exclude-filter"),
                )
                LazyColumn(modifier = Modifier.heightIn(max = DIALOG_LIST_HEIGHT)) {
                    items(shown, key = { it.id.value }) { book ->
                        val checked = book.id.value in picked
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { picked = if (checked) picked - book.id.value else picked + book.id.value }
                                .testTag("exclude-${book.id.value}"),
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(
                                book.title,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(picked) }, modifier = Modifier.testTag("exclude-confirm")) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun IndexSize(books: List<Book>, sizes: List<BookIndexSize>?) {
    var expanded by remember { mutableStateOf(false) }
    MenuSubheader("Index size")
    when {
        sizes == null -> Text("Measuring…", modifier = Modifier.testTag("index-size-total"))
        sizes.isEmpty() -> Text("Nothing is indexed yet.", modifier = Modifier.testTag("index-size-total"))
        else -> {
            val titles = remember(books) { books.associate { it.id to it.title } }
            val chapters = sizes.sumOf { it.chapters }
            Text(
                "${formatBytes(sizes.sumOf { it.bytes })} across ${sizes.size} books, $chapters chapters",
                modifier = Modifier.testTag("index-size-total"),
            )
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("index-size-toggle")) {
                Text(if (expanded) "Hide per-book sizes" else "Show per-book sizes")
            }
            if (expanded) {
                sizes.forEach { size ->
                    Row(modifier = Modifier.fillMaxWidth().testTag("index-size-${size.bookId.value}")) {
                        Text(
                            titles[size.bookId] ?: "Removed book",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(formatBytes(size.bytes), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    tag: String,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(tag).clickable(enabled = enabled) { onChange(!checked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, enabled = enabled, onCheckedChange = null)
    }
}
