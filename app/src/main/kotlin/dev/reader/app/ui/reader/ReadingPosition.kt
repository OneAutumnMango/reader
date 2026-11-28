package dev.reader.app.ui.reader

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow

/** The first visible item, or the last one once the list can't scroll further, so reaching the end reads as 100%. */
internal fun LazyListState.readingIndex(): Int {
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull() ?: return firstVisibleItemIndex
    val atEnd = last.index == info.totalItemsCount - 1 && last.offset + last.size <= info.viewportEndOffset
    return if (atEnd) last.index else firstVisibleItemIndex
}

private data class ReadingReport(val index: Int, val total: Int, val fits: Boolean)

/**
 * Reports [state]'s [readingIndex] out of [total] whenever it changes, once the list is laid out. A
 * list that can't scroll is reported as the only page of one, but not before [heightsFinal].
 * [userScrolled] latches on any scroll of [state]; the caller also sets it for scrolls that don't
 * mark the list as scrolling. It is passed on as null for a list that fits.
 */
@Composable
internal fun ReportReadingPosition(
    state: LazyListState,
    total: Int,
    userScrolled: MutableState<Boolean>,
    heightsFinal: () -> Boolean = { true },
    onChanged: (index: Int, total: Int, userScrolled: Boolean?) -> Unit,
) {
    val current by rememberUpdatedState(onChanged)
    val currentHeightsFinal by rememberUpdatedState(heightsFinal)
    LaunchedEffect(state, total, userScrolled) {
        var last: ReadingReport? = null
        snapshotFlow {
            val report = when {
                state.layoutInfo.totalItemsCount == 0 -> null
                state.canScrollForward || state.canScrollBackward -> ReadingReport(state.readingIndex(), total, false)
                currentHeightsFinal() -> ReadingReport(0, 1, true)
                else -> null
            }
            report to state.isScrollInProgress
        }.collect { (report, scrolling) ->
            if (scrolling) userScrolled.value = true
            if (report != null && report != last) {
                last = report
                current(report.index, report.total, if (report.fits) null else userScrolled.value)
            }
        }
    }
}
