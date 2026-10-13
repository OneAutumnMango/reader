package dev.reader.app.ui.export

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ImportExportScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `clicking export triggers the callback`() {
        var exported = false
        composeRule.setContent {
            ImportExportScreen(status = "", onExport = { exported = true }, onImport = {})
        }

        composeRule.onNodeWithText("Export library").performClick()

        assertTrue(exported)
    }

    @Test
    fun `clicking import triggers the callback`() {
        var imported = false
        composeRule.setContent {
            ImportExportScreen(status = "", onExport = {}, onImport = { imported = true })
        }

        composeRule.onNodeWithText("Import library").performClick()

        assertTrue(imported)
    }

    @Test
    fun `shows the status message`() {
        composeRule.setContent {
            ImportExportScreen(status = "Exported 4 books", onExport = {}, onImport = {})
        }

        composeRule.onNodeWithText("Exported 4 books").assertExists()
    }
}
