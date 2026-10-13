package dev.reader.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import dev.reader.core.model.Book
import dev.reader.core.model.Category
import dev.reader.core.model.CategoryId

/** Long-press menu for a library book: rename, change cover, change categories, mark read, remove. */
@Composable
fun BookContextMenu(
    book: Book,
    categories: List<Category>,
    onChangeCategories: (Set<CategoryId>) -> Unit,
    onRename: (String) -> Unit,
    onChangeCover: () -> Unit,
    onMarkAllRead: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    if (showCategoryPicker) {
        CategoryPickerDialog(
            categories = categories,
            selected = book.categoryIds,
            onConfirm = {
                onChangeCategories(it)
                showCategoryPicker = false
                onDismiss()
            },
            onDismiss = { showCategoryPicker = false },
        )
        return
    }

    if (showRenameDialog) {
        RenameDialog(
            currentTitle = book.title,
            onConfirm = {
                onRename(it)
                showRenameDialog = false
                onDismiss()
            },
            onDismiss = { showRenameDialog = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(book.title) },
        text = {
            Column {
                MenuRow("Rename", Modifier.testTag("book-menu-rename")) {
                    showRenameDialog = true
                }
                MenuRow("Change cover", Modifier.testTag("book-menu-change-cover")) {
                    onChangeCover()
                    onDismiss()
                }
                MenuRow("Change category", Modifier.testTag("book-menu-change-category")) {
                    showCategoryPicker = true
                }
                MenuRow("Mark all chapters as read", Modifier.testTag("book-menu-mark-read")) {
                    onMarkAllRead()
                    onDismiss()
                }
                MenuRow("Remove from library", Modifier.testTag("book-menu-remove")) {
                    onRemove()
                    onDismiss()
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MenuRow(label: String, modifier: Modifier, onClick: () -> Unit) {
    Text(
        label,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun CategoryPickerDialog(
    categories: List<Category>,
    selected: Set<CategoryId>,
    onConfirm: (Set<CategoryId>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(selected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change category") },
        text = {
            Column {
                categories.forEach { category ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = category.id in picked,
                            onCheckedChange = { checked ->
                                picked = if (checked) picked + category.id else picked - category.id
                            },
                            modifier = Modifier.testTag("category-picker-${category.id.value}"),
                        )
                        Text(category.name)
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(picked) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RenameDialog(currentTitle: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(currentTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.testTag("rename-title-field"),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title) }, enabled = title.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
