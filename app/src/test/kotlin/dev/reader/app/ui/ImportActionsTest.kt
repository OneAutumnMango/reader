package dev.reader.app.ui

import dev.reader.parser.epub.EpubContentMissingException
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ImportActionsTest {

    @Test
    fun `describeImportError surfaces the deepest cause message`() {
        val root = IllegalStateException("could not initialize class CssStyleKt")
        val wrapped = ExceptionInInitializerError(root)

        assertEquals("could not initialize class CssStyleKt", describeImportError(wrapped))
    }

    @Test
    fun `describeImportError falls back to the class name when there is no message`() {
        val error = NoSuchFieldError()

        assertEquals("NoSuchFieldError", describeImportError(error))
    }

    @Test
    fun `describeImportError explains a broken epub archive in plain terms`() {
        val error = EpubContentMissingException("EPUB is missing entry: OEBPS/Text/001_1.00.xhtml")

        assertEquals(
            "the file seems to be missing content — you may need a different copy",
            describeImportError(error),
        )
    }
}
