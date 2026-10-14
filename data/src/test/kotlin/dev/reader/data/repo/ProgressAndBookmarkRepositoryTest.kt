package dev.reader.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import dev.reader.core.model.Bookmark
import dev.reader.core.model.BookmarkId
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import dev.reader.core.model.Progress
import dev.reader.data.room.ReaderDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class ProgressAndBookmarkRepositoryTest {

    private lateinit var database: ReaderDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReaderDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `progress is stored and finished count updates`() = runBlocking {
        val repository = ProgressRepository(database)
        val bookId = BookId("book-1")

        assertNull(repository.observe(bookId).first())
        assertEquals(0, repository.observeFinishedCount().first())

        val progress = Progress(
            bookId = bookId,
            chapterId = ChapterId("ch-1"),
            position = 0.5f,
            updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
            finished = true,
        )
        repository.update(progress)

        assertEquals(progress, repository.observe(bookId).first())
        assertEquals(1, repository.observeFinishedCount().first())
    }

    @Test
    fun `chapters are marked read individually and book progress averages real chapters`() = runBlocking {
        val repository = ProgressRepository(database)
        val now = Instant.parse("2026-01-01T00:00:00Z")
        val book = Book(BookId("book-1"), "Book", format = BookFormat.EPUB, sourcePath = "/b.epub", addedAt = now)
        val chapters = listOf(
            Chapter(ChapterId("cover"), book.id, "Cover", "", 0, isExtra = true),
            Chapter(ChapterId("ch-1"), book.id, "One", "", 1),
            Chapter(ChapterId("ch-2"), book.id, "Two", "", 2),
            Chapter(ChapterId("ch-3"), book.id, "Three", "", 3),
            Chapter(ChapterId("ch-4"), book.id, "Four", "", 4),
        )
        LibraryRepository(database).upsertBook(book, chapters)

        repository.updateChapterProgress(Progress(book.id, ChapterId("ch-3"), 0.5f, now))
        repository.setChaptersRead(book.id, listOf(ChapterId("ch-1"), ChapterId("cover")), read = true, now = now)

        val progress = repository.observeChapterProgress(book.id).first()
        assertEquals(true, progress[ChapterId("ch-1")]?.finished)
        assertNull(progress[ChapterId("ch-2")])
        assertEquals(false, progress[ChapterId("ch-3")]?.finished)
        assertEquals((1f + 0.5f) / 4, repository.observeBookFractions().first()[book.id])

        repository.setChaptersRead(book.id, listOf(ChapterId("ch-1")), read = false, now = now)
        val unread = repository.observeChapterProgress(book.id).first()[ChapterId("ch-1")]
        assertEquals(false, unread?.finished)
        assertEquals(0f, unread?.position)
    }

    @Test
    fun `bookmarks can be added and removed`() = runBlocking {
        val repository = BookmarkRepository(database)
        val bookmark = Bookmark(
            id = BookmarkId("bm-1"),
            bookId = BookId("book-1"),
            chapterId = ChapterId("ch-1"),
            position = 0.25f,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

        repository.add(bookmark)
        assertEquals(listOf(bookmark), repository.observe(bookmark.bookId).first())

        repository.remove(bookmark)
        assertEquals(emptyList(), repository.observe(bookmark.bookId).first())
    }
}
