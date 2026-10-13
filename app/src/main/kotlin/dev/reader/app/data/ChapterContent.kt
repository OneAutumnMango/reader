package dev.reader.app.data

import dev.reader.core.model.BookFormat
import dev.reader.core.richtext.RichDocument
import java.io.File

/** What a chapter resolves to once loaded, so the reader UI knows which renderer to use. */
sealed interface ChapterContent {
    data class Text(val document: RichDocument) : ChapterContent
    data class Comic(val file: File, val format: BookFormat, val pages: List<String>) : ChapterContent
    data class Pdf(val file: File) : ChapterContent
}
