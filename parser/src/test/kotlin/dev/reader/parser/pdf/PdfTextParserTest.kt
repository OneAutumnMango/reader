package dev.reader.parser.pdf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import dev.reader.core.richtext.Block
import dev.reader.parser.ParsedChapter
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class PdfTextParserTest {

    @Test
    fun `extracts text from a pdf page`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(context)

        val file = File.createTempFile("test", ".pdf")
        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(50f, 700f)
                stream.showText("Hello reader")
                stream.endText()
            }
            doc.save(file)
        }

        val book = PdfTextParser(context).parse(file)

        val chapter = book.chapters.single() as ParsedChapter.TextChapter
        val text = chapter.document.blocks.filterIsInstance<Block.Paragraph>()
            .joinToString(" ") { paragraph -> paragraph.spans.joinToString("") { it.text } }
        assertTrue(text.contains("Hello reader"))
    }
}
