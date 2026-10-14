package dev.reader.app.ui.library

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.core.model.Book
import dev.reader.core.model.BookId
import dev.reader.core.model.Category
import dev.reader.core.model.CategoryId
import dev.reader.core.theme.LibraryLayout

private const val SWIPE_CATEGORY_THRESHOLD_PX = 100f

@Composable
fun LibraryScreen(
    books: List<Book>,
    categories: List<Category>,
    selectedCategory: CategoryId?,
    progressFor: (BookId) -> Float?,
    onCategorySelected: (CategoryId?) -> Unit,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit,
    onSearchClick: () -> Unit,
    onAddCategory: (String) -> Unit,
    layout: LibraryLayout = LibraryLayout.MEDIUM,
    finishedFor: (BookId) -> Boolean = { false },
    gridState: LazyGridState = rememberLazyGridState(),
    modifier: Modifier = Modifier,
) {
    val visibleBooks = if (selectedCategory == null) books else books.filter { selectedCategory in it.categoryIds }
    val orderedCategoryIds: List<CategoryId?> = listOf(null) + categories.map { it.id }
    val currentIndex = orderedCategoryIds.indexOf(selectedCategory).coerceAtLeast(0)

    Column(modifier = modifier) {
        SearchBarButton(onClick = onSearchClick, modifier = Modifier.padding(bottom = 8.dp))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(orderedCategoryIds, currentIndex) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDragEnd = {
                            when {
                                totalDrag <= -SWIPE_CATEGORY_THRESHOLD_PX &&
                                    currentIndex < orderedCategoryIds.lastIndex ->
                                    onCategorySelected(orderedCategoryIds[currentIndex + 1])
                                totalDrag >= SWIPE_CATEGORY_THRESHOLD_PX && currentIndex > 0 ->
                                    onCategorySelected(orderedCategoryIds[currentIndex - 1])
                            }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount
                    }
                },
        ) {
            CategoryTabs(categories, selectedCategory, onCategorySelected, onAddCategory)
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = layout.gridColumnMinWidthDp.dp),
                contentPadding = PaddingValues(8.dp),
                modifier = Modifier.testTag("library-grid"),
            ) {
                items(visibleBooks, key = { it.id.value }) { book ->
                    BookCard(
                        book = book,
                        progress = progressFor(book.id),
                        finished = finishedFor(book.id),
                        onClick = { onBookClick(book) },
                        onLongClick = { onBookLongClick(book) },
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
        }
    }
}
