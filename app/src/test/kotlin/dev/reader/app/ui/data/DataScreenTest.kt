package dev.reader.app.ui.data

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import dev.reader.data.export.ExportScope
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DataScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var exported: Set<ExportScope>? = null
    private var includeBooks = false
    private var danger: DangerAction? = null

    private fun setContent() = composeRule.setContent {
        DataScreen(
            status = "",
            busy = false,
            onExport = { exported = it },
            onImport = {},
            includeBooksInBackup = includeBooks,
            onIncludeBooksInBackupChange = { includeBooks = it },
            onCreateBackup = {},
            onRestoreBackup = {},
            onDanger = { danger = it },
        )
    }

    @Test
    fun `export everything and partial exports report their scopes`() {
        setContent()

        composeRule.onNodeWithTag("export-everything").performClick()
        assertEquals(ExportScope.entries.toSet(), exported)

        composeRule.onNodeWithTag("export-STATS").performScrollTo().performClick()
        assertEquals(setOf(ExportScope.STATS), exported)
    }

    @Test
    fun `book files are off by default in backups and can be turned on`() {
        setContent()
        assertFalse(includeBooks)

        composeRule.onNodeWithTag("backup-include-books").performScrollTo().performClick()

        assertTrue(includeBooks)
    }

    @Test
    fun `danger buttons report their action`() {
        setContent()

        composeRule.onNodeWithTag("danger-RESET_STATS").performScrollTo().performClick()

        assertEquals(DangerAction.RESET_STATS, danger)
    }
}

@RunWith(RobolectricTestRunner::class)
class DangerConfirmDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `needs a continue and the typed confirmation word before it runs`() {
        var confirmed = false
        composeRule.setContent {
            DangerConfirmDialog(DangerAction.REMOVE_ALL_BOOKS, onConfirm = { confirmed = true }, onDismiss = {})
        }

        composeRule.onNodeWithTag("danger-continue").performClick()
        composeRule.onNodeWithTag("danger-confirm").assertIsNotEnabled()
        composeRule.onNodeWithTag("danger-confirm-field").performTextInput("delete")
        composeRule.onNodeWithTag("danger-confirm").assertIsNotEnabled()
        assertFalse(confirmed)

        composeRule.onNodeWithTag("danger-confirm-field").performTextClearance()
        composeRule.onNodeWithTag("danger-confirm-field").performTextInput(DANGER_CONFIRM_WORD)
        composeRule.onNodeWithTag("danger-confirm").assertIsEnabled().performClick()

        assertTrue(confirmed)
    }
}
