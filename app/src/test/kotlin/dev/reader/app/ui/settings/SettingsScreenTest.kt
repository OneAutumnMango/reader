package dev.reader.app.ui.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `clicking add book triggers the callback`() {
        var clicked = false
        composeRule.setContent {
            SettingsScreen(
                status = "",
                rootFolders = emptyList(),
                onAddBook = { clicked = true },
                onAddLightNovel = {},
                onAddRootFolder = {},
                onRemoveRootFolder = {},
            )
        }

        composeRule.onNodeWithText("Add book").performClick()

        assertTrue(clicked)
    }

    @Test
    fun `clicking add light novel triggers the callback`() {
        var clicked = false
        composeRule.setContent {
            SettingsScreen(
                status = "",
                rootFolders = emptyList(),
                onAddBook = {},
                onAddLightNovel = { clicked = true },
                onAddRootFolder = {},
                onRemoveRootFolder = {},
            )
        }

        composeRule.onNodeWithText("Add light novel").performClick()

        assertTrue(clicked)
    }

    @Test
    fun `clicking add root folder triggers the callback`() {
        var clicked = false
        composeRule.setContent {
            SettingsScreen(
                status = "",
                rootFolders = emptyList(),
                onAddBook = {},
                onAddLightNovel = {},
                onAddRootFolder = { clicked = true },
                onRemoveRootFolder = {},
            )
        }

        composeRule.onNodeWithText("Add root folder").performClick()

        assertTrue(clicked)
    }

    @Test
    fun `clicking remove on a root folder triggers the callback with its uri`() {
        var removed: String? = null
        composeRule.setContent {
            SettingsScreen(
                status = "",
                rootFolders = listOf(RootFolderDisplay(uri = "content://tree/1", displayName = "Books")),
                onAddBook = {},
                onAddLightNovel = {},
                onAddRootFolder = {},
                onRemoveRootFolder = { removed = it },
            )
        }

        composeRule.onNodeWithContentDescription("Remove Books").performClick()

        assertEquals("content://tree/1", removed)
    }

    @Test
    fun `shows the status message`() {
        composeRule.setContent {
            SettingsScreen(
                status = "Imported 3 of 3 file(s)",
                rootFolders = emptyList(),
                onAddBook = {},
                onAddLightNovel = {},
                onAddRootFolder = {},
                onRemoveRootFolder = {},
            )
        }

        composeRule.onNodeWithText("Imported 3 of 3 file(s)").assertExists()
    }
}
