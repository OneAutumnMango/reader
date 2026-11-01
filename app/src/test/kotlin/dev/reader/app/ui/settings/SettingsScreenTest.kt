package dev.reader.app.ui.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertContentEquals

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val clickedDestinations = mutableListOf<String>()

    private fun setContent() = composeRule.setContent {
        SettingsScreen(
            onAppearanceClick = { clickedDestinations += "appearance" },
            onReaderClick = { clickedDestinations += "reader" },
            onLibraryClick = { clickedDestinations += "library" },
            onSearchClick = { clickedDestinations += "search" },
            onDataClick = { clickedDestinations += "data" },
        )
    }

    @Test
    fun `settings links invoke their destination callbacks`() {
        setContent()

        composeRule.onNodeWithText("App appearance").performScrollTo().performClick()
        composeRule.onNodeWithText("Reader settings").performScrollTo().performClick()
        composeRule.onNodeWithText("Library settings").performScrollTo().performClick()
        composeRule.onNodeWithText("Search settings").performScrollTo().performClick()
        composeRule.onNodeWithText("Backup and reset").performScrollTo().performClick()

        assertContentEquals(listOf("appearance", "reader", "library", "search", "data"), clickedDestinations)
    }

    @Test
    fun `settings hub has no source management or log actions`() {
        setContent()

        composeRule.onAllNodesWithTag("add-book-file").assertCountEquals(0)
        composeRule.onAllNodesWithTag("add-book-folder").assertCountEquals(0)
        composeRule.onAllNodesWithTag("scan-root-folders").assertCountEquals(0)
        composeRule.onAllNodesWithText("View error/log history").assertCountEquals(0)
    }
}
