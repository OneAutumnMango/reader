package dev.reader.data.export

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import dev.reader.core.model.Bookmark
import dev.reader.core.model.BookmarkId
import dev.reader.core.model.Category
import dev.reader.core.model.CategoryId
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import dev.reader.data.repo.BookmarkRepository
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
import kotlin.test.assertEquals
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class ExportRepositoryTest {

    private lateinit var database: ReaderDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var exporter: ExportRepository

    private fun newSettings(name: String): SettingsRepository {
        val fileSystem = FakeFileSystem()
        val directory = "/$name".toPath().also { fileSystem.createDirectories(it) }
        return SettingsRepository(ReaderStores(fileSystem, directory, CoroutineScope(Job())))
    }

    private fun newDatabase(): ReaderDatabase =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReaderDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @Before
    fun setUp() {
        database = newDatabase()
        settings = newSettings("export-test")
        exporter = ExportRepository(database, settings)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `export then import on a fresh database restores everything`() = runBlocking {
        val categoryId = CategoryId("cat-1")
        LibraryRepository(database).upsertCategory(Category(categoryId, "Novels"))
        val book = Book(
            id = BookId("book-1"),
            title = "My Book",
            format = BookFormat.TXT,
            sourcePath = "/books/my-book.txt",
            categoryIds = setOf(categoryId),
            addedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
        LibraryRepository(database).upsertBook(book, listOf(Chapter(ChapterId("ch-1"), book.id, "One", "", 0)))
        val bookmark = Bookmark(
            id = BookmarkId("bm-1"),
            bookId = book.id,
            chapterId = ChapterId("ch-1"),
            position = 0.5f,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
        BookmarkRepository(database).add(bookmark)
        settings.update { it.copy(activeThemePresetName = "Sepia") }

        val bundleText = exporter.export()

        val freshDb = newDatabase()
        val freshSettings = newSettings("export-test-2")
        ExportRepository(freshDb, freshSettings).import(bundleText)

        assertEquals(listOf(book), LibraryRepository(freshDb).observeBooks().first())
        assertEquals("One", LibraryRepository(freshDb).observeChapters(book.id).first().single().title)
        assertEquals("Sepia", freshSettings.preferences.first().activeThemePresetName)
        freshDb.close()
    }
}
