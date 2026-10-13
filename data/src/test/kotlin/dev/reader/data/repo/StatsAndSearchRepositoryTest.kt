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
    fun `indexed chapter text is searchable`() = runBlocking {
        val repository = SearchRepository(database)
        val chapterId = ChapterId("ch-1")
        repository.indexChapter(chapterId, BookId("book-1"), "The quick brown fox jumps")

        assertEquals(listOf(chapterId), repository.search("quick"))
        assertEquals(emptyList(), repository.search("elephant"))
    }
}
