package dev.reader.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.data.importer.BookImporter
import dev.reader.data.repo.LibraryRepository
import dev.reader.data.repo.SettingsRepository
import dev.reader.data.store.CollectionKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Scans the user's saved root folders and imports any entry that isn't already in the library —
 * each top-level file and/or subfolder (per its [CollectionKind]) becomes a book.
 */
class RootFolderScanner(
    private val context: Context,
    private val settings: SettingsRepository,
    private val library: LibraryRepository,
    private val importer: BookImporter,
) {

    private val mutex = Mutex()

    suspend fun scanAll(): Int = mutex.withLock {
        withContext(Dispatchers.IO) {
            var imported = 0
            val prefs = settings.preferences.first()
            AppLog.debug("Scanning ${prefs.rootFolders.size} library folder(s)", "LibraryScan")
            for (rootUri in prefs.rootFolders) {
                runCatching { imported += scanRoot(Uri.parse(rootUri), prefs.rootFolderKinds[rootUri]) }
                    .onFailure { AppLog.error("Couldn't scan library folder", "LibraryScan", it) }
            }
            AppLog.info("Library folder scan imported $imported new item(s)", "LibraryScan")
            imported
        }
    }

    private suspend fun scanRoot(rootUri: Uri, kind: CollectionKind?): Int {
        val root = DocumentFile.fromTreeUri(context, rootUri)
        if (root == null || !root.canRead()) {
            AppLog.warn("Saved library folder is unavailable or unreadable", "LibraryScan")
            return 0
        }
        var imported = 0
        val entries = root.listFiles().filter { entry ->
            when (kind) {
                CollectionKind.FILES -> entry.isFile
                CollectionKind.FOLDERS -> entry.isDirectory
                null -> true
            }
        }
        AppLog.debug("Found ${entries.size} matching library-folder entry(s)", "LibraryScan")
        for (child in entries) {
            val key = child.uri.toString()
            val knownSource = settings.preferences.first().rootEntryBooks[key]
            if (knownSource != null && library.findBySourcePath(knownSource) != null) continue

            runCatching { importEntry(child) }
                .onFailure { AppLog.error("Library entry import failed", "LibraryScan", it) }
                .getOrNull()
                ?.let { book ->
                    settings.update { it.copy(rootEntryBooks = it.rootEntryBooks + (key to book.sourcePath)) }
                    imported++
                }
        }
        return imported
    }

    private suspend fun importEntry(entry: DocumentFile): Book? {
        if (entry.isDirectory) {
            return importer.importLightNovelFolder(ContentFileCopier.copyFolderToCache(context, entry))
        }
        BookFormat.fromExtension(entry.name.orEmpty().substringAfterLast('.', "")) ?: return null
        val file = ContentFileCopier.copyDocumentToCache(context, entry)
        if (file == null) {
            AppLog.warn("Couldn't read a supported library-folder entry", "LibraryScan")
            return null
        }
        return importer.importFile(file, pdfContext = context)
    }
}
