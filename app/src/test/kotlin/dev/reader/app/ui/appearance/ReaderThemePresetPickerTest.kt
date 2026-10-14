package dev.reader.app.ui.appearance

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.reader.core.theme.ReaderTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ReaderThemePresetPickerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val presets = listOf(
        ReaderTheme(name = "Default"),
        ReaderTheme(name = "Sepia"),
        ReaderTheme(name = "Night"),
    )
    private var selected: String? = null
    private var saved: String? = null
    private var deleted: String? = null

    private fun setContent() = composeRule.setContent {
        ReaderThemePresetPicker(
            presets = presets,
            activeName = "Default",
            onSelect = { selected = it },
            onSaveAsNew = { saved = it },
            onDelete = { deleted = it },
        )
    }

    @Test
    fun `opens like the font picker and selects a preset`() {
        setContent()

        composeRule.onNodeWithTag("theme-preset-dropdown").performClick()
        composeRule.onNodeWithTag("theme-preset-Sepia").performClick()

        assertEquals("Sepia", selected)
    }

    @Test
    fun `adds a new preset by name and rejects duplicates`() {
        setContent()

        composeRule.onNodeWithTag("theme-preset-dropdown").performClick()
        composeRule.onNodeWithTag("theme-preset-add").performClick()
        composeRule.onNodeWithTag("theme-preset-name").performTextInput("Night")
        composeRule.onNodeWithTag("theme-preset-save").assertIsNotEnabled()
        composeRule.onNodeWithTag("theme-preset-name").performTextInput(" owl")
        composeRule.onNodeWithTag("theme-preset-save").performClick()

        assertEquals("Night owl", saved)
    }

    @Test
    fun `deletes a preset from the menu`() {
        setContent()

        composeRule.onNodeWithTag("theme-preset-dropdown").performClick()
        composeRule.onNodeWithTag("theme-preset-delete-Night").performClick()

        assertEquals("Night", deleted)
    }
}
