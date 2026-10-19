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
        val root = runCatching { DocumentFile.fromTreeUri(context, rootUri) }.getOrNull()
        val providerName = runCatching { root?.name }.getOrNull()
        val uriString = rootUri.toString()
        if (settings.preferences.first().rootFolderNames[uriString].isNullOrBlank()) {
            settings.update { preferences ->
                if (!preferences.rootFolderNames[uriString].isNullOrBlank()) {
                    preferences
                } else {
                    preferences.copy(
                        rootFolderNames = preferences.rootFolderNames +
                            (uriString to collectionDisplayName(rootUri, providerName = providerName)),
                    )
                }
            }
        }
        if (root == null || !root.canRead()) {
            AppLog.warn("Saved library folder is unavailable or unreadable", "LibraryScan")
            return 0
        }
        var imported = 0
        val entries = when (kind) {
            CollectionKind.FILES -> collectBookFiles(root)
            CollectionKind.FOLDERS -> root.listFiles().filter { it.isDirectory }
            null -> root.listFiles().toList()
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
            return importer.importLightNovelFolder(
                ContentFileCopier.copyFolderToCache(context, entry),
                originUri = entry.uri.toString(),
            )
        }
        BookFormat.fromExtension(entry.name.orEmpty().substringAfterLast('.', "")) ?: return null
        val file = ContentFileCopier.copyDocumentToCache(context, entry)
        if (file == null) {
            AppLog.warn("Couldn't read a supported library-folder entry", "LibraryScan")
            return null
        }
        return importer.importFile(file, pdfContext = context, originUri = entry.uri.toString())
    }
}

private const val MAX_FOLDER_DEPTH = 12

/** Every supported book file under [folder], however deeply nested; hidden folders (".thumbnails" etc.) are skipped. */
internal fun collectBookFiles(folder: DocumentFile, depth: Int = 0): List<DocumentFile> {
    if (depth > MAX_FOLDER_DEPTH) return emptyList()
    return folder.listFiles().flatMap { entry ->
        val name = entry.name.orEmpty()
        when {
            entry.isDirectory && !name.startsWith(".") -> collectBookFiles(entry, depth + 1)
            entry.isFile && BookFormat.fromExtension(name.substringAfterLast('.', "")) != null -> listOf(entry)
            else -> emptyList()
        }
    }
}
