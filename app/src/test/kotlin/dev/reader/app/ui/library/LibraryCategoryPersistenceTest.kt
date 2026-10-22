package dev.reader.app.ui.library

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.reader.core.model.CategoryId
import dev.reader.core.model.Category
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryCategoryPersistenceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val novels = CategoryId("novels")
    private val categoryList = listOf(Category(novels, "Novels"), Category(CategoryId("scifi"), "Sci-fi"))

    private fun slowCategoryFlow() = flow {
        delay(50)
        emit(categoryList)
    }

    @Test
    fun `collecting categories inside a toggled subtree resets the selected category on remount`() {
        var show by mutableStateOf(true)
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            if (show) {
                val liveCategories by slowCategoryFlow().collectAsState(initial = emptyList())
                LibraryScreen(
                    books = emptyList(),
                    categories = liveCategories,
                    selectedCategory = novels,
                    progressFor = { null },
                    onCategorySelected = {},
                    onBookClick = {},
                    onBookLongClick = {},
                    onSearchClick = {},
                    onAddCategory = {},
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000) // let the first mount's flow fully deliver
        composeRule.onNodeWithText("Novels").assertIsSelected()

        // `show` is written here from outside composition (on the test thread, not from within a
        // Composable/effect). The GlobalSnapshotManager batches apply-notifications behind a
        // Choreographer-style frame callback, so without the explicit Snapshot.sendApplyNotifications()
        // nudge, flipping show false then true without an intervening composition pass would coalesce
        // into a no-observable-op: the subtree would never actually see `false`, never dispose, and
        // never remount. Sending the notification synchronously after each write (and before the clock
        // advance that drives the resulting frame) makes each toggle its own, independently observable
        // recomposition.
        show = false
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(16)
        show = true
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(16) // one frame after remount: the new flow hasn't delivered yet

        // The fresh mount's categories haven't arrived yet, so pages == [null] ("All") and the pager
        // is constructed already sitting on page 0 — the regression described in the spec: the tab
        // bar visibly resets to "All" (and the "Novels" tab doesn't even exist yet) before sliding
        // back to "Novels" once the real list arrives.
        composeRule.onNodeWithText("All").assertIsSelected()
    }

    @Test
    fun `collecting categories outside the toggled subtree survives remount without resetting`() {
        var show by mutableStateOf(true)
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            val liveCategories by slowCategoryFlow().collectAsState(initial = emptyList())
            if (show) {
                LibraryScreen(
                    books = emptyList(),
                    categories = liveCategories,
                    selectedCategory = novels,
                    progressFor = { null },
                    onCategorySelected = {},
                    onBookClick = {},
                    onBookLongClick = {},
                    onSearchClick = {},
                    onAddCategory = {},
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithText("Novels").assertIsSelected()

        show = false
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(16)
        show = true
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(16)

        // The categories flow lives outside the toggled subtree, so it was never torn down and already
        // holds the real list by the time the screen remounts: no empty-frame window, no reset.
        composeRule.onNodeWithText("Novels").assertIsSelected()
    }
}
