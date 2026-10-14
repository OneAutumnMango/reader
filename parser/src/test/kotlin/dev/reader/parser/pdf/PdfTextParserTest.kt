package dev.reader.parser.pdf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitWidthDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import dev.reader.core.richtext.Block
import dev.reader.parser.ParsedChapter
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val LEADING = 14f
private const val PARAGRAPH_GAP = 36f

@RunWith(RobolectricTestRunner::class)
class PdfTextParserTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PDFBoxResourceLoader.init(context)
    }

    /** Each page is a list of paragraphs, each a list of visual lines. */
    private fun pdf(pages: List<List<List<String>>>, outline: List<Pair<String, Int>> = emptyList()): File {
        val file = File.createTempFile("test", ".pdf")
        PDDocument().use { doc ->
            pages.forEach { paragraphs ->
                val page = PDPage()
                doc.addPage(page)
                PDPageContentStream(doc, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.newLineAtOffset(50f, 720f)
                    paragraphs.forEachIndexed { p, lines ->
                        if (p > 0) stream.newLineAtOffset(0f, -PARAGRAPH_GAP)
                        lines.forEachIndexed { l, line ->
                            if (l > 0) stream.newLineAtOffset(0f, -LEADING)
                            stream.showText(line)
                        }
                    }
                    stream.endText()
                }
            }
            if (outline.isNotEmpty()) {
                val root = PDDocumentOutline()
                outline.forEach { (title, page) ->
                    root.addLast(
                        PDOutlineItem().apply {
                            this.title = title
                            destination = PDPageFitWidthDestination().apply { setPage(doc.getPage(page)) }
                        },
                    )
                }
                doc.documentCatalog.documentOutline = root
            }
            doc.save(file)
        }
        return file
    }

    private fun ParsedChapter.paragraphs() =
        (this as ParsedChapter.TextChapter).document.blocks.map { block ->
            when (block) {
                is Block.Paragraph -> block.spans.joinToString("") { it.text }
                is Block.Heading -> block.spans.joinToString("") { it.text }
                else -> ""
            }
        }

    @Test
    fun `wrapped lines join into paragraphs and hyphenated words are rejoined`() {
        val file = pdf(
            listOf(
                listOf(
                    listOf(
                        "The quick brown fox jumps over the lazy dog and keeps",
                        "running far into the dis-",
                        "tance.",
                    ),
                    listOf("A second paragraph begins here with plenty of words", "and continues on this line."),
                ),
            ),
        )

        val chapter = PdfTextParser(context).parse(file).chapters.single()

        assertEquals(
            listOf(
                "The quick brown fox jumps over the lazy dog and keeps running far into the distance.",
                "A second paragraph begins here with plenty of words and continues on this line.",
            ),
            chapter.paragraphs(),
        )
    }

    @Test
    fun `a sentence continuing onto the next page stays one paragraph and page numbers are dropped`() {
        val file = pdf(
            listOf(
                listOf(listOf("This sentence starts at the bottom of the first page and"), listOf("1")),
                listOf(listOf("ends at the top of the next one."), listOf("2")),
            ),
        )

        val chapter = PdfTextParser(context).parse(file).chapters.single()

        assertEquals(
            listOf("This sentence starts at the bottom of the first page and ends at the top of the next one."),
            chapter.paragraphs(),
        )
    }

    @Test
    fun `bookmarks become chapters with their page ranges`() {
        val file = pdf(
            pages = listOf(
                listOf(listOf("Title page")),
                listOf(listOf("Chapter One"), listOf("First chapter text.")),
                listOf(listOf("More of chapter one.")),
                listOf(listOf("Chapter Two"), listOf("Second chapter text.")),
            ),
            outline = listOf("Chapter One" to 1, "Chapter Two" to 3),
        )

        val chapters = PdfTextParser(context).parse(file).chapters.map { it as ParsedChapter.TextChapter }

        assertEquals(listOf(file.nameWithoutExtension, "Chapter One", "Chapter Two"), chapters.map { it.title })
        assertEquals(listOf(0..0, 1..2, 3..3), chapters.map { it.pages })
        assertTrue(chapters[1].paragraphs().contains("More of chapter one."))
    }

    @Test
    fun `a chapter pattern splits on the extracted text and maps chapters to pages`() {
        val file = pdf(
            listOf(
                listOf(listOf("Chapter 1"), listOf("Opening text of the book.")),
                listOf(listOf("Chapter 2"), listOf("Second chapter text.")),
            ),
        )
        val parser = PdfTextParser(context)

        val breakLines = parser.text(file).lines().withIndex().filter { it.value.startsWith("Chapter") }
        val chapters = parser.parse(file, "^Chapter \\d").chapters.map { it as ParsedChapter.TextChapter }

        assertEquals(2, breakLines.size)
        assertEquals(listOf("Chapter 1", "Chapter 2"), chapters.map { it.title })
        assertEquals(listOf(0..0, 1..1), chapters.map { it.pages })
        assertEquals(1, parser.parse(file, "^Chapter \\d", setOf(breakLines[1].index)).chapters.size)
    }
}
