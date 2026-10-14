package dev.reader.app.ui.data

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.reader.app.ui.MenuSection
import dev.reader.data.export.ExportScope

@Composable
fun DataScreen(
    status: String,
    busy: Boolean,
    onExport: (Set<ExportScope>) -> Unit,
    onImport: () -> Unit,
    includeBooksInBackup: Boolean,
    onIncludeBooksInBackupChange: (Boolean) -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onDanger: (DangerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodyMedium)

        MenuSection("Import / export") {
            Hint(
                "Everything: settings, themes, library, categories, bookmarks, reading progress and stats. " +
                    "Book files aren't included; use a backup for those.",
            )
            Button(
                onClick = { onExport(ExportScope.entries.toSet()) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("export-everything"),
            ) { Text("Export everything") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onImport, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("import")) {
                Text("Import")
            }
            Spacer(Modifier.height(16.dp))
            Text("Export just one part", style = MaterialTheme.typography.titleSmall)
            Hint("Importing a partial export only changes that part.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PartialExport("Settings", ExportScope.SETTINGS, busy, onExport, Modifier.weight(1f))
                PartialExport("Books", ExportScope.LIBRARY, busy, onExport, Modifier.weight(1f))
                PartialExport("Stats", ExportScope.STATS, busy, onExport, Modifier.weight(1f))
            }
        }

        MenuSection("Backup") {
            Hint("A single file that can rebuild the whole app exactly as it is now, on this or another device.")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("backup-include-books")
                    .clickable { onIncludeBooksInBackupChange(!includeBooksInBackup) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Include book files")
                    Hint("Makes the backup self-contained, but can be very large.")
                }
                Switch(checked = includeBooksInBackup, onCheckedChange = null)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onCreateBackup,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("create-backup"),
            ) { Text("Create backup") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onRestoreBackup,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("restore-backup"),
            ) { Text("Apply backup") }
        }

        DangerZone(busy, onDanger)
    }
}

@Composable
private fun PartialExport(
    label: String,
    scope: ExportScope,
    busy: Boolean,
    onExport: (Set<ExportScope>) -> Unit,
    modifier: Modifier,
) {
    OutlinedButton(
        onClick = { onExport(setOf(scope)) },
        enabled = !busy,
        modifier = modifier.testTag("export-${scope.name}"),
    ) { Text(label) }
}

@Composable
private fun DangerZone(busy: Boolean, onDanger: (DangerAction) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier.fillMaxWidth().testTag("danger-zone"),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(
                    "DANGER ZONE",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "These actions permanently delete data and cannot be undone. Make a backup first.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            DangerAction.entries.forEach { action ->
                Button(
                    onClick = { onDanger(action) },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("danger-${action.name}"),
                ) { Text(action.label) }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
