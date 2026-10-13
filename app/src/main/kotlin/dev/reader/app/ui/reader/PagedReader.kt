package dev.reader.app.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.reader.core.reader.ReaderSettings
import dev.reader.core.reader.ReadingMode
import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.core.theme.ReaderTheme

const val DEFAULT_BLOCKS_PER_PAGE = 6

/** Groups blocks into fixed-size pages. An approximation of true, measured pagination. */
fun RichDocument.paginate(blocksPerPage: Int = DEFAULT_BLOCKS_PER_PAGE): List<List<Block>> =
    if (blocks.isEmpty()) listOf(emptyList()) else blocks.chunked(blocksPerPage)

@Composable
fun PagedReader(
    document: RichDocument,
    theme: ReaderTheme,
    settings: ReaderSettings,
    modifier: Modifier = Modifier,
    onPageChanged: (index: Int, pageCount: Int) -> Unit = { _, _ -> },
) {
    val pages = document.paginate()
    val pagerState = rememberPagerState(pageCount = { pages.size })

    LaunchedEffect(pagerState.currentPage) { onPageChanged(pagerState.currentPage, pagerState.pageCount) }

    val pageContent: @Composable (Int) -> Unit = { index ->
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(theme.paragraphSpacingSp.dp),
        ) {
            pages[index].forEach { block -> PagedReaderBlock(block, theme) }
        }
    }

    when (settings.readingMode) {
        ReadingMode.PAGED_VERTICAL -> VerticalPager(state = pagerState, modifier = modifier) { pageContent(it) }
        ReadingMode.PAGED_HORIZONTAL_RTL -> CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl,
        ) {
            HorizontalPager(state = pagerState, modifier = modifier) { pageContent(it) }
        }
        else -> HorizontalPager(state = pagerState, modifier = modifier) { pageContent(it) }
    }
}

@Composable
private fun PagedReaderBlock(block: Block, theme: ReaderTheme) {
    when (block) {
        is Block.Paragraph -> Text(
            text = block.spans.toAnnotatedString(theme.wordSpacingSp),
            style = theme.bodyTextStyle(),
        )
        is Block.Heading -> Text(text = block.spans.toAnnotatedString(), style = theme.headingTextStyle())
        is Block.ListItem -> Text(text = block.spans.toAnnotatedString(), style = theme.bodyTextStyle())
        is Block.Image -> Unit
    }
}
