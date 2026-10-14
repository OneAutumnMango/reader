package dev.reader.app.ui.stats

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import dev.reader.core.model.BookId
import dev.reader.core.stats.BookReadingTime
import dev.reader.core.stats.DailyReading
import dev.reader.core.stats.StatsSummary
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
class StatsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `shows streak, insights and all-time stats`() {
        val summary = StatsSummary(
            booksFinished = 3,
            totalReadingTime = 90.minutes,
            totalWordsRead = 12000,
            averageWpm = 150.0,
            currentStreakDays = 5,
            longestStreakDays = 5,
            sessionCount = 4,
            totalPagesTurned = 42,
            last7Days = List(7) { DailyReading(LocalDate(2026, 1, 4 + it), (it * 10).minutes) },
            topBooks = listOf(BookReadingTime(BookId("a"), "Dune", 60.minutes)),
        )
        composeRule.setContent { StatsScreen(summary) }

        composeRule.onNodeWithText("5 days").assertExists()
        composeRule.onNodeWithText("Best: 5").assertExists()
        composeRule.onNodeWithText("1h 30m").assertExists()
        composeRule.onNodeWithText("12,000").assertExists()
        composeRule.onNodeWithText("150 wpm").assertExists()
        composeRule.onNodeWithText("42").assertExists()
        composeRule.onNodeWithTag("week-chart").assertExists()
        composeRule.onNodeWithText("You're on your longest streak ever: 5 days!").assertExists()
        composeRule.onNodeWithText("Dune").assertExists()
    }

    @Test
    fun `shows an empty state before any reading`() {
        composeRule.setContent { StatsScreen(StatsSummary()) }

        composeRule.onNodeWithText("No reading yet").assertExists()
    }
}
