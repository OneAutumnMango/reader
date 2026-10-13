package dev.reader.core.richtext

/** A normalized, format-agnostic document produced by every text-based parser. */
data class RichDocument(val blocks: List<Block>)

sealed interface Block {
    data class Paragraph(val spans: List<Span>) : Block
    data class Heading(val level: Int, val spans: List<Span>) : Block
    data class ListItem(val spans: List<Span>, val ordered: Boolean, val index: Int) : Block
    data class Image(val path: String, val alt: String = "") : Block
}

data class Span(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
)
