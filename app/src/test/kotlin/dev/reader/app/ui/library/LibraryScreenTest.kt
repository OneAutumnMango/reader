package dev.reader.app.ui.library

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
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
class LibraryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val novels = CategoryId("novels")
    private val categories = listOf(Category(novels, "Novels"))

    private fun book(id: String, title: String, categoryIds: Set<CategoryId> = emptySet()) = Book(
        id = BookId(id),
        title = title,
        format = BookFormat.TXT,
        sourcePath = "/books/$id.txt",
        categoryIds = categoryIds,
        addedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    @Test
    fun `shows all books when no category selected`() {
        val books = listOf(book("1", "Alpha", setOf(novels)), book("2", "Beta"))
        composeRule.setContent {
            LibraryScreen(
                books,
                categories,
                null,
                progressFor = { null },
                onCategorySelected = {},
                onBookClick = {},
                onBookLongClick = {},
                onSearchClick = {},
                onAddCategory = {},
            )
        }

        composeRule.onNodeWithText("Alpha").assertExists()
        composeRule.onNodeWithText("Beta").assertExists()
    }

    @Test
    fun `filters to the selected category`() {
        val books = listOf(book("1", "Alpha", setOf(novels)), book("2", "Beta"))
        composeRule.setContent {
            LibraryScreen(
                books,
                categories,
                novels,
                progressFor = { null },
                onCategorySelected = {},
                onBookClick = {},
                onBookLongClick = {},
                onSearchClick = {},
                onAddCategory = {},
            )
        }

        composeRule.onNodeWithText("Alpha").assertExists()
        composeRule.onAllNodesWithText("Beta").assertCountEquals(0)
    }

    @Test
    fun `pre-groups books by category while preserving book order`() {
        val other = CategoryId("other")
        val first = book("1", "Alpha", setOf(novels, other))
        val second = book("2", "Beta", setOf(novels))
        val grouped = groupBooksByCategory(listOf(first, second), categories + Category(other, "Other"))

        assertEquals(listOf(first, second), grouped[novels])
        assertEquals(listOf(first), grouped[other])
    }

    @Test
    fun `book cards keep a consistent edge margin in the grid`() {
        composeRule.setContent {
            LibraryScreen(
                listOf(book("1", "Alpha")),
                categories,
                null,
                progressFor = { null },
                onCategorySelected = {},
                onBookClick = {},
                onBookLongClick = {},
                onSearchClick = {},
                onAddCategory = {},
                modifier = Modifier.size(360.dp, 640.dp),
            )
        }

        val card = composeRule.onNodeWithTag("book-card-1").getUnclippedBoundsInRoot()
        assertEquals(8.dp, card.left)
    }

    @Test
    fun `clicking the search bar reports the click`() {
        var clicked = false
        composeRule.setContent {
            LibraryScreen(
                emptyList(),
                categories,
                null,
                progressFor = { null },
                onCategorySelected = {},
                onBookClick = {},
                onBookLongClick = {},
                onSearchClick = { clicked = true },
                onAddCategory = {},
            )
        }

        composeRule.onNodeWithText("Search books, chapters...").performClick()

        assertTrue(clicked)
    }

    @Test
    fun `swiping left on the grid selects the next category`() {
        var selected: CategoryId? = null
        composeRule.setContent {
            LibraryScreen(
                listOf(book("1", "Alpha")),
                categories,
                null,
                progressFor = { null },
                onCategorySelected = { selected = it },
                onBookClick = {},
                onBookLongClick = {},
                onSearchClick = {},
                onAddCategory = {},
            )
        }

        composeRule.onNodeWithTag("library-grid").performTouchInput {
            swipeLeft()
        }
        composeRule.waitForIdle()

        assertEquals(novels, selected)
    }

    @Test
    fun `swiping right from All does nothing`() {
        var selected: CategoryId? = CategoryId("unchanged")
        composeRule.setContent {
            LibraryScreen(
                listOf(book("1", "Alpha")),
                categories,
                null,
                progressFor = { null },
                onCategorySelected = { selected = it },
                onBookClick = {},
                onBookLongClick = {},
                onSearchClick = {},
                onAddCategory = {},
            )
        }

        composeRule.onNodeWithTag("library-grid").performTouchInput {
            swipeRight()
        }

        assertEquals(CategoryId("unchanged"), selected)
    }

    @Test
    fun `hiding All starts on the first category`() {
        var selected: CategoryId? = null
        composeRule.setContent {
            LibraryScreen(
                listOf(book("1", "Alpha", setOf(novels)), book("2", "Beta")),
                categories,
                null,
                progressFor = { null },
                onCategorySelected = { selected = it },
                onBookClick = {},
                onBookLongClick = {},
                onSearchClick = {},
                onAddCategory = {},
                showAllCategory = false,
            )
        }

        composeRule.onAllNodesWithText("All").assertCountEquals(0)
        composeRule.onAllNodesWithText("Beta").assertCountEquals(0)
        assertEquals(novels, selected)
    }
}
