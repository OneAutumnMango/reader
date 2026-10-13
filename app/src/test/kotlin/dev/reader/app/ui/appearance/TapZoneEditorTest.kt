package dev.reader.app.ui.appearance

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.reader.core.reader.TapAction
import dev.reader.core.reader.TapZoneConfig
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class TapZoneEditorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `tapping a cell cycles to the next action`() {
        var config = TapZoneConfig()
        composeRule.setContent {
            TapZoneEditor(config, onChange = { config = it })
        }

        val before = config.actionAt(0, 0)
        composeRule.onNodeWithTag("tap-zone-editor-0-0").performClick()

        val expectedNext = TapAction.entries[(TapAction.entries.indexOf(before) + 1) % TapAction.entries.size]
        assertEquals(expectedNext, config.actionAt(0, 0))
    }

    @Test
    fun `cycling only changes the tapped cell`() {
        var config = TapZoneConfig()
        composeRule.setContent {
            TapZoneEditor(config, onChange = { config = it })
        }

        composeRule.onNodeWithTag("tap-zone-editor-0-0").performClick()

        assertEquals(TapAction.OPEN_MENU, config.actionAt(0, 1))
        assertEquals(TapAction.NEXT, config.actionAt(0, 2))
    }
}
