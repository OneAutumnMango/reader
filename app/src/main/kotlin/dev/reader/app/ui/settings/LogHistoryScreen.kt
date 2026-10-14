package dev.reader.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.app.data.LogEntry

@Composable
fun LogHistoryScreen(
    entries: List<LogEntry>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    onCopy: () -> Unit = {},
    onExport: () -> Unit = {},
) {
    LazyColumn(modifier = modifier) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCopy,
                    enabled = entries.isNotEmpty(),
                    modifier = Modifier.testTag("log-copy"),
                ) { Text("Copy") }
                OutlinedButton(
                    onClick = onExport,
                    enabled = entries.isNotEmpty(),
                    modifier = Modifier.testTag("log-export"),
                ) { Text("Export") }
                Button(onClick = onClear, enabled = entries.isNotEmpty()) { Text("Clear log") }
            }
        }
        if (entries.isEmpty()) {
            item { Text("Nothing logged yet.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(entries, key = { it.timestamp.toString() + it.message }) { entry ->
            Text(entry.timestamp.toString(), style = MaterialTheme.typography.labelSmall)
            Text(entry.message, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}
