package dev.reader.app.ui.stats

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.reader.core.stats.GraphAxis
import dev.reader.core.stats.GraphData
import dev.reader.core.stats.GraphMetric
import dev.reader.core.stats.GraphPlot
import dev.reader.core.stats.GraphPoint
import dev.reader.core.stats.GraphSeries
import dev.reader.core.stats.StatsGraph
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w320dp-h800dp")
class StatsGraphChartTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val data = GraphData(
        listOf(
            GraphSeries(
                "Reading time", GraphMetric.READING_MINUTES,
                listOf(
                    GraphPoint("Chapter One", 0.0, 5.0),
                    GraphPoint("Chapter Two", 1.0, 15.0),
                    GraphPoint("Chapter Three", 2.0, 10.0),
                ),
            ),
        ),
        "Chapter",
    )

    @Test
    fun `line supports point inspection at phone width`() = verifyPlot(GraphPlot.LINE)

    @Test
    fun `scatter supports point inspection at phone width`() = verifyPlot(GraphPlot.SCATTER)

    @Test
    fun `bars support point inspection at phone width`() = verifyPlot(GraphPlot.BAR)

    @Test
    fun `area supports point inspection at phone width`() = verifyPlot(GraphPlot.AREA)

    @Test
    fun `shared and independent scales retain real values and sub minute positions`() {
        val mixed = GraphData(
            listOf(
                GraphSeries("Time", GraphMetric.READING_MINUTES, listOf(GraphPoint("One", 0.0, 0.5))),
                GraphSeries("Words", GraphMetric.WORDS, listOf(GraphPoint("One", 0.0, 1000.0))),
            ),
            "Chapter",
        )
        val shared = GraphGeometry(StatsGraph(), mixed)
        assertEquals(1000.0, shared.maximum(0))
        val independent = GraphGeometry(StatsGraph(independentScales = true), mixed)
        assertEquals(0.5, independent.maximum(0))
        assertEquals(1f, independent.yFraction(mixed.series[0].points.single(), 0))
        assertEquals(1f, independent.yFraction(mixed.series[1].points.single(), 1))
        assertEquals(0f, independent.yFraction(GraphPoint("Zero", 0.0, 0.0), 0))
        assertTrue(shared.xFraction(0.0).isFinite())
    }

    private fun verifyPlot(plot: GraphPlot) {
        composeRule.setContent {
            MaterialTheme {
                StatsGraphChart(StatsGraph(id = "test", plot = plot, xAxis = GraphAxis.CHAPTER), data)
            }
        }
        val node = composeRule.onNodeWithTag("stats-graph-test")
        node.assertIsDisplayed().assertHeightIsAtLeast(240.dp)
        node.performTouchInput {
            down(center)
            up()
        }
        composeRule.onNodeWithText("Chapter Two / Reading time: 15 min").assertExists()
    }
}
