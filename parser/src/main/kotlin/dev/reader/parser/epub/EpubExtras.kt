package dev.reader.parser.epub

/** What's known about one spine entry when deciding whether it's a real chapter. */
internal data class SpineItem(
    val entryName: String,
    val tocLabel: String?,
    val heading: String?,
    /** epub:type values in the document plus any OPF `<guide>` type pointing at it. */
    val semanticTypes: Set<String>,
    val isNav: Boolean,
    val linear: Boolean,
    val wordCount: Int,
    val beforeFirstTocEntry: Boolean,
    val headingIsBookTitle: Boolean = false,
)

/**
 * Separates front/back matter (cover, title page, copyright, contents, also-by…) from real chapters,
 * using EPUB semantics first and falling back to well-known titles/filenames on short documents.
 */
internal object EpubExtras {

    private const val MAX_NAMED_EXTRA_WORDS = 1500
    private const val MAX_UNTITLED_FRONT_WORDS = 300

    private val TYPE_LABELS = mapOf(
        "cover" to "Cover",
        "titlepage" to "Title page",
        "title-page" to "Title page",
        "halftitlepage" to "Title page",
        "copyright-page" to "Copyright",
        "copyright" to "Copyright",
        "imprint" to "Copyright",
        "colophon" to "Colophon",
        "toc" to "Contents",
        "loi" to "List of illustrations",
        "lot" to "List of tables",
        "dedication" to "Dedication",
        "acknowledgements" to "Acknowledgements",
        "acknowledgments" to "Acknowledgements",
        "other-credits" to "Credits",
        "contributors" to "Contributors",
        "index" to "Index",
    )

    private val NAMED_EXTRAS = listOf(
        "cover" to "Cover",
        "title ?page|half ?title" to "Title page",
        "copyright|imprint" to "Copyright",
        "colophon" to "Colophon",
        "table of contents|contents|toc" to "Contents",
        "dedication" to "Dedication",
        "acknowledge?ments?" to "Acknowledgements",
        "about the (author|publisher)" to "About the author",
        "also by|other (books|titles|works) by|by the same author" to "Also by",
        "newsletter|mailing list|sign up" to "Newsletter",
    ).map { (pattern, label) -> Regex("\\b(?:$pattern)\\b") to label }

    /** The display label for an extra, or null when [item] is a regular chapter. */
    fun classify(item: SpineItem): String? {
        val typeLabel = item.semanticTypes.firstNotNullOfOrNull { TYPE_LABELS[it] }
        return when {
            item.isNav -> "Contents"
            typeLabel != null -> typeLabel
            !item.linear -> item.tocLabel ?: item.heading ?: "Extra"
            item.wordCount <= MAX_NAMED_EXTRA_WORDS && namedLabel(item) != null -> namedLabel(item)
            item.beforeFirstTocEntry && item.tocLabel == null && item.wordCount <= MAX_UNTITLED_FRONT_WORDS ->
                if (item.headingIsBookTitle) "Title page" else "Front matter"
            else -> null
        }
    }

    private fun namedLabel(item: SpineItem): String? {
        val titles = listOfNotNull(item.tocLabel, item.heading).map { normalize(it) }
        val fileName = normalize(item.entryName.substringAfterLast('/').substringBeforeLast('.'))
        return NAMED_EXTRAS.firstOrNull { (regex, _) ->
            titles.any { title -> regex.find(title)?.range?.first == 0 } || regex.containsMatchIn(fileName)
        }?.second
    }

    private fun normalize(text: String): String =
        text.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
}
