package dev.reader.app.ui.appearance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.reader.core.theme.Justification
import dev.reader.core.theme.ReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderThemeEditorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `selecting justify updates the theme`() {
        var theme = ReaderTheme.Default
        composeRule.setContent {
            ReaderThemeEditor(theme, onChange = { theme = it })
        }

        composeRule.onNodeWithTag("justification-JUSTIFY").performClick()

        assertEquals(Justification.JUSTIFY, theme.justification)
    }

    @Test
    fun `shows the current font size`() {
        val theme = ReaderTheme.Default.copy(fontSizeSp = 22f)
        composeRule.setContent {
            ReaderThemeEditor(theme, onChange = {})
        }

        composeRule.onNodeWithText("Font size: 22sp").assertExists()
    }

    @Test
    fun `the preview box grows to fit large text instead of clipping it`() {
        val theme = ReaderTheme.Default.copy(fontSizeSp = 36f, lineSpacing = 2f)
        composeRule.setContent {
            Column(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                ReaderThemeEditor(theme, onChange = {}, showPreview = true)
            }
        }

        val box = composeRule.onNodeWithTag("reader-theme-preview-box").getUnclippedBoundsInRoot()
        val text = composeRule.onNodeWithTag("reader-theme-preview").getUnclippedBoundsInRoot()
        assertTrue(box.bottom - box.top > 72.dp)
        assertTrue(text.bottom <= box.bottom)
    }
}
