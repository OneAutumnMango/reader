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
import dev.reader.app.data.collectionDisplayName
import dev.reader.app.ui.library.MissingBooksDialog
import dev.reader.app.ui.rememberImportActions
import dev.reader.core.model.Book
import dev.reader.data.store.CollectionKind
import dev.reader.data.store.PreferencesFile
import kotlinx.coroutines.launch

@Composable
fun LibrarySourcesRoute(graph: ReaderGraph, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val actions = rememberImportActions(graph)
    val preferences by graph.settings.preferences.collectAsState(initial = PreferencesFile())
    var scanStatus by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var missingBooks by remember { mutableStateOf<List<Book>>(emptyList()) }

    fun scan(message: (Int) -> String) {
        scanning = true
        scanStatus = "Scanning library folders…"
        scope.launch {
            val result = graph.rootFolderScanner.scanAll()
            scanStatus = message(result.imported)
            missingBooks = result.missingBooks
            scanning = false
        }
    }

    var pendingKind by remember { mutableStateOf(CollectionKind.FILES) }
    val addCollectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri == null) return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val kind = pendingKind
        val providerName = runCatching { DocumentFile.fromTreeUri(context, treeUri)?.name }.getOrNull()
        scope.launch {
            graph.settings.update {
                val uri = treeUri.toString()
                it.copy(
                    rootFolders = (it.rootFolders + uri).distinct(),
                    rootFolderKinds = it.rootFolderKinds + (uri to kind),
                    rootFolderNames = it.rootFolderNames +
                        (uri to collectionDisplayName(treeUri, it.rootFolderNames[uri], providerName)),
                )
            }
            scan { "Added collection, imported $it new item(s)" }
        }
    }

    val rootFolders = preferences.rootFolders.map { uriString ->
        val uri = Uri.parse(uriString)
        val providerName = runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
        val name = collectionDisplayName(uri, preferences.rootFolderNames[uriString], providerName)
        RootFolderDisplay(uri = uriString, displayName = name, kind = preferences.rootFolderKinds[uriString])
    }

    LibrarySourcesScreen(
        status = scanStatus.ifBlank { actions.status },
        rootFolders = rootFolders,
        onAddBookFile = actions.addFiles,
        onAddBookFolder = actions.addFolder,
        onAddCollection = { kind ->
            pendingKind = kind
            addCollectionLauncher.launch(null)
        },
        onScanRootFolders = { scan { "Scan complete, imported $it new item(s)" } },
        scanning = scanning,
        onRemoveRootFolder = { uri ->
            scope.launch {
                graph.settings.update {
                    it.copy(
                        rootFolders = it.rootFolders - uri,
                        rootFolderKinds = it.rootFolderKinds - uri,
                        rootFolderNames = it.rootFolderNames - uri,
                    )
                }
            }
        },
        modifier = modifier,
    )

    if (missingBooks.isNotEmpty()) {
        MissingBooksDialog(
            books = missingBooks,
            onRemoveAll = {
                val toRemove = missingBooks
                missingBooks = emptyList()
                scope.launch { toRemove.forEach { graph.library.deleteBook(it.id) } }
            },
            onDismiss = { missingBooks = emptyList() },
        )
    }
}
