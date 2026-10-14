package dev.reader.core.stats

import dev.reader.core.model.BookId
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private const val SECONDS_PER_MINUTE = 60.0
private const val WEEK_DAYS = 7
private const val TOP_BOOKS = 3
private const val MORNING_START = 5
private const val AFTERNOON_START = 12
private const val EVENING_START = 17
private const val NIGHT_START = 21

enum class TimeOfDay { MORNING, AFTERNOON, EVENING, NIGHT }

data class DailyReading(val date: LocalDate, val time: Duration)

data class BookReadingTime(val bookId: BookId, val title: String, val time: Duration)

data class StatsSummary(
    val booksFinished: Int = 0,
    val booksStarted: Int = 0,
    val totalReadingTime: Duration = Duration.ZERO,
    val todayReadingTime: Duration = Duration.ZERO,
    val thisWeekReadingTime: Duration = Duration.ZERO,
    val lastWeekReadingTime: Duration = Duration.ZERO,
    val totalWordsRead: Int = 0,
    val totalPagesTurned: Int = 0,
    val averageWpm: Double = 0.0,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val activeDays: Int = 0,
    val sessionCount: Int = 0,
    val averageSession: Duration = Duration.ZERO,
    val longestSession: Duration = Duration.ZERO,
    val last7Days: List<DailyReading> = emptyList(),
    val favoriteTimeOfDay: TimeOfDay? = null,
    val busiestWeekday: DayOfWeek? = null,
    val topBooks: List<BookReadingTime> = emptyList(),
)

object StatsCalculator {

    fun summarize(
        sessions: List<ReadingSessionEvent>,
        booksFinished: Int,
        bookTitles: Map<BookId, String> = emptyMap(),
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
        now: Instant = Clock.System.now(),
    ): StatsSummary {
        val today = now.toLocalDateTime(timeZone).date
        val byDay = sessions.groupBy { it.endedAt.toLocalDateTime(timeZone).date }.mapValues { it.value.totalTime() }
        val readDays = byDay.keys.sorted()
        val total = sessions.totalTime()
        val words = sessions.sumOf { it.wordsRead }
        return StatsSummary(
            booksFinished = booksFinished,
            booksStarted = sessions.map { it.bookId }.distinct().size,
            totalReadingTime = total,
            todayReadingTime = byDay[today] ?: Duration.ZERO,
            thisWeekReadingTime = timeInRange(byDay, today, 0 until WEEK_DAYS),
            lastWeekReadingTime = timeInRange(byDay, today, WEEK_DAYS until 2 * WEEK_DAYS),
            totalWordsRead = words,
            totalPagesTurned = sessions.sumOf { it.pagesTurned },
            averageWpm = if (total.inWholeSeconds > 0) words / (total.inWholeSeconds / SECONDS_PER_MINUTE) else 0.0,
            currentStreakDays = currentStreak(readDays, today),
            longestStreakDays = longestStreak(readDays),
            activeDays = readDays.size,
            sessionCount = sessions.size,
            averageSession = if (sessions.isEmpty()) Duration.ZERO else total / sessions.size,
            longestSession = sessions.maxOfOrNull { it.duration() } ?: Duration.ZERO,
            last7Days = (WEEK_DAYS - 1 downTo 0).map { daysAgo ->
                val date = today.minus(daysAgo, DateTimeUnit.DAY)
                DailyReading(date, byDay[date] ?: Duration.ZERO)
            },
            favoriteTimeOfDay = sessions
                .groupBy { timeOfDay(it.startedAt.toLocalDateTime(timeZone).hour) }
                .maxByOrNull { it.value.totalTime() }?.key,
            busiestWeekday = byDay.entries
                .groupBy({ it.key.dayOfWeek }, { it.value })
                .maxByOrNull { (_, times) -> times.fold(Duration.ZERO, Duration::plus) }?.key,
            topBooks = sessions.groupBy { it.bookId }
                .map { (id, list) -> BookReadingTime(id, bookTitles[id] ?: "Unknown book", list.totalTime()) }
                .sortedByDescending { it.time }
                .take(TOP_BOOKS),
        )
    }

    private fun ReadingSessionEvent.duration(): Duration = (endedAt - startedAt).inWholeSeconds.seconds

    private fun List<ReadingSessionEvent>.totalTime(): Duration = sumOf { it.duration().inWholeSeconds }.seconds

    private fun timeInRange(byDay: Map<LocalDate, Duration>, today: LocalDate, daysAgo: IntRange): Duration =
        byDay.filterKeys { it.daysUntil(today) in daysAgo }.values.fold(Duration.ZERO, Duration::plus)

    private fun timeOfDay(hour: Int): TimeOfDay = when (hour) {
        in MORNING_START until AFTERNOON_START -> TimeOfDay.MORNING
        in AFTERNOON_START until EVENING_START -> TimeOfDay.AFTERNOON
        in EVENING_START until NIGHT_START -> TimeOfDay.EVENING
        else -> TimeOfDay.NIGHT
    }

    /** Consecutive days ending today, or yesterday (a streak isn't broken until a whole day is missed). */
    private fun currentStreak(readDays: List<LocalDate>, today: LocalDate): Int {
        val last = readDays.lastOrNull() ?: return 0
        if (last.daysUntil(today) > 1) return 0
        var streak = 1
        for (i in readDays.size - 2 downTo 0) {
            if (readDays[i].daysUntil(readDays[i + 1]) != 1) break
            streak++
        }
        return streak
    }

    private fun longestStreak(readDays: List<LocalDate>): Int {
        if (readDays.isEmpty()) return 0
        var longest = 1
        var run = 1
        for (i in 1 until readDays.size) {
            run = if (readDays[i - 1].daysUntil(readDays[i]) == 1) run + 1 else 1
            longest = maxOf(longest, run)
        }
        return longest
    }
}
