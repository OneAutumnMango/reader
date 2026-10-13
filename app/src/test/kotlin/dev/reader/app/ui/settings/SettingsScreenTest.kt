package dev.reader.app.ui.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `clicking add files triggers the callback`() {
        var clicked = false
        composeRule.setContent {
            SettingsScreen(status = "", onAddFiles = { clicked = true }, onAddFolder = {})
        }

        composeRule.onNodeWithText("Add files").performClick()

        assertTrue(clicked)
    }

    @Test
    fun `clicking add folder triggers the callback`() {
        var clicked = false
        composeRule.setContent {
            SettingsScreen(status = "", onAddFiles = {}, onAddFolder = { clicked = true })
        }

        composeRule.onNodeWithText("Add light novel folder").performClick()

        assertTrue(clicked)
    }

    @Test
    fun `shows the status message`() {
        composeRule.setContent {
            SettingsScreen(status = "Imported 3 of 3 file(s)", onAddFiles = {}, onAddFolder = {})
        }

        composeRule.onNodeWithText("Imported 3 of 3 file(s)").assertExists()
    }
}
