package dev.reader.app.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import dev.reader.core.model.BookId
import dev.reader.core.model.ChapterId
import dev.reader.core.stats.ReadingSessionEvent
import dev.reader.core.stats.StatsGraph
import dev.reader.core.stats.StatsSummary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class StatsGraphsSectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `create edit cancel deletion and confirm deletion preserve saved graph identity`() {
        var savedGraphs = emptyList<StatsGraph>()
        val start = Instant.parse("2026-10-19T10:00:00Z")
        val end = Instant.parse("2026-10-19T10:10:00Z")
        val sessions = listOf(ReadingSessionEvent(BookId("book"), ChapterId("chapter"), start, end, 1000))
        composeRule.setContent {
            var graphs by remember { mutableStateOf(emptyList<StatsGraph>()) }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                StatsGraphsSection(graphs, sessions, emptyList(), emptyList(), onSave = { graph ->
                    graphs = if (graphs.any { it.id == graph.id }) {
                        graphs.map { if (it.id == graph.id) graph else it }
                    } else {
                        graphs + graph
                    }
                    savedGraphs = graphs
                }, onDelete = { graph ->
                    graphs = graphs.filterNot { it.id == graph.id }
                    savedGraphs = graphs
                })
            }
        }
        composeRule.onNodeWithContentDescription("Add graph").performClick()
        composeRule.onNodeWithTag("graph-title").performTextReplacement("My reading")
        composeRule.onNodeWithTag("graph-editor-save").performClick()
        val id = savedGraphs.single().id
        composeRule.onNodeWithTag("stats-graph-$id").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Edit My reading").performScrollTo().performClick()
        composeRule.onNodeWithTag("graph-title").performTextReplacement("Chapter speed")
        composeRule.onNodeWithTag("graph-editor-save").performClick()
        assertEquals(id, savedGraphs.single().id)
        assertEquals("Chapter speed", savedGraphs.single().title)
        composeRule.onNodeWithContentDescription("Delete Chapter speed").performScrollTo().performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(1, savedGraphs.size)
        composeRule.onNodeWithContentDescription("Delete Chapter speed").performClick()
        composeRule.onNodeWithText("Delete", substring = false).performClick()
        assertEquals(0, savedGraphs.size)
    }

    @Test
    fun `empty stats still offers graph creation`() {
        composeRule.setContent {
            StatsScreen(StatsSummary()) {
                StatsGraphsSection(emptyList(), emptyList(), emptyList(), emptyList(), {}, {})
            }
        }
        composeRule.onNodeWithContentDescription("Add graph").performClick()
        composeRule.onNodeWithTag("graph-title").assertIsDisplayed()
        composeRule.onNodeWithTag("graph-editor-cancel").performClick()
        composeRule.onNodeWithText("No reading yet").assertIsDisplayed()
    }
}
