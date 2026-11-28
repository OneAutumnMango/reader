package dev.reader.app.ui.sync

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.reader.core.sync.ItemKey
import dev.reader.core.sync.ItemKind
import dev.reader.core.sync.PositionValue
import dev.reader.data.sync.Change
import dev.reader.data.sync.RejectedFile
import dev.reader.data.sync.SkippedBook
import dev.reader.data.sync.SyncReport
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

private val APPLIED = Change(
    ItemKey("hash", ItemKind.BOOK_POSITION),
    "Dune",
    null,
    PositionValue(1, 0.5f, false, 0),
    "peer",
)
private val SKIPPED = SkippedBook("Dune", "Not in the library")
private val REJECTED = RejectedFile("reader-sync-x.json", "Not valid")

@RunWith(RobolectricTestRunner::class)
class QuickSyncButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()

    private fun setContent(state: SyncUiState) = composeRule.setContent {
        QuickSyncButton(
            state,
            onSync = { calls += "sync" },
            onPullOnly = { calls += "pull" },
            onPushOnly = { calls += "push" },
            onMerge = { calls += "merge" },
            onSettings = { calls += "settings" },
        )
    }

    private val button get() = composeRule.onNodeWithTag("library-sync")

    private fun item(text: String) = composeRule.onNodeWithText(text)

    private fun openMenu() = button.performTouchInput { longClick() }

    @Test
    fun `hidden without a folder`() {
        setContent(SyncUiState())

        button.assertDoesNotExist()
    }

    @Test
    fun `waiting merges are counted on the icon and read out`() {
        setContent(SyncUiState(folderName = "Shared", questionCount = 3))

        button.assertContentDescriptionEquals("Sync, 3 to merge")
        composeRule.onNodeWithText("3", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a large count is capped on the icon only`() {
        setContent(SyncUiState(folderName = "Shared", questionCount = 120))

        button.assertContentDescriptionEquals("Sync, 120 to merge")
        composeRule.onNodeWithText("99+", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `tap syncs once`() {
        setContent(SyncUiState(folderName = "Shared"))

        button.assertContentDescriptionEquals("Sync").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        button.performClick()

        assertEquals(listOf("sync"), calls)
    }

    @Test
    fun `while running it says so, ignores taps and disables pull and push`() {
        setContent(SyncUiState(folderName = "Shared", running = true))

        button.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Syncing"))
        button.performClick()
        openMenu()

        item("Pull only").assertIsNotEnabled()
        item("Push only").assertIsNotEnabled()
        item("Online Sync settings").assertIsEnabled()
        assertEquals(emptyList(), calls)
    }

    @Test
    fun `long press shows the four items and each does its job`() {
        setContent(SyncUiState(folderName = "Shared", questionCount = 2))

        listOf("Pull only", "Push only", "Merge (2)", "Online Sync settings").forEach {
            openMenu()
            item(it).assertIsDisplayed().assertIsEnabled().performClick()
            item(it).assertDoesNotExist()
        }

        assertEquals(listOf("pull", "push", "merge", "settings"), calls)
    }

    @Test
    fun `merge is disabled without questions`() {
        setContent(SyncUiState(folderName = "Shared"))
        openMenu()

        item("Merge (0)").assertIsNotEnabled()
    }

    @Test
    fun `the long press action is labelled`() {
        setContent(SyncUiState(folderName = "Shared"))

        assertEquals("Sync options", button.fetchSemanticsNode().config[SemanticsActions.OnLongClick].label)
    }

    private fun message(report: SyncReport = SyncReport(), questions: Int = 0, error: String? = null) =
        quickSyncMessage(SyncUiState(report = report, questionCount = questions, error = error))

    @Test
    fun `an error comes first`() {
        assertEquals("Folder is gone", message(questions = 2, error = "Folder is gone"))
    }

    @Test
    fun `an error other than the publish error beats it`() {
        val report = SyncReport(applied = listOf(APPLIED), publishError = "Disk full")

        assertEquals(DISCARDED, message(report, error = DISCARDED))
    }

    @Test
    fun `a failed publish after applying says so`() {
        val report = SyncReport(applied = listOf(APPLIED), publishError = "Disk full")

        assertEquals("Changes applied but not published", message(report, error = "Disk full"))
    }

    @Test
    fun `a failed publish with nothing applied shows why`() {
        assertEquals("Disk full", message(SyncReport(publishError = "Disk full"), error = "Disk full"))
    }

    @Test
    fun `counts applied changes`() {
        assertEquals("1 change applied", message(SyncReport(applied = listOf(APPLIED))))
        assertEquals("2 changes applied", message(SyncReport(applied = listOf(APPLIED, APPLIED))))
    }

    @Test
    fun `nothing applied is up to date`() {
        assertEquals("Already up to date", message())
    }

    @Test
    fun `adds waiting questions`() {
        assertEquals("Already up to date · 1 question", message(questions = 1))
        assertEquals("Already up to date · 3 questions", message(questions = 3))
    }

    @Test
    fun `says when books or files were left out`() {
        val expected = "Already up to date · some files or books skipped"

        assertEquals(expected, message(SyncReport(skipped = listOf(SKIPPED))))
        assertEquals(expected, message(SyncReport(rejected = listOf(REJECTED))))
        assertEquals(
            "Already up to date · 2 questions · some files or books skipped",
            message(SyncReport(skipped = listOf(SKIPPED)), questions = 2),
        )
    }

    @Test
    fun `the action is merge for questions, else details for anything left out, else none`() {
        val skipped = SyncReport(skipped = listOf(SKIPPED))

        assertEquals("Merge", quickSyncAction(SyncUiState(report = skipped, questionCount = 1)))
        assertEquals("Details", quickSyncAction(SyncUiState(report = skipped)))
        assertEquals("Details", quickSyncAction(SyncUiState(report = SyncReport(rejected = listOf(REJECTED)))))
        assertEquals(null, quickSyncAction(SyncUiState()))
    }

    @Test
    fun `a failed run offers no action, since what is on show may be old`() {
        assertEquals(null, quickSyncAction(SyncUiState(questionCount = 2, error = "Folder is gone")))
    }
}
