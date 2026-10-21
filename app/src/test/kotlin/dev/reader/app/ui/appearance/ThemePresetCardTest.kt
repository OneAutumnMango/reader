package dev.reader.app.ui.appearance

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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

    @Test
    fun `long-pressing a preset card triggers its onLongClick`() {
        var longClicked = false
        composeRule.setContent {
            ThemePresetCard(
                seedColor = 0xFF2E7D32L,
                mode = AppThemeMode.LIGHT,
                systemInDarkTheme = false,
                selected = false,
                onClick = {},
                onLongClick = { longClicked = true },
            )
        }

        composeRule.onNodeWithTag("seed-swatch-FF2E7D32").performTouchInput {
            down(center)
            advanceEventTime(600L)
            up()
        }

        assertTrue(longClicked)
    }

    @Test
    fun `a card with no onLongClick does not crash on long-press`() {
        composeRule.setContent {
            ThemePresetCard(seedColor = 0xFF2E7D32L, mode = AppThemeMode.LIGHT, systemInDarkTheme = false, selected = false, onClick = {})
        }

        composeRule.onNodeWithTag("seed-swatch-FF2E7D32").performTouchInput {
            down(center)
            advanceEventTime(600L)
            up()
        }
        // No assertion beyond "this didn't throw" — the point is the null onLongClick path is safe.
    }

    @Test
    fun `a custom testTag overrides the default seed-based one`() {
        composeRule.setContent {
            ThemePresetCard(
                seedColor = 0xFF2E7D32L,
                mode = AppThemeMode.LIGHT,
                systemInDarkTheme = false,
                selected = false,
                onClick = {},
                testTag = "saved-theme-abc",
            )
        }

        composeRule.onNodeWithTag("saved-theme-abc").assertExists()
        composeRule.onAllNodesWithTag("seed-swatch-FF2E7D32").assertCountEquals(0)
    }
}
