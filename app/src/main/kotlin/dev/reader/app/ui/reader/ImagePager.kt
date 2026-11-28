package dev.reader.app.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.core.reader.ReadingMode

private val PAGE_GAP = 8.dp

/**
 * Page viewer for fixed-layout content (comic archives and PDFs rendered as images) that follows
 * the user's reading mode: a vertical strip for continuous scroll, otherwise a vertical,
 * left-to-right or right-to-left pager. [pageContent] receives `continuous = true` when laid out
 * in the strip, where it should fill the width at its own aspect ratio instead of the screen. It
 * must apply `finalHeight` once the page has that real height, not while a placeholder stands in.
 */
@Composable
fun ImagePager(
    pageCount: Int,
    readingMode: ReadingMode,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
    zoomEnabled: Boolean = true,
    doubleTapZoomEnabled: Boolean = true,
    doubleTapZoomScale: Float = 3f,
    pageGap: Boolean = true,
    onPageChanged: (index: Int, pageCount: Int, userScrolled: Boolean?) -> Unit = { _, _, _ -> },
    resetUserScrolledKey: Any? = null,
    pageContent: @Composable (index: Int, continuous: Boolean, finalHeight: Modifier) -> Unit,
) {
    val startPage = initialPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    val gap = if (pageGap) PAGE_GAP else 0.dp

    if (readingMode == ReadingMode.CONTINUOUS_SCROLL) {
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = startPage)
        val userScrolled = remember(resetUserScrolledKey) { mutableStateOf(false) }
        val sizedPages = remember { mutableStateMapOf<Int, Unit>() }
        ReportReadingPosition(
            listState,
            pageCount,
            userScrolled,
            // Placeholders can make a long chapter look like it fits the screen.
            heightsFinal = { listState.layoutInfo.visibleItemsInfo.all { it.index in sizedPages } },
            onChanged = onPageChanged,
        )
        Zoomable(zoomEnabled, modifier.fillMaxSize(), doubleTapZoomEnabled, doubleTapZoomScale) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("image-strip"),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                items(pageCount) { index ->
                    DisposableEffect(index) { onDispose { sizedPages.remove(index) } }
                    pageContent(index, true, Modifier.onGloballyPositioned { sizedPages[index] = Unit })
                }
            }
        }
        return
    }

    val pagerState = rememberPagerState(initialPage = startPage, pageCount = { pageCount })
    LaunchedEffect(pagerState.currentPage) { onPageChanged(pagerState.currentPage, pageCount, null) }
    val page: @Composable (Int) -> Unit = { index ->
        Zoomable(zoomEnabled, Modifier.fillMaxSize(), doubleTapZoomEnabled, doubleTapZoomScale) {
            pageContent(index, false, Modifier)
        }
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
