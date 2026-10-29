package dev.reader.app.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
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
    onFindCoverOnline: (() -> Unit)? = null,
    onMarkAllRead: () -> Unit,
    onMarkAllUnread: () -> Unit = {},
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    allRead: Boolean = false,
    onDetectChapters: (() -> Unit)? = null,
    onReimport: (() -> Unit)? = null,
    onShowDetails: (() -> Unit)? = null,
    singleCategoryPerBook: Boolean = false,
) {
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showCoverSourcePicker by remember { mutableStateOf(false) }

    if (showCategoryPicker) {
        CategoryPickerDialog(
            categories = categories,
            selected = book.categoryIds,
            singleCategoryPerBook = singleCategoryPerBook,
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

    if (showCoverSourcePicker) {
        CoverSourcePickerDialog(
            onChooseFromDevice = {
                showCoverSourcePicker = false
                onChangeCover()
                onDismiss()
            },
            onSearchOnline = {
                showCoverSourcePicker = false
                onFindCoverOnline?.invoke()
                onDismiss()
            },
            onDismiss = { showCoverSourcePicker = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(book.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (onShowDetails != null) {
                    MenuRow("Details", Modifier.testTag("book-menu-details")) {
                        onShowDetails()
                        onDismiss()
                    }
                }
                MenuRow("Rename", Modifier.testTag("book-menu-rename")) {
                    showRenameDialog = true
                }
                MenuRow("Change cover", Modifier.testTag("book-menu-change-cover")) {
                    if (onFindCoverOnline != null) {
                        showCoverSourcePicker = true
                    } else {
                        onChangeCover()
                        onDismiss()
                    }
                }
                MenuRow(
                    "Change category",
                    Modifier.testTag("book-menu-change-category"),
                    leadingIcon = Icons.AutoMirrored.Filled.Label,
                ) {
                    showCategoryPicker = true
                }
                if (onDetectChapters != null) {
                    MenuRow("Detect chapters", Modifier.testTag("book-menu-detect-chapters")) {
                        onDetectChapters()
                        onDismiss()
                    }
                }
                MenuRow(
                    if (allRead) "Mark all chapters as unread" else "Mark all chapters as read",
                    Modifier.testTag("book-menu-mark-read"),
                ) {
                    if (allRead) onMarkAllUnread() else onMarkAllRead()
                    onDismiss()
                }
                if (onReimport != null) {
                    MenuRow("Reimport", Modifier.testTag("book-menu-reimport")) {
                        onReimport()
                        onDismiss()
                    }
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
private fun MenuRow(label: String, modifier: Modifier, leadingIcon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                leadingIcon,
                contentDescription = null,
                modifier = Modifier.padding(end = 12.dp)
                    .testTag("book-menu-change-category-icon"),
            )
        }
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CategoryPickerDialog(
    categories: List<Category>,
    selected: Set<CategoryId>,
    onConfirm: (Set<CategoryId>) -> Unit,
    onDismiss: () -> Unit,
    singleCategoryPerBook: Boolean,
) {
    var picked by remember { mutableStateOf(selected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change category") },
        text = {
            Column {
                if (singleCategoryPerBook) {
                    CategoryChoiceRow(
                        label = "No category",
                        checked = picked.isEmpty(),
                        tag = "category-picker-none",
                        single = true,
                    ) { onConfirm(emptySet()) }
                }
                categories.forEach { category ->
                    CategoryChoiceRow(
                        label = category.name,
                        checked = category.id in picked,
                        tag = "category-picker-${category.id.value}",
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

@Composable
private fun CoverSourcePickerDialog(onChooseFromDevice: () -> Unit, onSearchOnline: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change cover") },
        text = {
            Column {
                MenuRow(
                    "Choose from device",
                    Modifier.testTag("book-menu-cover-from-device"),
                    onClick = onChooseFromDevice,
                )
                MenuRow(
                    "Search online",
                    Modifier.testTag("book-menu-cover-online"),
                    onClick = onSearchOnline,
                )
            }
        },
        confirmButton = {},
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
