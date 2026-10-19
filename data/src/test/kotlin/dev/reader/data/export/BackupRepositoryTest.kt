package dev.reader.data.export

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import dev.reader.data.repo.LibraryRepository
import dev.reader.data.repo.SettingsRepository
import dev.reader.data.room.ReaderDatabase
import dev.reader.data.store.ReaderStores
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {

    private lateinit var tempDir: File
    private val databases = mutableListOf<ReaderDatabase>()

    private fun newDatabase(): ReaderDatabase =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReaderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
            .also { databases += it }

    private fun newSettings(name: String): SettingsRepository {
        val fileSystem = FakeFileSystem()
        val directory = "/$name-${java.util.UUID.randomUUID()}".toPath().also { fileSystem.createDirectories(it) }
        return SettingsRepository(ReaderStores(fileSystem, directory, CoroutineScope(Job())))
    }

    @Before
    fun setUp() {
        tempDir = File.createTempFile("backup", "").apply {
            delete()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        databases.forEach { it.close() }
        tempDir.deleteRecursively()
    }

    private fun seededSource(): Triple<ReaderDatabase, ExportRepository, Book> {
        val database = newDatabase()
        val source = File(tempDir, "source/My Book.txt").apply {
            parentFile?.mkdirs()
            writeText("Hello")
        }
        val sourceCover = File(tempDir, "source/My Book.jpg").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val book = Book(
            id = BookId("book-1"),
            title = "My Book",
            format = BookFormat.TXT,
            sourcePath = source.path,
            coverPath = sourceCover.path,
            addedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
        val chapters = listOf(Chapter(ChapterId("ch-1"), book.id, "One", "", 0))
        runBlocking { LibraryRepository(database).upsertBook(book, chapters) }
        return Triple(database, ExportRepository(database, newSettings("source")), book)
    }

    @Test
    fun `a backup with book files restores a readable copy of each book`() = runBlocking {
        val (_, exporter, book) = seededSource()
        val zip = ByteArrayOutputStream().also { BackupRepository(exporter, tempDir).write(it, includeBooks = true) }

        val restoredDb = newDatabase()
        val restoreRoot = File(tempDir, "restore")
        BackupRepository(ExportRepository(restoredDb, newSettings("dest")), restoreRoot, now = { 1L })
            .restore(ByteArrayInputStream(zip.toByteArray()))

        val restored = LibraryRepository(restoredDb).observeBooks().first().single()
        assertEquals(book.id, restored.id)
        assertNotEquals(book.sourcePath, restored.sourcePath)
        assertEquals("Hello", File(restored.sourcePath).readText())
    }

    @Test
    fun `a backup without book files restores cover assets`() = runBlocking {
        val (_, exporter, book) = seededSource()
        val zip = ByteArrayOutputStream().also { BackupRepository(exporter, tempDir).write(it, includeBooks = false) }
        val entries = ZipInputStream(ByteArrayInputStream(zip.toByteArray())).use { input ->
            generateSequence { input.nextEntry }.map { it.name }.toList()
        }

        val restoredDb = newDatabase()
        val restoredCoverDirectory = File(tempDir, "durable-covers")
        BackupRepository(
            ExportRepository(restoredDb, newSettings("dest"), coverDirectory = restoredCoverDirectory),
            File(tempDir, "restore"),
        )
            .restore(ByteArrayInputStream(zip.toByteArray()))

        val restored = LibraryRepository(restoredDb).observeBooks().first().single()
        assertEquals(book.sourcePath, restored.sourcePath)
        assertEquals(byteArrayOf(4, 5, 6).toList(), File(requireNotNull(restored.coverPath)).readBytes().toList())
        assertEquals(listOf("backup.json"), entries)
    }

    @Test
    fun `entries that escape the restore folder are rejected`() {
        val zip = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { out ->
                out.putNextEntry(ZipEntry("../../evil.txt"))
                out.write("x".toByteArray())
                out.closeEntry()
            }
        }
        val backups = BackupRepository(ExportRepository(newDatabase(), newSettings("dest")), File(tempDir, "restore"))

        assertFailsWith<IllegalArgumentException> {
            runBlocking { backups.restore(ByteArrayInputStream(zip.toByteArray())) }
        }
    }
}
