package dev.reader.app.ui.export

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Triggers an export/import of the full library bundle; the actual file picker is the caller's job. */
@Composable
fun ImportExportScreen(
    status: String,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Button(onClick = onExport) { Text("Export library") }
        Button(onClick = onImport) { Text("Import library") }
        if (status.isNotBlank()) Text(status)
    }
}
