package dev.reader.app.ui.reader

import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument

/** The document's blocks concatenated into one string (blocks joined by "\n"), with each block's start offset. */
internal class DocumentText(val text: String, val starts: List<Int>)

internal fun RichDocument.toDocumentText(): DocumentText {
    val texts = blocks.map { block ->
        val spans = when (block) {
            is Block.Paragraph -> block.spans
            is Block.Heading -> block.spans
            is Block.ListItem -> block.spans
            is Block.Image -> emptyList()
        }
        spans.joinToString("") { it.text }
    }
    val starts = texts.runningFold(0) { offset, blockText -> offset + blockText.length + 1 }
    return DocumentText(texts.joinToString("\n"), starts)
}

/** Fraction of the way through the document's text that [charOffset] falls at, 0..1. */
internal fun DocumentText.fractionOf(charOffset: Int): Float =
    (charOffset.toFloat() / text.length.coerceAtLeast(1)).coerceIn(0f, 1f)
