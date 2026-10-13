package dev.reader.app.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.reader.core.model.Book
import dev.reader.core.model.BookId
import dev.reader.core.model.Category
import dev.reader.core.model.CategoryId

private const val GRID_COLUMN_MIN_WIDTH_DP = 120

@Composable
fun LibraryScreen(
    books: List<Book>,
    categories: List<Category>,
    selectedCategory: CategoryId?,
    progressFor: (BookId) -> Float?,
    onCategorySelected: (CategoryId?) -> Unit,
    onBookClick: (Book) -> Unit,
    onSearchClick: () -> Unit,
    onAddCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleBooks = if (selectedCategory == null) books else books.filter { selectedCategory in it.categoryIds }

    Column(modifier = modifier) {
        SearchBarButton(onClick = onSearchClick, modifier = Modifier.padding(bottom = 8.dp))
        CategoryTabs(categories, selectedCategory, onCategorySelected, onAddCategory)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = GRID_COLUMN_MIN_WIDTH_DP.dp),
            contentPadding = PaddingValues(8.dp),
        ) {
            items(visibleBooks, key = { it.id.value }) { book ->
                BookCard(
                    book = book,
                    progress = progressFor(book.id),
                    onClick = { onBookClick(book) },
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
    }
}
