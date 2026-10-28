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
}
