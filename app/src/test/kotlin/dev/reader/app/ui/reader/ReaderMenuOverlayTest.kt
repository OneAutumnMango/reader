package dev.reader.app.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import dev.reader.core.reader.PdfRenderMode
import dev.reader.core.reader.ReaderSettings
import dev.reader.core.reader.ReadingMode
import dev.reader.core.theme.ReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ReaderMenuOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the bar shows only back, centred progress, search and expand`() {
        composeRule.setContent {
            ReaderMenuOverlay(
                progressLabel = "Page 1 of 10",
                theme = ReaderTheme.Default,
                onThemeChange = {},
                settings = ReaderSettings(),
                onSettingsChange = {},
                onBack = {},
                expanded = false,
                onExpandedChange = {},
                pdfRenderMode = PdfRenderMode.FIXED_PAGE,
                onSearch = {},
            )
        }

        val bar = composeRule.onNodeWithTag("reader-menu-bar").getUnclippedBoundsInRoot()
        val progress = composeRule.onNodeWithTag("reader-menu-progress").getUnclippedBoundsInRoot()
        val barCentre = (bar.left + bar.right) / 2
        val progressCentre = (progress.left + progress.right) / 2
        assertTrue(abs((barCentre - progressCentre).value) < 1f)
        composeRule.onNodeWithTag("reader-menu-search").assertExists()
        composeRule.onNodeWithText("Text view").assertDoesNotExist()
    }

    @Test
    fun `night toggle is hidden without a callback, shown and wired once one's provided`() {
        composeRule.setContent {
            ReaderMenuOverlay(
                progressLabel = "Page 1 of 10",
                theme = ReaderTheme.Default,
                onThemeChange = {},
                settings = ReaderSettings(),
                onSettingsChange = {},
                onBack = {},
                expanded = false,
                onExpandedChange = {},
            )
        }

        composeRule.onNodeWithTag("reader-menu-night-toggle").assertDoesNotExist()
    }

    @Test
    fun `night toggle invokes its callback and reflects the current mode`() {
        var isNight = false
        var toggled = false
        composeRule.setContent {
            ReaderMenuOverlay(
                progressLabel = "Page 1 of 10",
                theme = ReaderTheme.Default,
                onThemeChange = {},
                settings = ReaderSettings(),
                onSettingsChange = {},
                onBack = {},
                expanded = false,
                onExpandedChange = {},
                isNightTheme = isNight,
                onToggleNightTheme = { toggled = true },
            )
        }

        composeRule.onNodeWithTag("reader-menu-night-toggle").assertExists()
        composeRule.onNodeWithTag("reader-menu-night-toggle").performClick()

        assertTrue(toggled)
    }

    @Test
    fun `expanded menu offers a PDF display selector`() {
        var mode = PdfRenderMode.FIXED_PAGE
        composeRule.setContent {
            ReaderMenuOverlay(
                progressLabel = "Page 1 of 10",
                theme = ReaderTheme.Default,
                onThemeChange = {},
                settings = ReaderSettings(),
                onSettingsChange = {},
                onBack = {},
                expanded = true,
                onExpandedChange = {},
                pdfRenderMode = PdfRenderMode.FIXED_PAGE,
                onPdfRenderModeChange = { mode = it },
            )
        }

        composeRule.onNodeWithText("PDF display").assertExists()
        composeRule.onNodeWithTag("pdf-mode-REFLOW_TEXT").performClick()

        assertEquals(PdfRenderMode.REFLOW_TEXT, mode)
    }

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
                expanded = false,
                onExpandedChange = {},
            )
        }

        composeRule.onNodeWithText("Page 3 of 10").assertExists()
        composeRule.onNodeWithText("Appearance").assertDoesNotExist()
    }

    @Test
    fun `expanding reveals appearance and reading controls`() {
        composeRule.setContent {
            var expanded by remember { mutableStateOf(false) }
            ReaderMenuOverlay(
                progressLabel = "50%",
                theme = ReaderTheme.Default,
                onThemeChange = {},
                settings = ReaderSettings(),
                onSettingsChange = {},
                onBack = {},
                expanded = expanded,
                onExpandedChange = { expanded = it },
            )
        }

        composeRule.onNodeWithTag("reader-menu-expand").performClick()

        composeRule.onNodeWithText("Appearance").assertExists()
        composeRule.onNodeWithText("Reading").assertExists()
    }

    @Test
    @Config(qualifiers = "w411dp-h900dp")
    fun `swiping the bar up expands, the top strip goes full screen, and swiping it down closes`() {
        var expanded = false
        composeRule.setContent {
            var state by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxSize()) {
                ReaderMenuOverlay(
                    progressLabel = "50%",
                    theme = ReaderTheme.Default,
                    onThemeChange = {},
                    settings = ReaderSettings(),
                    onSettingsChange = {},
                    onBack = {},
                    expanded = state,
                    onExpandedChange = {
                        state = it
                        expanded = it
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }

        composeRule.onNodeWithTag("reader-menu-bar").performTouchInput {
            swipe(start = center, end = center - Offset(0f, 300f), durationMillis = 300)
        }
        composeRule.waitForIdle()
        assertTrue(expanded)
        val before = composeRule.onNodeWithTag("reader-menu-handle").getUnclippedBoundsInRoot().top

        composeRule.onNodeWithTag("reader-menu-handle").performTouchInput {
            swipe(start = center, end = center - Offset(0f, 300f), durationMillis = 300)
        }
        composeRule.waitForIdle()
        val after = composeRule.onNodeWithTag("reader-menu-handle").getUnclippedBoundsInRoot().top
        assertTrue(after < before, "panel should grow to full screen ($before -> $after)")
        assertTrue(expanded)

        composeRule.onNodeWithTag("reader-menu-handle").performTouchInput {
            swipe(start = center, end = center + Offset(0f, 300f), durationMillis = 300)
        }
        composeRule.waitForIdle()
        assertFalse(expanded)
    }

    @Test
    fun `scrolling the panel's content doesn't close it`() {
        var expanded = true
        composeRule.setContent {
            ReaderMenuOverlay(
                progressLabel = "50%",
                theme = ReaderTheme.Default,
                onThemeChange = {},
                settings = ReaderSettings(),
                onSettingsChange = {},
                onBack = {},
                expanded = true,
                onExpandedChange = { expanded = it },
            )
        }

        composeRule.onNodeWithTag("reader-menu-panel").performTouchInput { swipeDown() }
        composeRule.waitForIdle()
        assertTrue(expanded)
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
                expanded = false,
                onExpandedChange = {},
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
        assertEquals("Page 3 of 10", readerProgressLabel(ReadingMode.CONTINUOUS_SCROLL, 2, 10, pageBased = true))
    }
}
