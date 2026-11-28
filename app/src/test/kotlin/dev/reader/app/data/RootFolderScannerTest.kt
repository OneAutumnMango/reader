package dev.reader.app.data

import androidx.documentfile.provider.DocumentFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.reader.data.importer.BookImporter
import dev.reader.data.repo.LibraryRepository
import dev.reader.data.repo.SettingsRepository
import dev.reader.data.room.ReaderDatabase
import dev.reader.data.store.CollectionKind
import dev.reader.data.store.ReaderStores
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class RootFolderScannerTest {

    @Test
    fun `file collections find books in nested folders and skip everything else`() = runBlocking {
        val root = createTempDirectory("library").toFile()
        try {
            fun touch(path: String) = File(root, path).apply { parentFile?.mkdirs() }.writeText("x")
            touch("top.epub")
            touch("Novel.fb2.zip")
            touch("Palm document.prc")
            touch("Series/Book 1.txt")
            touch("Series/Volume 2/Book 2.pdf")
            touch("Series/cover.jpg")
            touch(".thumbnails/hidden.epub")
            touch("notes.docx.tmp")

            val found = collectBookFiles(DocumentFile.fromFile(root)).mapNotNull { it.name }.sorted()

            assertEquals(
                listOf("Book 1.txt", "Book 2.pdf", "Novel.fb2.zip", "Palm document.prc", "top.epub"),
                found,
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `scanning a folder with two copies of the same content only imports it once`() = runBlocking {
        val root = createTempDirectory("library-dup").toFile()
        val settingsDir = createTempDirectory("library-dup-settings").toFile()
        val scope = CoroutineScope(Job())
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(appContext, ReaderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            File(root, "Book.txt").writeText("Chapter 1\n\nSame content.")
            File(root, "Book (copy).txt").writeText("Chapter 1\n\nSame content.")

            val library = LibraryRepository(database)
            val settings = SettingsRepository(ReaderStores(FileSystem.SYSTEM, settingsDir.path.toPath(), scope))
            val scanner = RootFolderScanner(appContext, settings, library, BookImporter(library))

            scanner.scanEntries(DocumentFile.fromFile(root), CollectionKind.FILES)

            assertEquals(1, library.observeBooks().first().size)
        } finally {
            database.close()
            scope.cancel()
            root.deleteRecursively()
            settingsDir.deleteRecursively()
        }
    }

    @Test
    fun `collectSeriesFolders maps nested files to their immediate parent folder`() = runBlocking {
        val root = createTempDirectory("library-series").toFile()
        try {
            fun touch(path: String) = File(root, path).apply { parentFile?.mkdirs() }.writeText("x")
            touch("top.epub")
            touch("Series/Book 1.txt")
            touch("Series/Volume 2/Book 2.pdf")
            touch(".thumbnails/x.epub")

            val found = collectSeriesFolders(DocumentFile.fromFile(root))
                .mapKeys { File(java.net.URI(it.key)).name }

            assertEquals(mapOf("Book 1.txt" to "Series", "Book 2.pdf" to "Volume 2"), found)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `tagSeries sets series from folder, clears it at the root, and skips books under an unreadable root`() =
        runBlocking {
            val root = createTempDirectory("library-tag").toFile()
            val settingsDir = createTempDirectory("library-tag-settings").toFile()
            val scope = CoroutineScope(Job())
            val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
            val database = Room.inMemoryDatabaseBuilder(appContext, ReaderDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            try {
                File(root, "Saga").mkdirs()
                File(root, "Saga/A.txt").writeText("Chapter 1\n\nAlpha.")
                File(root, "Saga/B.txt").writeText("Chapter 1\n\nBeta.")
                val library = LibraryRepository(database)
                val settings = SettingsRepository(ReaderStores(FileSystem.SYSTEM, settingsDir.path.toPath(), scope))
                val scanner = RootFolderScanner(appContext, settings, library, BookImporter(library))
                val rootKey = root.path
                val resolve = { key: String -> File(key).takeIf { it.isDirectory }?.let { DocumentFile.fromFile(it) } }
                scanner.scanEntries(DocumentFile.fromFile(root), CollectionKind.FILES)
                settings.update {
                    it.copy(rootFolders = listOf(rootKey), rootFolderKinds = mapOf(rootKey to CollectionKind.FILES))
                }

                scanner.tagSeries(resolve)
                assertEquals(listOf("Saga", "Saga"), library.observeBooks().first().map { it.series })

                // A missing root: nothing is cleared, even though the books are no longer found.
                val missing = "$rootKey-missing"
                settings.update {
                    it.copy(
                        rootFolders = listOf(rootKey, missing),
                        rootFolderKinds = mapOf(rootKey to CollectionKind.FILES, missing to CollectionKind.FILES),
                    )
                }
                File(root, "Saga").deleteRecursively()
                scanner.tagSeries(resolve)
                assertEquals(listOf("Saga", "Saga"), library.observeBooks().first().map { it.series })

                // Readable roots: the file is gone from its folder, so the series is cleared.
                settings.update { it.copy(rootFolders = listOf(rootKey)) }
                scanner.tagSeries(resolve)
                assertEquals(listOf(null, null), library.observeBooks().first().map { it.series })
            } finally {
                database.close()
                scope.cancel()
                root.deleteRecursively()
                settingsDir.deleteRecursively()
            }
        }
}
