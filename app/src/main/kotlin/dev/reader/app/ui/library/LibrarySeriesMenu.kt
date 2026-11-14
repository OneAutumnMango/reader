package dev.reader.app.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

const val SERIES_EXPLANATION = "Series are taken from the folder name a book is in. Your files aren't moved."

/** Top-bar series button: toggle grouping books by their folder, or re-detect series from folders. */
@Composable
fun LibrarySeriesMenu(groupBySeries: Boolean, onToggle: (Boolean) -> Unit, onDetect: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    var confirmDetect by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.testTag("library-series")) {
            Icon(Icons.Filled.Folder, contentDescription = "Series")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Group by series")
                        Text(SERIES_EXPLANATION, style = MaterialTheme.typography.bodySmall)
                    }
                },
                trailingIcon = { Switch(checked = groupBySeries, onCheckedChange = null) },
                onClick = { onToggle(!groupBySeries) },
                modifier = Modifier.testTag("library-series-toggle"),
            )
            DropdownMenuItem(
                text = { Text("Detect series") },
                onClick = {
                    open = false
                    confirmDetect = true
                },
                modifier = Modifier.testTag("library-series-detect"),
            )
        }
    }
    if (confirmDetect) {
        AlertDialog(
            onDismissRequest = { confirmDetect = false },
            title = { Text("Detect series") },
            text = { Text(SERIES_EXPLANATION) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDetect = false
                    onDetect()
                }) { Text("Detect") }
            },
            dismissButton = { TextButton(onClick = { confirmDetect = false }) { Text("Cancel") } },
        )
    }
}
