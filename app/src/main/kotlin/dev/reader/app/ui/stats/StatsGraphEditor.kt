package dev.reader.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.reader.core.model.Book
import dev.reader.core.stats.GraphAxis
import dev.reader.core.stats.GraphGrouping
import dev.reader.core.stats.GraphMetric
import dev.reader.core.stats.GraphPlot
import dev.reader.core.stats.GraphRange
import dev.reader.core.stats.StatsGraph

@Composable
internal fun StatsGraphEditor(
    initial: StatsGraph,
    books: List<Book>,
    onDismiss: () -> Unit,
    onSave: (StatsGraph) -> Unit,
    preview: @Composable (StatsGraph) -> Unit = {},
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    val availableBooks = remember(books, draft.bookIds) {
        (books.map { it.id.value } + draft.bookIds).distinct().map { id ->
            id to (books.firstOrNull { it.id.value == id }?.title ?: "Unknown book ($id)")
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.fillMaxHeight()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Edit graph",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("graph-editor-close")) {
                        Icon(Icons.Filled.Close, contentDescription = "Close graph editor")
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedTextField(
                        value = draft.title,
                        onValueChange = { draft = draft.copy(title = it) },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("graph-title"),
                    )
                    DropdownField(
                        value = draft.plot,
                        options = GraphPlot.entries,
                        label = "Plot",
                        tag = "graph-plot",
                        optionLabel = { it.label },
                        optionTag = { it.name },
                        onSelected = { draft = draft.copy(plot = it) },
                    )
                    DropdownField(
                        value = draft.xAxis,
                        options = GraphAxis.entries,
                        label = "X axis",
                        tag = "graph-axis",
                        optionLabel = { it.label },
                        optionTag = { it.name },
                        onSelected = { axis ->
                            draft = draft.copy(xAxis = axis, grouping = axis.defaultGrouping())
                        },
                    )
                    SectionTitle("Metrics")
                    GraphMetric.entries.forEach { metric ->
                        CheckRow(
                            label = metric.label,
                            checked = metric in draft.metrics,
                            tag = "graph-metric-${metric.name}",
                            onCheckedChange = { checked ->
                                val metrics = if (checked) draft.metrics + metric else draft.metrics - metric
                                draft = draft.copy(metrics = metrics.distinct())
                            },
                        )
                    }
                    DropdownField(
                        value = draft.grouping,
                        options = draft.xAxis.groupingOptions(),
                        label = "Grouping",
                        tag = "graph-grouping",
                        optionLabel = { it.label() },
                        optionTag = { it.name },
                        onSelected = { draft = draft.copy(grouping = it) },
                    )
                    DropdownField(
                        value = draft.range,
                        options = GraphRange.entries,
                        label = "Date range",
                        tag = "graph-range",
                        optionLabel = { it.label },
                        optionTag = { it.name },
                        onSelected = { draft = draft.copy(range = it) },
                    )
                    SectionTitle("Books")
                    CheckRow(
                        label = "All books",
                        checked = draft.bookIds.isEmpty(),
                        tag = "graph-books-all",
                        onCheckedChange = { checked ->
                            if (checked) draft = draft.copy(bookIds = emptySet())
                        },
                    )
                    availableBooks.forEach { (id, title) ->
                        CheckRow(
                            label = title,
                            checked = id in draft.bookIds,
                            tag = "graph-book-$id",
                            onCheckedChange = { checked ->
                                val selected = draft.bookIds.toMutableSet()
                                if (checked) selected.add(id) else selected.remove(id)
                                draft = draft.copy(bookIds = selected)
                            },
                        )
                    }
                    CheckRow(
                        label = "Separate series per book",
                        checked = draft.splitByBook,
                        tag = "graph-split-by-book",
                        onCheckedChange = { draft = draft.copy(splitByBook = it) },
                    )
                    CheckRow(
                        label = "Independent scales",
                        checked = draft.independentScales,
                        tag = "graph-independent-scales",
                        onCheckedChange = { draft = draft.copy(independentScales = it) },
                    )
                    SectionTitle("Preview")
                    preview(draft)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("graph-editor-cancel")) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = {
                            onSave(draft)
                            onDismiss()
                        },
                        enabled = draft.metrics.isNotEmpty(),
                        modifier = Modifier.testTag("graph-editor-save"),
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownField(
    value: T,
    options: List<T>,
    label: String,
    tag: String,
    optionLabel: (T) -> String,
    optionTag: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = optionLabel(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag("$tag-dropdown"),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                    modifier = Modifier.testTag("$tag-option-${optionTag(option)}"),
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun CheckRow(label: String, checked: Boolean, tag: String, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.size(48.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun GraphAxis.defaultGrouping(): GraphGrouping = when (this) {
    GraphAxis.DATE -> GraphGrouping.DAY
    GraphAxis.BOOK -> GraphGrouping.BOOK
    GraphAxis.CHAPTER -> GraphGrouping.CHAPTER
    GraphAxis.SESSION -> GraphGrouping.SESSION
    else -> GraphGrouping.DAY
}

private fun GraphAxis.groupingOptions(): List<GraphGrouping> = when (this) {
    GraphAxis.DATE -> listOf(GraphGrouping.DAY, GraphGrouping.WEEK, GraphGrouping.MONTH)
    GraphAxis.BOOK -> listOf(GraphGrouping.BOOK)
    GraphAxis.CHAPTER -> listOf(GraphGrouping.CHAPTER)
    GraphAxis.SESSION -> listOf(GraphGrouping.SESSION)
    else -> GraphGrouping.entries
}

private fun GraphGrouping.label(): String = when (this) {
    GraphGrouping.DAY -> "Day"
    GraphGrouping.WEEK -> "Week"
    GraphGrouping.MONTH -> "Month"
    GraphGrouping.BOOK -> "Book"
    GraphGrouping.CHAPTER -> "Chapter"
    GraphGrouping.SESSION -> "Session"
}
