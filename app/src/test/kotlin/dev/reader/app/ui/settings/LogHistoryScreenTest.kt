package dev.reader.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.reader.app.data.LogEntry
import dev.reader.app.data.LogLevel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class LogHistoryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val entries = LogLevel.entries.mapIndexed { index, level ->
        LogEntry(Instant.parse("2026-01-01T00:00:0${index}Z"), "entry-${level.label.lowercase()}", level)
    }

    @Test
    fun `warn is default and filter retains warn and error`() {
        var copied: List<LogEntry> = emptyList()
        composeRule.setContent {
            LogHistoryScreen(entries, onClear = {}, onCopy = { copied = it })
        }

        composeRule.onNodeWithTag("log-level-warn").performClick()
        composeRule.onAllNodesWithText("entry-debug", substring = false).assertCountEquals(0)
        composeRule.onAllNodesWithText("entry-info", substring = false).assertCountEquals(0)
        composeRule.onAllNodesWithText("entry-warn", substring = false).assertCountEquals(1)
        composeRule.onAllNodesWithText("entry-err", substring = false).assertCountEquals(1)
        composeRule.onNodeWithTag("log-copy").performClick()

        assertEquals(listOf(LogLevel.WARN, LogLevel.ERROR), copied.map { it.level })
    }

    @Test
    fun `copy receives only entries at or above the selected level`() {
        var copied: List<LogEntry> = emptyList()
        var minimum by mutableStateOf(LogLevel.WARN)
        composeRule.setContent {
            LogHistoryScreen(
                entries = entries,
                onClear = {},
                minimumLevel = minimum,
                onMinimumLevelChange = { minimum = it },
                onCopy = { copied = it },
            )
        }

        composeRule.onNodeWithTag("log-level-info").performClick()
        composeRule.onNodeWithTag("log-copy").performClick()

        assertEquals(listOf(LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR), copied.map { it.level })
    }
}
