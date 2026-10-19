package dev.reader.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.reader.core.stats.GraphAxis
import dev.reader.core.stats.GraphData
import dev.reader.core.stats.GraphPlot
import dev.reader.core.stats.GraphPoint
import dev.reader.core.stats.StatsGraph
import java.text.DecimalFormat
import kotlin.math.abs

private const val GRID_STEPS = 4
private const val AREA_ALPHA = 0.16f
private const val NUMERIC_X_PADDING = 0.05
private const val ZERO_X_PADDING = 1.0
private const val CATEGORY_X_PADDING = 0.5
private const val CENTER_FRACTION = 0.5f
private const val MIN_Y_RANGE = 1.0
private const val NO_BAR_GAP = 0.5f
private const val BAR_GAP_RATIO = 0.7f
private const val BAR_FILL_RATIO = 0.9f
private const val MIN_BAR_WIDTH = 1f
private const val MAX_BAR_WIDTH = 24f
private const val KILO_SCALE = 1_000.0
private const val MEGA_SCALE = 1_000_000.0
private const val AXIS_LABEL_GAP_DP = 8
private const val X_LABEL_GAP_DP = 4
private val CHART_HEIGHT = 250.dp
private val CHART_BOTTOM = 32.dp
private val CHART_INSET = 12.dp

@Composable
internal fun StatsGraphChart(graph: StatsGraph, data: GraphData, modifier: Modifier = Modifier) {
    val points = data.series.flatMap { it.points }
    if (points.isEmpty()) {
        Text("No reading sessions match this graph.", modifier = modifier.testTag("graph-empty"))
        return
    }

    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        Color(0xFFC75A29),
        Color(0xFF16877A),
        Color(0xFFB04575),
    )
    val textStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val measurer = rememberTextMeasurer()
    val geometry = remember(graph, data) { GraphGeometry(graph, data) }
    val axisLabels = remember(graph, geometry) {
        (0..GRID_STEPS).map { step ->
            if (graph.independentScales) {
                "${step * 100 / GRID_STEPS}%"
            } else {
                graphAxisNumber(geometry.maximum(0) * step / GRID_STEPS)
            }
        }
    }
    val density = LocalDensity.current
    val leftInset = remember(axisLabels, textStyle, measurer, density) {
        with(density) {
            (axisLabels.maxOfOrNull { measurer.measure(it, textStyle).size.width.toFloat() } ?: 0f) +
                AXIS_LABEL_GAP_DP.dp.toPx()
        }
    }
    var selected by remember(graph, data) { mutableStateOf<Double?>(null) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            data.series.forEachIndexed { index, series ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(8.dp).background(palette[index % palette.size]))
                    val range = if (graph.independentScales) {
                        "; 0..${graphNumber(geometry.maximum(index))} ${series.metric.unit}"
                    } else {
                        ""
                    }
                    Text(
                        "${series.label} (${series.metric.unit})$range",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        Canvas(
            Modifier.fillMaxWidth().height(CHART_HEIGHT).testTag("stats-graph-${graph.id}")
                .semantics {
                    contentDescription = "${graph.plot.label} graph. X axis: ${data.xLabel}. " +
                        data.series.joinToString { series -> "${series.label}, ${series.points.size} points" }
                }
                .pointerInput(geometry, leftInset) {
                    detectTapGestures { offset ->
                        val width = (size.width - leftInset - CHART_INSET.toPx()).coerceAtLeast(1f)
                        val fraction = ((offset.x - leftInset) / width).coerceIn(0f, 1f)
                        selected = points.minByOrNull { abs(geometry.xFraction(it.x) - fraction) }?.x
                    }
                },
        ) {
            drawStatsGraph(
                ChartDrawConfig(
                    graph = graph,
                    data = data,
                    geometry = geometry,
                    palette = palette,
                    gridColor = gridColor,
                    textStyle = textStyle,
                    measurer = measurer,
                    leftInset = leftInset,
                    axisLabels = axisLabels,
                    selected = selected,
                ),
            )
        }
        Text(
            data.xLabel,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        selected?.let { selectedX ->
            data.series.forEach { series ->
                series.points.filter { it.x == selectedX }.forEach { point ->
                    Text(
                        "${point.label} / ${series.label}: ${graphNumber(point.y)} ${series.metric.unit}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private data class ChartPlotArea(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = (right - left).coerceAtLeast(1f)
    val height: Float get() = (bottom - top).coerceAtLeast(1f)
}

private data class BarLayout(val width: Float, val seriesIndex: Int, val seriesCount: Int)

private data class ChartDrawConfig(
    val graph: StatsGraph,
    val data: GraphData,
    val geometry: GraphGeometry,
    val palette: List<Color>,
    val gridColor: Color,
    val textStyle: TextStyle,
    val measurer: TextMeasurer,
    val leftInset: Float,
    val axisLabels: List<String>,
    val selected: Double?,
)

private fun DrawScope.drawStatsGraph(config: ChartDrawConfig) {
    val inset = CHART_INSET.toPx()
    val area = ChartPlotArea(
        left = config.leftInset,
        top = inset,
        right = size.width - inset,
        bottom = size.height - CHART_BOTTOM.toPx(),
    )
    drawGridAndYAxis(area, config.axisLabels, config.gridColor, config.textStyle, config.measurer)
    drawSeries(config.graph, config.data, config.geometry, area, config.palette)
    config.selected?.let { drawSelectedX(it, config.geometry, area, config.gridColor) }
    drawXAxisLabels(config.graph, config.data, config.geometry, area, config.textStyle, config.measurer)
}

private fun DrawScope.drawGridAndYAxis(
    area: ChartPlotArea,
    labels: List<String>,
    gridColor: Color,
    textStyle: TextStyle,
    measurer: TextMeasurer,
) {
    labels.forEachIndexed { index, label ->
        val fraction = index.toFloat() / GRID_STEPS
        val vertical = area.top + area.height * (1f - fraction)
        drawLine(gridColor, Offset(area.left, vertical), Offset(area.left + area.width, vertical))
        drawText(measurer.measure(label, textStyle), topLeft = Offset(0f, vertical - 6.dp.toPx()))
    }
}

private fun DrawScope.drawSeries(
    graph: StatsGraph,
    data: GraphData,
    geometry: GraphGeometry,
    area: ChartPlotArea,
    palette: List<Color>,
) {
    data.series.forEachIndexed { index, series ->
        val points = if (graph.plot != GraphPlot.BAR && graph.xAxis !in CATEGORY_AXES) {
            series.points.sortedBy { it.x }
        } else {
            series.points
        }
        val positions = points.map { point ->
            Offset(
                area.left + area.width * geometry.xFraction(point.x),
                area.top + area.height * (1f - geometry.yFraction(point, index)),
            )
        }
        drawGraphSeries(
            graph.plot,
            positions,
            palette[index % palette.size],
            area,
            BarLayout(geometry.barWidth(area.width, data.series.size), index, data.series.size),
        )
    }
}

private fun DrawScope.drawSelectedX(
    value: Double,
    geometry: GraphGeometry,
    area: ChartPlotArea,
    gridColor: Color,
) {
    val horizontal = area.left + area.width * geometry.xFraction(value)
    drawLine(gridColor, Offset(horizontal, area.top), Offset(horizontal, area.bottom), strokeWidth = 2.dp.toPx())
}

private fun DrawScope.drawXAxisLabels(
    graph: StatsGraph,
    data: GraphData,
    geometry: GraphGeometry,
    area: ChartPlotArea,
    textStyle: TextStyle,
    measurer: TextMeasurer,
) {
    val points = data.series.flatMap { it.points }.distinctBy { it.x }.sortedBy { it.x }
    if (points.isEmpty()) return

    val indices = listOf(0, points.lastIndex / 2, points.lastIndex).distinct()
    var lastRight = Float.NEGATIVE_INFINITY
    indices.forEach { index ->
        val point = points[index]
        val label = if (graph.xAxis in CATEGORY_AXES) point.label else graphAxisNumber(point.x)
        val measured = measurer.measure(
            label,
            textStyle,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            constraints = Constraints(maxWidth = (area.width / 3).toInt().coerceAtLeast(1)),
        )
        val horizontal = (area.left + area.width * geometry.xFraction(point.x) - measured.size.width / 2)
            .coerceIn(area.left, (area.left + area.width - measured.size.width).coerceAtLeast(area.left))
        val minimumGap = X_LABEL_GAP_DP.dp.toPx()
        if (horizontal >= lastRight + minimumGap) {
            drawText(measured, topLeft = Offset(horizontal, area.bottom + 8.dp.toPx()))
            lastRight = horizontal + measured.size.width
        }
    }
}

private fun DrawScope.drawGraphSeries(
    plot: GraphPlot,
    positions: List<Offset>,
    color: Color,
    area: ChartPlotArea,
    bar: BarLayout,
) {
    if (positions.isEmpty()) return
    val path = Path().apply {
        moveTo(positions.first().x, positions.first().y)
        positions.drop(1).forEach { lineTo(it.x, it.y) }
    }
    when (plot) {
        GraphPlot.AREA -> {
            val fill = Path().apply {
                addPath(path)
                lineTo(positions.last().x, area.bottom)
                lineTo(positions.first().x, area.bottom)
                close()
            }
            drawPath(fill, color.copy(alpha = AREA_ALPHA))
            drawPath(path, color, style = Stroke(2.dp.toPx()))
        }
        GraphPlot.LINE -> drawPath(path, color, style = Stroke(2.dp.toPx()))
        GraphPlot.BAR -> positions.groupBy { it.x }.values.forEach { duplicates ->
            val offset = (bar.seriesIndex - (bar.seriesCount - 1) / 2f) * bar.width
            val available = minOf(bar.width * BAR_FILL_RATIO, area.width)
            val barWidth = available / duplicates.size
            duplicates.forEachIndexed { index, point ->
                val left = (point.x + offset - available / 2 + index * barWidth)
                    .coerceIn(area.left, area.left + area.width - barWidth)
                drawRect(color, Offset(left, point.y), Size(barWidth, (area.bottom - point.y).coerceAtLeast(0f)))
            }
        }
        GraphPlot.SCATTER -> Unit
    }
    if (plot != GraphPlot.BAR) positions.forEach { drawCircle(color, 3.dp.toPx(), it) }
}

internal class GraphGeometry(private val graph: StatsGraph, private val data: GraphData) {
    private val points = data.series.flatMap { it.points }
    private val category = graph.xAxis in CATEGORY_AXES
    private val rawMinX = points.minOfOrNull { it.x } ?: 0.0
    private val rawMaxX = points.maxOfOrNull { it.x } ?: 1.0
    private val numericPadding = if (rawMinX == rawMaxX) {
        maxOf(abs(rawMinX) * NUMERIC_X_PADDING, ZERO_X_PADDING)
    } else {
        (rawMaxX - rawMinX) * NUMERIC_X_PADDING
    }
    private val minX = rawMinX - if (category) CATEGORY_X_PADDING else numericPadding
    private val maxX = rawMaxX + if (category) CATEGORY_X_PADDING else numericPadding
    private val sharedMaximum = points.maxOfOrNull { it.y }?.coerceAtLeast(0.0) ?: 0.0

    fun maximum(index: Int): Double = if (graph.independentScales) {
        data.series.getOrNull(index)?.points?.maxOfOrNull { it.y }?.coerceAtLeast(0.0) ?: 0.0
    } else {
        sharedMaximum
    }

    fun xFraction(value: Double): Float =
        if (maxX == minX) CENTER_FRACTION else ((value - minX) / (maxX - minX)).toFloat()

    fun yFraction(point: GraphPoint, index: Int): Float {
        val maximum = maximum(index).takeIf { it > 0.0 } ?: MIN_Y_RANGE
        return (point.y / maximum).toFloat().coerceIn(0f, 1f)
    }

    fun barWidth(width: Float, seriesCount: Int): Float {
        val positions = points.map { xFraction(it.x) }.distinct().sorted()
        val gap = positions.zipWithNext { first, second -> second - first }.minOrNull() ?: NO_BAR_GAP
        val maximumWidth = minOf(MAX_BAR_WIDTH, width.coerceAtLeast(MIN_BAR_WIDTH))
        return (width * gap * BAR_GAP_RATIO / seriesCount.coerceAtLeast(1))
            .coerceIn(MIN_BAR_WIDTH, maximumWidth)
    }
}

private fun graphNumber(value: Double): String = DecimalFormat("#,##0.#").format(value)

private fun graphAxisNumber(value: Double): String {
    val magnitude = abs(value)
    val divisor = when {
        magnitude >= MEGA_SCALE -> MEGA_SCALE
        magnitude >= KILO_SCALE -> KILO_SCALE
        else -> 1.0
    }
    val suffix = when (divisor) {
        MEGA_SCALE -> "M"
        KILO_SCALE -> "K"
        else -> ""
    }
    return "${DecimalFormat("#,##0.#").format(value / divisor)}$suffix"
}

private val CATEGORY_AXES = setOf(GraphAxis.DATE, GraphAxis.BOOK, GraphAxis.CHAPTER, GraphAxis.SESSION)
