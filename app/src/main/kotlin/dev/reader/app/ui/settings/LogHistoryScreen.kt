package dev.reader.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import dev.reader.app.data.LogLevel
import dev.reader.app.ui.RadioOptions

@Composable
fun LogHistoryScreen(
    entries: List<LogEntry>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    minimumLevel: LogLevel = LogLevel.WARN,
    onMinimumLevelChange: (LogLevel) -> Unit = {},
    onCopy: (List<LogEntry>) -> Unit = {},
    onExport: (List<LogEntry>) -> Unit = {},
) {
    val filteredEntries = entries.filter { it.level.priority >= minimumLevel.priority }
    LazyColumn(modifier = modifier) {
        item {
            Text("Minimum level", style = MaterialTheme.typography.labelLarge)
            RadioOptions(
                options = LogLevel.entries,
                selected = minimumLevel,
                label = { it.label },
                tag = { "log-level-${it.name.lowercase()}" },
                onSelect = onMinimumLevelChange,
            )
            Text("Showing ${minimumLevel.label} and above", style = MaterialTheme.typography.labelSmall)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onCopy(filteredEntries) },
                    enabled = filteredEntries.isNotEmpty(),
                    modifier = Modifier.testTag("log-copy"),
                ) { Text("Copy") }
                OutlinedButton(
                    onClick = { onExport(filteredEntries) },
                    enabled = filteredEntries.isNotEmpty(),
                    modifier = Modifier.testTag("log-export"),
                ) { Text("Export") }
                Button(onClick = onClear, enabled = entries.isNotEmpty()) { Text("Clear log") }
            }
        }
        if (filteredEntries.isEmpty()) {
            item {
                Text(
                    if (entries.isEmpty()) "Nothing logged yet." else "No entries at this level.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(filteredEntries, key = { it.timestamp.toString() + it.source + it.message }) { entry ->
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    "${entry.level.label} · ${entry.source} · ${entry.timestamp}",
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(entry.message, style = MaterialTheme.typography.bodyMedium)
                entry.details?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}
