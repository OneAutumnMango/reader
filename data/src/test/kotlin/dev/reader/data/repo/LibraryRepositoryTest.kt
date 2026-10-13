package dev.reader.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import dev.reader.core.model.Category
import dev.reader.core.model.CategoryId
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import dev.reader.data.room.ReaderDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class LibraryRepositoryTest {

    private lateinit var database: ReaderDatabase
    private lateinit var repository: LibraryRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReaderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LibraryRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `upsertBook stores the book, its chapters and category links`() = runBlocking {
        val categoryId = CategoryId("cat-1")
        repository.upsertCategory(Category(categoryId, "Novels"))

        val book = Book(
            id = BookId("book-1"),
            title = "My Book",
            format = BookFormat.EPUB,
            sourcePath = "/books/my-book.epub",
            categoryIds = setOf(categoryId),
            addedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
        val chapters = listOf(
            Chapter(ChapterId("ch-2"), book.id, "Two", "ch2.xhtml", orderIndex = 1),
            Chapter(ChapterId("ch-1"), book.id, "One", "ch1.xhtml", orderIndex = 0),
        )

        repository.upsertBook(book, chapters)

        val stored = repository.observeBooks().first()
        assertEquals(listOf(book), stored)

        val storedChapters = repository.observeChapters(book.id).first()
        assertEquals(listOf("One", "Two"), storedChapters.map { it.title })

        val inCategory = repository.observeBooksInCategory(categoryId).first()
        assertTrue(inCategory.single().id == book.id)
    }

    @Test
    fun `deleteBook removes the book and its chapters`() = runBlocking {
        val book = Book(
            id = BookId("book-1"),
            title = "Gone",
            format = BookFormat.TXT,
            sourcePath = "/books/gone.txt",
            addedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
        repository.upsertBook(book, listOf(Chapter(ChapterId("ch-1"), book.id, "One", "ch1.txt", 0)))

        repository.deleteBook(book.id)

        assertEquals(emptyList(), repository.observeBooks().first())
        assertEquals(emptyList(), repository.observeChapters(book.id).first())
    }

    @Test
    fun `updateCategories replaces a book's category links`() = runBlocking {
        val novels = CategoryId("novels")
        val comics = CategoryId("comics")
        repository.upsertCategory(Category(novels, "Novels"))
        repository.upsertCategory(Category(comics, "Comics"))

        val book = Book(
            id = BookId("book-1"),
            title = "Recategorized",
            format = BookFormat.TXT,
            sourcePath = "/books/recat.txt",
            categoryIds = setOf(novels),
            addedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
        repository.upsertBook(book, emptyList())

        repository.updateCategories(book.id, setOf(comics))

        assertEquals(emptyList(), repository.observeBooksInCategory(novels).first())
        assertEquals(listOf(book.id), repository.observeBooksInCategory(comics).first().map { it.id })
    }
}
