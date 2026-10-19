package dev.reader.app.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import dev.reader.core.model.Category
import dev.reader.core.model.CategoryId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class BookContextMenuTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val novels = CategoryId("novels")
    private val comics = CategoryId("comics")
    private val categories = listOf(Category(novels, "Novels"), Category(comics, "Comics"))

    private val book = Book(
        id = BookId("book-1"),
        title = "My Book",
        format = BookFormat.EPUB,
        sourcePath = "/books/my-book.epub",
        categoryIds = setOf(novels),
        addedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    @Test
    fun `details opens the book's details and dismisses`() {
        var shown = false
        var dismissed = false
        composeRule.setContent {
            BookContextMenu(
                book = book,
                categories = categories,
                onChangeCategories = {},
                onRename = {},
                onChangeCover = {},
                onMarkAllRead = {},
                onRemove = {},
                onDismiss = { dismissed = true },
                onShowDetails = { shown = true },
            )
        }

        composeRule.onNodeWithTag("book-menu-details").performClick()

        assertTrue(shown)
        assertTrue(dismissed)
    }

    @Test
    fun `mark all read triggers the callback and dismisses`() {
        var marked = false
        var dismissed = false
        composeRule.setContent {
            BookContextMenu(
                book = book,
                categories = categories,
                onChangeCategories = {},
                onRename = {},
                onChangeCover = {},
                onMarkAllRead = { marked = true },
                onRemove = {},
                onDismiss = { dismissed = true },
            )
        }

        composeRule.onNodeWithTag("book-menu-mark-read").performClick()

        assertTrue(marked)
        assertTrue(dismissed)
    }

    @Test
    fun `remove triggers the callback and dismisses`() {
        var removed = false
        var dismissed = false
        composeRule.setContent {
            BookContextMenu(
                book = book,
                categories = categories,
                onChangeCategories = {},
                onRename = {},
                onChangeCover = {},
                onMarkAllRead = {},
                onRemove = { removed = true },
                onDismiss = { dismissed = true },
            )
        }

        composeRule.onNodeWithTag("book-menu-remove").performClick()

        assertTrue(removed)
        assertTrue(dismissed)
    }

    @Test
    fun `changing category reports the new selection`() {
        var newCategories: Set<CategoryId>? = null
        composeRule.setContent {
            BookContextMenu(
                book = book,
                categories = categories,
                onChangeCategories = { newCategories = it },
                onRename = {},
                onChangeCover = {},
                onMarkAllRead = {},
                onRemove = {},
                onDismiss = {},
            )
        }

        composeRule.onNodeWithTag("book-menu-change-category").performClick()
        composeRule.onNodeWithTag("category-picker-${comics.value}").performClick()
        composeRule.onNodeWithText("Save").performClick()

        assertEquals(setOf(novels, comics), newCategories)
    }

    @Test
    fun `renaming reports the new title and dismisses`() {
        var renamedTo: String? = null
        var dismissed = false
        composeRule.setContent {
            BookContextMenu(
                book = book,
                categories = categories,
                onChangeCategories = {},
                onRename = { renamedTo = it },
                onChangeCover = {},
                onMarkAllRead = {},
                onRemove = {},
                onDismiss = { dismissed = true },
            )
        }

        composeRule.onNodeWithTag("book-menu-rename").performClick()
        composeRule.onNodeWithTag("rename-title-field").performTextClearance()
        composeRule.onNodeWithTag("rename-title-field").performTextInput("New Title")
        composeRule.onNodeWithText("Save").performClick()

        assertEquals("New Title", renamedTo)
        assertTrue(dismissed)
    }

    @Test
    fun `change cover triggers the callback and dismisses`() {
        var clicked = false
        var dismissed = false
        composeRule.setContent {
            BookContextMenu(
                book = book,
                categories = categories,
                onChangeCategories = {},
                onRename = {},
                onChangeCover = { clicked = true },
                onMarkAllRead = {},
                onRemove = {},
                onDismiss = { dismissed = true },
            )
        }

        composeRule.onNodeWithTag("book-menu-change-cover").performClick()

        assertTrue(clicked)
        assertTrue(dismissed)
    }

    @Test
    fun `detect chapters appears for supported formats and invokes its callback`() {
        val htmlBook = book.copy(format = BookFormat.HTML)
        var detected = false
        var showHtml by mutableStateOf(true)
        composeRule.setContent {
            val selectedBook = if (showHtml) htmlBook else book
            val detectChapters: (() -> Unit)? = if (selectedBook.supportsChapterDetection()) {
                {
                    detected = true
                    showHtml = false
                }
            } else {
                null
            }
            BookContextMenu(
                book = selectedBook,
                categories = categories,
                onChangeCategories = {},
                onRename = {},
                onChangeCover = {},
                onMarkAllRead = {},
                onRemove = {},
                onDismiss = {},
                onDetectChapters = detectChapters,
            )
        }

        composeRule.onNodeWithTag("book-menu-detect-chapters").performClick()
        assertTrue(detected)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("book-menu-detect-chapters").assertCountEquals(0)
    }
}
