package dev.reader.core.richtext

import dev.reader.core.theme.Justification

/** A normalized, format-agnostic document produced by every text-based parser. */
data class RichDocument(val blocks: List<Block>)

sealed interface Block {
    data class Paragraph(val spans: List<Span>, val textAlign: Justification? = null) : Block
    data class Heading(val level: Int, val spans: List<Span>, val textAlign: Justification? = null) : Block
    data class ListItem(val spans: List<Span>, val ordered: Boolean, val index: Int) : Block
    data class Image(val path: String, val alt: String = "") : Block
}

data class Span(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    /** CSS colour override; null means "use the reader theme's text colour". */
    val color: Long? = null,
    val highlighted: Boolean = false,
)

fun RichDocument.wordCount(): Int = blocks.sumOf { it.wordCount() }

private fun Block.wordCount(): Int = when (this) {
    is Block.Paragraph -> spans.wordCount()
    is Block.Heading -> spans.wordCount()
    is Block.ListItem -> spans.wordCount()
    is Block.Image -> 0
}

private fun List<Span>.wordCount(): Int =
    sumOf { span -> span.text.trim().split(Regex("\\s+")).count { it.isNotBlank() } }
