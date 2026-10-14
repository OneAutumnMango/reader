package dev.reader.parser.text

import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.core.richtext.wordCount
import dev.reader.parser.ParsedChapter

private const val MAX_FRONT_MATTER_WORDS = 300

/**
 * Turns chunks into chapters. A lone untitled chunk is the whole book under [bookTitle]; a short
 * preamble before the first heading becomes a front-matter extra, a long one an "Opening" chapter.
 */
internal fun List<TextChunk>.toChapters(bookTitle: String, render: (String) -> RichDocument): List<ParsedChapter> =
    map { chunk ->
        val document = render(chunk.text)
        when {
            chunk.title != null -> ParsedChapter.TextChapter(chunk.title, document.withHeadingFirst())
            size == 1 -> ParsedChapter.TextChapter(bookTitle, document)
            document.wordCount() <= MAX_FRONT_MATTER_WORDS ->
                ParsedChapter.TextChapter("Front matter", document, isExtra = true)
            else -> ParsedChapter.TextChapter("Opening", document)
        }
    }

/** A chunk's first block is its heading line; plain-text renders it as a paragraph, so promote it. */
private fun RichDocument.withHeadingFirst(): RichDocument {
    val first = blocks.firstOrNull() as? Block.Paragraph ?: return this
    return RichDocument(listOf(Block.Heading(1, first.spans, first.textAlign)) + blocks.drop(1))
}

/** A detected chapter heading: the 0-based [line] it starts on, its display [title] and a peek at what follows. */
data class ChapterBreak(val line: Int, val title: String, val preview: String)

/** A slice of the source text: [title] is null for any preamble before the first heading. */
data class TextChunk(val title: String?, val text: String)

/** One way auto-detection could split a book: a human [name], its [pattern] and how many breaks it finds. */
data class ChapterSuggestion(val name: String, val pattern: String, val count: Int)

/**
 * A compiled heading pattern. Patterns starting with [ChapterSplitter.STANDALONE_PREFIX] also require
 * the heading to be followed by a blank line, i.e. to stand alone as its own paragraph.
 */
internal class HeadingRule(pattern: String) {
    val standalone = pattern.startsWith(ChapterSplitter.STANDALONE_PREFIX)
    val regex = Regex(pattern.removePrefix(ChapterSplitter.STANDALONE_PREFIX))
}

/**
 * Splits plain text / markdown into chapters on heading lines matched by a regex. A heading must
 * be short and start the file or follow a blank line, which keeps list items and prose that
 * happen to start with "Chapter" or "1." from being treated as breaks.
 */
object ChapterSplitter {

    /** Marks a pattern whose matches must also be followed by a blank line (see [HeadingRule]). */
    const val STANDALONE_PREFIX = "standalone:"

    private const val MAX_HEADING_LENGTH = 100
    private const val PREVIEW_LENGTH = 120
    private const val PREVIEW_LINES = 3

    /** Literal "line starts with" matching for a user-typed prefix such as `# ` or `Chapter`. */
    fun prefixPattern(prefix: String): String = "^" + Regex.escape(prefix)

    /** The most chapter-like heading pattern in [text], or null if nothing stands out. */
    fun detectPattern(text: String, markdown: Boolean): String? = suggestions(text, markdown).firstOrNull()?.pattern

    /** Every plausible way to split [text], best first (see [ChapterDetector]). */
    fun suggestions(text: String, markdown: Boolean): List<ChapterSuggestion> =
        ChapterDetector.suggest(text.lines(), markdown)

    /** Where [pattern] would split [text]. Throws on an invalid regex. */
    fun breaks(text: String, pattern: String): List<ChapterBreak> {
        val lines = text.lines()
        val starts = headingLines(lines, HeadingRule(pattern))
        return starts.mapIndexed { i, index ->
            val end = starts.getOrNull(i + 1) ?: lines.size
            val preview = lines.subList(index + 1, end).asSequence()
                .filter { it.isNotBlank() }
                .take(PREVIEW_LINES)
                .joinToString(" ") { it.trim() }
                .take(PREVIEW_LENGTH)
            ChapterBreak(index, headingTitle(lines[index]), preview)
        }
    }

    /** [text] cut at every heading, except any whose line is in [excludedLines] (merged into the previous chunk). */
    fun split(text: String, pattern: String, excludedLines: Set<Int> = emptySet()): List<TextChunk> {
        val lines = text.lines()
        val starts = runCatching { headingLines(lines, HeadingRule(pattern)) }.getOrDefault(emptyList())
            .filterNot { it in excludedLines }
        if (starts.isEmpty()) return listOf(TextChunk(null, text))
        val preamble = lines.subList(0, starts.first()).joinToString("\n")
        val chapters = starts.mapIndexed { i, start ->
            val end = starts.getOrNull(i + 1) ?: lines.size
            TextChunk(headingTitle(lines[start]), lines.subList(start, end).joinToString("\n"))
        }
        return if (preamble.isBlank()) chapters else listOf(TextChunk(null, preamble)) + chapters
    }

    internal fun headingLines(lines: List<String>, rule: HeadingRule): List<Int> = lines.indices.filter { i ->
        val line = lines[i].trim()
        line.isNotEmpty() &&
            line.length <= MAX_HEADING_LENGTH &&
            (i == 0 || lines[i - 1].isBlank()) &&
            (!rule.standalone || i == lines.lastIndex || lines[i + 1].isBlank()) &&
            rule.regex.containsMatchIn(line)
    }

    internal fun headingTitle(line: String): String =
        line.trim().trimStart('#').trim().trim('*', '_').trim().ifEmpty { line.trim() }
}
