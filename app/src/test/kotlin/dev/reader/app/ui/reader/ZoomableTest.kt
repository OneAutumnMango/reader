package dev.reader.app.ui.reader

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
}
