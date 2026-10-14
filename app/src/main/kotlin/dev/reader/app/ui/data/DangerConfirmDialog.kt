package dev.reader.app.ui.data

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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

/** Two explicit confirmations on top of the original tap, each spelling out what will be lost. */
@Composable
fun DangerConfirmDialog(action: DangerAction, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var step by remember { mutableStateOf(1) }
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
            title = { Text("Are you absolutely sure?", color = MaterialTheme.colorScheme.error) },
            text = {
                Column {
                    Text(action.finalWarning, fontWeight = FontWeight.SemiBold)
                    Text(
                        "This cannot be undone.",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirm,
                    colors = dangerColors,
                    modifier = Modifier.testTag("danger-confirm"),
                ) { Text(action.confirmLabel) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}
