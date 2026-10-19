package dev.reader.app.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
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
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class ReaderSelectionInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `long pressing continuous text offers note without changing clipboard`() {
        verifyNote(ReadingMode.CONTINUOUS_SCROLL)
    }

    @Test
    fun `long pressing paged text offers note without changing clipboard`() {
        verifyNote(ReadingMode.PAGED_HORIZONTAL_LTR)
    }

    private fun verifyNote(mode: ReadingMode) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("previous", "keep this"))
        var received: Pair<SelectionAction, String>? = null
        composeRule.setContent {
            ReaderScreen(
                RichDocument(listOf(Block.Paragraph(listOf(Span("hello"))))),
                ReaderTheme.Default,
                ReaderSettings(readingMode = mode),
                modifier = Modifier.size(300.dp, 500.dp),
                onSelectionAction = { action, text, _ -> received = action to text },
            )
        }
        composeRule.onNodeWithText("hello").performTouchInput {
            down(centerLeft + androidx.compose.ui.geometry.Offset(8f, 0f))
            advanceEventTime(700L)
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Note").performClick()
        composeRule.runOnIdle {
            assertEquals(SelectionAction.NOTE to "hello", received)
            assertEquals("keep this", clipboard.primaryClip?.getItemAt(0)?.text)
        }
    }
}
