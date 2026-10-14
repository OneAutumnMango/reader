package dev.reader.app.ui

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.reader.app.data.AppLog
import dev.reader.app.data.ContentFileCopier
import dev.reader.app.data.ReaderGraph
import kotlinx.coroutines.launch

private const val LOG_TAG = "ReaderImport"

/** Logs the full stack trace, records it to [AppLog], and returns the deepest error message. */
internal fun describeImportError(error: Throwable): String {
    Log.e(LOG_TAG, "Import failed", error)
    val root = generateSequence(error) { it.cause }.last()
    val message = root.message?.takeIf { it.isNotBlank() } ?: root.javaClass.simpleName
    AppLog.record("Import failed: $message")
    return message
}

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
            var lastError: String? = null
            uris.forEach { uri ->
                val file = ContentFileCopier.copyToCache(context, uri)
                if (file == null) {
                    lastError = "Could not read the selected file"
                    return@forEach
                }
                runCatching { graph.importer.importFile(file, pdfContext = context) }
                    .onSuccess { imported++ }
                    .onFailure { lastError = describeImportError(it) }
            }
            status = when {
                imported == uris.size -> "Imported $imported of ${uris.size} file(s)"
                lastError != null -> "Imported $imported of ${uris.size} file(s) — $lastError"
                else -> "Imported $imported of ${uris.size} file(s)"
            }
            AppLog.record(status)
        }
    }
    val addFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val folder = ContentFileCopier.copyTreeToCache(context, treeUri)
            status = if (folder == null) {
                "Could not read that folder"
            } else {
                runCatching { graph.importer.importLightNovelFolder(folder) }
                    .fold(
                        onSuccess = { "Imported book from folder" },
                        onFailure = { "Could not import that folder — ${describeImportError(it)}" },
                    )
            }
            AppLog.record(status)
        }
    }

    return ImportActions(
        status = status,
        addFiles = { addFilesLauncher.launch(arrayOf("*/*")) },
        addFolder = { addFolderLauncher.launch(null) },
    )
}
