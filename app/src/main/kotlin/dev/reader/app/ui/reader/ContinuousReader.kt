package dev.reader.app.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import dev.reader.core.reader.ReaderSettings
import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.core.theme.ReaderTheme

private val READER_TOP_SPACING = 48.dp
private val READER_BOTTOM_SPACING = 24.dp
private val READER_IMAGE_HEIGHT = 320.dp

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
) {
    val initialIndex = (initialPositionFraction * document.blocks.size)
        .toInt()
        .coerceIn(0, (document.blocks.size - 1).coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    ReportReadingPosition(listState, document.blocks.size, onPositionChanged)
    LaunchedEffect(jump) {
        jump?.let { listState.scrollToItem(it.block.coerceIn(0, (document.blocks.size - 1).coerceAtLeast(0))) }
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
                ReaderBlock(block, theme, settings.useBookStyling, highlight, onBookmarkTap) { coordinates, layout ->
                    onBlockLayout?.invoke(index, coordinates, layout)
                }
            }
        }
    }

    if (settings.selectableText) {
        SelectionContainer { content() }
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
        is Block.Image -> ReaderImage(
            block,
            Modifier.fillMaxWidth().height(READER_IMAGE_HEIGHT).padding(horizontal = 16.dp),
        )
    }
}
