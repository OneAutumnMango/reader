package dev.reader.app.ui

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
}
