package dev.reader.app.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.core.reader.ReadingMode

private val PAGE_GAP = 8.dp

/**
 * Page viewer for fixed-layout content (comic archives and PDFs rendered as images) that follows
 * the user's reading mode: a vertical strip for continuous scroll, otherwise a vertical,
 * left-to-right or right-to-left pager. [pageContent] receives `continuous = true` when laid out
 * in the strip, where it should fill the width at its own aspect ratio instead of the screen.
 */
@Composable
fun ImagePager(
    pageCount: Int,
    readingMode: ReadingMode,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    zoomEnabled: Boolean = true,
    pageGap: Boolean = true,
    onPageChanged: (index: Int) -> Unit = {},
    pageContent: @Composable (index: Int, continuous: Boolean) -> Unit,
) {
    val startPage = initialPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    val gap = if (pageGap) PAGE_GAP else 0.dp

    if (readingMode == ReadingMode.CONTINUOUS_SCROLL) {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = startPage)
        ReportReadingPosition(listState, pageCount) { index, _ -> onPageChanged(index) }
        Zoomable(zoomEnabled, modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("image-strip"),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                items(pageCount) { index -> pageContent(index, true) }
            }
        }
        return
    }

    val pagerState = rememberPagerState(initialPage = startPage, pageCount = { pageCount })
    LaunchedEffect(pagerState.currentPage) { onPageChanged(pagerState.currentPage) }
    val page: @Composable (Int) -> Unit = { index ->
        Zoomable(zoomEnabled, Modifier.fillMaxSize()) { pageContent(index, false) }
    }

    when (readingMode) {
        ReadingMode.PAGED_VERTICAL -> VerticalPager(
            state = pagerState,
            pageSpacing = gap,
            modifier = modifier.fillMaxSize(),
        ) { page(it) }
        ReadingMode.PAGED_HORIZONTAL_RTL -> HorizontalPager(
            state = pagerState,
            reverseLayout = true,
            pageSpacing = gap,
            modifier = modifier.fillMaxSize(),
        ) { page(it) }
        else -> HorizontalPager(state = pagerState, pageSpacing = gap, modifier = modifier.fillMaxSize()) { page(it) }
    }
}
