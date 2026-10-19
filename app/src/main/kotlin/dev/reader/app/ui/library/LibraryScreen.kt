package dev.reader.app.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.core.model.Book
import dev.reader.core.model.BookId
import dev.reader.core.model.Category
import dev.reader.core.model.CategoryId
import dev.reader.core.theme.LibraryLayout

private const val ALL_PAGE_KEY = "__all__"

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
    showAllCategory: Boolean = true,
    gridStateFor: (CategoryId?) -> LazyGridState? = { null },
    modifier: Modifier = Modifier,
) {
    // "All" can only be hidden when there's another category to show instead.
    val showAll = showAllCategory || categories.isEmpty()
    val pages: List<CategoryId?> = (if (showAll) listOf(null) else emptyList()) + categories.map { it.id }
    val currentIndex = pages.indexOf(selectedCategory).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = currentIndex) { pages.size }

    LaunchedEffect(currentIndex) {
        if (pagerState.currentPage != currentIndex) pagerState.animateScrollToPage(currentIndex)
    }
    LaunchedEffect(pagerState.settledPage, pages) {
        val settled = pages.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
        if (settled != selectedCategory) onCategorySelected(settled)
    }

    Column(modifier = modifier) {
        SearchBarButton(onClick = onSearchClick, modifier = Modifier.padding(bottom = 8.dp))
        CategoryTabs(categories, pages.getOrNull(pagerState.currentPage), onCategorySelected, onAddCategory, showAll)
        HorizontalPager(
            state = pagerState,
            key = { pages.getOrNull(it)?.value ?: ALL_PAGE_KEY },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val category = pages.getOrNull(page)
            val pageBooks = if (category == null) books else books.filter { category in it.categoryIds }
            LazyVerticalGrid(
                state = gridStateFor(category) ?: rememberLazyGridState(),
                columns = GridCells.Adaptive(minSize = layout.gridColumnMinWidthDp.dp),
                contentPadding = PaddingValues(4.dp),
                modifier = Modifier.fillMaxSize().testTag("library-grid"),
            ) {
                items(pageBooks, key = { it.id.value }) { book ->
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
