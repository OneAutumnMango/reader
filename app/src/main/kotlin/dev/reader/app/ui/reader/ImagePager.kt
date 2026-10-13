package dev.reader.app.ui.reader

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

/**
 * Swipeable page-by-page viewer for fixed-layout content: comic archives and PDFs rendered as
 * page images. [pageContent] draws one page (bitmap loading is the caller's concern, since the
 * source differs between a cbz/cbr entry and a rendered PDF page).
 */
@Composable
fun ImagePager(
    pageCount: Int,
    modifier: Modifier = Modifier,
    onPageChanged: (index: Int) -> Unit = {},
    pageContent: @Composable (index: Int) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { pageCount })
    LaunchedEffect(pagerState.currentPage) { onPageChanged(pagerState.currentPage) }

    HorizontalPager(state = pagerState, modifier = modifier.fillMaxSize()) { index ->
        pageContent(index)
    }
}
