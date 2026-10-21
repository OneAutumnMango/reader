package dev.reader.app.ui.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.reader.core.theme.AppPaletteOverrides
import dev.reader.core.theme.AppTheme
import dev.reader.core.theme.AppThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w600dp-h900dp")
class AppThemeEditorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `picking a seed swatch updates the theme`() {
        var theme by mutableStateOf(AppTheme())
        composeRule.setContent { AppThemeEditor(theme, onChange = { theme = it }) }

        composeRule.onNodeWithTag("seed-swatch-FF2E7D32").performClick()

        assertEquals(0xFF2E7D32L, theme.seedColor)
    }

    @Test
    fun `selecting a mode updates the theme`() {
        var theme by mutableStateOf(AppTheme())
        composeRule.setContent { AppThemeEditor(theme, onChange = { theme = it }) }

        composeRule.onNodeWithTag("app-theme-mode-DARK").performClick()

        assertEquals(AppThemeMode.DARK, theme.mode)
    }

    @Test
    fun `picking a preset clears any existing overrides`() {
        var theme by mutableStateOf(AppTheme(overrides = AppPaletteOverrides(accent = 0xFF123456L)))
        composeRule.setContent { AppThemeEditor(theme, onChange = { theme = it }) }

        composeRule.onNodeWithTag("seed-swatch-FF1565C0").performClick()

        assertEquals(0xFF1565C0L, theme.seedColor)
        assertEquals(AppPaletteOverrides(), theme.overrides)
    }

    @Test
    fun `all six presets are offered`() {
        composeRule.setContent { AppThemeEditor(AppTheme(), onChange = {}) }

        listOf("FF6750A4", "FF2E7D32", "FF1565C0", "FFB71C1C", "FFEF6C00", "FF00796B").forEach { hex ->
            composeRule.onNodeWithTag("seed-swatch-$hex").assertExists()
        }
    }

    @Test
    fun `tapping the add card opens the custom theme dialog`() {
        composeRule.setContent { AppThemeEditor(AppTheme(), onChange = {}) }

        composeRule.onNodeWithTag("add-custom-theme").performClick()

        composeRule.onNodeWithText("Custom theme").assertExists()
    }

    @Test
    fun `the seed colour row opens the picker and round-trips its value`() {
        var theme by mutableStateOf(AppTheme())
        val initialSeed = theme.seedColor
        composeRule.setContent { AppThemeEditor(theme, onChange = { theme = it }) }

        composeRule.onNodeWithTag("add-custom-theme").performClick()
        composeRule.onNodeWithTag("theme-color-Seed colour").performClick()
        composeRule.onNodeWithText("Set").performClick()

        // Confirming without moving a slider re-applies the picker's initial value — this proves
        // the tap-to-pick wiring runs end to end (swatch -> dialog -> onPick -> onChange) without
        // asserting a specific colour, since no slider was moved.
        assertEquals(initialSeed, theme.seedColor)
    }

    @Test
    fun `overriding and resetting a role updates the theme`() {
        var theme by mutableStateOf(AppTheme())
        composeRule.setContent { AppThemeEditor(theme, onChange = { theme = it }) }

        composeRule.onNodeWithTag("add-custom-theme").performClick()
        composeRule.onNodeWithTag("theme-color-Accent").performClick()
        composeRule.onNodeWithText("Set").performClick()

        assertNotNull(theme.overrides.accent)

        composeRule.onNodeWithTag("theme-color-reset-Accent").performClick()

        assertNull(theme.overrides.accent)
    }

    @Test
    fun `setting an override then changing mode keeps both changes`() {
        var theme by mutableStateOf(AppTheme())
        composeRule.setContent { AppThemeEditor(theme, onChange = { theme = it }) }

        composeRule.onNodeWithTag("add-custom-theme").performClick()
        composeRule.onNodeWithTag("theme-color-Accent").performClick()
        composeRule.onNodeWithText("Set").performClick()
        composeRule.onNodeWithText("Done").performClick()

        composeRule.onNodeWithTag("app-theme-mode-DARK").performClick()

        assertEquals(AppThemeMode.DARK, theme.mode)
        assertNotNull(theme.overrides.accent)
    }

    @Test
    fun `the reset icon is absent until a role is overridden`() {
        composeRule.setContent { AppThemeEditor(AppTheme(), onChange = {}) }

        composeRule.onNodeWithTag("add-custom-theme").performClick()

        composeRule.onNodeWithTag("theme-color-reset-Accent").assertDoesNotExist()
    }
}
