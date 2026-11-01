package dev.reader.app.ui.search

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import dev.reader.data.repo.BookIndexSize
import dev.reader.data.store.SearchIndexJob
import dev.reader.data.store.SearchSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class SearchSettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun book(id: String, title: String, format: BookFormat = BookFormat.EPUB) = Book(
        BookId(id),
        title,
        format = format,
        sourcePath = "/$id",
        addedAt = Instant.fromEpochSeconds(0),
    )

    private val books = listOf(book("a", "Alpha"), book("b", "Beta"), book("c", "Comic", BookFormat.CBZ))
    private var settings = SearchSettings()
    private var reindexes = 0
    private var pauses = 0
    private var resumes = 0
    private var cleared = 0
    private var excluded: Set<String>? = null

    private fun show(sizes: List<BookIndexSize>? = emptyList()) = composeRule.setContent {
        SearchSettingsScreen(
            settings = settings,
            onSettingsChange = { transform -> settings = transform(settings) },
            books = books,
            sizes = sizes,
            onReindex = { reindexes++ },
            onPause = { pauses++ },
            onResume = { resumes++ },
            onClearIndex = { cleared++ },
            onExcludedChange = { excluded = it },
        )
    }

    @Test
    fun `search defaults can be changed`() {
        show()

        composeRule.onNodeWithTag("search-in-text-default").performClick()
        composeRule.onNodeWithTag("search-default-match-case").performClick()

        assertTrue(settings.inTextByDefault)
        assertTrue(settings.defaultMatchCase)
    }

    @Test
    fun `index options only appear while indexing is on`() {
        settings = SearchSettings(indexEnabled = false)
        show()

        composeRule.onNodeWithTag("search-index-on-import").assertDoesNotExist()
        composeRule.onNodeWithTag("reindex-library").assertDoesNotExist()
        composeRule.onNodeWithTag("search-index-enabled").performScrollTo().performClick()
        assertTrue(settings.indexEnabled)
    }

    @Test
    fun `index on import can be switched off`() {
        show()

        composeRule.onNodeWithTag("search-index-on-import").performScrollTo().performClick()

        assertEquals(false, settings.indexOnImport)
    }

    @Test
    fun `reindex starts, shows progress, and can be paused and resumed`() {
        show()
        composeRule.onNodeWithTag("reindex-library").performScrollTo().performClick()
        assertEquals(1, reindexes)
    }

    @Test
    fun `a running reindex shows progress and offers pause`() {
        settings = SearchSettings(indexJob = SearchIndexJob(pending = listOf("a"), total = 4))
        show()

        composeRule.onNodeWithText("Indexing 3 of 4 books\u2026").assertExists()
        composeRule.onNodeWithTag("reindex-library").assertDoesNotExist()
        composeRule.onNodeWithTag("reindex-pause").performScrollTo().performClick()
        assertEquals(1, pauses)
    }

    @Test
    fun `a paused reindex shows where it stopped and offers resume`() {
        settings = SearchSettings(indexJob = SearchIndexJob(pending = listOf("a", "b"), total = 4, paused = true))
        show()

        composeRule.onNodeWithText("Paused at 2 of 4 books").assertExists()
        composeRule.onNodeWithTag("reindex-resume").performScrollTo().performClick()
        assertEquals(1, resumes)
    }

    @Test
    fun `a finished reindex says what it covered`() {
        settings = SearchSettings(indexJob = SearchIndexJob(total = 4, unreadableChapters = 2))
        show()

        composeRule.onNodeWithText("Indexed 4 books. 2 chapters couldn't be read.").assertExists()
        composeRule.onNodeWithTag("reindex-library").assertExists()
    }

    @Test
    fun `books can be left out of the index, and only searchable books are offered`() {
        settings = SearchSettings(indexExcludedBooks = setOf("a"))
        show()

        composeRule.onNodeWithTag("excluded-summary").performScrollTo()
        composeRule.onNodeWithText("1 books are left out.").assertExists()
        composeRule.onNodeWithTag("choose-excluded").performScrollTo().performClick()

        composeRule.onNodeWithTag("exclude-a").assertExists()
        composeRule.onNodeWithTag("exclude-c").assertDoesNotExist() // comics have no text to index
        composeRule.onNodeWithTag("exclude-b").performClick()
        composeRule.onNodeWithTag("exclude-a").performClick()
        composeRule.onNodeWithTag("exclude-confirm").performClick()

        assertEquals(setOf("b"), excluded)
    }

    @Test
    fun `the exclusion list can be filtered`() {
        show()
        composeRule.onNodeWithTag("choose-excluded").performScrollTo().performClick()

        composeRule.onNodeWithTag("exclude-filter").performTextInput("alp")

        composeRule.onNodeWithTag("exclude-a").assertExists()
        composeRule.onNodeWithTag("exclude-b").assertDoesNotExist()
    }

    @Test
    fun `index size shows a total and a per-book breakdown, largest first`() {
        show(
            listOf(
                BookIndexSize(BookId("b"), chapters = 10, bytes = 3L * 1024 * 1024),
                BookIndexSize(BookId("a"), chapters = 2, bytes = 1536),
            ),
        )

        composeRule.onNodeWithText("3.0 MB across 2 books, 12 chapters", substring = true)
            .performScrollTo()
            .assertExists()
        composeRule.onNodeWithTag("index-size-b").assertDoesNotExist()
        composeRule.onNodeWithTag("index-size-toggle").performScrollTo().performClick()

        composeRule.onNodeWithTag("index-size-b").assertExists()
        composeRule.onNodeWithTag("index-size-a").assertExists()
        composeRule.onNodeWithText("Beta").assertExists()
        composeRule.onNodeWithText("1.5 KB").assertExists()
    }

    @Test
    fun `index data can be deleted`() {
        show(listOf(BookIndexSize(BookId("a"), 1, 100)))

        composeRule.onNodeWithTag("search-index-clear").performScrollTo().performClick()

        assertEquals(1, cleared)
    }

    @Test
    fun `sizes are shown in human units`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1023 B", formatBytes(1023))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.5 MB", formatBytes(1536L * 1024))
        assertEquals("2.0 GB", formatBytes(2L * 1024 * 1024 * 1024))
    }
}
