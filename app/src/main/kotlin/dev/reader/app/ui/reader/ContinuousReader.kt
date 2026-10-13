package dev.reader.app.ui.reader

import androidx.compose.foundation.layout.Arrangement
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

@Composable
fun ContinuousReader(
    document: RichDocument,
    theme: ReaderTheme,
    settings: ReaderSettings,
    modifier: Modifier = Modifier,
    onPositionChanged: (index: Int, total: Int) -> Unit = { _, _ -> },
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState.firstVisibleItemIndex, document.blocks.size) {
        onPositionChanged(listState.firstVisibleItemIndex, document.blocks.size)
    }

    val content: @Composable () -> Unit = {
        LazyColumn(
            state = listState,
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(theme.paragraphSpacingSp.dp),
        ) {
            items(document.blocks) { block -> ReaderBlock(block, theme, settings.useBookStyling) }
        }
    }

    if (settings.selectableText) {
        SelectionContainer { content() }
    } else {
        DisableSelection { content() }
    }
}

@Composable
private fun ReaderBlock(block: Block, theme: ReaderTheme, useBookStyling: Boolean) {
    when (block) {
        is Block.Paragraph -> Text(
            text = block.spans.toAnnotatedString(theme.wordSpacingSp, useBookStyling),
            style = theme.bodyTextStyle(block.textAlign, useBookStyling),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        is Block.Heading -> Text(
            text = block.spans.toAnnotatedString(useBookStyling = useBookStyling),
            style = theme.headingTextStyle(block.textAlign, useBookStyling),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        is Block.ListItem -> Text(
            text = block.spans.toAnnotatedString(useBookStyling = useBookStyling),
            style = theme.bodyTextStyle(useBookStyling = useBookStyling),
            modifier = Modifier.padding(start = 24.dp, end = 16.dp),
        )
        is Block.Image -> Unit // Rendered by the caller once cover/image caching exists (M5).
    }
}
