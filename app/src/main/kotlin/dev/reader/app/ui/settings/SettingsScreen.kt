package dev.reader.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.app.ui.MenuSection
import dev.reader.core.theme.LibraryLayout

@Composable
fun SettingsScreen(
    status: String,
    rootFolders: List<RootFolderDisplay>,
    onAddBook: () -> Unit,
    onAddLightNovel: () -> Unit,
    onAddRootFolder: () -> Unit,
    onRemoveRootFolder: (String) -> Unit,
    onViewLogHistory: () -> Unit,
    libraryLayout: LibraryLayout,
    onLibraryLayoutChange: (LibraryLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MenuSection("Library") {
            Button(onClick = onAddBook, modifier = Modifier.fillMaxWidth()) { Text("Add book") }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onAddLightNovel, modifier = Modifier.fillMaxWidth()) {
                Text("Add light novel")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Cover size", style = MaterialTheme.typography.bodyMedium)
            Row {
                LibraryLayout.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .testTag("library-layout-${option.name}")
                            .clickable { onLibraryLayoutChange(option) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = libraryLayout == option, onClick = null)
                        Text(option.name)
                    }
                }
            }
        }

        MenuSection("Root folders") {
            Text(
                "Folders here are checked for new books every time the app launches.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            rootFolders.forEach { folder ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = null)
                    Text(
                        folder.displayName,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    IconButton(onClick = { onRemoveRootFolder(folder.uri) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove ${folder.displayName}")
                    }
                }
                HorizontalDivider()
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onAddRootFolder, modifier = Modifier.fillMaxWidth()) {
                Text("Add root folder")
            }
        }

        MenuSection("Logs") {
            Text(
                "See everything that's been imported or failed to import.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onViewLogHistory, modifier = Modifier.fillMaxWidth()) {
                Text("View error/log history")
            }
        }

        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodyMedium)
    }
}
