package dev.reader.app.ui.library

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.reader.data.store.LibrarySort
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class LibrarySortMenuTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var change: Pair<LibrarySort, Boolean>? = null

    private fun open(sort: LibrarySort, descending: Boolean) {
        composeRule.setContent { LibrarySortMenu(sort, descending) { s, d -> change = s to d } }
        composeRule.onNodeWithTag("library-sort").performClick()
    }

    @Test
    fun `picking a new field uses its natural direction`() {
        open(LibrarySort.ADDED, descending = true)
        composeRule.onNodeWithTag("library-sort-TITLE").performClick()
        assertEquals(LibrarySort.TITLE to false, change)
    }

    @Test
    fun `picking the current field again flips its direction`() {
        open(LibrarySort.LAST_READ, descending = true)
        composeRule.onNodeWithTag("library-sort-LAST_READ").performClick()
        assertEquals(LibrarySort.LAST_READ to false, change)
    }
}
