package dev.reader.core.stats

import dev.reader.core.model.BookId
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class StatsGraphCalculatorTest {

    @Test
    fun `reading speed is weighted across sessions`() {
        val result = calculate(
            graph = StatsGraph(metrics = listOf(GraphMetric.READING_SPEED)),
            sessions = listOf(
                session("2026-06-10T10:00:00Z", "2026-06-10T10:01:00Z", 300),
                session("2026-06-10T10:02:00Z", "2026-06-10T10:12:00Z", 600),
            ),
        )

        assertEquals(900.0 / 11.0, result.series.single().points.single().y)
    }

    @Test
    fun `range includes today and the prior six local dates`() {
        val result = calculate(
            graph = StatsGraph(range = GraphRange.LAST_7_DAYS),
            sessions = listOf(
                session("2026-06-03T10:00:00Z", "2026-06-03T10:01:00Z", 1),
                session("2026-06-04T10:00:00Z", "2026-06-04T10:01:00Z", 1),
                session("2026-06-10T10:00:00Z", "2026-06-10T10:01:00Z", 1),
                session("2026-06-11T10:00:00Z", "2026-06-11T10:01:00Z", 1),
            ),
        )

        assertEquals(listOf("2026-06-04", "2026-06-10"), result.series.single().points.map { it.label })
    }

    @Test
    fun `book filtering ignores invalid durations and clamps negative counts`() {
        val result = calculate(
            graph = StatsGraph(xAxis = GraphAxis.BOOK, metrics = listOf(GraphMetric.WORDS, GraphMetric.PAGES)),
            sessions = listOf(
                session("2026-06-10T10:00:00Z", "2026-06-10T10:01:00Z", 10, book = "keep", pages = 2),
                session("2026-06-10T10:00:00Z", "2026-06-10T09:59:00Z", 100, book = "keep", pages = 10),
                session("2026-06-10T10:00:00Z", "2026-06-10T10:01:00Z", 100, book = "skip"),
                session("2026-06-10T10:00:00Z", "2026-06-10T10:01:00Z", -10, book = "keep", pages = -2),
            ),
            bookIds = setOf("keep"),
        )

        assertEquals(listOf(10.0), result.series[0].points.map { it.y })
        assertEquals(listOf(2.0), result.series[1].points.map { it.y })
    }

    @Test
    fun `chapters sort by book and order index and use title fallback`() {
        val chapters = listOf(
            chapter("second", "b", "Second", 1),
            chapter("later", "a", "Later", 2),
            chapter("first", "a", "First", 0),
        )
        val result = calculate(
            graph = StatsGraph(xAxis = GraphAxis.CHAPTER, metrics = listOf(GraphMetric.SESSIONS)),
            sessions = listOf(
                session("2026-06-10T10:00:00Z", "2026-06-10T10:01:00Z", 1, book = "b", chapter = "second"),
                session("2026-06-10T10:00:00Z", "2026-06-10T10:01:00Z", 1, book = "a", chapter = "later"),
                session("2026-06-10T10:00:00Z", "2026-06-10T10:01:00Z", 1, book = "a", chapter = "first"),
                session("2026-06-10T10:00:00Z", "2026-06-10T10:01:00Z", 1, book = "z", chapter = "removed"),
            ),
            chapters = chapters,
        )

        assertEquals(
            listOf("a: First", "a: Later", "b: Second", "z: removed"),
            result.series.single().points.map { it.label },
        )
    }

    @Test
    fun `split series share category coordinates and include empty category values`() {
        val result = calculate(
            graph = StatsGraph(xAxis = GraphAxis.DATE, splitByBook = true, metrics = listOf(GraphMetric.WORDS)),
            sessions = listOf(
                session("2026-06-09T10:00:00Z", "2026-06-09T10:01:00Z", 10, book = "a"),
                session("2026-06-10T10:00:00Z", "2026-06-10T10:01:00Z", 20, book = "b"),
            ),
        )

        assertEquals(listOf("a - Words read", "b - Words read"), result.series.map { it.label })
        assertEquals(result.series[0].points.map { it.x }, result.series[1].points.map { it.x })
        assertEquals(listOf(10.0, 0.0), result.series[0].points.map { it.y })
        assertEquals(listOf(0.0, 20.0), result.series[1].points.map { it.y })
    }

    @Test
    fun `split chapter series preserve same-named categories across books`() {
        val result = calculate(
            graph = StatsGraph(xAxis = GraphAxis.CHAPTER, splitByBook = true, metrics = listOf(GraphMetric.SESSIONS)),
            sessions = listOf(
                session("2026-06-09T10:00:00Z", "2026-06-09T10:01:00Z", 10, book = "a", chapter = "a1"),
                session("2026-06-10T10:00:00Z", "2026-06-10T10:01:00Z", 20, book = "b", chapter = "b1"),
            ),
            chapters = listOf(
                chapter("a1", "a", "Opening", 0),
                chapter("b1", "b", "Opening", 0),
            ),
        )

        assertEquals(listOf("a - Sessions", "b - Sessions"), result.series.map { it.label })
        assertEquals(listOf(1.0, 0.0), result.series[0].points.map { it.y })
        assertEquals(listOf(0.0, 1.0), result.series[1].points.map { it.y })
        assertEquals(result.series[0].points.map { it.x }, result.series[1].points.map { it.x })
    }

    @Test
    fun `chapter labels include book names only when multiple books are plotted`() {
        val graph = StatsGraph(xAxis = GraphAxis.CHAPTER, metrics = listOf(GraphMetric.SESSIONS))
        val sessions = listOf(
            session("2026-06-09T10:00:00Z", "2026-06-09T10:01:00Z", 10, book = "a", chapter = "a1"),
            session("2026-06-10T10:00:00Z", "2026-06-10T10:01:00Z", 20, book = "b", chapter = "b1"),
        )
        val chapters = listOf(
            chapter("a1", "a", "Opening", 0),
            chapter("b1", "b", "Opening", 0),
        )
        val bookTitles = mapOf(BookId("a") to "Book A", BookId("b") to "Book B")

        val allBooks = calculate(graph, sessions, chapters = chapters, bookTitles = bookTitles)
        val oneBook = calculate(graph, sessions, bookIds = setOf("a"), chapters = chapters, bookTitles = bookTitles)

        assertEquals(listOf("Book A: Opening", "Book B: Opening"), allBooks.series.single().points.map { it.label })
        assertEquals(listOf("Opening"), oneBook.series.single().points.map { it.label })
    }

    @Test
    fun `area series on numeric axes sort points by x value`() {
        val result = calculate(
            graph = StatsGraph(plot = GraphPlot.AREA, xAxis = GraphAxis.WORDS, metrics = listOf(GraphMetric.SESSIONS)),
            sessions = listOf(
                session("2026-06-09T10:00:00Z", "2026-06-09T10:01:00Z", 30),
                session("2026-06-10T10:00:00Z", "2026-06-10T10:01:00Z", 10),
            ),
        )

        assertEquals(listOf(10.0, 30.0), result.series.single().points.map { it.x })
    }

    @Test
    fun `empty graph data retains requested metrics`() {
        val result = calculate(
            graph = StatsGraph(metrics = listOf(GraphMetric.WORDS, GraphMetric.PAGES)),
            sessions = emptyList(),
        )

        assertEquals(listOf(GraphMetric.WORDS, GraphMetric.PAGES), result.series.map { it.metric })
        assertTrue(result.series.all { it.points.isEmpty() })
        assertEquals("Date", result.xLabel)
    }

    private fun calculate(
        graph: StatsGraph,
        sessions: List<ReadingSessionEvent>,
        bookIds: Set<String> = emptySet(),
        chapters: List<Chapter> = emptyList(),
        bookTitles: Map<BookId, String> = emptyMap(),
    ) = StatsGraphCalculator.calculate(
        graph.copy(bookIds = bookIds),
        sessions,
        bookTitles = bookTitles,
        chapters = chapters,
        today = LocalDate(2026, 6, 10),
    )

    private fun session(
        start: String,
        end: String,
        words: Int,
        book: String = "book-1",
        chapter: String = "chapter-1",
        pages: Int = 0,
    ) = ReadingSessionEvent(
        bookId = BookId(book),
        chapterId = ChapterId(chapter),
        startedAt = Instant.parse(start),
        endedAt = Instant.parse(end),
        wordsRead = words,
        pagesTurned = pages,
    )

    private fun chapter(id: String, book: String, title: String, order: Int) = Chapter(
        id = ChapterId(id),
        bookId = BookId(book),
        title = title,
        sourcePath = "",
        orderIndex = order,
    )
}
