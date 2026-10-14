package dev.reader.core.stats

import dev.reader.core.model.BookId
import dev.reader.core.model.ChapterId
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private fun instant(iso: String): Instant = Instant.parse(iso)

private fun session(start: String, end: String, words: Int, book: String = "book-1", pages: Int = 0) =
    ReadingSessionEvent(
        bookId = BookId(book),
        chapterId = ChapterId("chapter-1"),
        startedAt = instant(start),
        endedAt = instant(end),
        wordsRead = words,
        pagesTurned = pages,
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

        val summary = summarizeAt(sessions, now = "2026-01-03T20:00:00Z")

        assertEquals(3, summary.currentStreakDays)
    }

    @Test
    fun `streak survives until a whole day is missed`() {
        val sessions = listOf(session("2026-01-01T10:00:00Z", "2026-01-01T10:01:00Z", 10))

        assertEquals(1, summarizeAt(sessions, now = "2026-01-02T20:00:00Z").currentStreakDays)
        assertEquals(0, summarizeAt(sessions, now = "2026-01-03T20:00:00Z").currentStreakDays)
    }

    @Test
    fun `longest streak is kept after the current one breaks`() {
        val sessions = listOf(
            session("2026-01-01T10:00:00Z", "2026-01-01T10:01:00Z", 10),
            session("2026-01-02T10:00:00Z", "2026-01-02T10:01:00Z", 10),
            session("2026-01-03T10:00:00Z", "2026-01-03T10:01:00Z", 10),
            session("2026-01-10T10:00:00Z", "2026-01-10T10:01:00Z", 10),
        )

        val summary = summarizeAt(sessions, now = "2026-01-10T20:00:00Z")

        assertEquals(1, summary.currentStreakDays)
        assertEquals(3, summary.longestStreakDays)
        assertEquals(4, summary.activeDays)
    }

    @Test
    fun `splits time into today, this week and last week`() {
        val sessions = listOf(
            session("2026-01-10T10:00:00Z", "2026-01-10T10:30:00Z", 10),
            session("2026-01-08T10:00:00Z", "2026-01-08T10:10:00Z", 10),
            session("2026-01-01T10:00:00Z", "2026-01-01T10:20:00Z", 10),
        )

        val summary = summarizeAt(sessions, now = "2026-01-10T20:00:00Z")

        assertEquals(30.minutes, summary.todayReadingTime)
        assertEquals(40.minutes, summary.thisWeekReadingTime)
        assertEquals(20.minutes, summary.lastWeekReadingTime)
        assertEquals(7, summary.last7Days.size)
        assertEquals(30.minutes, summary.last7Days.last().time)
    }

    @Test
    fun `derives session, time-of-day and top-book insights`() {
        val sessions = listOf(
            session("2026-01-10T22:00:00Z", "2026-01-10T23:00:00Z", 10, book = "a", pages = 5),
            session("2026-01-09T08:00:00Z", "2026-01-09T08:20:00Z", 10, book = "b", pages = 3),
        )

        val summary = StatsCalculator.summarize(
            sessions,
            booksFinished = 0,
            bookTitles = mapOf(BookId("a") to "Dune"),
            timeZone = TimeZone.UTC,
            now = instant("2026-01-10T23:30:00Z"),
        )

        assertEquals(2, summary.sessionCount)
        assertEquals(40.minutes, summary.averageSession)
        assertEquals(60.minutes, summary.longestSession)
        assertEquals(8, summary.totalPagesTurned)
        assertEquals(2, summary.booksStarted)
        assertEquals(TimeOfDay.NIGHT, summary.favoriteTimeOfDay)
        assertEquals(DayOfWeek.SATURDAY, summary.busiestWeekday)
        assertEquals(listOf("Dune", "Unknown book"), summary.topBooks.map { it.title })
    }

    private fun summarizeAt(sessions: List<ReadingSessionEvent>, now: String) =
        StatsCalculator.summarize(sessions, booksFinished = 0, timeZone = TimeZone.UTC, now = instant(now))

    @Test
    fun `streak breaks on a missed day`() {
        val sessions = listOf(
            session("2026-01-01T10:00:00Z", "2026-01-01T10:01:00Z", 10),
            session("2026-01-03T10:00:00Z", "2026-01-03T10:01:00Z", 10),
        )

        val summary = summarizeAt(sessions, now = "2026-01-03T20:00:00Z")

        assertEquals(1, summary.currentStreakDays)
    }
}
