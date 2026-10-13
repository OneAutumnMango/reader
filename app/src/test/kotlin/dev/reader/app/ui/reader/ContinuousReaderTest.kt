package dev.reader.app.ui.reader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.reader.core.reader.ReaderSettings
import dev.reader.core.richtext.Block
import dev.reader.core.richtext.RichDocument
import dev.reader.core.richtext.Span
import dev.reader.core.theme.ReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContinuousReaderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val document = RichDocument(
        listOf(
            Block.Heading(1, listOf(Span("Chapter One"))),
            Block.Paragraph(listOf(Span("Hello world."))),
        ),
    )

    @Test
    fun `renders every block as text`() {
        composeRule.setContent {
            ContinuousReader(document, ReaderTheme.Default, ReaderSettings())
        }

        composeRule.onNodeWithText("Chapter One").assertIsDisplayed()
        composeRule.onNodeWithText("Hello world.").assertIsDisplayed()
    }
}
