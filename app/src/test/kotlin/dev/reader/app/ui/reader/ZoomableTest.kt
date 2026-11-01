package dev.reader.app.ui.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
class ZoomableTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `toggling zoom keeps the content's state`() {
        var zoomEnabled by mutableStateOf(true)
        composeRule.setContent {
            Zoomable(zoomEnabled) {
                var count by remember { mutableIntStateOf(0) }
                Button(onClick = { count++ }) { Text("Count $count") }
            }
        }

        composeRule.onNodeWithText("Count 0").performClick()
        composeRule.onNodeWithText("Count 1").assertExists()

        zoomEnabled = false
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Count 1").assertExists()
    }

    @Test
    fun `a double tap is consumed even when zoom is fully disabled, so it can't reach text selection below`() {
        var doubleTapped = false
        composeRule.setContent {
            Zoomable(enabled = false) {
                Box(
                    Modifier
                        .size(200.dp)
                        .testTag("content")
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { doubleTapped = true })
                        },
                )
            }
        }

        composeRule.onNodeWithTag("content").performTouchInput {
            down(center)
            up()
            advanceEventTime(50)
            down(center)
            up()
        }

        assertFalse(doubleTapped)
    }
}
