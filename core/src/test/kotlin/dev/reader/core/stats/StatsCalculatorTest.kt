package dev.reader.core.stats

import dev.reader.core.model.BookId
import dev.reader.core.model.ChapterId
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

private fun instant(iso: String): Instant = Instant.parse(iso)

private fun session(start: String, end: String, words: Int) = ReadingSessionEvent(
    bookId = BookId("book-1"),
    chapterId = ChapterId("chapter-1"),
    startedAt = instant(start),
    endedAt = instant(end),
    wordsRead = words,
)

class StatsCalculatorTest {

    @Test
    fun `sums reading time and words across sessions`() {
        val sessions = listOf(
            session("2026-01-01T10:00:00Z", "2026-01-01T10:10:00Z", 1500),
            session("2026-01-02T10:00:00Z", "2026-01-02T10:05:00Z", 750),
        )

        val summary = StatsCalculator.summarize(sessions, booksFinished = 1)

        assertEquals(15 * 60, summary.totalReadingTime.inWholeSeconds.toInt())
        assertEquals(2250, summary.totalWordsRead)
        assertEquals(150.0, summary.averageWpm)
        assertEquals(1, summary.booksFinished)
    }

    @Test
    fun `empty session list has zero stats`() {
        val summary = StatsCalculator.summarize(emptyList(), booksFinished = 0)

        assertEquals(0, summary.totalWordsRead)
        assertEquals(0.0, summary.averageWpm)
        assertEquals(0, summary.currentStreakDays)
    }

    @Test
    fun `streak counts consecutive reading days ending today`() {
        val sessions = listOf(
            session("2026-01-01T10:00:00Z", "2026-01-01T10:01:00Z", 10),
            session("2026-01-02T10:00:00Z", "2026-01-02T10:01:00Z", 10),
            session("2026-01-03T10:00:00Z", "2026-01-03T10:01:00Z", 10),
        )

        val summary = StatsCalculator.summarize(sessions, booksFinished = 0, timeZone = TimeZone.UTC)

        assertEquals(3, summary.currentStreakDays)
    }

    @Test
    fun `streak breaks on a missed day`() {
        val sessions = listOf(
            session("2026-01-01T10:00:00Z", "2026-01-01T10:01:00Z", 10),
            session("2026-01-03T10:00:00Z", "2026-01-03T10:01:00Z", 10),
        )

        val summary = StatsCalculator.summarize(sessions, booksFinished = 0, timeZone = TimeZone.UTC)

        assertEquals(1, summary.currentStreakDays)
    }
}
