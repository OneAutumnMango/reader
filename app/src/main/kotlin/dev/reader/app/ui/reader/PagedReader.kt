package dev.reader.app.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.reader.core.reader.ReaderSettings
import dev.reader.core.reader.ReadingMode
import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.core.theme.ReaderTheme

private val READER_TOP_SPACING = 48.dp

@Composable
fun PagedReader(
    document: RichDocument,
    theme: ReaderTheme,
    settings: ReaderSettings,
    modifier: Modifier = Modifier,
    initialPositionFraction: Float = 0f,
    onPageChanged: (index: Int, pageCount: Int) -> Unit = { _, _ -> },
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val maxHeightPx = with(density) { maxHeight.roundToPx() }

        val pages = remember(document, theme, settings.useBookStyling, maxWidthPx, maxHeightPx) {
            val heights = document.blocks.map { block ->
                if (block is Block.Image) {
                    maxHeightPx.toFloat()
                } else {
                    textMeasurer.measure(
                        text = block.spansOrEmpty().toAnnotatedString(useBookStyling = settings.useBookStyling),
                        style = block.styleOf(theme, settings.useBookStyling),
                        constraints = Constraints(maxWidth = maxWidthPx),
                    ).size.height.toFloat()
                }
            }
            buildPages(heights, maxHeightPx.toFloat()).map { range -> document.blocks.slice(range) }
        }
        val initialPage = (initialPositionFraction * pages.size)
            .toInt()
            .coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pages.size })

        LaunchedEffect(pagerState.currentPage) { onPageChanged(pagerState.currentPage, pagerState.pageCount) }

        val pageContent: @Composable (Int) -> Unit = { index ->
            Column(
                modifier = Modifier.fillMaxSize().padding(top = READER_TOP_SPACING),
                verticalArrangement = Arrangement.spacedBy(theme.paragraphSpacingSp.dp),
            ) {
                pages[index].forEach { block -> PagedReaderBlock(block, theme, settings.useBookStyling) }
            }
        }

        when (settings.readingMode) {
            ReadingMode.PAGED_VERTICAL -> VerticalPager(state = pagerState) { pageContent(it) }
            ReadingMode.PAGED_HORIZONTAL_RTL -> CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
            ) {
                HorizontalPager(state = pagerState) { pageContent(it) }
            }
            else -> HorizontalPager(state = pagerState) { pageContent(it) }
        }
    }
}

private fun Block.spansOrEmpty() = when (this) {
    is Block.Paragraph -> spans
    is Block.Heading -> spans
    is Block.ListItem -> spans
    is Block.Image -> emptyList()
}

private fun Block.styleOf(theme: ReaderTheme, useBookStyling: Boolean) = when (this) {
    is Block.Paragraph -> theme.bodyTextStyle(textAlign, useBookStyling)
    is Block.Heading -> theme.headingTextStyle(level, textAlign, useBookStyling)
    is Block.ListItem -> theme.bodyTextStyle(useBookStyling = useBookStyling)
    is Block.Image -> theme.bodyTextStyle()
}

@Composable
private fun PagedReaderBlock(block: Block, theme: ReaderTheme, useBookStyling: Boolean) {
    when (block) {
        is Block.Paragraph -> Text(
            text = block.spans.toAnnotatedString(theme.wordSpacingSp, useBookStyling),
            style = theme.bodyTextStyle(block.textAlign, useBookStyling),
        )
        is Block.Heading -> Text(
            text = block.spans.toAnnotatedString(useBookStyling = useBookStyling),
            style = theme.headingTextStyle(block.level, block.textAlign, useBookStyling),
        )
        is Block.ListItem -> Text(
            text = block.spans.toAnnotatedString(useBookStyling = useBookStyling),
            style = theme.bodyTextStyle(useBookStyling = useBookStyling),
        )
        is Block.Image -> Unit
    }
}
