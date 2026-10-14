package dev.reader.app.ui.categories

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import dev.reader.app.data.ReaderGraph
import dev.reader.core.model.Category
import dev.reader.core.model.CategoryId
import dev.reader.data.store.PreferencesFile
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun CategoriesRoute(graph: ReaderGraph, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val categories by graph.library.observeCategories().collectAsState(initial = emptyList())
    val preferences by graph.settings.preferences.collectAsState(initial = PreferencesFile())

    CategoriesScreen(
        categories = categories,
        showAllCategory = preferences.showAllCategory,
        onShowAllCategoryChange = { show ->
            scope.launch { graph.settings.update { it.copy(showAllCategory = show) } }
        },
        onAdd = { name ->
            scope.launch {
                graph.library.upsertCategory(
                    Category(
                        id = CategoryId(UUID.randomUUID().toString()),
                        name = name.trim(),
                        orderIndex = (categories.maxOfOrNull { it.orderIndex } ?: -1) + 1,
                    ),
                )
            }
        },
        onDelete = { category -> scope.launch { graph.library.deleteCategory(category) } },
        onMove = { from, to ->
            val reordered = categories.toMutableList().apply { add(to, removeAt(from)) }
            scope.launch { graph.library.reorderCategories(reordered) }
        },
        onDefaultChange = { id -> scope.launch { graph.library.setDefaultCategory(id) } },
        modifier = modifier,
    )
}
