package dev.reader.app.ui.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.reader.data.store.CollectionKind
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

    private var addedFile = false
    private var addedFolder = false
    private var collection: CollectionKind? = null
    private var removed: String? = null
    private var scanned = false
    private var viewedLogs = false

    private fun setContent(folders: List<RootFolderDisplay> = emptyList()) = composeRule.setContent {
        SettingsScreen(
            status = "",
            rootFolders = folders,
            onAddBookFile = { addedFile = true },
            onAddBookFolder = { addedFolder = true },
            onAddCollection = { collection = it },
            onRemoveRootFolder = { removed = it },
            onViewLogHistory = { viewedLogs = true },
            onScanRootFolders = { scanned = true },
        )
    }

    @Test
    fun `add book buttons report file and folder`() {
        setContent()

        composeRule.onNodeWithText("Add book (file)").performClick()
        composeRule.onNodeWithText("Add book (folder)").performClick()

        assertTrue(addedFile)
        assertTrue(addedFolder)
    }

    @Test
    fun `collections can be added as files or folders`() {
        setContent()

        composeRule.onNodeWithTag("add-collection-FOLDERS").performScrollTo().performClick()
        assertEquals(CollectionKind.FOLDERS, collection)

        composeRule.onNodeWithTag("add-collection-FILES").performScrollTo().performClick()
        assertEquals(CollectionKind.FILES, collection)
    }

    @Test
    fun `library folders show their kind and can be removed or scanned`() {
        setContent(listOf(RootFolderDisplay("content://tree/1", "Books", CollectionKind.FILES)))

        composeRule.onNodeWithText("Each file is a book").assertExists()
        composeRule.onNodeWithContentDescription("Remove Books").performScrollTo().performClick()
        composeRule.onNodeWithTag("scan-root-folders").performScrollTo().performClick()

        assertEquals("content://tree/1", removed)
        assertTrue(scanned)
    }

    @Test
    fun `log history button reports the click`() {
        setContent()

        composeRule.onNodeWithText("View error/log history").performScrollTo().performClick()

        assertTrue(viewedLogs)
    }
}
