package dev.reader.app.ui.reader

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.reader.core.search.SearchMatch
import dev.reader.core.search.SearchOptions
import dev.reader.core.search.Snippet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ReaderSearchPanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val hits = listOf(
        SearchHit(0, "Chapter One", SearchMatch(1, 4..7, Snippet("the cat sat", 4..6))),
        SearchHit(2, "Chapter Three", SearchMatch(0, 0..2, Snippet("cat nap", 0..2))),
    )

    @Test
    fun `toggles report match case, regex and whitespace options`() {
        var options = SearchOptions()
        composeRule.setContent {
            ReaderSearchPanel(SearchUiState(options = options), {}, { options = it }, {}, {})
        }

        composeRule.onNodeWithTag("search-match-case").performClick()
        assertEquals(SearchOptions(matchCase = true), options)
        composeRule.onNodeWithTag("search-whitespace").performClick()
        assertEquals(SearchOptions(anyWhitespace = true), options)
        composeRule.onNodeWithTag("search-regex").performClick()
        assertEquals(SearchOptions(regex = true), options)
    }

    @Test
    fun `whitespace matching is disabled in regex mode`() {
        composeRule.setContent {
            ReaderSearchPanel(SearchUiState(options = SearchOptions(regex = true)), {}, {}, {}, {})
        }

        composeRule.onNodeWithTag("search-whitespace").assertIsNotEnabled()
    }

    @Test
    fun `shows matches, selects them and steps with next and previous`() {
        var selected = -1
        composeRule.setContent {
            ReaderSearchPanel(SearchUiState(query = "cat", hits = hits, active = 0), {}, {}, { selected = it }, {})
        }

        composeRule.onNodeWithText("1 of 2 matches").assertExists()
        composeRule.onAllNodesWithTag("reader-search-hit")[1].performClick()
        assertEquals(1, selected)
        composeRule.onNodeWithTag("reader-search-next").performClick()
        assertEquals(1, selected)
        composeRule.onNodeWithTag("reader-search-previous").performClick()
        assertEquals(1, selected)
    }

    @Test
    fun `reports invalid regex and no matches`() {
        composeRule.setContent {
            ReaderSearchPanel(SearchUiState(query = "(", invalid = true), {}, {}, {}, {})
        }

        composeRule.onNodeWithText("That isn't a valid regular expression.").assertExists()
    }
}
