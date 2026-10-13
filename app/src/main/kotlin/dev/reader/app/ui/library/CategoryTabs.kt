package dev.reader.app.ui.library

import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.reader.core.model.Category
import dev.reader.core.model.CategoryId

/** Swipeable category tabs. A null selection means "All". */
@Composable
fun CategoryTabs(
    categories: List<Category>,
    selected: CategoryId?,
    onSelect: (CategoryId?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = categories.indexOfFirst { it.id == selected }.let { if (it < 0) 0 else it + 1 }

    PrimaryScrollableTabRow(selectedTabIndex = selectedIndex, modifier = modifier) {
        Tab(selected = selected == null, onClick = { onSelect(null) }, text = { Text("All") })
        categories.forEach { category ->
            Tab(
                selected = selected == category.id,
                onClick = { onSelect(category.id) },
                text = { Text(category.name) },
            )
        }
    }
}
