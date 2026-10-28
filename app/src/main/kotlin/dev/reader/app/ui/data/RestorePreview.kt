package dev.reader.app.ui.data

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.core.model.Book

/** What restoring a backup would change, compared against the current library. */
data class RestorePreview(val added: List<Book>, val updated: List<Book>, val onlyInLibrary: List<Book>)

/** Books are matched by id — the same key a restore or merge upserts by. */
fun restorePreview(current: List<Book>, backup: List<Book>): RestorePreview {
    val currentIds = current.map { it.id }.toSet()
    val backupIds = backup.map { it.id }.toSet()
    return RestorePreview(
        added = backup.filterNot { it.id in currentIds },
        updated = backup.filter { it.id in currentIds },
        onlyInLibrary = current.filterNot { it.id in backupIds },
    )
}

/** Shows what a backup would change, then lets the user merge it in or replace the library outright. */
@Composable
fun RestorePreviewDialog(preview: RestorePreview, onMerge: () -> Unit, onReplace: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore this backup?") },
        text = {
            Column {
                Text("${preview.added.size} new book(s) will be added.")
                if (preview.added.isNotEmpty()) BookTitles(preview.added, "restore-added-list")
                Text("${preview.updated.size} book(s) you already have will be overwritten by the backup's version.")
                if (preview.onlyInLibrary.isNotEmpty()) {
                    Text(
                        "${preview.onlyInLibrary.size} book(s) in your library aren't in this backup — " +
                            "removed if you Replace, kept if you Merge:",
                    )
                    BookTitles(preview.onlyInLibrary, "restore-removed-list")
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onMerge, modifier = Modifier.testTag("restore-merge")) { Text("Merge") }
                TextButton(onClick = onReplace, modifier = Modifier.testTag("restore-replace")) { Text("Replace all") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BookTitles(books: List<Book>, tag: String) {
    LazyColumn(modifier = Modifier.testTag(tag).heightIn(max = 120.dp)) {
        items(books, key = { it.id.value }) { book -> Text("• ${book.title}") }
    }
}
