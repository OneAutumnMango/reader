package dev.reader.app.ui.reader

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.reader.core.reader.ReaderSettings
import dev.reader.core.reader.ReadingMode
import dev.reader.core.theme.ReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ReaderMenuOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `shows the progress label and nothing expanded by default`() {
        composeRule.setContent {
            ReaderMenuOverlay(
                progressLabel = "Page 3 of 10",
                theme = ReaderTheme.Default,
                onThemeChange = {},
                settings = ReaderSettings(),
                onSettingsChange = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithText("Page 3 of 10").assertExists()
        composeRule.onNodeWithText("Appearance").assertDoesNotExist()
    }

    @Test
    fun `expanding reveals appearance and reading controls`() {
        composeRule.setContent {
            ReaderMenuOverlay(
                progressLabel = "50%",
                theme = ReaderTheme.Default,
                onThemeChange = {},
                settings = ReaderSettings(),
                onSettingsChange = {},
                onBack = {},
            )
        }

        composeRule.onNodeWithTag("reader-menu-expand").performClick()

        composeRule.onNodeWithText("Appearance").assertExists()
        composeRule.onNodeWithText("Reading").assertExists()
    }

    @Test
    fun `back button triggers the callback`() {
        var backed = false
        composeRule.setContent {
            ReaderMenuOverlay(
                progressLabel = "50%",
                theme = ReaderTheme.Default,
                onThemeChange = {},
                settings = ReaderSettings(),
                onSettingsChange = {},
                onBack = { backed = true },
            )
        }

        composeRule.onNodeWithTag("reader-menu-back").performClick()

        assertTrue(backed)
    }

    @Test
    fun `progress label formats paged and continuous modes differently`() {
        assertEquals("Page 3 of 10", readerProgressLabel(ReadingMode.PAGED_VERTICAL, 2, 10))
        assertEquals("30%", readerProgressLabel(ReadingMode.CONTINUOUS_SCROLL, 2, 10))
        assertEquals("", readerProgressLabel(ReadingMode.CONTINUOUS_SCROLL, 0, 0))
    }
}
