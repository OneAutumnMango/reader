package dev.reader.app.ui.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import dev.reader.core.reader.ReaderSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AutoScrollEditorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `tapping the preview sets the reading line position`() {
        var settings = ReaderSettings(readingLineEnabled = true)
        composeRule.setContent { AutoScrollEditor(settings, onChange = { settings = it }) }

        composeRule.onNodeWithTag("reading-line-preview").performTouchInput {
            click(Offset(width / 2f, height * 0.75f))
        }

        assertEquals(0.75f, settings.readingLinePosition, 0.02f)
    }

    @Test
    fun `tapping the very edge keeps the line on screen`() {
        var settings = ReaderSettings(readingLineEnabled = true)
        composeRule.setContent { AutoScrollEditor(settings, onChange = { settings = it }) }

        composeRule.onNodeWithTag("reading-line-preview").performTouchInput { click(Offset(width / 2f, 0f)) }

        assertTrue(settings.readingLinePosition in 0.02f..0.1f)
    }

    @Test
    fun `toggle enables the reading line`() {
        var settings = ReaderSettings()
        composeRule.setContent { AutoScrollEditor(settings, onChange = { settings = it }) }

        composeRule.onNodeWithTag("reading-line-toggle").performClick()

        assertTrue(settings.readingLineEnabled)
    }

    @Test
    fun `dragging on the preview keeps moving the line`() {
        var settings by mutableStateOf(ReaderSettings(readingLineEnabled = true))
        composeRule.setContent { AutoScrollEditor(settings, onChange = { settings = it }) }

        composeRule.onNodeWithTag("reading-line-preview").performTouchInput {
            down(Offset(width / 2f, height * 0.2f))
            moveTo(Offset(width / 2f, height * 0.4f))
            moveTo(Offset(width / 2f, height * 0.6f))
            moveTo(Offset(width / 2f, height * 0.8f))
            up()
        }

        assertEquals(0.8f, settings.readingLinePosition, 0.05f)
    }
}
