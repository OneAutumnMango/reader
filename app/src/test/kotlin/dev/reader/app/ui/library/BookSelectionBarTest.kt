package dev.reader.app.ui.library

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class BookSelectionBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `removing selected books waits for confirmation`() {
        var removed = 0
        composeRule.setContent {
            BookSelectionBar(
                selectedCount = 2,
                totalCount = 5,
                onSelectAll = {},
                onClose = {},
                onChangeCategory = {},
                onMarkRead = {},
                onMarkUnread = {},
                onRemove = { removed++ },
            )
        }

        composeRule.onNodeWithTag("book-selection-remove").performClick()
        assertEquals(0, removed)
        composeRule.onNodeWithTag("cancel-remove").performClick()
        assertEquals(0, removed)

        composeRule.onNodeWithTag("book-selection-remove").performClick()
        composeRule.onNodeWithTag("confirm-remove").performClick()
        assertEquals(1, removed)
    }
}
