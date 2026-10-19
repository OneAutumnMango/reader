package dev.reader.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.core.model.Book
import dev.reader.core.model.Chapter
import dev.reader.core.stats.ReadingSessionEvent
import dev.reader.core.stats.StatsGraph
import dev.reader.core.stats.StatsGraphCalculator
import java.util.UUID

@Composable
internal fun StatsGraphsSection(
    graphs: List<StatsGraph>,
    sessions: List<ReadingSessionEvent>,
    books: List<Book>,
    chapters: List<Chapter>,
    onSave: (StatsGraph) -> Unit,
    onDelete: (StatsGraph) -> Unit,
) {
    var editing by remember { mutableStateOf<StatsGraph?>(null) }
    var deleting by remember { mutableStateOf<StatsGraph?>(null) }
    val titles = remember(books) { books.associate { it.id to it.title } }
    Column(Modifier.fillMaxWidth().testTag("stats-graphs"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Graphs", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            GraphIconButton("Add graph", Icons.Default.Add) {
                editing = StatsGraph(id = UUID.randomUUID().toString(), title = "Reading time")
            }
        }
        graphs.forEach { graph ->
            val data = remember(graph, sessions, titles, chapters) {
                StatsGraphCalculator.calculate(graph, sessions, titles, chapters)
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("saved-graph-${graph.id}"),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(graph.title, style = MaterialTheme.typography.titleSmall)
                        Text("${graph.plot.label} / ${graph.range.label}", style = MaterialTheme.typography.labelSmall)
                    }
                    GraphIconButton("Edit ${graph.title}", Icons.Default.Edit) { editing = graph }
                    GraphIconButton("Delete ${graph.title}", Icons.Default.Delete) { deleting = graph }
                }
                StatsGraphChart(graph, data)
                HorizontalDivider(Modifier.padding(top = 8.dp))
            }
        }
    }
    editing?.let { graph ->
        StatsGraphEditor(graph, books, onDismiss = { editing = null }, onSave = onSave) { draft ->
            val preview = remember(draft, sessions, titles, chapters) {
                StatsGraphCalculator.calculate(draft, sessions, titles, chapters)
            }
            StatsGraphChart(draft, preview)
        }
    }
    deleting?.let { graph ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete graph?") },
            text = { Text(graph.title) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(graph)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GraphIconButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick) { Icon(icon, contentDescription = label) }
    }
}
