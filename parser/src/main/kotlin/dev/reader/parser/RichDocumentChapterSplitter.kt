package dev.reader.parser

import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.core.richtext.Span
import dev.reader.core.richtext.wordCount
import dev.reader.parser.text.ChapterSplitter

private const val MAX_FRONT_MATTER_WORDS = 300

/** Canonical detector text is each rich block's text, with one line break between blocks. */
fun RichDocument.chapterDetectionText(): String =
    richLines().joinToString("\n") { line ->
        line.joinToString("") { fragment -> fragment.spans.joinToString("") { it.text } }
    }

/** Splits on canonical detector lines without flattening rich blocks into plain paragraphs. */
fun RichDocument.splitChapters(
    bookTitle: String,
    pattern: String,
    excludedLines: Set<Int> = emptySet(),
): List<ParsedChapter.TextChapter> {
    val lines = richLines()
    val text = lines.joinToString("\n") { line ->
        line.joinToString("") { it.spans.joinToString("") { span -> span.text } }
    }
    val starts = runCatching { ChapterSplitter.breaks(text, pattern) }
        .getOrDefault(emptyList())
        .map { it.line }
        .filterNot { it in excludedLines }
    if (starts.isEmpty()) return listOf(ParsedChapter.TextChapter(bookTitle, this))

    val ranges = buildList {
        if (starts.first() > 0) add(0 until starts.first())
        starts.forEachIndexed { index, start -> add(start until (starts.getOrNull(index + 1) ?: lines.size)) }
    }
    return ranges.map { range ->
        val document = RichDocument(mergeAdjacentFragments(lines.slice(range)))
        val title = if (range.first in starts) {
            lines[range.first].joinToString("") { it.spans.joinToString("") { span -> span.text } }
                .let { ChapterSplitter.headingTitle(it) }
        } else {
            if (document.wordCount() <= MAX_FRONT_MATTER_WORDS) "Front matter" else "Opening"
        }
        ParsedChapter.TextChapter(
            title,
            document,
            isExtra = range.first !in starts && document.wordCount() <= MAX_FRONT_MATTER_WORDS,
        )
    }
}

private data class BlockFragment(val sourceIndex: Int, val block: Block, val spans: List<Span>)

private fun RichDocument.richLines(): List<List<BlockFragment>> = buildList {
    blocks.forEachIndexed { sourceIndex, block ->
        when (block) {
            is Block.Image -> add(listOf(BlockFragment(sourceIndex, block, emptyList())))
            is Block.Paragraph -> addSpanLines(sourceIndex, block, block.spans)
            is Block.Heading -> addSpanLines(sourceIndex, block, block.spans)
            is Block.ListItem -> addSpanLines(sourceIndex, block, block.spans)
        }
        if (sourceIndex != blocks.lastIndex) add(emptyList())
    }
}

private fun MutableList<List<BlockFragment>>.addSpanLines(sourceIndex: Int, block: Block, spans: List<Span>) {
    val lineSpans = mutableListOf<MutableList<Span>>(mutableListOf())
    spans.forEach { span ->
        val parts = span.text.split(Regex("\\r\\n|\\n|\\r"))
        parts.forEachIndexed { index, part ->
            if (part.isNotEmpty()) lineSpans.last() += span.copy(text = part)
            if (index != parts.lastIndex) lineSpans.add(mutableListOf())
        }
    }
    lineSpans.forEach { add(listOf(BlockFragment(sourceIndex, block, it))) }
}

private fun mergeAdjacentFragments(lines: List<List<BlockFragment>>): List<Block> {
    val result = mutableListOf<Block>()
    var previous: BlockFragment? = null
    lines.forEach { fragments ->
        if (fragments.isEmpty()) {
            previous = null
            return@forEach
        }
        fragments.forEach { fragment ->
            val before = previous
            if (before != null && before.sourceIndex == fragment.sourceIndex) {
                val spans = before.spans + Span("\n") + fragment.spans
                result[result.lastIndex] = before.block.withSpans(spans)
                previous = before.copy(spans = spans)
            } else {
                result += fragment.block.withSpans(fragment.spans)
                previous = fragment
            }
        }
    }
    return result
}

private fun Block.withSpans(spans: List<Span>): Block = when (this) {
    is Block.Paragraph -> copy(spans = spans)
    is Block.Heading -> copy(spans = spans)
    is Block.ListItem -> copy(spans = spans)
    is Block.Image -> this
}
