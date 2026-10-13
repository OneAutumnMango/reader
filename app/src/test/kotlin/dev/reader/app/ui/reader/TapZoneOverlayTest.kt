package dev.reader.app.ui.reader

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
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
    fun `tapping the left third triggers previous`() {
        var lastAction: TapAction? = null
        composeRule.setContent {
            TapZoneOverlay(config = TapZoneConfig(), showLabels = false, onAction = { lastAction = it })
        }

        composeRule.onNodeWithTag("tap-zone-overlay")
            .performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.1f, height * 0.5f)) }

        assertEquals(TapAction.PREVIOUS, lastAction)
    }

    @Test
    fun `tapping the right third triggers next`() {
        var lastAction: TapAction? = null
        composeRule.setContent {
            TapZoneOverlay(config = TapZoneConfig(), showLabels = false, onAction = { lastAction = it })
        }

        composeRule.onNodeWithTag("tap-zone-overlay")
            .performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.9f, height * 0.5f)) }

        assertEquals(TapAction.NEXT, lastAction)
    }

    @Test
    fun `disabled config renders nothing`() {
        var lastAction: TapAction? = null
        composeRule.setContent {
            TapZoneOverlay(config = TapZoneConfig(enabled = false), showLabels = false, onAction = { lastAction = it })
        }

        composeRule.onAllNodesWithTag("tap-zone-overlay").assertCountEquals(0)
        assertEquals(null, lastAction)
    }
}
