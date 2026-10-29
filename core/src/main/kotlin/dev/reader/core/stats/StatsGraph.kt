package dev.reader.core.stats

import dev.reader.core.model.BookId
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Clock

@Serializable
data class StatsGraph(
    val id: String = "",
    val title: String = "",
    val plot: GraphPlot = GraphPlot.LINE,
    val xAxis: GraphAxis = GraphAxis.DATE,
    val metrics: List<GraphMetric> = listOf(GraphMetric.READING_MINUTES),
    val grouping: GraphGrouping = GraphGrouping.DAY,
    val bookIds: Set<String> = emptySet(),
    val range: GraphRange = GraphRange.ALL_TIME,
    val independentScales: Boolean = false,
    val splitByBook: Boolean = false,
)

@Serializable
enum class GraphPlot(val label: String) {
    LINE("Line"),
    SCATTER("Scatter"),
    BAR("Bar"),
    AREA("Area"),
}

@Serializable
enum class GraphAxis(val label: String) {
    DATE("Date"),
    BOOK("Book"),
    CHAPTER("Chapter"),
    SESSION("Session"),
    READING_MINUTES("Reading minutes"),
    READING_SPEED("Reading speed"),
    WORDS("Words"),
    PAGES("Pages"),
    SESSIONS("Sessions"),
    AVERAGE_SESSION_MINUTES("Avg session length"),
    BOOKS_READ("Books read"),
}

@Serializable
enum class GraphMetric(val label: String, val unit: String) {
    READING_MINUTES("Reading time", "min"),
    READING_SPEED("Reading speed", "wpm"),
    WORDS("Words read", "words"),
    PAGES("Pages turned", "pages"),
    SESSIONS("Sessions", "sessions"),
    AVERAGE_SESSION_MINUTES("Avg session length", "min"),
    BOOKS_READ("Books read", "books"),
}

@Serializable
enum class GraphGrouping {
    DAY,
    WEEK,
    MONTH,
    BOOK,
    CHAPTER,
    SESSION,
}

@Serializable
enum class GraphRange(val label: String) {
    ALL_TIME("All time"),
    LAST_7_DAYS("Last 7 days"),
    LAST_30_DAYS("Last 30 days"),
    LAST_90_DAYS("Last 90 days"),
    LAST_365_DAYS("Last 365 days"),
}

data class GraphPoint(val label: String, val x: Double, val y: Double)

data class GraphSeries(val label: String, val metric: GraphMetric, val points: List<GraphPoint>)

data class GraphData(val series: List<GraphSeries>, val xLabel: String)

@Suppress("TooManyFunctions")
object StatsGraphCalculator {

    fun calculate(
        graph: StatsGraph,
        sessions: List<ReadingSessionEvent>,
        bookTitles: Map<BookId, String> = emptyMap(),
        chapters: List<Chapter> = emptyList(),
        today: LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
    ): GraphData {
        val timeZone = TimeZone.currentSystemDefault()
        val filtered = filterSessions(graph, sessions, today, timeZone)
        val grouping = groupingFor(graph)
        val chapterByKey = chapters.associateBy { it.bookId to it.id }
        val buckets = makeBuckets(filtered, grouping, chapterByKey, bookTitles, timeZone)
            .let { buckets ->
                if (grouping == GraphGrouping.CHAPTER && buckets.map { it.bookId }.distinct().size > 1) {
                    buckets.map { bucket ->
                        bucket.copy(label = "${bookTitles[bucket.bookId] ?: bucket.bookId.value}: ${bucket.label}")
                    }
                } else {
                    buckets
                }
            }
        return GraphData(
            series = makeSeries(graph, buckets, bookTitles),
            xLabel = graph.xAxis.label,
        )
    }

    private fun filterSessions(
        graph: StatsGraph,
        sessions: List<ReadingSessionEvent>,
        today: LocalDate,
        timeZone: TimeZone,
    ) = sessions.withIndex().filter { (_, session) ->
        session.endedAt > session.startedAt &&
            (graph.bookIds.isEmpty() || session.bookId.value in graph.bookIds) &&
            isInRange(session, graph.range, today, timeZone)
    }

    private fun groupingFor(graph: StatsGraph): GraphGrouping = when (graph.xAxis) {
        GraphAxis.DATE -> graph.grouping.takeIf { it in DATE_GROUPINGS } ?: GraphGrouping.DAY
        GraphAxis.BOOK -> GraphGrouping.BOOK
        GraphAxis.CHAPTER -> GraphGrouping.CHAPTER
        GraphAxis.SESSION -> GraphGrouping.SESSION
        else -> graph.grouping
    }

    private fun makeBuckets(
        filtered: List<IndexedValue<ReadingSessionEvent>>,
        grouping: GraphGrouping,
        chapterByKey: Map<Pair<BookId, ChapterId>, Chapter>,
        bookTitles: Map<BookId, String>,
        timeZone: TimeZone,
    ): List<Bucket> = filtered.groupBy { (index, session) -> bucketKey(session, grouping, index, timeZone) }
        .map { (key, entries) ->
            makeBucket(key, entries.map { it.value }, chapterByKey, bookTitles, grouping, timeZone)
        }
        .sortedWith(bucketComparator(grouping, bookTitles, chapterByKey))

    private fun makeSeries(
        graph: StatsGraph,
        buckets: List<Bucket>,
        bookTitles: Map<BookId, String>,
    ): List<GraphSeries> {
        val splitBooks = if (graph.splitByBook) {
            buckets.flatMap { bucket -> bucket.sessions.map { it.bookId } }
                .distinct()
                .sortedBy { bookTitles[it] ?: it.value }
        } else {
            listOf(null)
        }
        val categoryPositions = buckets.mapIndexed { index, bucket -> bucket.key to index.toDouble() }.toMap()
        return splitBooks.flatMap { splitBook ->
            graph.metrics.map { metric -> makeSeries(graph, buckets, categoryPositions, splitBook, metric, bookTitles) }
        }
    }

    private fun makeSeries(
        graph: StatsGraph,
        buckets: List<Bucket>,
        categoryPositions: Map<String, Double>,
        splitBook: BookId?,
        metric: GraphMetric,
        bookTitles: Map<BookId, String>,
    ): GraphSeries {
        val points = buckets.asSequence()
            .filter { bucket ->
                graph.xAxis in CATEGORY_AXES || splitBook == null || bucket.sessions.any { it.bookId == splitBook }
            }
            .map { bucket -> makePoint(graph, bucket, categoryPositions, splitBook, metric) }
            .toList()
        val orderedPoints = if (
            graph.xAxis !in CATEGORY_AXES && (graph.plot == GraphPlot.LINE || graph.plot == GraphPlot.AREA)
        ) {
            points.sortedBy { it.x }
        } else {
            points
        }
        val label = if (splitBook == null) {
            metric.label
        } else {
            "${bookTitles[splitBook] ?: splitBook.value} - ${metric.label}"
        }
        return GraphSeries(label, metric, orderedPoints)
    }

    private fun makePoint(
        graph: StatsGraph,
        bucket: Bucket,
        categoryPositions: Map<String, Double>,
        splitBook: BookId?,
        metric: GraphMetric,
    ): GraphPoint {
        val relevant = if (splitBook == null) bucket.sessions else bucket.sessions.filter { it.bookId == splitBook }
        return GraphPoint(
            label = bucket.label,
            x = xValue(graph.xAxis, bucket, relevant, categoryPositions),
            y = metricValue(metric, relevant),
        )
    }

    private fun isInRange(
        session: ReadingSessionEvent,
        range: GraphRange,
        today: LocalDate,
        timeZone: TimeZone,
    ): Boolean {
        val days = when (range) {
            GraphRange.ALL_TIME -> return true
            GraphRange.LAST_7_DAYS -> DAYS_IN_LAST_7
            GraphRange.LAST_30_DAYS -> DAYS_IN_LAST_30
            GraphRange.LAST_90_DAYS -> DAYS_IN_LAST_90
            GraphRange.LAST_365_DAYS -> DAYS_IN_LAST_365
        }
        val date = session.endedAt.toLocalDateTime(timeZone).date
        return date >= today.minus(days - 1, DateTimeUnit.DAY) && date <= today
    }

    private fun bucketKey(
        session: ReadingSessionEvent,
        grouping: GraphGrouping,
        sessionIndex: Int,
        timeZone: TimeZone,
    ): String = when (grouping) {
        GraphGrouping.DAY, GraphGrouping.WEEK, GraphGrouping.MONTH -> {
            val date = session.endedAt.toLocalDateTime(timeZone).date
            val start = when (grouping) {
                GraphGrouping.DAY -> date
                GraphGrouping.WEEK -> date.minus(date.dayOfWeek.ordinal, DateTimeUnit.DAY)
                GraphGrouping.MONTH -> LocalDate(date.year, date.month, 1)
            }
            "date:$start"
        }
        GraphGrouping.BOOK -> "book:${session.bookId.value}"
        GraphGrouping.CHAPTER -> "chapter:${session.bookId.value}:${session.chapterId.value}"
        GraphGrouping.SESSION -> "session:$sessionIndex"
    }

    private fun makeBucket(
        key: String,
        sessions: List<ReadingSessionEvent>,
        chapterByKey: Map<Pair<BookId, ChapterId>, Chapter>,
        bookTitles: Map<BookId, String>,
        grouping: GraphGrouping,
        timeZone: TimeZone,
    ): Bucket {
        val first = sessions.first()
        val date = when (grouping) {
            GraphGrouping.DAY, GraphGrouping.WEEK, GraphGrouping.MONTH -> first.endedAt.toLocalDateTime(timeZone).date
            else -> null
        }
        val chapter = chapterByKey[first.bookId to first.chapterId]
        val label = when (grouping) {
            GraphGrouping.DAY, GraphGrouping.WEEK, GraphGrouping.MONTH -> {
                val start = when (grouping) {
                    GraphGrouping.DAY -> date!!
                    GraphGrouping.WEEK -> date!!.minus(date.dayOfWeek.ordinal, DateTimeUnit.DAY)
                    GraphGrouping.MONTH -> LocalDate(date!!.year, date.month, 1)
                }
                start.toString()
            }
            GraphGrouping.BOOK -> bookTitles[first.bookId] ?: first.bookId.value
            GraphGrouping.CHAPTER -> chapter?.title ?: first.chapterId.value
            GraphGrouping.SESSION -> first.endedAt.toString()
        }
        return Bucket(key, label, date, first.bookId, chapter, first.endedAt.toString(), sessions)
    }

    private fun bucketComparator(
        grouping: GraphGrouping,
        bookTitles: Map<BookId, String>,
        chapterByKey: Map<Pair<BookId, ChapterId>, Chapter>,
    ): Comparator<Bucket> = when (grouping) {
        GraphGrouping.DAY, GraphGrouping.WEEK, GraphGrouping.MONTH -> compareBy { it.date }
        GraphGrouping.BOOK -> compareBy<Bucket> { bookTitles[it.bookId] ?: it.bookId.value }.thenBy { it.bookId.value }
        GraphGrouping.CHAPTER -> compareBy<Bucket> { it.bookId.value }
            .thenBy { chapterByKey[it.bookId to it.chapter?.id]?.orderIndex ?: Int.MAX_VALUE }
            .thenBy { it.chapter?.id?.value ?: it.key }
        GraphGrouping.SESSION -> compareBy<Bucket> { it.sessionOrder }
    }

    private fun xValue(
        axis: GraphAxis,
        bucket: Bucket,
        sessions: List<ReadingSessionEvent>,
        categoryPositions: Map<String, Double>,
    ): Double = when (axis) {
        in CATEGORY_AXES -> categoryPositions.getValue(bucket.key)
        else -> metricValue(axis.toMetric(), sessions)
    }

    private fun metricValue(metric: GraphMetric, sessions: List<ReadingSessionEvent>): Double {
        val totalSeconds = sessions.sumOf { (it.endedAt - it.startedAt).inWholeNanoseconds / NANOS_PER_SECOND }
        val words = sessions.sumOf { maxOf(0, it.wordsRead) }
        return when (metric) {
            GraphMetric.READING_MINUTES -> totalSeconds / SECONDS_PER_MINUTE
            GraphMetric.READING_SPEED -> if (totalSeconds > 0.0) words / (totalSeconds / SECONDS_PER_MINUTE) else 0.0
            GraphMetric.WORDS -> words.toDouble()
            GraphMetric.PAGES -> sessions.sumOf { maxOf(0, it.pagesTurned) }.toDouble()
            GraphMetric.SESSIONS -> sessions.size.toDouble()
            GraphMetric.AVERAGE_SESSION_MINUTES -> if (sessions.isNotEmpty()) {
                totalSeconds / SECONDS_PER_MINUTE / sessions.size
            } else {
                0.0
            }
            GraphMetric.BOOKS_READ -> sessions.map { it.bookId }.distinct().size.toDouble()
        }
    }

    private fun GraphAxis.toMetric(): GraphMetric = when (this) {
        GraphAxis.READING_MINUTES -> GraphMetric.READING_MINUTES
        GraphAxis.READING_SPEED -> GraphMetric.READING_SPEED
        GraphAxis.WORDS -> GraphMetric.WORDS
        GraphAxis.PAGES -> GraphMetric.PAGES
        GraphAxis.SESSIONS -> GraphMetric.SESSIONS
        GraphAxis.AVERAGE_SESSION_MINUTES -> GraphMetric.AVERAGE_SESSION_MINUTES
        GraphAxis.BOOKS_READ -> GraphMetric.BOOKS_READ
        else -> error("Category axes do not have numeric values")
    }

    private data class Bucket(
        val key: String,
        val label: String,
        val date: LocalDate?,
        val bookId: BookId,
        val chapter: Chapter?,
        val sessionOrder: String,
        val sessions: List<ReadingSessionEvent>,
    )

    private val DATE_GROUPINGS = setOf(GraphGrouping.DAY, GraphGrouping.WEEK, GraphGrouping.MONTH)
    private val CATEGORY_AXES = setOf(GraphAxis.DATE, GraphAxis.BOOK, GraphAxis.CHAPTER, GraphAxis.SESSION)
    private const val DAYS_IN_LAST_7 = 7
    private const val DAYS_IN_LAST_30 = 30
    private const val DAYS_IN_LAST_90 = 90
    private const val DAYS_IN_LAST_365 = 365
    private const val SECONDS_PER_MINUTE = 60.0
    private const val NANOS_PER_SECOND = 1_000_000_000.0
}
