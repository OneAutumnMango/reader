package dev.reader.app.ui.search

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class SearchScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun book(id: String, title: String) = Book(
        id = BookId(id),
        title = title,
        format = BookFormat.TXT,
        sourcePath = "/books/$id.txt",
        addedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    @Test
    fun `clicking a result reports the book`() {
        val books = listOf(book("1", "The Hobbit"), book("2", "Dune"))
        var clicked: Book? = null
        composeRule.setContent {
            SearchScreen(books, query = "", onQueryChange = {}, onBookClick = { clicked = it })
        }

        composeRule.onNodeWithText("The Hobbit").performClick()

        assertEquals(books[0], clicked)
    }

    @Test
    fun `a query filters the visible results`() {
        val books = listOf(book("1", "The Hobbit"), book("2", "Dune"))
        composeRule.setContent {
            SearchScreen(books, query = "dune", onQueryChange = {}, onBookClick = {})
        }

        composeRule.onNodeWithText("Dune").assertExists()
    }
}
