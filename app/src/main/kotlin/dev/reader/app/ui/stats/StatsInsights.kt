package dev.reader.app.ui.stats

import dev.reader.core.stats.StatsSummary
import dev.reader.core.stats.TimeOfDay
import kotlinx.datetime.DayOfWeek
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private const val NOVEL_WORDS = 90_000
private const val PERCENT = 100

/** Plain-language observations derived from the summary, most interesting first. */
fun statsInsights(summary: StatsSummary): List<String> = buildList {
    if (summary.sessionCount == 0) return@buildList
    streakInsight(summary)?.let(::add)
    weekTrend(summary.thisWeekReadingTime, summary.lastWeekReadingTime)?.let(::add)
    summary.favoriteTimeOfDay?.let { add(timeOfDayInsight(it)) }
    summary.busiestWeekday?.let { add("${it.displayName()}s are your biggest reading day.") }
    if (summary.averageWpm >= 1) {
        val novel = (NOVEL_WORDS / summary.averageWpm).minutes.toReadableDuration()
        add("At ${summary.averageWpm.roundToInt()} wpm, a typical 90,000-word novel takes you about $novel.")
    }
}

fun DayOfWeek.displayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }

private fun streakInsight(summary: StatsSummary): String? {
    val current = summary.currentStreakDays
    val best = summary.longestStreakDays
    return when {
        current > 1 && current >= best -> "You're on your longest streak ever: $current days!"
        current > 0 && best > current -> "${best - current + 1} more days to beat your best streak of $best."
        current == 0 && best > 1 -> "Your best streak was $best days. Read today to start a new one."
        else -> null
    }
}

private fun weekTrend(thisWeek: Duration, lastWeek: Duration): String? {
    if (lastWeek <= Duration.ZERO) return null
    val change = ((thisWeek - lastWeek) / lastWeek * PERCENT).roundToInt()
    return when {
        change > 0 -> "You've read $change% more than last week."
        change < 0 -> "You've read ${-change}% less than last week. Still time to catch up!"
        else -> "Right on last week's pace."
    }
}

private fun timeOfDayInsight(timeOfDay: TimeOfDay): String = when (timeOfDay) {
    TimeOfDay.MORNING -> "Early bird: most of your reading happens in the morning."
    TimeOfDay.AFTERNOON -> "Afternoons are your reading sweet spot."
    TimeOfDay.EVENING -> "You do most of your reading in the evening."
    TimeOfDay.NIGHT -> "Night owl: most of your reading happens after 9pm."
}
