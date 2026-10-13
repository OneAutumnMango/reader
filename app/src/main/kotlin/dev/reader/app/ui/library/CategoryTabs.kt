package dev.reader.app.ui.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.reader.core.model.Category
import dev.reader.core.model.CategoryId

/** Swipeable category tabs, with a trailing "+" to create a new one. A null selection means "All". */
@Composable
fun CategoryTabs(
    categories: List<Category>,
    selected: CategoryId?,
    onSelect: (CategoryId?) -> Unit,
    onAddCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = categories.indexOfFirst { it.id == selected }.let { if (it < 0) 0 else it + 1 }
    var showAddDialog by remember { mutableStateOf(false) }

    PrimaryScrollableTabRow(selectedTabIndex = selectedIndex, modifier = modifier) {
        Tab(selected = selected == null, onClick = { onSelect(null) }, text = { Text("All") })
        categories.forEach { category ->
            Tab(
                selected = selected == category.id,
                onClick = { onSelect(category.id) },
                text = { Text(category.name) },
            )
        }
        Tab(
            selected = false,
            onClick = { showAddDialog = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = "Add category") },
        )
    }

    if (showAddDialog) {
        AddCategoryDialog(
            onConfirm = { name ->
                onAddCategory(name)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun AddCategoryDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New category") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.testTag("category_name_field"),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
