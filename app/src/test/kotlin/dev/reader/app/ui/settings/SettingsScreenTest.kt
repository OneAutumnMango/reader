package dev.reader.app.ui.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.reader.core.theme.LibraryLayout
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
                onViewLogHistory = {},
                libraryLayout = LibraryLayout.MEDIUM,
                onLibraryLayoutChange = {},
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
                onViewLogHistory = {},
                libraryLayout = LibraryLayout.MEDIUM,
                onLibraryLayoutChange = {},
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
                onViewLogHistory = {},
                libraryLayout = LibraryLayout.MEDIUM,
                onLibraryLayoutChange = {},
            )
        }

        composeRule.onNodeWithText("Add root folder").performClick()

        assertTrue(clicked)
    }

    @Test
    fun `scan now triggers the callback when root folders exist`() {
        var scanned = false
        composeRule.setContent {
            SettingsScreen(
                status = "",
                rootFolders = listOf(RootFolderDisplay(uri = "content://tree/1", displayName = "Books")),
                onAddBook = {},
                onAddLightNovel = {},
                onAddRootFolder = {},
                onRemoveRootFolder = {},
                onViewLogHistory = {},
                libraryLayout = LibraryLayout.MEDIUM,
                onLibraryLayoutChange = {},
                onScanRootFolders = { scanned = true },
            )
        }

        composeRule.onNodeWithTag("scan-root-folders").performScrollTo().performClick()

        assertTrue(scanned)
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
                onViewLogHistory = {},
                libraryLayout = LibraryLayout.MEDIUM,
                onLibraryLayoutChange = {},
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
                onViewLogHistory = {},
                libraryLayout = LibraryLayout.MEDIUM,
                onLibraryLayoutChange = {},
            )
        }

        composeRule.onNodeWithText("Imported 3 of 3 file(s)").assertExists()
    }

    @Test
    fun `clicking view log history triggers the callback`() {
        var clicked = false
        composeRule.setContent {
            SettingsScreen(
                status = "",
                rootFolders = emptyList(),
                onAddBook = {},
                onAddLightNovel = {},
                onAddRootFolder = {},
                onRemoveRootFolder = {},
                onViewLogHistory = { clicked = true },
                libraryLayout = LibraryLayout.MEDIUM,
                onLibraryLayoutChange = {},
            )
        }

        composeRule.onNodeWithText("View error/log history").performScrollTo().performClick()

        assertTrue(clicked)
    }

    @Test
    fun `selecting a layout size reports it`() {
        var selected: LibraryLayout? = null
        composeRule.setContent {
            SettingsScreen(
                status = "",
                rootFolders = emptyList(),
                onAddBook = {},
                onAddLightNovel = {},
                onAddRootFolder = {},
                onRemoveRootFolder = {},
                onViewLogHistory = {},
                libraryLayout = LibraryLayout.MEDIUM,
                onLibraryLayoutChange = { selected = it },
            )
        }

        composeRule.onNodeWithTag("library-layout-${LibraryLayout.LARGE.name}").performClick()

        assertEquals(LibraryLayout.LARGE, selected)
    }
}
