package dev.reader.app.ui.stats

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import dev.reader.core.stats.GraphAxis
import dev.reader.core.stats.GraphGrouping
import dev.reader.core.stats.GraphMetric
import dev.reader.core.stats.GraphPlot
import dev.reader.core.stats.GraphRange
import dev.reader.core.stats.StatsGraph
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class StatsGraphEditorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val books = listOf(book("one", "First book"), book("two", "Second book"))

    @Test
    fun `saves multiple metrics and selected book`() {
        var saved: StatsGraph? = null
        composeRule.setContent {
            StatsGraphEditor(StatsGraph(), books, onDismiss = {}, onSave = { saved = it })
        }

        composeRule.onNodeWithTag("graph-metric-READING_SPEED").performScrollTo().performClick()
        composeRule.onNodeWithTag("graph-book-one").performScrollTo().performClick()
        composeRule.onNodeWithTag("graph-editor-save").performClick()

        assertEquals(listOf(GraphMetric.READING_MINUTES, GraphMetric.READING_SPEED), saved?.metrics)
        assertEquals(setOf("one"), saved?.bookIds)
    }

    @Test
    fun `edits date grouping plot and range`() {
        var saved: StatsGraph? = null
        composeRule.setContent {
            StatsGraphEditor(StatsGraph(), books, onDismiss = {}, onSave = { saved = it })
        }

        composeRule.onNodeWithTag("graph-plot-dropdown").performClick()
        composeRule.onNodeWithTag("graph-plot-option-AREA").performClick()
        composeRule.onNodeWithTag("graph-grouping-dropdown").performScrollTo().performClick()
        composeRule.onNodeWithTag("graph-grouping-option-WEEK").performClick()
        composeRule.onNodeWithTag("graph-range-dropdown").performScrollTo().performClick()
        composeRule.onNodeWithTag("graph-range-option-LAST_30_DAYS").performClick()
        composeRule.onNodeWithTag("graph-editor-save").performClick()

        assertEquals(GraphPlot.AREA, saved?.plot)
        assertEquals(GraphGrouping.WEEK, saved?.grouping)
        assertEquals(GraphRange.LAST_30_DAYS, saved?.range)
        assertEquals(GraphAxis.DATE, saved?.xAxis)
    }

    @Test
    fun `swap button exchanges the x axis and the single y metric`() {
        var saved: StatsGraph? = null
        composeRule.setContent {
            StatsGraphEditor(
                StatsGraph(xAxis = GraphAxis.WORDS, metrics = listOf(GraphMetric.READING_MINUTES)),
                books,
                onDismiss = {},
                onSave = { saved = it },
            )
        }

        composeRule.onNodeWithTag("graph-swap-axes").performScrollTo().performClick()
        composeRule.onNodeWithTag("graph-editor-save").performClick()

        assertEquals(GraphAxis.READING_MINUTES, saved?.xAxis)
        assertEquals(listOf(GraphMetric.WORDS), saved?.metrics)
    }

    @Test
    fun `swap button is hidden for category axes or multiple metrics`() {
        composeRule.setContent {
            StatsGraphEditor(StatsGraph(), books, onDismiss = {}, onSave = {})
        }

        composeRule.onAllNodesWithTag("graph-swap-axes").assertCountEquals(0)
    }

    @Test
    fun `save is disabled when no metric is selected`() {
        composeRule.setContent {
            StatsGraphEditor(
                StatsGraph(metrics = listOf(GraphMetric.READING_MINUTES)),
                books,
                onDismiss = {},
                onSave = {},
            )
        }

        composeRule.onNodeWithTag("graph-metric-READING_MINUTES").performScrollTo().performClick()
        composeRule.onNodeWithTag("graph-editor-save").assertIsNotEnabled()
    }

    @Test
    fun `cancel dismisses without saving`() {
        var dismissed = false
        var saved = false
        composeRule.setContent {
            StatsGraphEditor(
                StatsGraph(),
                books,
                onDismiss = { dismissed = true },
                onSave = { saved = true },
            )
        }

        composeRule.onNodeWithTag("graph-editor-cancel").performClick()

        assertEquals(true, dismissed)
        assertFalse(saved)
    }

    private fun book(id: String, title: String) = Book(
        id = BookId(id),
        title = title,
        format = BookFormat.EPUB,
        sourcePath = "/$id.epub",
        addedAt = Instant.fromEpochMilliseconds(0),
    )
}
