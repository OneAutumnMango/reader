package dev.reader.core.model

enum class BookFormat {
    EPUB,
    TXT,
    MARKDOWN,
    HTML,
    PDF,
    CBZ,
    CBR,
    ;

    companion object {
        fun fromExtension(extension: String): BookFormat? = when (extension.lowercase()) {
            "epub" -> EPUB
            "txt" -> TXT
            "md", "markdown" -> MARKDOWN
            "html", "htm", "xhtml" -> HTML
            "pdf" -> PDF
            "cbz" -> CBZ
            "cbr" -> CBR
            else -> null
        }
    }
}
