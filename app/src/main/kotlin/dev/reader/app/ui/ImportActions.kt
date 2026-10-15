package dev.reader.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
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

internal fun describeImportError(error: Throwable): String {
    val root = generateSequence(error) { it.cause }.last()
    val message = root.message?.takeIf { it.isNotBlank() } ?: root.javaClass.simpleName
    return message
}

class ImportActions(val status: String, val addFiles: () -> Unit, val addFolder: () -> Unit)

/** Keeps permission to the picked file/folder so its original can be opened from the book's details later. */
private fun keepReadAccess(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.onFailure { AppLog.debug("No persistable access to the picked item", LOG_TAG) }
}

/** Add-files / add-light-novel-folder pickers, shared by the library quick-add FAB and Settings. */
@Composable
fun rememberImportActions(graph: ReaderGraph): ImportActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }

    val addFilesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            AppLog.debug("Starting import of ${uris.size} file(s)", LOG_TAG)
            var imported = 0
            var lastError: String? = null
            uris.forEach { uri ->
                runCatching {
                    val file = requireNotNull(ContentFileCopier.copyToCache(context, uri)) {
                        "Could not read the selected file"
                    }
                    keepReadAccess(context, uri)
                    graph.importer.importFile(file, pdfContext = context, originUri = uri.toString())
                }
                    .onSuccess { imported++ }
                    .onFailure {
                        lastError = describeImportError(it)
                        AppLog.error("File import failed: $lastError", LOG_TAG, it)
                    }
            }
            status = when {
                imported == uris.size -> "Imported $imported of ${uris.size} file(s)"
                lastError != null -> "Imported $imported of ${uris.size} file(s) — $lastError"
                else -> "Imported $imported of ${uris.size} file(s)"
            }
            if (imported == uris.size) AppLog.info(status, LOG_TAG) else AppLog.warn(status, LOG_TAG)
        }
    }
    val addFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri == null) return@rememberLauncherForActivityResult
        scope.launch {
            AppLog.debug("Starting book-folder import", LOG_TAG)
            status = runCatching {
                val folder = requireNotNull(ContentFileCopier.copyTreeToCache(context, treeUri)) {
                    "Could not read that folder"
                }
                keepReadAccess(context, treeUri)
                graph.importer.importLightNovelFolder(folder, originUri = treeUri.toString())
            }.fold(
                onSuccess = {
                    AppLog.info("Imported book from folder", LOG_TAG)
                    "Imported book from folder"
                },
                onFailure = {
                    val message = "Could not import that folder — ${describeImportError(it)}"
                    AppLog.error(message, LOG_TAG, it)
                    message
                },
            )
        }
    }

    return ImportActions(
        status = status,
        addFiles = { addFilesLauncher.launch(arrayOf("*/*")) },
        addFolder = { addFolderLauncher.launch(null) },
    )
}
