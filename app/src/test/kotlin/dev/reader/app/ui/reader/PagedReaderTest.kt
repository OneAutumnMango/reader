package dev.reader.app.ui.reader

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import dev.reader.core.reader.ReaderSettings
import dev.reader.core.reader.ReadingMode
import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.core.richtext.Span
import dev.reader.core.theme.ReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PagedReaderTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `renders the first page within the measured viewport`() {
        val document = RichDocument((1..20).map { Block.Paragraph(listOf(Span("Paragraph $it"))) })
        val settings = ReaderSettings(readingMode = ReadingMode.PAGED_HORIZONTAL_LTR)

        composeRule.setContent {
            PagedReader(document, ReaderTheme.Default, settings, modifier = Modifier.size(300.dp, 500.dp))
        }

        composeRule.onNodeWithText("Paragraph 1").assertExists()
    }
}
