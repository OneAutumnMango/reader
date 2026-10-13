package dev.reader.app.ui.appearance

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.reader.core.theme.Justification
import dev.reader.core.theme.ReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
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
}
