package dev.reader.app.ui.data

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

const val DANGER_CONFIRM_WORD = "DELETE"

enum class DangerAction(val label: String, val consequence: String) {
    REMOVE_ALL_BOOKS(
        "Remove all books",
        "Every book is removed from your library, along with its chapters, bookmarks and reading progress. " +
            "Your book files on disk aren't touched.",
    ),
    RESET_STATS(
        "Reset all stats",
        "Every recorded reading session is deleted: reading time, words read, streaks and insights " +
            "all go back to zero.",
    ),
}

/** Two confirmations on top of the original tap: an explanation, then typing [DANGER_CONFIRM_WORD]. */
@Composable
fun DangerConfirmDialog(action: DangerAction, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var step by remember { mutableStateOf(1) }
    var typed by remember { mutableStateOf("") }
    val dangerColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)

    if (step == 1) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("${action.label}?") },
            text = { Text(action.consequence) },
            confirmButton = {
                TextButton(
                    onClick = { step = 2 },
                    colors = dangerColors,
                    modifier = Modifier.testTag("danger-continue"),
                ) {
                    Text("Continue")
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("This can't be undone", color = MaterialTheme.colorScheme.error) },
            text = {
                Column {
                    Text("Type $DANGER_CONFIRM_WORD to ${action.label.lowercase()}.", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("danger-confirm-field"),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirm,
                    enabled = typed.trim() == DANGER_CONFIRM_WORD,
                    colors = dangerColors,
                    modifier = Modifier.testTag("danger-confirm"),
                ) { Text(action.label) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}
