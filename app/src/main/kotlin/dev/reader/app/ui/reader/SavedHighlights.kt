package dev.reader.app.ui.reader

import dev.reader.core.model.Bookmark
import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.core.richtext.Span
import kotlin.math.abs

internal fun RichDocument.withSavedHighlights(bookmarks: List<Bookmark>): RichDocument {
    val spansByBlock = blocks.map { block ->
        when (block) {
            is Block.Paragraph -> block.spans
            is Block.Heading -> block.spans
            is Block.ListItem -> block.spans
            is Block.Image -> emptyList()
        }
    }
    val texts = spansByBlock.map { spans -> spans.joinToString("") { it.text } }
    val starts = texts.runningFold(0) { offset, text -> offset + text.length + 1 }
    val text = texts.joinToString("\n")
    val ranges = bookmarks.mapNotNull { bookmark ->
        val quote = bookmark.note.takeIf { it.startsWith("[Highlight]\n") }
            ?.removePrefix("[Highlight]\n")?.trim().orEmpty()
        if (quote.isEmpty()) return@mapNotNull null
        val pattern = quote.split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) }.toRegex()
        pattern.findAll(text).minByOrNull { match ->
            val blockIndex = starts.indexOfLast { it <= match.range.first }.coerceAtMost(blocks.lastIndex)
            abs(blockIndex.toFloat() / blocks.size.coerceAtLeast(1) - bookmark.position)
        }?.range
    }
    if (ranges.isEmpty()) return this
    return copy(
        blocks = blocks.mapIndexed { index, block ->
            var offset = starts[index]
            val spans = spansByBlock[index].flatMap { span ->
                val highlighted = span.withHighlightRanges(offset, ranges)
                offset += span.text.length
                highlighted
            }
            when (block) {
                is Block.Paragraph -> block.copy(spans = spans)
                is Block.Heading -> block.copy(spans = spans)
                is Block.ListItem -> block.copy(spans = spans)
                is Block.Image -> block
            }
        },
    )
}

private fun Span.withHighlightRanges(offset: Int, ranges: List<IntRange>): List<Span> {
    val highlightBoundaries = ranges.flatMap { range ->
        listOf((range.first - offset).coerceIn(0, text.length), (range.last + 1 - offset).coerceIn(0, text.length))
    }
    val boundaries = (listOf(0, text.length) + highlightBoundaries).distinct().sorted()
    return boundaries.zipWithNext { start, end ->
        copy(
            text = text.substring(start, end),
            highlighted = highlighted || ranges.any { offset + start in it },
        )
    }
}
