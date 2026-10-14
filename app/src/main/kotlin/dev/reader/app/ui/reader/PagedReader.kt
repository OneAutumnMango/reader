package dev.reader.app.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.reader.core.reader.ReaderSettings
import dev.reader.core.reader.ReadingMode
import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.core.richtext.Span
import dev.reader.core.theme.ReaderTheme

private val READER_TOP_SPACING = 48.dp
private val READER_BOTTOM_SPACING = 32.dp
private val READER_HORIZONTAL_SPACING = 20.dp

@Composable
fun PagedReader(
    document: RichDocument,
    theme: ReaderTheme,
    settings: ReaderSettings,
    modifier: Modifier = Modifier,
    initialPositionFraction: Float = 0f,
    onPageChanged: (index: Int, pageCount: Int) -> Unit = { _, _ -> },
    highlight: Regex? = null,
    jump: BlockJump? = null,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val textWidthPx = with(density) { (maxWidth - READER_HORIZONTAL_SPACING * 2).roundToPx() }.coerceAtLeast(1)
        val pageHeightPx = with(density) { (maxHeight - READER_TOP_SPACING - READER_BOTTOM_SPACING).toPx() }
        val spacingPx = with(density) { theme.paragraphSpacingSp.dp.toPx() }
        val useBookStyling = settings.useBookStyling

        val pages = remember(document, theme, useBookStyling, textWidthPx, pageHeightPx) {
            fun layout(block: Block) = textMeasurer.measure(
                text = block.annotated(theme, useBookStyling),
                style = block.styleOf(theme, useBookStyling),
                constraints = Constraints(maxWidth = textWidthPx),
            )
            // Indexed so a split paragraph's halves still point back at their source block.
            paginate(
                items = document.blocks.withIndex().toList(),
                pageHeight = pageHeightPx,
                spacing = spacingPx,
                measure = { if (it.value is Block.Image) pageHeightPx else layout(it.value).size.height.toFloat() },
                split = { item, available ->
                    splitBlock(item.value, available, theme, ::layout)
                        ?.let { (head, tail) -> IndexedValue(item.index, head) to IndexedValue(item.index, tail) }
                },
                keepWithNext = { it.value is Block.Heading },
            )
        }
        val initialPage = (initialPositionFraction * pages.size)
            .toInt()
            .coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { pages.size })

        LaunchedEffect(pagerState.currentPage, pages.size) { onPageChanged(pagerState.currentPage, pages.size) }
        LaunchedEffect(jump) {
            val target = jump?.let { j -> pages.indexOfFirst { page -> page.any { it.index == j.block } } } ?: -1
            if (target >= 0) pagerState.scrollToPage(target)
        }

        val pageContent: @Composable (Int) -> Unit = { index ->
            Column(
                modifier = Modifier.fillMaxSize().padding(
                    start = READER_HORIZONTAL_SPACING,
                    end = READER_HORIZONTAL_SPACING,
                    top = READER_TOP_SPACING,
                    bottom = READER_BOTTOM_SPACING,
                ),
                verticalArrangement = Arrangement.spacedBy(theme.paragraphSpacingSp.dp),
            ) {
                pages.getOrNull(index)?.forEach { PagedReaderBlock(it.value, theme, useBookStyling, highlight) }
            }
        }

        when (settings.readingMode) {
            ReadingMode.PAGED_VERTICAL -> VerticalPager(state = pagerState) { pageContent(it) }
            ReadingMode.PAGED_HORIZONTAL_RTL -> HorizontalPager(state = pagerState, reverseLayout = true) {
                pageContent(it)
            }
            else -> HorizontalPager(state = pagerState) { pageContent(it) }
        }
    }
}

/** The exact text that gets drawn for [this] block, so measuring and rendering always agree. */
private fun Block.annotated(theme: ReaderTheme, useBookStyling: Boolean): AnnotatedString = when (this) {
    is Block.Paragraph -> spans.toAnnotatedString(theme.wordSpacingSp, useBookStyling)
    else -> spansOrEmpty().toAnnotatedString(useBookStyling = useBookStyling)
}

/**
 * Cuts a paragraph or list item after the last line that fits in [available] px, so the rest
 * continues on the next page. Headings are never split; null when not even one line fits.
 */
private fun splitBlock(
    block: Block,
    available: Float,
    theme: ReaderTheme,
    layout: (Block) -> TextLayoutResult,
): Pair<Block, Block>? {
    if (block !is Block.Paragraph && block !is Block.ListItem) return null
    val result = layout(block)
    var line = result.getLineForVerticalPosition(available)
    if (result.getLineBottom(line) > available) line--
    if (line < 0) return null
    val end = result.getLineEnd(line)
    if (end <= 0 || end >= result.layoutInput.text.length) return null
    val separator = if (block is Block.Paragraph && theme.wordSpacingSp > 0f) 1 else 0
    val (head, tail) = splitSpans(block.spansOrEmpty(), end, separator)
    return if (block is Block.Paragraph) {
        block.copy(spans = head) to block.copy(spans = tail)
    } else {
        (block as Block.ListItem).copy(spans = head) to block.copy(spans = tail)
    }
}

/** Splits [spans] at a character [offset] of their joined text ([separator] chars sit between spans). */
internal fun splitSpans(spans: List<Span>, offset: Int, separator: Int = 0): Pair<List<Span>, List<Span>> {
    val head = mutableListOf<Span>()
    val tail = mutableListOf<Span>()
    var pos = 0
    spans.forEachIndexed { i, span ->
        val start = pos
        val end = pos + span.text.length
        when {
            end <= offset -> head += span
            start >= offset -> tail += span
            else -> {
                head += span.copy(text = span.text.substring(0, offset - start))
                tail += span.copy(text = span.text.substring(offset - start))
            }
        }
        pos = end + if (i < spans.lastIndex) separator else 0
    }
    tail.firstOrNull()?.let { tail[0] = it.copy(text = it.text.trimStart()) }
    return head to tail
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
private fun PagedReaderBlock(block: Block, theme: ReaderTheme, useBookStyling: Boolean, highlight: Regex?) {
    if (block is Block.Image) {
        ReaderImage(block, Modifier.fillMaxSize())
    } else {
        Text(
            text = block.annotated(theme, useBookStyling).highlighted(highlight),
            style = block.styleOf(theme, useBookStyling),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
