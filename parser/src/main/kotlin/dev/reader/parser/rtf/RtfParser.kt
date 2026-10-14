package dev.reader.parser.rtf

import dev.reader.core.model.BookFormat
import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.core.richtext.Span
import dev.reader.parser.ParsedBook
import dev.reader.parser.ParsedChapter
import java.io.File

/** Groups whose text content (fonts, colours, metadata) should never reach the plain-text output. */
private val SKIPPED_GROUP_WORDS = setOf("fonttbl", "colortbl", "stylesheet", "info", "pict", "object", "generator")
private const val HEX_ESCAPE_DIGITS = 2
private const val HEX_RADIX = 16

/** Minimal RTF reader: strips control words/groups and keeps the plain text, paragraph by paragraph. */
object RtfParser {

    fun parse(file: File): ParsedBook {
        val paragraphs = toPlainText(file.readText(Charsets.ISO_8859_1))
            .split(Regex("\n{2,}"))
            .map { it.replace('\n', ' ').trim() }
            .filter { it.isNotEmpty() }
            .map { Block.Paragraph(listOf(Span(it))) }

        return ParsedBook(
            title = file.nameWithoutExtension,
            author = "",
            coverPath = null,
            format = BookFormat.RTF,
            chapters = listOf(ParsedChapter.TextChapter(file.nameWithoutExtension, RichDocument(paragraphs))),
        )
    }

    private fun toPlainText(rtf: String): String {
        val out = StringBuilder()
        var i = 0
        var depth = 0
        var skipDepth = -1
        while (i < rtf.length) {
            when (val ch = rtf[i]) {
                '{' -> {
                    depth++
                    i++
                }
                '}' -> {
                    if (skipDepth == depth) skipDepth = -1
                    depth--
                    i++
                }
                '\\' -> i = consumeControl(rtf, i + 1, depth, out, skipDepth) { newDepth -> skipDepth = newDepth }
                else -> {
                    if (skipDepth < 0) out.append(ch)
                    i++
                }
            }
        }
        return out.toString()
    }

    private fun consumeControl(
        rtf: String,
        start: Int,
        depth: Int,
        out: StringBuilder,
        skipDepth: Int,
        setSkipDepth: (Int) -> Unit,
    ): Int {
        if (start >= rtf.length) return start
        val c = rtf[start]
        return when {
            c == '\'' -> {
                val end = minOf(start + 1 + HEX_ESCAPE_DIGITS, rtf.length)
                if (skipDepth < 0) rtf.substring(start + 1, end).toIntOrNull(HEX_RADIX)?.let { out.append(it.toChar()) }
                end
            }
            c == '\\' || c == '{' || c == '}' -> {
                if (skipDepth < 0) out.append(c)
                start + 1
            }
            c.isLetter() -> consumeWord(rtf, start, depth, out, skipDepth, setSkipDepth)
            else -> start + 1
        }
    }

    private fun consumeWord(
        rtf: String,
        start: Int,
        depth: Int,
        out: StringBuilder,
        skipDepth: Int,
        setSkipDepth: (Int) -> Unit,
    ): Int {
        val (word, num, afterWord) = lexControlWord(rtf, start)
        var i = afterWord
        if (i < rtf.length && rtf[i] == ' ') i++
        when (word) {
            "par", "line" -> if (skipDepth < 0) out.append('\n')
            "tab" -> if (skipDepth < 0) out.append('\t')
            "u" -> {
                if (skipDepth < 0 && num != null) out.append(num.toChar())
                if (i < rtf.length) i++ // skip the ANSI fallback char that follows \uN
            }
            in SKIPPED_GROUP_WORDS -> setSkipDepth(depth)
        }
        return i
    }

    /** The letters of a control word, its optional numeric parameter, and the index right after both. */
    private fun lexControlWord(rtf: String, start: Int): Triple<String, Int?, Int> {
        var i = start
        while (i < rtf.length && rtf[i].isLetter()) i++
        val word = rtf.substring(start, i)
        var numEnd = i
        if (numEnd < rtf.length && (rtf[numEnd] == '-' || rtf[numEnd].isDigit())) {
            if (rtf[numEnd] == '-') numEnd++
            while (numEnd < rtf.length && rtf[numEnd].isDigit()) numEnd++
        }
        return Triple(word, rtf.substring(i, numEnd).toIntOrNull(), numEnd)
    }
}
