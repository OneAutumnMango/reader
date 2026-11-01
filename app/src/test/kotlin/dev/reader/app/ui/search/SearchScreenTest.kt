package dev.reader.app.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import dev.reader.core.search.SearchMatch
import dev.reader.core.search.SearchOptions
import dev.reader.core.search.Snippet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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

    private fun chapter(id: String, bookId: String, title: String) =
        Chapter(ChapterId(id), BookId(bookId), title, "", 0)

    @Test
    fun `chapters whose title matched can be opened`() {
        val hobbit = book("1", "The Hobbit")
        val matched = chapter("c1", "1", "A Dragon Appears")
        var opened: Pair<Book, Chapter>? = null
        composeRule.setContent {
            SearchScreen(
                listOf(hobbit),
                query = "dragon",
                onQueryChange = {},
                onBookClick = {},
                chapterMatches = listOf(ChapterMatch(hobbit, matched)),
                onChapterClick = { b, c -> opened = b to c },
            )
        }

        composeRule.onNodeWithText("A Dragon Appears").performClick()

        assertEquals(hobbit to matched, opened)
    }

    private val textResults: TextSearchResults
        get() {
            val hobbit = book("1", "The Hobbit")
            val one = chapter("c1", "1", "Chapter One")
            val match = SearchMatch(block = 4, range = 6..11, snippet = Snippet("A big dragon slept", 6..11))
            return TextSearchResults(listOf(BookMatches(hobbit, listOf(ChapterMatches(one, listOf(match))))), 1)
        }

    @Test
    fun `text results are a tree and picking a match reports its book, chapter and block`() {
        var picked: Triple<Book, Chapter, Int>? = null
        composeRule.setContent {
            SearchScreen(
                emptyList(),
                query = "dragon",
                onQueryChange = {},
                onBookClick = {},
                inText = true,
                textResults = textResults,
                onMatchClick = { b, c, block -> picked = Triple(b, c, block) },
            )
        }

        composeRule.onNodeWithText("\"dragon\": 1 hits in 1 book").assertExists()
        composeRule.onNodeWithText("Chapter One (1)").assertExists()
        composeRule.onNodeWithTag("search-match-4").performClick()

        assertEquals(Triple(textResults.books[0].book, textResults.books[0].chapters[0].chapter, 4), picked)
    }

    @Test
    fun `collapsing a book hides its matches`() {
        var collapsed by mutableStateOf(emptySet<BookId>())
        composeRule.setContent {
            SearchScreen(
                emptyList(),
                query = "dragon",
                onQueryChange = {},
                onBookClick = {},
                inText = true,
                textResults = textResults,
                collapsed = collapsed,
                onCollapsedChange = { collapsed = it },
            )
        }

        composeRule.onNodeWithTag("search-book-1").performClick()

        composeRule.onNodeWithTag("search-match-4").assertDoesNotExist()
    }

    @Test
    fun `text search shows the option toggles and reports changes`() {
        var options = SearchOptions()
        composeRule.setContent {
            SearchScreen(
                emptyList(),
                query = "dragon",
                onQueryChange = {},
                onBookClick = {},
                inText = true,
                options = options,
                onOptionsChange = { options = it },
            )
        }

        composeRule.onNodeWithTag("search-match-case").performClick()
        assertTrue(options.matchCase)
        composeRule.onNodeWithTag("search-regex").performClick()
        assertTrue(options.regex)
    }

    @Test
    fun `title search has no option toggles`() {
        composeRule.setContent { SearchScreen(emptyList(), query = "", onQueryChange = {}, onBookClick = {}) }

        composeRule.onNodeWithTag("search-regex").assertDoesNotExist()
    }

    @Test
    fun `an invalid regular expression is explained instead of showing results`() {
        composeRule.setContent {
            SearchScreen(
                emptyList(),
                query = "(",
                onQueryChange = {},
                onBookClick = {},
                inText = true,
                options = SearchOptions(regex = true),
                invalidQuery = true,
                textResults = textResults,
            )
        }

        composeRule.onNodeWithText("That isn't a valid regular expression.").assertExists()
        composeRule.onNodeWithTag("search-match-4").assertDoesNotExist()
    }
}
