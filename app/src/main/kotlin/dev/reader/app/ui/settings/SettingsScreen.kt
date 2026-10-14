package dev.reader.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.app.ui.MenuSection
import dev.reader.data.store.CollectionKind

@Composable
fun SettingsScreen(
    status: String,
    rootFolders: List<RootFolderDisplay>,
    onAddBookFile: () -> Unit,
    onAddBookFolder: () -> Unit,
    onAddCollection: (CollectionKind) -> Unit,
    onRemoveRootFolder: (String) -> Unit,
    onViewLogHistory: () -> Unit,
    modifier: Modifier = Modifier,
    onScanRootFolders: () -> Unit = {},
    scanning: Boolean = false,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MenuSection("Library") {
            Button(onClick = onAddBookFile, modifier = Modifier.fillMaxWidth().testTag("add-book-file")) {
                Text("Add book (file)")
            }
            Hint("A single book file: epub, pdf, txt, mobi, comics and more.")
            OutlinedButton(onClick = onAddBookFolder, modifier = Modifier.fillMaxWidth().testTag("add-book-folder")) {
                Text("Add book (folder)")
            }
            Hint("One book made of a folder of files, each file being a chapter.")
        }

        MenuSection("Library folders") {
            Hint("Folders checked for new books every time the app launches.")
            rootFolders.forEach { folder ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (folder.kind == CollectionKind.FILES) {
                            Icons.AutoMirrored.Filled.LibraryBooks
                        } else {
                            Icons.Filled.Folder
                        },
                        contentDescription = null,
                    )
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(folder.displayName)
                        Text(
                            collectionKindLabel(folder.kind),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onRemoveRootFolder(folder.uri) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove ${folder.displayName}")
                    }
                }
                HorizontalDivider()
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onAddCollection(CollectionKind.FILES) },
                modifier = Modifier.fillMaxWidth().testTag("add-collection-FILES"),
            ) { Text("Add collection (each file is a book)") }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onAddCollection(CollectionKind.FOLDERS) },
                modifier = Modifier.fillMaxWidth().testTag("add-collection-FOLDERS"),
            ) { Text("Add collection (each folder is a book)") }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onScanRootFolders,
                enabled = rootFolders.isNotEmpty() && !scanning,
                modifier = Modifier.fillMaxWidth().testTag("scan-root-folders"),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (scanning) "Scanning…" else "Scan now")
            }
        }

        MenuSection("Logs") {
            Hint("See everything that's been imported or failed to import.")
            OutlinedButton(onClick = onViewLogHistory, modifier = Modifier.fillMaxWidth()) {
                Text("View error/log history")
            }
        }

        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun collectionKindLabel(kind: CollectionKind?): String = when (kind) {
    CollectionKind.FILES -> "Each file is a book"
    CollectionKind.FOLDERS -> "Each folder is a book"
    null -> "Files and folders are both books"
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
    )
}
