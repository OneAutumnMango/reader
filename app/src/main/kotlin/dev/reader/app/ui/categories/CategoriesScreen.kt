package dev.reader.app.ui.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.app.ui.MenuSection
import dev.reader.app.ui.library.AddCategoryDialog
import dev.reader.core.model.Category
import dev.reader.core.model.CategoryId

@Composable
fun CategoriesScreen(
    categories: List<Category>,
    showAllCategory: Boolean,
    onShowAllCategoryChange: (Boolean) -> Unit,
    onAdd: (String) -> Unit,
    onDelete: (Category) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onDefaultChange: (CategoryId?) -> Unit,
    singleCategoryPerBook: Boolean = false,
    onSingleCategoryPerBookChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Category?>(null) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MenuSection("Library tabs") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("show-all-toggle")
                    .clickable { onShowAllCategoryChange(!showAllCategory) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Show \"All\" tab")
                Switch(checked = showAllCategory, onCheckedChange = null)
            }
            if (showAllCategory.not() && categories.isEmpty()) {
                Text(
                    "\"All\" stays visible until you add a category.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        MenuSection("Categories") {
            Row(
                modifier = Modifier.fillMaxWidth().testTag("single-category-toggle")
                    .clickable { onSingleCategoryPerBookChange(!singleCategoryPerBook) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("One category per book")
                    Text(
                        "Selecting a category removes its other category assignments.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = singleCategoryPerBook, onCheckedChange = null)
            }
            val categoryBounds = remember { mutableStateMapOf<CategoryId, Rect>() }
            if (categories.isEmpty()) {
                Text("No categories yet.", style = MaterialTheme.typography.bodyMedium)
            }
            categories.forEachIndexed { index, category ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        category.name,
                        modifier = Modifier.weight(1f).onGloballyPositioned {
                            categoryBounds[category.id] = it.boundsInRoot()
                        },
                    )
                    CategoryDragHandle(category, categories, categoryBounds, onMove)
                    IconButton(
                        onClick = { pendingDelete = category },
                        modifier = Modifier.testTag("delete-${category.id.value}"),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${category.name}")
                    }
                }
                HorizontalDivider()
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Add category")
            }
        }

        MenuSection("Default category for new books") {
            DefaultOption("None", selected = categories.none { it.isDefault }, tag = "default-none") {
                onDefaultChange(null)
            }
            categories.forEach { category ->
                DefaultOption(category.name, selected = category.isDefault, tag = "default-${category.id.value}") {
                    onDefaultChange(category.id)
                }
            }
        }
    }

    if (showAddDialog) {
        AddCategoryDialog(
            onConfirm = { name ->
                onAdd(name)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    pendingDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"${category.name}\"?") },
            text = { Text("Books in this category stay in your library.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(category)
                        pendingDelete = null
                    },
                    modifier = Modifier.testTag("confirm-delete"),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun CategoryDragHandle(
    category: Category,
    categories: List<Category>,
    bounds: Map<CategoryId, Rect>,
    onMove: (Int, Int) -> Unit,
) {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    androidx.compose.foundation.layout.Box(
        Modifier.padding(horizontal = 4.dp).testTag("drag-category-${category.id.value}")
            .onGloballyPositioned { coordinates = it }
            .pointerInput(category.id, bounds, categories, onMove) {
                var lastTarget = categories.indexOfFirst { it.id == category.id }
                detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()
                        val point = coordinates?.localToRoot(change.position) ?: return@detectDragGestures
                        val target = categories.indexOfFirst { item ->
                            val rect = bounds[item.id] ?: return@indexOfFirst false
                            point.y >= rect.top && point.y <= rect.bottom
                        }
                        val from = categories.indexOfFirst { it.id == category.id }
                        if (target >= 0 && target != from && target != lastTarget) {
                            lastTarget = target
                            onMove(from, target)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.DragHandle, contentDescription = "Reorder ${category.name}")
    }
}

@Composable
private fun DefaultOption(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(tag).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
