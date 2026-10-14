package dev.reader.app.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
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
            modifier = modifier,
            contentPadding = PaddingValues(top = READER_TOP_SPACING, bottom = READER_BOTTOM_SPACING),
            verticalArrangement = Arrangement.spacedBy(theme.paragraphSpacingSp.dp),
        ) {
            items(document.blocks) { block -> ReaderBlock(block, theme, settings.useBookStyling, highlight) }
        }
    }

    if (settings.selectableText) {
        SelectionContainer { content() }
    } else {
        DisableSelection { content() }
    }
}

@Composable
private fun ReaderBlock(block: Block, theme: ReaderTheme, useBookStyling: Boolean, highlight: Regex?) {
    when (block) {
        is Block.Paragraph -> Text(
            text = block.spans.toAnnotatedString(theme.wordSpacingSp, useBookStyling).highlighted(highlight),
            style = theme.bodyTextStyle(block.textAlign, useBookStyling),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        is Block.Heading -> Text(
            text = block.spans.toAnnotatedString(useBookStyling = useBookStyling).highlighted(highlight),
            style = theme.headingTextStyle(block.level, block.textAlign, useBookStyling),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        is Block.ListItem -> Text(
            text = block.spans.toAnnotatedString(useBookStyling = useBookStyling).highlighted(highlight),
            style = theme.bodyTextStyle(useBookStyling = useBookStyling),
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp),
        )
        is Block.Image -> ReaderImage(
            block,
            Modifier.fillMaxWidth().height(READER_IMAGE_HEIGHT).padding(horizontal = 16.dp),
        )
    }
}
