package dev.reader.app.ui.library

import androidx.compose.foundation.layout.Column
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

/** Lists books whose source file can no longer be found, with a one-tap way to drop them all. */
@Composable
fun MissingBooksDialog(books: List<Book>, onRemoveAll: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (books.size == 1) "1 book is missing" else "${books.size} books are missing") },
        text = {
            Column {
                Text("Their files can no longer be found — they may have been moved or deleted:")
                LazyColumn(modifier = Modifier.testTag("missing-books-list").heightIn(max = 240.dp)) {
                    items(books) { book -> Text("• ${book.title}") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRemoveAll, modifier = Modifier.testTag("missing-books-remove-all")) {
                Text("Remove all")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep") } },
    )
}
