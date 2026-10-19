package dev.reader.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibrarySettingsTabsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `library sources categories and display are subsections`() {
        composeRule.setContent {
            var selected by remember { mutableIntStateOf(0) }
            LibrarySettingsTabs(selected, onSelect = { selected = it })
        }

        composeRule.onNodeWithTag("library-settings-tab-0").assertIsSelected()
        composeRule.onNodeWithTag("library-settings-tab-1").performClick().assertIsSelected()
        composeRule.onNodeWithTag("library-settings-tab-2").performClick().assertIsSelected()
    }
}
