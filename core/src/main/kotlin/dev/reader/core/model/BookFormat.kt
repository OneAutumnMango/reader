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
    ODT,
    ;

    companion object {
        fun fromExtension(extension: String): BookFormat? = when (extension.lowercase()) {
            "epub" -> EPUB
            "txt", "text", "nfo", "log" -> TXT
            "md", "markdown", "mdown", "mkd", "mkdn", "mdwn", "mdtxt", "rmd", "qmd" -> MARKDOWN
            "html", "htm", "xhtml", "xht", "shtml" -> HTML
            "pdf" -> PDF
            "cbz" -> CBZ
            "cbr" -> CBR
            "fb2" -> FB2
            // Palm database suffixes are candidates only; MobiParser validates the database type.
            "mobi", "azw", "azw3", "pdb", "prc" -> MOBI
            "rtf" -> RTF
            "docx" -> DOCX
            "odt", "fodt" -> ODT
            else -> null
        }

        fun fromFileName(name: String): BookFormat? = when {
            name.endsWith(".fb2.zip", ignoreCase = true) -> FB2
            else -> fromExtension(name.substringAfterLast('.', ""))
        }
    }
}
