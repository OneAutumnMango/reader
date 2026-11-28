package dev.reader.data.sync

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import dev.reader.core.model.Category
import dev.reader.core.model.CategoryId
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import dev.reader.core.sync.CategoriesValue
import dev.reader.core.sync.ItemKey
import dev.reader.core.sync.ItemKind
import dev.reader.core.sync.PositionValue
import dev.reader.data.repo.LibraryRepository
import dev.reader.data.room.ReaderDatabase
import dev.reader.data.room.entity.ChapterProgressEntity
import dev.reader.data.room.entity.ProgressEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class SyncLibraryTest {

    private lateinit var database: ReaderDatabase
    private lateinit var repository: LibraryRepository
    private lateinit var library: SyncLibrary

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReaderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LibraryRepository(database)
        library = SyncLibrary(database)
    }

    @After
    fun tearDown() = database.close()

    private fun chapters(bookId: String) = listOf(
        Chapter(ChapterId("$bookId-0"), BookId(bookId), "Cover", "", 0, wordCount = 1, isExtra = true),
        Chapter(ChapterId("$bookId-1"), BookId(bookId), "One", "", 1, wordCount = 100),
    )

    private suspend fun addBook(id: String, hash: String? = "hash-$id", vararg categories: String) {
        val book = Book(
            BookId(id),
            "Title $id",
            format = BookFormat.EPUB,
            sourcePath = "/$id.epub",
            addedAt = Instant.fromEpochMilliseconds(0),
            contentHash = hash,
            categoryIds = categories.map { CategoryId(it) }.toSet(),
        )
        repository.upsertBook(book, chapters(id))
    }

    private suspend fun addCategory(id: String, name: String, order: Int = 0) =
        repository.upsertCategory(Category(CategoryId(id), name, order))

    private suspend fun categoryNames() = repository.observeCategories().first().map { it.name }

    private fun positionKey(id: String) = ItemKey("hash-$id", ItemKind.BOOK_POSITION)

    private fun chapterKey(id: String, chapter: Int) = ItemKey("hash-$id", ItemKind.CHAPTER, chapter)

    private fun categoriesKey(id: String) = ItemKey("hash-$id", ItemKind.CATEGORIES)

    @Test
    fun `snapshot maps positions chapters and categories`() = runBlocking {
        addCategory("c1", " Reading ")
        addCategory("blank", "  ", 1)
        addBook("a", "hash-a", "c1", "blank", "gone")
        database.progress().upsert(ProgressEntity("a", "a-1", 0.5f, 10, false))
        database.chapterProgress().upsert(ChapterProgressEntity("a", "a-1", 0.25f, 9, true))

        val book = library.snapshot().books.getValue("hash-a")

        assertEquals(BookId("a"), book.bookId)
        assertEquals("Title a", book.title)
        assertEquals(chapterSignature(chapters("a")), book.signature)
        assertEquals(mapOf(0 to ChapterId("a-0"), 1 to ChapterId("a-1")), book.chapterIds)
        assertEquals(PositionValue(1, 0.5f, false, 10), book.position)
        assertEquals(
            mapOf(0 to PositionValue(0, 0f, false, 0), 1 to PositionValue(1, 0.25f, true, 9)),
            book.chapters,
        )
        assertEquals(CategoriesValue(listOf("Reading")), book.categories)
    }

    @Test
    fun `out of range stored positions are made publishable`() = runBlocking {
        addBook("a")
        database.progress().upsert(ProgressEntity("a", "a-1", 1.5f, -1, false))

        assertEquals(PositionValue(1, 1f, false, 0), library.snapshot().books.getValue("hash-a").position)
    }

    @Test
    fun `signature changes with word count but not list order`() {
        val chapters = chapters("a")

        assertEquals(chapterSignature(chapters), chapterSignature(chapters.reversed()))
        assertNotEquals(
            chapterSignature(chapters),
            chapterSignature(listOf(chapters[0], chapters[1].copy(wordCount = 101))),
        )
    }

    @Test
    fun `books without a hash or sharing one are skipped`() = runBlocking {
        addBook("a", null)
        addBook("b", "same")
        addBook("c", "same")

        val snapshot = library.snapshot()

        assertEquals(emptyMap(), snapshot.books)
        assertEquals(
            setOf(
                SkippedBook("Title a", "No file hash"),
                SkippedBook("Title b", "Same file appears twice"),
                SkippedBook("Title c", "Same file appears twice"),
            ),
            snapshot.skipped.toSet(),
        )
    }

    @Test
    fun `duplicate category names skip categories for affected books only`() = runBlocking {
        addCategory("c1", "Reading")
        addCategory("c2", "reading ", 1)
        addCategory("c3", "Done", 2)
        addBook("a", "hash-a", "c1")
        addBook("b", "hash-b", "c3")

        val books = library.snapshot().books

        assertNull(books.getValue("hash-a").categories)
        assertEquals(CategoriesValue(listOf("Done")), books.getValue("hash-b").categories)

        library.write(library.snapshot(), mapOf(categoriesKey("a") to CategoriesValue(listOf("Done"))))
        assertEquals(listOf("c1"), database.books().categoryIdsFor("a"))
    }

    @Test
    fun `write stores positions with their change time`() = runBlocking {
        addBook("a")
        val value = PositionValue(1, 0.5f, true, 42)

        library.write(library.snapshot(), mapOf(positionKey("a") to value, chapterKey("a", 1) to value))

        val book = library.snapshot().books.getValue("hash-a")
        assertEquals(value, book.position)
        assertEquals(value, book.chapters[1])
    }

    @Test
    fun `write creates a missing category once for two books`() = runBlocking {
        addCategory("c1", "Old", 4)
        addBook("a")
        addBook("b")

        library.write(
            library.snapshot(),
            mapOf(
                categoriesKey("a") to CategoriesValue(listOf("New", " new ")),
                categoriesKey("b") to CategoriesValue(listOf("NEW")),
            ),
        )

        assertEquals(listOf("Old", "New"), categoryNames())
        assertEquals(listOf(4, 5), database.categories().all().map { it.orderIndex })
        assertEquals(database.books().categoryIdsFor("a"), database.books().categoryIdsFor("b"))
        assertEquals(1, database.books().categoryIdsFor("a").size)
    }

    @Test
    fun `write reuses a category differing in case and spaces`() = runBlocking {
        addCategory("c1", "Reading")
        addBook("a")

        library.write(library.snapshot(), mapOf(categoriesKey("a") to CategoriesValue(listOf("reading "))))

        assertEquals(listOf("Reading"), categoryNames())
        assertEquals(listOf("c1"), database.books().categoryIdsFor("a"))
    }

    @Test
    fun `write never creates a blank category`() = runBlocking {
        addBook("a")

        library.write(
            library.snapshot(),
            mapOf(categoriesKey("a") to CategoriesValue(listOf("", " \t\n", "\u0000", "​"))),
        )

        assertEquals(emptyList(), categoryNames())
        assertEquals(emptyList(), database.books().categoryIdsFor("a"))
    }

    @Test
    fun `write removes categories but keeps blank local ones`() = runBlocking {
        addCategory("c1", "Reading")
        addCategory("blank", " ", 1)
        addBook("a", "hash-a", "c1", "blank")

        library.write(library.snapshot(), mapOf(categoriesKey("a") to null))

        assertEquals(listOf("blank"), database.books().categoryIdsFor("a"))
    }

    @Test
    fun `write with null position deletes the row`() = runBlocking {
        addBook("a")
        database.progress().upsert(ProgressEntity("a", "a-1", 0.5f, 10, false))
        database.chapterProgress().upsert(ChapterProgressEntity("a", "a-1", 0.5f, 10, false))

        library.write(library.snapshot(), mapOf(positionKey("a") to null, chapterKey("a", 1) to null))

        assertNull(database.progress().observe("a").first())
        assertEquals(PositionValue(1, 0f, false, 0), library.snapshot().books.getValue("hash-a").chapters[1])
    }

    @Test
    fun `unknown chapter index is ignored`() = runBlocking {
        addBook("a")
        database.progress().upsert(ProgressEntity("a", "a-1", 0.5f, 10, false))
        val value = PositionValue(9, 0.9f, true, 42)

        library.write(
            library.snapshot(),
            mapOf(positionKey("a") to value, chapterKey("a", 9) to value, positionKey("nope") to value),
        )

        assertEquals(ProgressEntity("a", "a-1", 0.5f, 10, false), database.progress().observe("a").first())
        assertEquals(emptyList(), database.chapterProgress().all())
    }

    @Test
    fun `a failure during write leaves the database unchanged`() = runBlocking {
        addCategory("c1", "Old")
        addBook("a", "hash-a", "c1")
        var calls = 0
        val failing = SyncLibrary(database) { if (++calls == 1) "n1" else error("boom") }

        assertFailsWith<IllegalStateException> {
            failing.write(
                failing.snapshot(),
                mapOf(
                    positionKey("a") to PositionValue(1, 0.5f, false, 1),
                    categoriesKey("a") to CategoriesValue(listOf("First", "Second")),
                ),
            )
        }

        assertNull(database.progress().observe("a").first())
        assertEquals(listOf("Old"), categoryNames())
        assertEquals(listOf("c1"), database.books().categoryIdsFor("a"))
    }

    @Test
    fun `local category names are normalized the same way as a peer's`() = runBlocking {
        val long = "n".repeat(120)
        addCategory("c1", " Sci\u0001Fi ")
        addCategory("c2", long, 1)
        addBook("a", "hash-a", "c1", "c2")

        val published = library.snapshot().books.getValue("hash-a").categories
        assertEquals(CategoriesValue(listOf("SciFi", "n".repeat(100))), published)

        library.write(library.snapshot(), mapOf(categoriesKey("a") to published))

        assertEquals(listOf(" Sci\u0001Fi ", long), categoryNames())
        assertEquals(setOf("c1", "c2"), database.books().categoryIdsFor("a").toSet())
        assertEquals(published, library.snapshot().books.getValue("hash-a").categories)
    }

    @Test
    fun `write creates a normalized category name`() = runBlocking {
        addBook("a")

        library.write(
            library.snapshot(),
            mapOf(categoriesKey("a") to CategoriesValue(listOf(" Ne\u0001w ", "m".repeat(120)))),
        )

        assertEquals(listOf("New", "m".repeat(100)), categoryNames())
    }

    @Test
    fun `local names that normalize alike are ambiguous`() = runBlocking {
        addCategory("c1", "AB")
        addCategory("c2", "a\u0001b", 1)
        addBook("a", "hash-a", "c1")

        assertNull(library.snapshot().books.getValue("hash-a").categories)
    }

    // Reading through a Flow would hang here or miss the transaction's own writes.
    @Test
    fun `snapshot reads within the surrounding transaction`() = runBlocking {
        withTimeout(10_000) {
            database.withTransaction {
                addBook("a")
                database.progress().upsert(ProgressEntity("a", "a-1", 0.5f, 10, false))

                assertEquals(PositionValue(1, 0.5f, false, 10), library.snapshot().books.getValue("hash-a").position)
            }
        }
    }
}
