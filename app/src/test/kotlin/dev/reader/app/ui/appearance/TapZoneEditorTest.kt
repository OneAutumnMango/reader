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
    fun `selecting a zone then an action updates that zone`() {
        var config = TapZoneConfig()
        composeRule.setContent {
            TapZoneEditor(config, onChange = { config = it })
        }

        composeRule.onNodeWithTag("tap-zone-editor-0-0").performClick()
        composeRule.onNodeWithTag("tap-zone-action-${TapAction.TOGGLE_BOOKMARK.name}").performClick()

        assertEquals(TapAction.TOGGLE_BOOKMARK, config.actionAt(0, 0))
    }

    @Test
    fun `picking an action only changes the selected zone`() {
        var config = TapZoneConfig()
        composeRule.setContent {
            TapZoneEditor(config, onChange = { config = it })
        }

        composeRule.onNodeWithTag("tap-zone-editor-0-0").performClick()
        composeRule.onNodeWithTag("tap-zone-action-${TapAction.TOGGLE_BOOKMARK.name}").performClick()

        assertEquals(TapAction.OPEN_MENU, config.actionAt(0, 1))
        assertEquals(TapAction.NEXT, config.actionAt(0, 2))
    }

    @Test
    fun `selecting a different zone does not change any action`() {
        var config = TapZoneConfig()
        composeRule.setContent {
            TapZoneEditor(config, onChange = { config = it })
        }

        composeRule.onNodeWithTag("tap-zone-editor-0-0").performClick()
        composeRule.onNodeWithTag("tap-zone-editor-1-1").performClick()

        assertEquals(TapZoneConfig().zones, config.zones)
    }
}
