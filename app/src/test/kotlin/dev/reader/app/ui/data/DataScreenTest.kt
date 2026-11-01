package dev.reader.app.ui.data

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.reader.data.export.ExportScope
import dev.reader.data.store.AutomaticBackupFrequency
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
    private var automaticBackupsEnabled = false
    private var backupFolderName: String? = null
    private var backupFrequency = AutomaticBackupFrequency.DAILY
    private var automaticIncludeBooks = false

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
            automaticBackupsEnabled = automaticBackupsEnabled,
            onAutomaticBackupsEnabledChange = { automaticBackupsEnabled = it },
            automaticBackupFolderName = backupFolderName,
            onChooseAutomaticBackupFolder = { backupFolderName = "Reader backups" },
            automaticBackupFrequency = backupFrequency,
            onAutomaticBackupFrequencyChange = { backupFrequency = it },
            automaticBackupIncludeBooks = automaticIncludeBooks,
            onAutomaticBackupIncludeBooksChange = { automaticIncludeBooks = it },
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

    @Test
    fun `automatic backups cannot be enabled before choosing a folder`() {
        setContent()
        composeRule.onNodeWithTag("automatic-backup-enabled").performScrollTo().performClick()
        assertFalse(automaticBackupsEnabled)

        composeRule.onNodeWithTag("automatic-backup-folder").performScrollTo().performClick()
        assertEquals("Reader backups", backupFolderName)
    }

    @Test
    fun `automatic backup frequency and book file inclusion can be selected`() {
        backupFrequency = AutomaticBackupFrequency.DAILY
        automaticIncludeBooks = false
        composeRule.setContent {
            DataScreen(
                status = "",
                busy = false,
                onExport = {},
                onImport = {},
                includeBooksInBackup = false,
                onIncludeBooksInBackupChange = {},
                onCreateBackup = {},
                onRestoreBackup = {},
                onDanger = {},
                automaticBackupsEnabled = true,
                automaticBackupFolderName = "Reader backups",
                automaticBackupFrequency = backupFrequency,
                onAutomaticBackupFrequencyChange = { backupFrequency = it },
                automaticBackupIncludeBooks = automaticIncludeBooks,
                onAutomaticBackupIncludeBooksChange = { automaticIncludeBooks = it },
            )
        }
        composeRule.onNodeWithTag("automatic-backup-frequency-WEEKLY").performScrollTo().performClick()
        composeRule.onNodeWithTag("automatic-backup-include-books").performScrollTo().performClick()
        assertEquals(AutomaticBackupFrequency.WEEKLY, backupFrequency)
        assertTrue(automaticIncludeBooks)
    }
}

@RunWith(RobolectricTestRunner::class)
class DangerConfirmDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `needs two explicit confirmations before it runs`() {
        var confirmed = false
        composeRule.setContent {
            DangerConfirmDialog(DangerAction.REMOVE_ALL_BOOKS, onConfirm = { confirmed = true }, onDismiss = {})
        }

        composeRule.onNodeWithText(DangerAction.REMOVE_ALL_BOOKS.consequence).assertExists()
        composeRule.onNodeWithTag("danger-continue").performClick()
        assertFalse(confirmed)

        composeRule.onNodeWithText(DangerAction.REMOVE_ALL_BOOKS.finalWarning).assertExists()
        composeRule.onNodeWithTag("danger-confirm").performClick()

        assertTrue(confirmed)
    }
}
