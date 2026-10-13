package dev.reader.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.reader.app.ui.describeImportError
import dev.reader.data.repo.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Scans the user's saved root folders for files/subfolders not seen before and imports them —
 * each top-level file becomes a book, each top-level subfolder becomes a light novel.
 */
class RootFolderScanner(
    private val context: Context,
    private val settings: SettingsRepository,
    private val importer: dev.reader.data.importer.BookImporter,
) {

    suspend fun scanAll(): Int {
        val prefs = settings.preferences.first()
        var imported = 0
        for (rootUri in prefs.rootFolders) {
            imported += scanRoot(Uri.parse(rootUri))
        }
        if (imported > 0) AppLog.record("Root folder scan imported $imported new item(s)")
        return imported
    }

    private suspend fun scanRoot(rootUri: Uri): Int {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return 0
        var imported = 0
        for (child in root.listFiles()) {
            val key = child.uri.toString()
            val alreadySeen = settings.preferences.first().importedRootEntries.contains(key)
            if (alreadySeen) continue

            val success = if (child.isDirectory) {
                val folder = ContentFileCopier.copyFolderToCache(context, child)
                runCatching { importer.importLightNovelFolder(folder) }
                    .onFailure { describeImportError(it) }
                    .isSuccess
            } else {
                val file = ContentFileCopier.copyDocumentToCache(context, child)
                file != null &&
                    runCatching { importer.importFile(file, pdfContext = context) }
                        .onFailure { describeImportError(it) }
                        .isSuccess
            }

            if (success) imported++
            settings.update { it.copy(importedRootEntries = it.importedRootEntries + key) }
        }
        return imported
    }
}
