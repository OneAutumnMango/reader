package dev.reader.parser.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import dev.reader.core.richtext.Block
import dev.reader.core.richtext.Span
import kotlin.math.abs

private const val PARAGRAPH_MARK = "\u2029"
private const val SHORT_LINE_RATIO = 0.6f
private const val TYPICAL_LINE_PERCENTILE = 0.8f
private const val DESCENDER_BUFFER_RATIO = 0.25f
private val PAGE_NUMBER = Regex("^(page\\s+)?\\d{1,4}(\\s*(of|/)\\s*\\d{1,4})?$", RegexOption.IGNORE_CASE)
private val SENTENCE_END = Regex("[.!?:;\"”’)\\]]$")

/** One line of extracted text; blank lines separate paragraphs, so [text] line numbers match the chapter dialog's. */
internal data class PdfLine(val text: String, val page: Int, val fontSize: Float = 0f, val bold: Boolean = false)

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

private const val LINE_Y_TOLERANCE = 3f // PDF user-space units (~1 mm on a typical A4 page)

/**
 * For each page, returns the real bounding box of every non-blank extracted line using PDFBox
 * TextPosition data. The k-th entry corresponds to pageToLineIndices[page][k]. Lines matching the
 * page-number pattern are skipped to match [extractLines] behaviour.
 */
internal fun extractPageLineBounds(
    doc: PDDocument,
    pageToLineIndices: Map<Int, List<Int>>,
): Map<Int, List<PdfLineBounds>> {
    data class Group(
        val yCenter: Float,
        var xMin: Float,
        var xMax: Float,
        var yMin: Float,
        var yMax: Float,
        val text: StringBuilder = StringBuilder(),
    )

    val pageGroups = mutableMapOf<Int, MutableList<Group>>()

    val stripper = object : PDFTextStripper() {
        override fun writeString(string: String, textPositions: List<TextPosition>) {
            if (string.isBlank() || textPositions.isEmpty()) return
            val entries = textPositions.filter { it.unicode.isNotBlank() }
            if (entries.isEmpty()) return
            val page = currentPageNo - 1
            val baseline = entries.first().yDirAdj
            // yDirAdj is the baseline (y from page top); characters sit ABOVE the baseline.
            val ascent = entries.maxOf { it.heightDir }
            val xMin = entries.minOf { it.xDirAdj }
            val xMax = entries.maxOf { it.xDirAdj + it.widthDirAdj }
            val yMin = baseline - ascent // top of characters
            val yMax = baseline + ascent * DESCENDER_BUFFER_RATIO // small buffer below baseline for descenders
            val groups = pageGroups.getOrPut(page) { mutableListOf() }
            val existing = groups.firstOrNull { abs(it.yCenter - baseline) < LINE_Y_TOLERANCE }
            if (existing != null) {
                existing.xMin = minOf(existing.xMin, xMin)
                existing.xMax = maxOf(existing.xMax, xMax)
                existing.yMin = minOf(existing.yMin, yMin)
                existing.yMax = maxOf(existing.yMax, yMax)
                existing.text.append(string.trim())
            } else {
                groups += Group(baseline, xMin, xMax, yMin, yMax, StringBuilder(string.trim()))
            }
        }
    }
    for (p in 0 until doc.numberOfPages) {
        stripper.startPage = p + 1
        stripper.endPage = p + 1
        stripper.getText(doc)
    }

    val result = mutableMapOf<Int, List<PdfLineBounds>>()
    pageToLineIndices.forEach { (page, lineIndices) ->
        val mediaBox = doc.getPage(page).mediaBox
        val pH = mediaBox.height.coerceAtLeast(1f)
        val pW = mediaBox.width.coerceAtLeast(1f)
        val groups = pageGroups[page]?.sortedBy { it.yCenter }
            ?.filter { g -> !g.text.toString().trim().matches(PAGE_NUMBER) }
            ?: return@forEach
        result[page] = lineIndices.mapIndexedNotNull { k, lineIdx ->
            val g = groups.getOrNull(k) ?: return@mapIndexedNotNull null
            PdfLineBounds(
                lineIdx,
                (g.yMin / pH).coerceIn(0f, 1f),
                (g.yMax / pH).coerceIn(0f, 1f),
                (g.xMin / pW).coerceIn(0f, 1f),
                (g.xMax / pW).coerceIn(0f, 1f),
            )
        }
    }
    return result
}
