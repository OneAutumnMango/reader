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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** The result of a library scan: how many new items were imported, and any book whose source file is now gone. */
data class LibraryScanResult(val imported: Int, val missingBooks: List<Book>)

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

    suspend fun scanAll(): LibraryScanResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            var imported = 0
            val prefs = settings.preferences.first()
            AppLog.debug("Scanning ${prefs.rootFolders.size} library folder(s)", "LibraryScan")
            for (rootUri in prefs.rootFolders) {
                runCatching { imported += scanRoot(Uri.parse(rootUri), prefs.rootFolderKinds[rootUri]) }
                    .onFailure { AppLog.error("Couldn't scan library folder", "LibraryScan", it) }
            }
            AppLog.info("Library folder scan imported $imported new item(s)", "LibraryScan")
            val missing = library.observeBooks().first().filterNot { File(it.sourcePath).exists() }
            if (missing.isNotEmpty()) {
                AppLog.warn("${missing.size} book(s) in the library have a missing source file", "LibraryScan")
            }
            LibraryScanResult(imported, missing)
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
        return scanEntries(root, kind)
    }

    /** Imports every new entry under [root]. Internal so a test can drive it without a real SAF tree Uri. */
    internal suspend fun scanEntries(root: DocumentFile, kind: CollectionKind?): Int {
        val entries = when (kind) {
            CollectionKind.FILES -> collectBookFiles(root)
            CollectionKind.FOLDERS -> root.listFiles().filter { it.isDirectory }
            null -> root.listFiles().toList()
        }
        AppLog.debug("Found ${entries.size} matching library-folder entry(s)", "LibraryScan")
        // Each entry's "already known?" SAF/DB lookup is latency-bound rather than CPU-bound, so
        // running those concurrently is what keeps a 30-book library's rescan quick. The actual
        // imports below stay sequential: importEntry's content-hash dedup is a check-then-insert,
        // so importing two new, identical-content files concurrently could let both pass the
        // dedup check before either is inserted, creating two library entries for one book.
        val knownSources = settings.preferences.first().rootEntryBooks
        val newEntries = coroutineScope {
            entries.map { child ->
                async(Dispatchers.IO) {
                    val knownSource = knownSources[child.uri.toString()]
                    if (knownSource != null && library.findBySourcePath(knownSource) != null) null else child
                }
            }.awaitAll()
        }.filterNotNull()
        val imports = newEntries.mapNotNull { child ->
            runCatching { importEntry(child) }
                .onFailure { AppLog.error("Library entry import failed", "LibraryScan", it) }
                .getOrNull()
                ?.let { book -> child.uri.toString() to book.sourcePath }
        }
        if (imports.isNotEmpty()) {
            settings.update { it.copy(rootEntryBooks = it.rootEntryBooks + imports) }
        }
        return imports.size
    }

    private suspend fun importEntry(entry: DocumentFile): Book? {
        if (entry.isDirectory) {
            return importer.importLightNovelFolder(
                ContentFileCopier.copyFolderToCache(context, entry),
                originUri = entry.uri.toString(),
            )
        }
        BookFormat.fromFileName(entry.name.orEmpty()) ?: return null
        val file = ContentFileCopier.copyDocumentToCache(context, entry)
        if (file == null) {
            AppLog.warn("Couldn't read a supported library-folder entry", "LibraryScan")
            return null
        }
        return importer.importFile(file, pdfContext = context, originUri = entry.uri.toString())
    }
}

private const val MAX_FOLDER_DEPTH = 12

/**
 * Every supported book file under [folder], however deeply nested; hidden folders (".thumbnails"
 * etc.) are skipped. Each child's name/type check is its own SAF round trip over a tree Uri, so
 * siblings are probed concurrently rather than one at a time.
 */
internal suspend fun collectBookFiles(folder: DocumentFile, depth: Int = 0): List<DocumentFile> {
    if (depth > MAX_FOLDER_DEPTH) return emptyList()
    return coroutineScope {
        folder.listFiles().map { entry ->
            async(Dispatchers.IO) {
                val name = entry.name.orEmpty()
                when {
                    entry.isDirectory && !name.startsWith(".") -> collectBookFiles(entry, depth + 1)
                    entry.isFile && BookFormat.fromFileName(name) != null -> listOf(entry)
                    else -> emptyList()
                }
            }
        }.awaitAll().flatten()
    }
}
