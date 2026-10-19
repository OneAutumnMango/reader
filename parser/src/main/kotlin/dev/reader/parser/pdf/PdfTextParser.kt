package dev.reader.parser.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dev.reader.core.model.BookFormat
import dev.reader.core.richtext.RichDocument
import dev.reader.parser.ParsedBook
import dev.reader.parser.ParsedChapter
import dev.reader.parser.text.ChapterSplitter
import dev.reader.parser.text.TextChunk
import dev.reader.parser.text.toChapters
import java.io.File

/**
 * Bounding box of a single text line within its page; coordinates are fractions 0..1
 * (0 = top/left edge, 1 = bottom/right edge).
 */
data class PdfLineBounds(
    val lineIndex: Int,
    val top: Float,
    val bottom: Float,
    val left: Float,
    val right: Float,
)

/**
 * Reflow mode: extracts the text so it can be themed like any other format. Chapters come from a
 * user [chapterPattern] when set, otherwise the PDF's own bookmarks.
 */
class PdfTextParser(context: Context) {

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    /** The extracted text, line-numbered exactly as [parse] splits it on a chapter pattern. */
    fun text(file: File): String = PDDocument.load(file).use { doc -> extractLines(doc).joinToString("\n") { it.text } }

    /** Maps each PDF page number to the index of the first non-blank extracted text line on that page. */
    fun pageLineMap(file: File): Map<Int, Int> = PDDocument.load(file).use { doc ->
        val lines = extractLines(doc)
        buildMap { lines.forEachIndexed { i, line -> if (line.text.isNotBlank()) putIfAbsent(line.page, i) } }
    }

    /** Maps each PDF page number to all extracted text line indices on that page (non-blank only). */
    fun pageLines(file: File): Map<Int, List<Int>> = PDDocument.load(file).use { doc ->
        val lines = extractLines(doc)
        buildMap<Int, MutableList<Int>> {
            lines.forEachIndexed { i, line ->
                if (line.text.isNotBlank()) getOrPut(line.page) { mutableListOf() }.add(i)
            }
        }
    }

    /** Real bounding boxes (fractions 0..1) of each non-blank line per page, from actual TextPosition data. */
    fun pageLineBounds(file: File): Map<Int, List<PdfLineBounds>> = PDDocument.load(file).use { doc ->
        val lines = extractLines(doc)
        val pageToIndices = buildMap<Int, MutableList<Int>> {
            lines.forEachIndexed { i, line ->
                if (line.text.isNotBlank()) getOrPut(line.page) { mutableListOf() }.add(i)
            }
        }
        extractPageLineBounds(doc, pageToIndices)
    }

    fun parse(file: File, chapterPattern: String? = null, excludedLines: Set<Int> = emptySet()): ParsedBook {
        val chapters = PDDocument.load(file).use { doc ->
            val lines = extractLines(doc)
            val title = file.nameWithoutExtension
            chapterPattern?.let { patternChapters(lines, it, excludedLines, title) }
                ?: outlineChapters(doc, lines, title)
                ?: listOf(ParsedChapter.TextChapter(title, RichDocument(paragraphs(lines))))
        }
        return ParsedBook(
            title = file.nameWithoutExtension,
            author = "",
            coverPath = null,
            format = BookFormat.PDF,
            chapters = chapters,
        )
    }

    private fun patternChapters(
        lines: List<PdfLine>,
        pattern: String,
        excludedLines: Set<Int>,
        title: String,
    ): List<ParsedChapter>? {
        val text = lines.joinToString("\n") { it.text }
        val breaks = runCatching { ChapterSplitter.breaks(text, pattern) }.getOrDefault(emptyList())
            .filterNot { it.line in excludedLines }
        if (breaks.isEmpty()) return null
        val starts = (listOf(0) + breaks.map { it.line }).distinct()
        val titles = starts.associateWith { start -> breaks.find { it.line == start }?.title }
        val ranges = starts.mapIndexed { i, start -> start until (starts.getOrNull(i + 1) ?: lines.size) }
            .map { range -> range.filter { lines[it].text.isNotBlank() } }
            .filter { it.isNotEmpty() }
        val chunks = ranges.map { range ->
            TextChunk(titles[range.first()], (range.first()..range.last()).joinToString("\n") { lines[it].text })
        }
        return chunks.toChapters(title) { RichDocument(paragraphs(it.lines().map { line -> PdfLine(line, 0) })) }
    }

    private fun outlineChapters(doc: PDDocument, lines: List<PdfLine>, title: String): List<ParsedChapter>? {
        val outline = doc.documentCatalog.documentOutline ?: return null
        val top = outline.children().toList()
        val items = if (top.size == 1) top.single().children().toList().ifEmpty { top } else top
        val starts = items.mapNotNull { item ->
            val page = runCatching { item.findDestinationPage(doc) }.getOrNull() ?: return@mapNotNull null
            val index = doc.pages.indexOf(page)
            if (index < 0) null else item.title.orEmpty().trim().ifEmpty { "Chapter" } to index
        }.sortedBy { it.second }.distinctBy { it.second }
        if (starts.size < 2) return null
        val sections = (if (starts.first().second > 0) listOf(title to 0) + starts else starts)
        return sections.mapIndexed { i, (name, start) ->
            val end = (sections.getOrNull(i + 1)?.second ?: doc.numberOfPages) - 1
            val pages = start..end.coerceAtLeast(start)
            ParsedChapter.TextChapter(name, RichDocument(paragraphs(lines.filter { it.page in pages })))
        }
    }
}
