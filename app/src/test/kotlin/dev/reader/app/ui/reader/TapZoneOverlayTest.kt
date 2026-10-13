package dev.reader.app.ui.reader

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
class TapZoneOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `tapping the left column triggers previous`() {
        var lastAction: TapAction? = null
        composeRule.setContent {
            TapZoneOverlay(config = TapZoneConfig(), showLabels = false, onAction = { lastAction = it })
        }

        composeRule.onNodeWithTag("tap-zone-1-0").performClick()

        assertEquals(TapAction.PREVIOUS, lastAction)
    }

    @Test
    fun `tapping the right column triggers next`() {
        var lastAction: TapAction? = null
        composeRule.setContent {
            TapZoneOverlay(config = TapZoneConfig(), showLabels = false, onAction = { lastAction = it })
        }

        composeRule.onNodeWithTag("tap-zone-0-2").performClick()

        assertEquals(TapAction.NEXT, lastAction)
    }

    @Test
    fun `disabled config renders nothing`() {
        var lastAction: TapAction? = null
        composeRule.setContent {
            TapZoneOverlay(config = TapZoneConfig(enabled = false), showLabels = false, onAction = { lastAction = it })
        }

        composeRule.onAllNodesWithTag("tap-zone-0-0").assertCountEquals(0)
        assertEquals(null, lastAction)
    }
}
