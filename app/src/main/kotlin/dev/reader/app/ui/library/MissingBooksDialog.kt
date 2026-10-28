package dev.reader.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.dp
import dev.reader.core.model.Book

/** Lists books whose source file can no longer be found, letting the user pick which to drop. */
@Composable
fun MissingBooksDialog(books: List<Book>, onRemove: (List<Book>) -> Unit, onDismiss: () -> Unit) {
    var selected by remember(books) { mutableStateOf(books.map { it.id }.toSet()) }
    var confirming by remember { mutableStateOf(false) }
    val allSelected = selected.size == books.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (books.size == 1) "1 book is missing" else "${books.size} books are missing") },
        text = {
            Column {
                Text("Their files can no longer be found — they may have been moved or deleted:")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("missing-books-select-all")
                        .clickable { selected = if (allSelected) emptySet() else books.map { it.id }.toSet() }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = allSelected, onCheckedChange = null)
                    Text("Select all", modifier = Modifier.padding(start = 4.dp))
                }
                LazyColumn(modifier = Modifier.testTag("missing-books-list").heightIn(max = 240.dp)) {
                    items(books, key = { it.id.value }) { book ->
                        val checked = book.id in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("missing-book-${book.id.value}")
                                .clickable { selected = if (checked) selected - book.id else selected + book.id }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(book.title, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { confirming = true },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.testTag("missing-books-remove"),
            ) { Text("Remove (${selected.size})") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep") } },
    )

    if (confirming) {
        val toRemove = books.filter { it.id in selected }
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Remove ${toRemove.size} book(s)?") },
            text = { Text("They'll be removed from your library. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onRemove(toRemove)
                    },
                    modifier = Modifier.testTag("missing-books-remove-confirm"),
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }
}
