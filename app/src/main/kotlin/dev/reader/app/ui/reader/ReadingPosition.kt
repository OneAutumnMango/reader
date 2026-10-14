package dev.reader.app.ui.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** The first visible item, or the last one once the list can't scroll further, so reaching the end reads as 100%. */
internal fun LazyListState.readingIndex(): Int {
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull() ?: return firstVisibleItemIndex
    val atEnd = last.index == info.totalItemsCount - 1 && last.offset + last.size <= info.viewportEndOffset
    return if (atEnd) last.index else firstVisibleItemIndex
}

/** Reports [state]'s [readingIndex] out of [total] whenever it changes. */
@Composable
internal fun ReportReadingPosition(state: LazyListState, total: Int, onChanged: (index: Int, total: Int) -> Unit) {
    val current by rememberUpdatedState(onChanged)
    LaunchedEffect(state, total) {
        snapshotFlow { state.readingIndex() }.distinctUntilChanged().collect { current(it, total) }
    }
}
