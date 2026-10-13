package dev.reader.core.stats

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val SECONDS_PER_MINUTE = 60.0

data class StatsSummary(
    val booksFinished: Int,
    val totalReadingTime: Duration,
    val totalWordsRead: Int,
    val averageWpm: Double,
    val currentStreakDays: Int,
)

object StatsCalculator {

    fun summarize(
        sessions: List<ReadingSessionEvent>,
        booksFinished: Int,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): StatsSummary {
        val totalSeconds = sessions.sumOf { (it.endedAt - it.startedAt).inWholeSeconds }
        val totalReadingTime = totalSeconds.seconds
        val totalWordsRead = sessions.sumOf { it.wordsRead }
        val averageWpm = if (totalReadingTime > Duration.ZERO) {
            totalWordsRead / (totalReadingTime.inWholeSeconds / SECONDS_PER_MINUTE)
        } else {
            0.0
        }
        return StatsSummary(
            booksFinished = booksFinished,
            totalReadingTime = totalReadingTime,
            totalWordsRead = totalWordsRead,
            averageWpm = averageWpm,
            currentStreakDays = currentStreak(sessions, timeZone),
        )
    }

    private fun currentStreak(sessions: List<ReadingSessionEvent>, timeZone: TimeZone): Int {
        if (sessions.isEmpty()) return 0
        val readDays = sessions
            .map { it.endedAt.toLocalDateTime(timeZone).date }
            .toSortedSet()
            .toList()
        var streak = 1
        var day = readDays.last()
        for (i in readDays.size - 2 downTo 0) {
            val expectedPrevious = day.minus(DatePeriod(days = 1))
            if (readDays[i] != expectedPrevious) break
            streak++
            day = readDays[i]
        }
        return streak
    }
}
