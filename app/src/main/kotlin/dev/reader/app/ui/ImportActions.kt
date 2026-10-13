package dev.reader.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.reader.app.data.ContentFileCopier
import dev.reader.app.data.ReaderGraph
import kotlinx.coroutines.launch

class ImportActions(val status: String, val addFiles: () -> Unit, val addFolder: () -> Unit)

/** Add-files / add-light-novel-folder pickers, shared by the library quick-add FAB and Settings. */
@Composable
fun rememberImportActions(graph: ReaderGraph): ImportActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }

    val addFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        scope.launch {
            var imported = 0
            uris.forEach { uri ->
                val file = ContentFileCopier.copyToCache(context, uri) ?: return@forEach
                if (runCatching { graph.importer.importFile(file) }.isSuccess) imported++
            }
            status = "Imported $imported of ${uris.size} file(s)"
        }
    }
    val addFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val folder = ContentFileCopier.copyTreeToCache(context, treeUri)
            val imported = folder?.let { runCatching { graph.importer.importLightNovelFolder(it) }.isSuccess } == true
            status = if (imported) "Imported light novel" else "Could not import that folder"
        }
    }

    return ImportActions(
        status = status,
        addFiles = { addFilesLauncher.launch(arrayOf("*/*")) },
        addFolder = { addFolderLauncher.launch(null) },
    )
}
