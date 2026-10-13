package dev.reader.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.reader.core.model.BookId
import dev.reader.core.model.Bookmark
import dev.reader.core.model.BookmarkId
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
