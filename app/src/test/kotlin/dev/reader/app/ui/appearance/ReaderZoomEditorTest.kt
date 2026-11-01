package dev.reader.app.ui.appearance

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.reader.core.reader.ReaderSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
class ReaderZoomEditorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `toggling pinch to zoom off is reflected`() {
        var settings = ReaderSettings()
        composeRule.setContent {
            ReaderZoomEditor(settings, onChange = { settings = it })
        }

        composeRule.onNodeWithTag("zoom-toggle").performClick()

        assertFalse(settings.zoomEnabled)
    }

    @Test
    fun `toggling double tap to zoom off is reflected`() {
        var settings = ReaderSettings()
        composeRule.setContent {
            ReaderZoomEditor(settings, onChange = { settings = it })
        }

        composeRule.onNodeWithTag("double-tap-zoom-toggle").performClick()

        assertFalse(settings.doubleTapZoomEnabled)
    }
}
