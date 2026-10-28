package dev.reader.app.ui.library

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock

@RunWith(RobolectricTestRunner::class)
class MissingBooksDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun book(id: String, title: String) =
        Book(BookId(id), title, format = BookFormat.TXT, sourcePath = "/gone/$id", addedAt = Clock.System.now())

    private val books = listOf(book("a", "Alpha"), book("b", "Bravo"))

    @Test
    fun `deselecting a book and confirming removes only the rest`() {
        var removed: List<Book>? = null
        composeRule.setContent {
            MissingBooksDialog(books = books, onRemove = { removed = it }, onDismiss = {})
        }

        composeRule.onNodeWithTag("missing-book-b").performClick()
        composeRule.onNodeWithTag("missing-books-remove").performClick()
        assertNull(removed)
        composeRule.onNodeWithTag("missing-books-remove-confirm").performClick()

        assertEquals(listOf(books[0]), removed)
    }

    @Test
    fun `select all deselects everything and disables the remove button`() {
        composeRule.setContent {
            MissingBooksDialog(books = books, onRemove = {}, onDismiss = {})
        }

        composeRule.onNodeWithTag("missing-books-select-all").performClick()

        composeRule.onNodeWithTag("missing-books-remove").assertIsNotEnabled()
    }

    @Test
    fun `cancelling the confirmation removes nothing`() {
        var removed: List<Book>? = null
        composeRule.setContent {
            MissingBooksDialog(books = books, onRemove = { removed = it }, onDismiss = {})
        }

        composeRule.onNodeWithTag("missing-books-remove").performClick()
        composeRule.onNodeWithTag("missing-books-remove-confirm").assertExists()
    }
}
