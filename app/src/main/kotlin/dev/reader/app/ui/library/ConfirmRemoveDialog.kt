package dev.reader.app.ui.library

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** Asks before removing [count] book(s) from the library, since it also deletes their stored files. */
@Composable
fun ConfirmRemoveDialog(count: Int, title: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val what = if (count == 1 && title != null) "\"$title\"" else "$count books"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove from library?") },
        text = { Text("$what will be removed from your library, along with its reading progress and bookmarks.") },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm-remove")) { Text("Remove") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel-remove")) { Text("Cancel") }
        },
    )
}
