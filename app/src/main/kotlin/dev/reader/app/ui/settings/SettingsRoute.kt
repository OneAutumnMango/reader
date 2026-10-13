package dev.reader.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import dev.reader.app.data.ReaderGraph
import dev.reader.app.ui.rememberImportActions
import dev.reader.data.store.PreferencesFile
import kotlinx.coroutines.launch

@Composable
fun SettingsRoute(graph: ReaderGraph, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val actions = rememberImportActions(graph)
    val preferences by graph.settings.preferences.collectAsState(initial = PreferencesFile())
    var scanStatus by remember { mutableStateOf("") }

    val addRootFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri == null) return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        scope.launch {
            graph.settings.update { it.copy(rootFolders = it.rootFolders + treeUri.toString()) }
            val imported = graph.rootFolderScanner.scanAll()
            scanStatus = "Added root folder, imported $imported new item(s)"
        }
    }

    val rootFolders = preferences.rootFolders.map { uriString ->
        val uri = Uri.parse(uriString)
        val name = DocumentFile.fromTreeUri(context, uri)?.name ?: uri.lastPathSegment ?: uriString
        RootFolderDisplay(uri = uriString, displayName = name)
    }

    SettingsScreen(
        status = scanStatus.ifBlank { actions.status },
        rootFolders = rootFolders,
        onAddBook = actions.addFiles,
        onAddLightNovel = actions.addFolder,
        onAddRootFolder = { addRootFolderLauncher.launch(null) },
        onRemoveRootFolder = { uri ->
            scope.launch { graph.settings.update { it.copy(rootFolders = it.rootFolders - uri) } }
        },
        modifier = modifier,
    )
}
