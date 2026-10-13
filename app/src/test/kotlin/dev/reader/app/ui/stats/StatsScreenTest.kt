package dev.reader.app.ui.stats

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.reader.core.stats.StatsSummary
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
    fun `shows the summary fields`() {
        val summary = StatsSummary(
            booksFinished = 3,
            totalReadingTime = 90.minutes,
            totalWordsRead = 12000,
            averageWpm = 150.0,
            currentStreakDays = 5,
        )
        composeRule.setContent { StatsScreen(summary) }

        composeRule.onNodeWithText("Books finished: 3").assertExists()
        composeRule.onNodeWithText("Reading time: 1h 30m").assertExists()
        composeRule.onNodeWithText("Words read: 12000").assertExists()
        composeRule.onNodeWithText("Average WPM: 150").assertExists()
        composeRule.onNodeWithText("Current streak: 5 days").assertExists()
    }
}
