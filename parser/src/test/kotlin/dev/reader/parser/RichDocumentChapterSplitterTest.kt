package dev.reader.parser

import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.core.richtext.Span
import dev.reader.parser.text.ChapterSplitter
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RichDocumentChapterSplitterTest {

    @Test
    fun `splitting preserves block kinds span formatting and images`() {
        val image = Block.Image("cover.png", "cover")
        val document = RichDocument(
            listOf(
                Block.Paragraph(listOf(Span("Preface", italic = true))),
                image,
                Block.Heading(2, listOf(Span("Chapter 1", bold = true))),
                Block.Paragraph(listOf(Span("A "), Span("bold", bold = true), Span(" sentence."))),
                Block.Heading(2, listOf(Span("Chapter 2", underline = true))),
                Block.Paragraph(listOf(Span("End."))),
            ),
        )

        val chapters = document.splitChapters("Story", ChapterSplitter.linesPattern(listOf(4, 8)))

        assertEquals(listOf("Front matter", "Chapter 1", "Chapter 2"), chapters.map { it.title })
        assertEquals(document.blocks.take(2), chapters[0].document.blocks)
        assertEquals(image, chapters[0].document.blocks[1])
        val firstHeading = assertIs<Block.Heading>(chapters[1].document.blocks.first())
        assertEquals(Span("Chapter 1", bold = true), firstHeading.spans.single())
        val formattedParagraph = assertIs<Block.Paragraph>(chapters[1].document.blocks[1])
        assertEquals(listOf(Span("A "), Span("bold", bold = true), Span(" sentence.")), formattedParagraph.spans)
        val secondHeading = assertIs<Block.Heading>(chapters[2].document.blocks.first())
        assertEquals(Span("Chapter 2", underline = true), secondHeading.spans.single())
    }

    @Test
    fun `canonical text and picked lines use the same line positions`() {
        val document = RichDocument(
            listOf(
                Block.Paragraph(listOf(Span("Opening\ncontinues"))),
                Block.Heading(1, listOf(Span("Chapter 1"))),
            ),
        )

        assertEquals("Opening\ncontinues\n\nChapter 1", document.chapterDetectionText())
        val chapters = document.splitChapters("Story", ChapterSplitter.linesPattern(listOf(3)))
        assertEquals(listOf("Front matter", "Chapter 1"), chapters.map { it.title })
        assertEquals(
            "Opening\ncontinues",
            chapters.first().document.blocks.joinToString("") { block ->
                when (block) {
                    is Block.Paragraph -> block.spans.joinToString("") { it.text }
                    else -> ""
                }
            },
        )
    }
}
