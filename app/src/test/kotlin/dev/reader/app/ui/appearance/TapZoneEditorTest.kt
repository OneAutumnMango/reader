package dev.reader.app.ui.appearance

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.center
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import dev.reader.core.reader.TapAction
import dev.reader.core.reader.TapZoneConfig
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

        composeRule.onNodeWithTag("tap-zone-editor-0").performClick()
        composeRule.onNodeWithTag("tap-zone-action-${TapAction.TOGGLE_BOOKMARK.name}").performClick()

        assertEquals(TapAction.TOGGLE_BOOKMARK, config.zones[0].action)
    }

    @Test
    fun `picking an action only changes the selected zone`() {
        var config = TapZoneConfig()
        composeRule.setContent {
            TapZoneEditor(config, onChange = { config = it })
        }

        composeRule.onNodeWithTag("tap-zone-editor-0").performClick()
        composeRule.onNodeWithTag("tap-zone-action-${TapAction.TOGGLE_BOOKMARK.name}").performClick()

        assertEquals(TapAction.OPEN_MENU, config.zones[1].action)
        assertEquals(TapAction.NEXT, config.zones[2].action)
    }

    @Test
    fun `selecting a different zone does not change any action`() {
        var config = TapZoneConfig()
        composeRule.setContent {
            TapZoneEditor(config, onChange = { config = it })
        }

        composeRule.onNodeWithTag("tap-zone-editor-0").performClick()
        composeRule.onNodeWithTag("tap-zone-editor-1").performClick()

        assertEquals(TapZoneConfig().zones, config.zones)
    }

    @Test
    fun `removing a zone drops it from the config`() {
        var config = TapZoneConfig()
        composeRule.setContent {
            TapZoneEditor(config, onChange = { config = it })
        }

        composeRule.onNodeWithTag("tap-zone-editor-0").performClick()
        composeRule.onNodeWithTag("remove-zone-button").performClick()

        assertEquals(2, config.zones.size)
    }

    @Test
    fun `add zone button opens the region picker`() {
        val config = TapZoneConfig()
        composeRule.setContent {
            TapZoneEditor(config, onChange = {})
        }

        composeRule.onNodeWithTag("add-zone-button").performClick()

        composeRule.onNodeWithTag("zone-region-picker").assertExists()
    }

    @Test
    fun `dragging a region and confirming adds a new zone`() {
        var config = TapZoneConfig()
        composeRule.setContent {
            TapZoneEditor(config, onChange = { config = it })
        }

        composeRule.onNodeWithTag("add-zone-button").performClick()
        composeRule.onNodeWithTag("zone-region-picker").performTouchInput {
            val start = center - Offset(80f, 80f)
            val end = center + Offset(80f, 80f)
            down(start)
            repeat(10) { step ->
                moveTo(start + (end - start) * ((step + 1) / 10f))
            }
            up()
        }
        composeRule.onNodeWithText("Confirm zone").performClick()

        assertEquals(4, config.zones.size)
        val newZone = config.zones.last()
        assertEquals(TapAction.NONE, newZone.action)
        assertTrue(newZone.left < newZone.right)
        assertTrue(newZone.top < newZone.bottom)
    }
}
