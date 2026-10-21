package dev.reader.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.core.model.Category
import dev.reader.core.model.CategoryId

@Composable
fun BookSelectionBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onClose: () -> Unit,
    onChangeCategory: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().testTag("book-selection-bar"),
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tooltip("Clear selection") {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("book-selection-close")) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear selection")
                    }
                }
                Text(
                    "$selectedCount of $totalCount selected",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).testTag("book-selection-count"),
                )
                if (selectedCount < totalCount) {
                    Tooltip("Select all") {
                        IconButton(onClick = onSelectAll, modifier = Modifier.testTag("book-selection-select-all")) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "Select all")
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                Tooltip("Change category") {
                    IconButton(onClick = onChangeCategory, modifier = Modifier.testTag("book-selection-category")) {
                        Icon(Icons.AutoMirrored.Filled.Label, contentDescription = "Change category")
                    }
                }
                Tooltip("Mark as read") {
                    IconButton(onClick = onMarkRead, modifier = Modifier.testTag("book-selection-mark-read")) {
                        Icon(Icons.Filled.DoneAll, contentDescription = "Mark as read")
                    }
                }
                Tooltip("Mark as unread") {
                    IconButton(onClick = onMarkUnread, modifier = Modifier.testTag("book-selection-mark-unread")) {
                        Icon(Icons.Filled.RemoveDone, contentDescription = "Mark as unread")
                    }
                }
                Tooltip("Remove from library") {
                    IconButton(onClick = onRemove, modifier = Modifier.testTag("book-selection-remove")) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove from library")
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryPickerForBooks(
    categories: List<Category>,
    initial: Set<CategoryId>,
    onConfirm: (Set<CategoryId>) -> Unit,
    onDismiss: () -> Unit,
    singleCategoryPerBook: Boolean = false,
) {
    var picked by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change category") },
        text = {
            Column {
                if (singleCategoryPerBook) {
                    CategoryChoiceRow(
                        label = "No category",
                        checked = picked.isEmpty(),
                        tag = "bulk-category-picker-none",
                        single = true,
                    ) { onConfirm(emptySet()) }
                }
                categories.forEach { category ->
                    CategoryChoiceRow(
                        label = category.name,
                        checked = category.id in picked,
                        tag = "bulk-category-picker-${category.id.value}",
                        single = singleCategoryPerBook,
                    ) {
                        if (singleCategoryPerBook) {
                            onConfirm(if (category.id in picked) emptySet() else setOf(category.id))
                        } else {
                            picked = if (category.id in picked) picked - category.id else picked + category.id
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!singleCategoryPerBook) {
                TextButton(onClick = { onConfirm(picked) }) { Text("Save") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Tooltip(text: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        content = content,
    )
}
