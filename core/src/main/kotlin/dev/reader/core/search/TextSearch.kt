package dev.reader.core.search

import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument

/** [anyWhitespace] lets each run of whitespace in a plain-text query match any run of whitespace. */
data class SearchOptions(val matchCase: Boolean = false, val regex: Boolean = false, val anyWhitespace: Boolean = false)

/** A match in block [block] of a document, with a short excerpt around it for result lists. */
data class SearchMatch(val block: Int, val range: IntRange, val snippet: Snippet)

/** [text] is an excerpt; [match] is where the hit sits inside it. */
data class Snippet(val text: String, val match: IntRange)

private const val SNIPPET_RADIUS = 40
private const val ANY_WHITESPACE = "[\\s\\p{Z}]+"
private val WHITESPACE_RUN = Regex("[\\s\\p{Z}]+")

object TextSearch {

    /** The regex for [query], null for an empty query, or a failure for an invalid regular expression. */
    fun compile(query: String, options: SearchOptions): Result<Regex>? {
        if (query.isEmpty()) return null
        val pattern = when {
            options.regex -> query
            options.anyWhitespace -> query.split(WHITESPACE_RUN).joinToString(ANY_WHITESPACE) { Regex.escape(it) }
            else -> Regex.escape(query)
        }
        val regexOptions = if (options.matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
        return runCatching { Regex(pattern, regexOptions) }
    }

    /** Non-empty matches of [regex] in [document], in reading order, stopping after [limit]. */
    fun find(document: RichDocument, regex: Regex, limit: Int = Int.MAX_VALUE): List<SearchMatch> {
        val matches = mutableListOf<SearchMatch>()
        document.blocks.forEachIndexed { index, block ->
            val text = block.plainText()
            for (match in regex.findAll(text)) {
                if (matches.size >= limit) return matches
                if (match.range.isEmpty()) continue
                matches += SearchMatch(index, match.range, snippet(text, match.range))
            }
        }
        return matches
    }

    fun snippet(text: String, range: IntRange, radius: Int = SNIPPET_RADIUS): Snippet {
        val start = (range.first - radius).coerceAtLeast(0)
        val end = (range.last + 1 + radius).coerceAtMost(text.length)
        val prefix = if (start > 0) "\u2026" else ""
        val suffix = if (end < text.length) "\u2026" else ""
        val excerpt = prefix + text.substring(start, end).replace(WHITESPACE_RUN, " ") + suffix
        val before = prefix.length + text.substring(start, range.first).replace(WHITESPACE_RUN, " ").length
        val matchLength = text.substring(range.first, range.last + 1).replace(WHITESPACE_RUN, " ").length
        return Snippet(excerpt, before until before + matchLength)
    }
}

fun Block.plainText(): String = when (this) {
    is Block.Paragraph -> spans.joinToString("") { it.text }
    is Block.Heading -> spans.joinToString("") { it.text }
    is Block.ListItem -> spans.joinToString("") { it.text }
    is Block.Image -> ""
}
