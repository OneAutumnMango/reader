package dev.reader.app.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import dev.reader.core.reader.ReaderSettings
import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.core.theme.ReaderTheme

private val READER_TOP_SPACING = 48.dp
private val READER_BOTTOM_SPACING = 24.dp
private val READER_IMAGE_HEIGHT = 320.dp
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val MAX_FRAME_SECONDS = 0.1f

@Composable
fun ContinuousReader(
    document: RichDocument,
    theme: ReaderTheme,
    settings: ReaderSettings,
    modifier: Modifier = Modifier,
    initialPositionFraction: Float = 0f,
    onPositionChanged: (index: Int, total: Int) -> Unit = { _, _ -> },
    highlight: Regex? = null,
    jump: BlockJump? = null,
    onBookmarkTap: ((String) -> Unit)? = null,
    onBlockLayout: ((Int, LayoutCoordinates, TextLayoutResult) -> Unit)? = null,
    onBlockDisposed: ((Int) -> Unit)? = null,
    onSelectionContainerLayout: ((LayoutCoordinates) -> Unit)? = null,
    autoScrollSpeedDp: Float = 0f,
    autoScrolling: Boolean = false,
) {
    val initialIndex = (initialPositionFraction * document.blocks.size)
        .toInt()
        .coerceIn(0, (document.blocks.size - 1).coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    ReportReadingPosition(listState, document.blocks.size, onPositionChanged)
    LaunchedEffect(jump) {
        jump?.let { listState.scrollToItem(it.block.coerceIn(0, (document.blocks.size - 1).coerceAtLeast(0))) }
    }

    val density = LocalDensity.current
    val currentSpeedDp by rememberUpdatedState(autoScrollSpeedDp)
    LaunchedEffect(autoScrolling) {
        if (!autoScrolling) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val seconds = ((now - last) / NANOS_PER_SECOND).coerceAtMost(MAX_FRAME_SECONDS)
            last = now
            // Raw delta, not scrollBy: scrollBy takes the scroll mutex, and a user drag holding it
            // at higher priority would cancel (and so silently end) this loop. This just adds to it.
            // At the chapter's end this consumes nothing and idles; the next chapter is the user's to open.
            listState.dispatchRawDelta(with(density) { currentSpeedDp.dp.toPx() } * seconds)
        }
    }

    val content: @Composable () -> Unit = {
        LazyColumn(
            state = listState,
            modifier = modifier.readerScrollIndicator(Color(theme.textColor)) {
                val layout = listState.layoutInfo
                val viewport = (layout.viewportEndOffset - layout.viewportStartOffset).coerceAtLeast(1)
                val items = layout.visibleItemsInfo
                val averageHeight = items.map { it.size + layout.mainAxisItemSpacing }.average().toFloat()
                val estimatedHeight = averageHeight * layout.totalItemsCount
                val visible = if (estimatedHeight > 0f) (viewport / estimatedHeight).coerceAtMost(1f) else 1f
                val estimatedOffset = listState.firstVisibleItemIndex * averageHeight +
                    listState.firstVisibleItemScrollOffset
                val position = if (!listState.canScrollForward) {
                    1f
                } else {
                    estimatedOffset / (estimatedHeight - viewport).coerceAtLeast(1f)
                }
                position to visible
            },
            contentPadding = PaddingValues(top = READER_TOP_SPACING, bottom = READER_BOTTOM_SPACING),
            verticalArrangement = Arrangement.spacedBy(theme.paragraphSpacingSp.dp),
        ) {
            itemsIndexed(document.blocks) { index, block ->
                // A recycled LazyColumn slot can get reused for a different index as the user
                // scrolls; drop this index's anchor when that happens so a stale entry can't
                // later alias onto whatever block now lives at that slot's current position.
                DisposableEffect(index) { onDispose { onBlockDisposed?.invoke(index) } }
                ReaderBlock(block, theme, settings.useBookStyling, highlight, onBookmarkTap) { coordinates, layout ->
                    onBlockLayout?.invoke(index, coordinates, layout)
                }
            }
        }
    }

    if (settings.selectableText) {
        // The selection toolbar's reported rect is relative to SelectionContainer's own layout
        // node, not any ancestor Box, so this is the exact coordinate frame a resolved selection
        // anchor needs to be converted through.
        SelectionContainer(modifier = Modifier.onGloballyPositioned { onSelectionContainerLayout?.invoke(it) }) {
            content()
        }
    } else {
        DisableSelection { content() }
    }
}

@Composable
private fun ReaderBlock(
    block: Block,
    theme: ReaderTheme,
    useBookStyling: Boolean,
    highlight: Regex?,
    onBookmarkTap: ((String) -> Unit)?,
    onLayout: ((LayoutCoordinates, TextLayoutResult) -> Unit)? = null,
) {
    when (block) {
        is Block.Paragraph -> BookmarkAwareText(
            text = block.spans.toAnnotatedString(theme.wordSpacingSp, useBookStyling).highlighted(highlight),
            style = theme.bodyTextStyle(block.textAlign, useBookStyling),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            onBookmarkTap = onBookmarkTap,
            onLayout = onLayout,
        )
        is Block.Heading -> BookmarkAwareText(
            text = block.spans.toAnnotatedString(theme.wordSpacingSp, useBookStyling).highlighted(highlight),
            style = theme.headingTextStyle(block.level, block.textAlign, useBookStyling),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            onBookmarkTap = onBookmarkTap,
            onLayout = onLayout,
        )
        is Block.ListItem -> BookmarkAwareText(
            text = block.spans.toAnnotatedString(theme.wordSpacingSp, useBookStyling).highlighted(highlight),
            style = theme.bodyTextStyle(useBookStyling = useBookStyling),
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp),
            onBookmarkTap = onBookmarkTap,
            onLayout = onLayout,
        )
        is Block.Image -> if (block.width > 0 && block.height > 0) {
            // Natural size, centred, shrunk to the column width; never upscaled.
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                ReaderImage(
                    block,
                    Modifier.widthIn(max = with(LocalDensity.current) { block.width.toDp() })
                        .fillMaxWidth()
                        .aspectRatio(block.width.toFloat() / block.height),
                )
            }
        } else {
            ReaderImage(block, Modifier.fillMaxWidth().height(READER_IMAGE_HEIGHT).padding(horizontal = 16.dp))
        }
    }
}
