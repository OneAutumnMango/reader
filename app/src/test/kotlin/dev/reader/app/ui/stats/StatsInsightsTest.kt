package dev.reader.app.ui.stats

import dev.reader.core.stats.StatsSummary
import dev.reader.core.stats.TimeOfDay
import kotlinx.datetime.DayOfWeek
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class StatsInsightsTest {

    @Test
    fun `no insights without any sessions`() {
        assertTrue(statsInsights(StatsSummary()).isEmpty())
    }

    @Test
    fun `describes habits, weekly trend and reading pace`() {
        val summary = StatsSummary(
            sessionCount = 3,
            thisWeekReadingTime = 150.minutes,
            lastWeekReadingTime = 100.minutes,
            favoriteTimeOfDay = TimeOfDay.NIGHT,
            busiestWeekday = DayOfWeek.SUNDAY,
            averageWpm = 250.0,
            currentStreakDays = 2,
            longestStreakDays = 4,
        )

        assertEquals(
            listOf(
                "3 more days to beat your best streak of 4.",
                "You've read 50% more than last week.",
                "Night owl: most of your reading happens after 9pm.",
                "Sundays are your biggest reading day.",
                "At 250 wpm, a typical 90,000-word novel takes you about 6h 0m.",
            ),
            statsInsights(summary),
        )
    }
}
