package dev.reader.parser.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dev.reader.core.richtext.Block
import dev.reader.core.richtext.Span

private const val PARAGRAPH_MARK = "\u2029"
private const val SHORT_LINE_RATIO = 0.6f
private const val TYPICAL_LINE_PERCENTILE = 0.8f
private val PAGE_NUMBER = Regex("^(page\\s+)?\\d{1,4}(\\s*(of|/)\\s*\\d{1,4})?$", RegexOption.IGNORE_CASE)
private val SENTENCE_END = Regex("[.!?:;\"”’)\\]]$")

/** One line of extracted text; blank lines separate paragraphs, so [text] line numbers match the chapter dialog's. */
internal data class PdfLine(val text: String, val page: Int)

private data class RawLine(val text: String, val page: Int, val marked: Boolean)

/**
 * Extracts every page's visual lines, drops bare page numbers and inserts a blank line wherever a
 * paragraph ends (PDFBox's spacing/indent signal, a short last line, or a sentence ending a page).
 */
internal fun extractLines(doc: PDDocument): List<PdfLine> {
    val stripper = PDFTextStripper().apply { paragraphStart = PARAGRAPH_MARK }
    val raw = (0 until doc.numberOfPages).flatMap { page ->
        stripper.startPage = page + 1
        stripper.endPage = page + 1
        var blankBefore = false
        val lines = stripper.getText(doc).lines().mapNotNull { line ->
            val text = line.replace(PARAGRAPH_MARK, "").trim()
            if (text.isEmpty()) {
                blankBefore = true
                null
            } else {
                val marked = blankBefore || line.trimStart().startsWith(PARAGRAPH_MARK)
                blankBefore = false
                RawLine(text, page, marked)
            }
        }
        lines.dropWhile { it.text.matches(PAGE_NUMBER) }.dropLastWhile { it.text.matches(PAGE_NUMBER) }
    }
    val lengths = raw.map { it.text.length }.sorted()
    val typical = lengths.getOrNull((lengths.size * TYPICAL_LINE_PERCENTILE).toInt().coerceAtMost(lengths.lastIndex))
        ?: 0
    val result = mutableListOf<PdfLine>()
    raw.forEachIndexed { i, line ->
        val prev = raw.getOrNull(i - 1)
        if (prev != null) {
            val prevShort = prev.text.length < typical * SHORT_LINE_RATIO && !continuesWord(prev.text, line.text)
            val breaks = if (prev.page != line.page) {
                prevShort || SENTENCE_END.containsMatchIn(prev.text)
            } else {
                line.marked || prevShort
            }
            if (breaks) result += PdfLine("", line.page)
        }
        result += PdfLine(line.text, line.page)
    }
    return result
}

/** Paragraphs from blank-separated [lines], re-joining wrapped lines and words hyphenated across them. */
internal fun paragraphs(lines: List<PdfLine>): List<Block> {
    val blocks = mutableListOf<Block>()
    var current = ""
    fun flush() {
        if (current.isNotBlank()) blocks += Block.Paragraph(listOf(Span(current)))
        current = ""
    }
    lines.forEach { line ->
        when {
            line.text.isBlank() -> flush()
            current.isEmpty() -> current = line.text
            continuesWord(current, line.text) -> current = current.dropLast(1) + line.text
            else -> current = "$current ${line.text}"
        }
    }
    flush()
    return blocks
}

/** A word hyphenated across lines: "dis-" followed by "tance". */
private fun continuesWord(line: String, next: String): Boolean =
    line.endsWith('-') && line.getOrNull(line.length - 2)?.isLetter() == true && next.first().isLowerCase()
