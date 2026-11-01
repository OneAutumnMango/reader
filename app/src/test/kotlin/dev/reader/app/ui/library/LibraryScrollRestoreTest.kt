package dev.reader.app.ui.library

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class LibraryScrollRestoreTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val books = (1..80).map {
        Book(
            BookId("b$it"),
            "Book $it",
            format = BookFormat.EPUB,
            sourcePath = "/$it",
            addedAt = Instant.fromEpochSeconds(it.toLong()),
        )
    }

    private fun slowBooks() = flow {
        delay(50)
        emit(books)
    }

    @Test
    fun `scroll survives leaving and re-entering when the books are collected above the screen`() {
        var show by mutableStateOf(true)
        val grid = LazyGridState()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val allBooks by slowBooks().collectAsState(initial = emptyList())
            if (show) {
                LibraryScreen(
                    books = allBooks,
                    categories = emptyList(),
                    selectedCategory = null,
                    progressFor = { null },
                    onCategorySelected = {},
                    onBookClick = {},
                    onBookLongClick = {},
                    onSearchClick = {},
                    onAddCategory = {},
                    gridStateFor = { grid },
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithTag("library-grid").performScrollToIndex(40)
        composeRule.mainClock.advanceTimeBy(500)
        assertTrue(grid.firstVisibleItemIndex > 20)

        show = false
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(16)
        show = true
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(1_000)

        assertTrue(grid.firstVisibleItemIndex > 20)
    }
}
