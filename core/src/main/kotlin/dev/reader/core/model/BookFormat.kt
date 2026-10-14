package dev.reader.core.model

enum class BookFormat {
    EPUB,
    TXT,
    MARKDOWN,
    HTML,
    PDF,
    CBZ,
    CBR,
    FB2,
    MOBI,
    RTF,
    DOCX,
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
            "fb2" -> FB2
            // .azw and .azw3 share the classic MOBI/PalmDOC container Kindle devices still read.
            "mobi", "azw", "azw3" -> MOBI
            "rtf" -> RTF
            "docx" -> DOCX
            else -> null
        }
    }
}
