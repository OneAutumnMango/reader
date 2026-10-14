package dev.reader.app.ui.appearance

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.reader.core.reader.ReaderSettings
import dev.reader.core.reader.ReadingMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
class ReaderSettingsEditorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `selecting a reading mode updates settings`() {
        var settings = ReaderSettings()
        composeRule.setContent {
            ReaderSettingsEditor(settings, onChange = { settings = it })
        }

        composeRule.onNodeWithTag("reading-mode-PAGED_VERTICAL").performClick()

        assertEquals(ReadingMode.PAGED_VERTICAL, settings.readingMode)
    }

    @Test
    fun `toggling selectable text off is reflected`() {
        var settings = ReaderSettings()
        composeRule.setContent {
            ReaderSettingsEditor(settings, onChange = { settings = it })
        }

        composeRule.onNodeWithTag("selectable-text-toggle").performClick()

        assertFalse(settings.selectableText)
    }

    @Test
    fun `toggling zoom off is reflected`() {
        var settings = ReaderSettings()
        composeRule.setContent {
            ReaderSettingsEditor(settings, onChange = { settings = it })
        }

        composeRule.onNodeWithTag("zoom-toggle").performClick()

        assertFalse(settings.zoomEnabled)
    }
}
