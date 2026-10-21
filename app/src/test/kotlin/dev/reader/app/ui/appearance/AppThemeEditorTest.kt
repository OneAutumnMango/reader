package dev.reader.app.ui.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import dev.reader.core.theme.AppPaletteOverrides
import dev.reader.core.theme.AppTheme
import dev.reader.core.theme.AppThemeMode
import dev.reader.core.theme.SavedCustomTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w600dp-h900dp")
class AppThemeEditorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `picking a seed swatch updates the theme`() {
        var theme by mutableStateOf(AppTheme())
        composeRule.setContent {
            AppThemeEditor(
                theme,
                customThemes = emptyList(),
                onChange = { theme = it },
                onAddCustomTheme = {},
                onRemoveCustomTheme = {},
            )
        }

        composeRule.onNodeWithTag("seed-swatch-FF2E7D32").performClick()

        assertEquals(0xFF2E7D32L, theme.seedColor)
    }

    @Test
    fun `selecting a mode updates the theme`() {
        var theme by mutableStateOf(AppTheme())
        composeRule.setContent {
            AppThemeEditor(
                theme,
                customThemes = emptyList(),
                onChange = { theme = it },
                onAddCustomTheme = {},
                onRemoveCustomTheme = {},
            )
        }

        composeRule.onNodeWithTag("app-theme-mode-DARK").performClick()

        assertEquals(AppThemeMode.DARK, theme.mode)
    }

    @Test
    fun `picking a preset clears any existing overrides`() {
        var theme by mutableStateOf(AppTheme(overrides = AppPaletteOverrides(accent = 0xFF123456L)))
        composeRule.setContent {
            AppThemeEditor(
                theme,
                customThemes = emptyList(),
                onChange = { theme = it },
                onAddCustomTheme = {},
                onRemoveCustomTheme = {},
            )
        }

        composeRule.onNodeWithTag("seed-swatch-FF1565C0").performClick()

        assertEquals(0xFF1565C0L, theme.seedColor)
        assertEquals(AppPaletteOverrides(), theme.overrides)
    }

    @Test
    fun `all six presets are offered`() {
        composeRule.setContent {
            AppThemeEditor(
                AppTheme(),
                customThemes = emptyList(),
                onChange = {},
                onAddCustomTheme = {},
                onRemoveCustomTheme = {},
            )
        }

        listOf("FF6750A4", "FF2E7D32", "FF1565C0", "FFB71C1C", "FFEF6C00", "FF00796B").forEach { hex ->
            composeRule.onNodeWithTag("seed-swatch-$hex").assertExists()
        }
    }

    @Test
    fun `tapping the add card opens the custom theme dialog`() {
        composeRule.setContent {
            AppThemeEditor(
                AppTheme(),
                customThemes = emptyList(),
                onChange = {},
                onAddCustomTheme = {},
                onRemoveCustomTheme = {},
            )
        }

        composeRule.onNodeWithTag("add-custom-theme").performClick()

        composeRule.onNodeWithText("Custom theme").assertExists()
    }

    @Test
    fun `the seed colour row opens the picker and changes the theme`() {
        var theme by mutableStateOf(AppTheme())
        composeRule.setContent {
            AppThemeEditor(
                theme,
                customThemes = emptyList(),
                onChange = { theme = it },
                onAddCustomTheme = {},
                onRemoveCustomTheme = {},
            )
        }

        composeRule.onNodeWithTag("add-custom-theme").performClick()
        composeRule.onNodeWithTag("theme-color-Seed colour").performClick()
        composeRule.onNodeWithTag("color-picker-hex").performTextClearance()
        composeRule.onNodeWithTag("color-picker-hex").performTextInput("#FF112233")
        composeRule.onNodeWithText("Set").performClick()

        assertEquals(0xFF112233L, theme.seedColor)
    }

    @Test
    fun `overriding and resetting a role updates the theme`() {
        var theme by mutableStateOf(AppTheme())
        composeRule.setContent {
            AppThemeEditor(
                theme,
                customThemes = emptyList(),
                onChange = { theme = it },
                onAddCustomTheme = {},
                onRemoveCustomTheme = {},
            )
        }

        composeRule.onNodeWithTag("add-custom-theme").performClick()
        composeRule.onNodeWithTag("theme-color-Accent").performClick()
        composeRule.onNodeWithTag("color-picker-hex").performTextClearance()
        composeRule.onNodeWithTag("color-picker-hex").performTextInput("#FF112233")
        composeRule.onNodeWithText("Set").performClick()

        assertNotNull(theme.overrides.accent)

        composeRule.onNodeWithTag("theme-color-reset-Accent").performClick()

        assertNull(theme.overrides.accent)
    }

    @Test
    fun `setting an override then changing mode keeps both changes`() {
        var theme by mutableStateOf(AppTheme())
        composeRule.setContent {
            AppThemeEditor(
                theme,
                customThemes = emptyList(),
                onChange = { theme = it },
                onAddCustomTheme = {},
                onRemoveCustomTheme = {},
            )
        }

        composeRule.onNodeWithTag("add-custom-theme").performClick()
        composeRule.onNodeWithTag("theme-color-Accent").performClick()
        composeRule.onNodeWithTag("color-picker-hex").performTextClearance()
        composeRule.onNodeWithTag("color-picker-hex").performTextInput("#FF112233")
        composeRule.onNodeWithText("Set").performClick()
        composeRule.onNodeWithText("Done").performClick()

        composeRule.onNodeWithTag("app-theme-mode-DARK").performClick()

        assertEquals(AppThemeMode.DARK, theme.mode)
        assertNotNull(theme.overrides.accent)
    }

    @Test
    fun `the custom dialog reflects the theme it is given, not its own writes`() {
        composeRule.setContent {
            AppThemeEditor(
                AppTheme(),
                customThemes = emptyList(),
                onChange = {},
                onAddCustomTheme = {},
                onRemoveCustomTheme = {},
            )
        }
        composeRule.onNodeWithTag("add-custom-theme").performClick()
        composeRule.onNodeWithTag("theme-color-Accent").performClick()
        composeRule.onNodeWithTag("color-picker-hex").performTextClearance()
        composeRule.onNodeWithTag("color-picker-hex").performTextInput("#FF112233")
        composeRule.onNodeWithText("Set").performClick()
        composeRule.onNodeWithTag("theme-color-reset-Accent").assertDoesNotExist()
    }

    @Test
    fun `the reset icon is absent until a role is overridden`() {
        composeRule.setContent {
            AppThemeEditor(
                AppTheme(),
                customThemes = emptyList(),
                onChange = {},
                onAddCustomTheme = {},
                onRemoveCustomTheme = {},
            )
        }

        composeRule.onNodeWithTag("add-custom-theme").performClick()

        composeRule.onNodeWithTag("theme-color-reset-Accent").assertDoesNotExist()
    }

    @Test
    fun `the add card appears before the built-in and saved presets`() {
        composeRule.setContent {
            AppThemeEditor(
                AppTheme(),
                customThemes = emptyList(),
                onChange = {},
                onAddCustomTheme = {},
                onRemoveCustomTheme = {},
            )
        }

        val addLeft = composeRule.onNodeWithTag("add-custom-theme").getUnclippedBoundsInRoot().left
        val firstPresetLeft = composeRule.onNodeWithTag("seed-swatch-FF6750A4").getUnclippedBoundsInRoot().left

        assertTrue(addLeft < firstPresetLeft)
    }

    @Test
    fun `tapping Done creates a new saved theme carrying the dialog's final edits`() {
        var theme by mutableStateOf(AppTheme())
        var customThemes by mutableStateOf<List<SavedCustomTheme>>(emptyList())
        composeRule.setContent {
            AppThemeEditor(
                theme,
                customThemes = customThemes,
                onChange = { theme = it },
                onAddCustomTheme = { customThemes = customThemes + it },
                onRemoveCustomTheme = { id -> customThemes = customThemes.filterNot { it.id == id } },
            )
        }

        composeRule.onNodeWithTag("add-custom-theme").performClick()
        composeRule.onNodeWithTag("theme-color-Accent").performClick()
        composeRule.onNodeWithTag("color-picker-hex").performTextClearance()
        composeRule.onNodeWithTag("color-picker-hex").performTextInput("#FF445566")
        composeRule.onNodeWithText("Set").performClick()
        composeRule.onNodeWithText("Done").performClick()

        assertEquals(1, customThemes.size)
        assertEquals(0xFF445566L, customThemes[0].overrides.accent)

        val newId = customThemes.single().id
        composeRule.onNodeWithTag("saved-theme-$newId").assertExists()
        composeRule.onNodeWithTag("saved-theme-$newId").performClick()
    }

    @Test
    fun `tapping a saved tile applies its seed and overrides`() {
        val saved = SavedCustomTheme(
            id = "abc",
            seedColor = 0xFF445566L,
            overrides = AppPaletteOverrides(accent = 0xFF112233L),
        )
        var theme by mutableStateOf(AppTheme())
        composeRule.setContent {
            AppThemeEditor(
                theme,
                customThemes = listOf(saved),
                onChange = { theme = it },
                onAddCustomTheme = {},
                onRemoveCustomTheme = {},
            )
        }

        composeRule.onNodeWithTag("saved-theme-abc").performClick()

        assertEquals(0xFF445566L, theme.seedColor)
        assertEquals(0xFF112233L, theme.overrides.accent)
    }

    @Test
    fun `long-pressing a saved tile shows a Remove action that removes it`() {
        val saved = SavedCustomTheme(id = "abc", seedColor = 0xFF445566L)
        var customThemes by mutableStateOf(listOf(saved))
        composeRule.setContent {
            AppThemeEditor(
                AppTheme(),
                customThemes = customThemes,
                onChange = {},
                onAddCustomTheme = {},
                onRemoveCustomTheme = { id -> customThemes = customThemes.filterNot { it.id == id } },
            )
        }

        composeRule.onNodeWithTag("saved-theme-abc").performTouchInput {
            down(center)
            advanceEventTime(600L)
            up()
        }
        composeRule.onNodeWithTag("saved-theme-remove-abc").performClick()

        assertTrue(customThemes.isEmpty())
    }

    @Test
    fun `removing the currently-active saved theme does not change the live theme`() {
        val saved = SavedCustomTheme(
            id = "abc",
            seedColor = 0xFF445566L,
            overrides = AppPaletteOverrides(accent = 0xFF112233L),
        )
        var theme by mutableStateOf(AppTheme(seedColor = saved.seedColor, overrides = saved.overrides))
        var customThemes by mutableStateOf(listOf(saved))
        composeRule.setContent {
            AppThemeEditor(
                theme,
                customThemes = customThemes,
                onChange = { theme = it },
                onAddCustomTheme = {},
                onRemoveCustomTheme = { id -> customThemes = customThemes.filterNot { it.id == id } },
            )
        }

        composeRule.onNodeWithTag("saved-theme-abc").performTouchInput {
            down(center)
            advanceEventTime(600L)
            up()
        }
        composeRule.onNodeWithTag("saved-theme-remove-abc").performClick()

        assertEquals(saved.seedColor, theme.seedColor)
        assertEquals(saved.overrides, theme.overrides)
        assertTrue(customThemes.isEmpty())
    }

    @Test
    fun `two saved themes sharing a seed colour each get an independent tile`() {
        val first = SavedCustomTheme(id = "one", seedColor = 0xFF2E7D32L)
        val second = SavedCustomTheme(id = "two", seedColor = 0xFF2E7D32L)
        var customThemes by mutableStateOf(listOf(first, second))
        composeRule.setContent {
            AppThemeEditor(
                AppTheme(),
                customThemes = customThemes,
                onChange = {},
                onAddCustomTheme = {},
                onRemoveCustomTheme = { id -> customThemes = customThemes.filterNot { it.id == id } },
            )
        }

        composeRule.onNodeWithTag("saved-theme-one").assertExists()
        composeRule.onNodeWithTag("saved-theme-two").assertExists()

        composeRule.onNodeWithTag("saved-theme-one").performTouchInput {
            down(center)
            advanceEventTime(600L)
            up()
        }
        composeRule.onNodeWithTag("saved-theme-remove-one").performClick()

        assertEquals(listOf(second), customThemes)
        composeRule.onNodeWithTag("saved-theme-one").assertDoesNotExist()
        composeRule.onNodeWithTag("saved-theme-two").assertExists()
    }

    @Test
    fun `pressing Set without changing the colour does not create an override`() {
        var theme by mutableStateOf(AppTheme())
        composeRule.setContent {
            AppThemeEditor(
                theme,
                customThemes = emptyList(),
                onChange = { theme = it },
                onAddCustomTheme = {},
                onRemoveCustomTheme = {},
            )
        }

        composeRule.onNodeWithTag("add-custom-theme").performClick()
        composeRule.onNodeWithTag("theme-color-Accent").performClick()
        composeRule.onNodeWithText("Set").performClick()

        assertNull(theme.overrides.accent)
    }

    @Test
    fun `tapping the label text also opens the picker`() {
        composeRule.setContent {
            AppThemeEditor(
                AppTheme(),
                customThemes = emptyList(),
                onChange = {},
                onAddCustomTheme = {},
                onRemoveCustomTheme = {},
            )
        }

        composeRule.onNodeWithTag("add-custom-theme").performClick()
        composeRule.onNodeWithText("Accent").performClick()

        composeRule.onNodeWithTag("color-picker-hex").assertExists()
    }
}
