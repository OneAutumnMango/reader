package dev.reader.app.ui.appearance

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.reader.core.theme.AppThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ThemePresetCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `clicking a preset card triggers its callback and carries the seed-swatch tag`() {
        var clicked = false
        composeRule.setContent {
            ThemePresetCard(
                seedColor = 0xFF2E7D32L,
                mode = AppThemeMode.LIGHT,
                systemInDarkTheme = false,
                selected = false,
                onClick = { clicked = true },
            )
        }

        composeRule.onNodeWithTag("seed-swatch-FF2E7D32").performClick()

        assertTrue(clicked)
    }

    @Test
    fun `clicking the add-custom-theme card triggers its callback`() {
        var clicked = false
        composeRule.setContent { AddCustomThemeCard(onClick = { clicked = true }) }

        composeRule.onNodeWithTag("add-custom-theme").performClick()

        assertTrue(clicked)
    }
}
