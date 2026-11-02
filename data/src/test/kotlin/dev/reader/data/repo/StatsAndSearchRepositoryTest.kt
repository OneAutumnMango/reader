package dev.reader.data.repo

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.reader.core.model.BookId
import dev.reader.core.model.ChapterId
import dev.reader.core.stats.ReadingSessionEvent
import dev.reader.data.room.ReaderDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class StatsAndSearchRepositoryTest {

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
    fun `stats summary reflects recorded sessions`() = runBlocking {
        val repository = StatsRepository(database)
        repository.recordSession(
            ReadingSessionEvent(
                bookId = BookId("book-1"),
                chapterId = ChapterId("ch-1"),
                startedAt = Instant.parse("2026-01-01T10:00:00Z"),
                endedAt = Instant.parse("2026-01-01T10:10:00Z"),
                wordsRead = 1500,
            ),
        )

        val summary = repository.observeSummary().first()
        assertEquals(1500, summary.totalWordsRead)
    }

    @Test
    fun `all sessions are observed in chronological order across books`() = runBlocking {
        val repository = StatsRepository(database)
        val first = ReadingSessionEvent(
            bookId = BookId("book-1"),
            chapterId = ChapterId("ch-1"),
            startedAt = Instant.parse("2026-01-01T10:00:00Z"),
            endedAt = Instant.parse("2026-01-01T10:10:00Z"),
            wordsRead = 100,
        )
        val second = first.copy(
            bookId = BookId("book-2"),
            chapterId = ChapterId("ch-2"),
            startedAt = Instant.parse("2026-01-02T10:00:00Z"),
            endedAt = Instant.parse("2026-01-02T10:10:00Z"),
            wordsRead = 200,
        )
        repository.recordSession(second)
        repository.recordSession(first)

        assertEquals(listOf(first, second), repository.observeAllSessions().first())
        assertEquals(listOf(first), repository.observeSessions(BookId("book-1")).first())
    }

    @Test
    fun `a book's indexed chapters are replaced as a whole and paged in order`() = runBlocking {
        val repository = SearchRepository(database)
        val one = BookId("book-1")
        val two = BookId("book-2")
        repository.replaceBook(one, listOf(ChapterId("a") to "alpha", ChapterId("b") to "beta"))
        repository.replaceBook(two, listOf(ChapterId("c") to "gamma"))

        repository.replaceBook(one, listOf(ChapterId("a") to "alpha again"))

        val all = repository.page(after = 0, limit = 10)
        assertEquals(listOf("c", "a"), all.map { it.chapterId.value })
        assertEquals("alpha again", all.last().body)
        val firstPage = repository.page(after = 0, limit = 1)
        assertEquals(listOf("c"), firstPage.map { it.chapterId.value })
        assertEquals(listOf("a"), repository.page(after = firstPage.last().rowId, limit = 1).map { it.chapterId.value })

        repository.removeBook(two)
        assertEquals(listOf("a"), repository.page(0, 10).map { it.chapterId.value })
    }

    @Test
    fun `index sizes are totalled per book, largest first`() = runBlocking {
        val repository = SearchRepository(database)
        repository.replaceBook(BookId("small"), listOf(ChapterId("a") to "abc"))
        repository.replaceBook(
            BookId("big"),
            listOf(ChapterId("b") to "x".repeat(100), ChapterId("c") to "y".repeat(50)),
        )

        val sizes = repository.sizes()

        assertEquals(listOf("big", "small"), sizes.map { it.bookId.value })
        assertEquals(150L, sizes.first().bytes)
        assertEquals(2, sizes.first().chapters)
        repository.clear()
        assertEquals(emptyList(), repository.sizes())
    }

    @Test
    fun `chapters can be added to a book without disturbing the ones already indexed`() = runBlocking {
        val repository = SearchRepository(database)
        val book = BookId("book-1")
        repository.replaceBook(book, listOf(ChapterId("a") to "alpha"))
        repository.replaceBook(BookId("book-2"), listOf(ChapterId("z") to "zeta"))
        val aRow = repository.page(0, 10).first { it.chapterId.value == "a" }.rowId

        repository.addChapters(book, listOf(ChapterId("b") to "beta"))

        assertEquals(setOf("a", "b", "z"), repository.indexedChapterIds())
        assertEquals(setOf("a", "b"), repository.indexedChapterIds(book))
        assertEquals(aRow, repository.page(0, 10).first { it.chapterId.value == "a" }.rowId)
    }
}
