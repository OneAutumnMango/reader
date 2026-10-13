package dev.reader.app.ui.appearance

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.reader.core.theme.AppTheme
import dev.reader.core.theme.AppThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class AppThemeEditorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `picking a seed swatch updates the theme`() {
        var theme = AppTheme()
        composeRule.setContent { AppThemeEditor(theme, onChange = { theme = it }) }

        composeRule.onNodeWithTag("seed-swatch-FF2E7D32").performClick()

        assertEquals(0xFF2E7D32L, theme.seedColor)
    }

    @Test
    fun `selecting a mode updates the theme`() {
        var theme = AppTheme()
        composeRule.setContent { AppThemeEditor(theme, onChange = { theme = it }) }

        composeRule.onNodeWithTag("app-theme-mode-DARK").performClick()

        assertEquals(AppThemeMode.DARK, theme.mode)
    }
}
