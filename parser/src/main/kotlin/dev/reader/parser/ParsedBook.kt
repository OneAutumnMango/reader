package dev.reader.parser

import dev.reader.core.model.BookFormat
import dev.reader.core.richtext.RichDocument

sealed interface ParsedChapter {
    val title: String

    /** [isExtra] marks front/back matter (title page, copyright, contents…) rather than a real chapter. */
    data class TextChapter(
        override val title: String,
        val document: RichDocument,
        val isExtra: Boolean = false,
    ) : ParsedChapter
    data class ImageChapter(override val title: String, val pages: List<String>) : ParsedChapter
}

/**
 * [coverPath] identifies a cover image without extracting it: an in-archive entry name for
 * epub/comic sources, or an absolute path for a loose file. Callers decide whether to cache it.
 */
data class ParsedBook(
    val title: String,
    val author: String,
    val coverPath: String?,
    val format: BookFormat,
    val chapters: List<ParsedChapter>,
)
