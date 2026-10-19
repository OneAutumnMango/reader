package dev.reader.app.ui.reader

import dev.reader.core.model.Bookmark
import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.core.richtext.Span
import kotlin.math.abs

private const val POSITION_TOLERANCE = 0.02f

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
    val rangesWithId = bookmarks.mapNotNull { bookmark ->
        val quote = when {
            bookmark.note.startsWith("[Highlight]\n") -> bookmark.note.removePrefix("[Highlight]\n").trim()
            bookmark.note.startsWith("[Note]\n") ->
                bookmark.note.removePrefix("[Note]\n").substringBefore("\n\n").trim()
            else -> null
        }
        if (quote.isNullOrEmpty()) return@mapNotNull null
        val pattern = quote.split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) }.toRegex()
        val matches = pattern.findAll(text).toList()
        fun fractionOf(match: MatchResult): Float {
            val blockIndex = starts.indexOfLast { it <= match.range.first }.coerceAtMost(blocks.lastIndex)
            return blockIndex.toFloat() / blocks.size.coerceAtLeast(1)
        }
        // bookmark.position is recorded from the top of the viewport at selection time, so the
        // actual selected text is at or below it on screen — prefer the nearest match at or after
        // that position over one that merely looks closer by raw distance (which systematically
        // favored earlier occurrences the viewport had already scrolled past).
        val range = matches.filter { fractionOf(it) >= bookmark.position - POSITION_TOLERANCE }
            .minByOrNull { fractionOf(it) }
            ?.range
            ?: matches.minByOrNull { abs(fractionOf(it) - bookmark.position) }?.range
            ?: return@mapNotNull null
        range to bookmark.id.value
    }
    if (rangesWithId.isEmpty()) return this
    val ranges = rangesWithId.map { it.first }
    val rangeToId = rangesWithId.toMap()
    return copy(
        blocks = blocks.mapIndexed { index, block ->
            var offset = starts[index]
            val spans = spansByBlock[index].flatMap { span ->
                val highlighted = span.withHighlightRanges(offset, ranges, rangeToId)
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

private fun Span.withHighlightRanges(offset: Int, ranges: List<IntRange>, rangeToId: Map<IntRange, String>): List<Span> {
    val highlightBoundaries = ranges.flatMap { range ->
        listOf((range.first - offset).coerceIn(0, text.length), (range.last + 1 - offset).coerceIn(0, text.length))
    }
    val boundaries = (listOf(0, text.length) + highlightBoundaries).distinct().sorted()
    return boundaries.zipWithNext { start, end ->
        val activeRange = ranges.firstOrNull { offset + start in it }
        copy(
            text = text.substring(start, end),
            highlighted = highlighted || activeRange != null,
            bookmarkId = bookmarkId ?: activeRange?.let { rangeToId[it] },
        )
    }
}
