package dev.reader.app.ui.sync

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import dev.reader.core.sync.AskReason
import dev.reader.core.sync.CategoriesValue
import dev.reader.core.sync.ItemKey
import dev.reader.core.sync.ItemKind
import dev.reader.core.sync.PositionValue
import dev.reader.core.sync.Versioned
import dev.reader.data.sync.Change
import dev.reader.data.sync.DeviceInfo
import dev.reader.data.sync.Question
import dev.reader.data.sync.RejectedFile
import dev.reader.data.sync.SkippedBook
import dev.reader.data.sync.SyncReport
import dev.reader.data.sync.UndoResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

private const val EXPLANATION = "Online Sync uses a folder that an app such as Syncthing keeps in step " +
    "between your devices. Reader doesn't send the files itself."

private val KEY = ItemKey("hash", ItemKind.BOOK_POSITION)

private val CHANGE = Change(
    KEY,
    "Dune",
    PositionValue(2, 0.1f, false, 0),
    PositionValue(13, 0.62f, false, 0),
    "peer",
)

@RunWith(RobolectricTestRunner::class)
class OnlineSyncScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val names = mutableListOf<String>()
    private var undos = 0

    private fun setContent(state: SyncUiState) = composeRule.setContent {
        OnlineSyncScreen(
            state,
            onChooseFolder = {},
            onDeviceName = { names += it },
            onSyncNow = {},
            onMerge = {},
            onUndo = { undos++ },
        )
    }

    private fun node(text: String) = composeRule.run {
        onNodeWithTag("online-sync-list").performScrollToNode(hasText(text))
        onNodeWithText(text)
    }

    private fun assertAbsent(text: String) = composeRule.onNodeWithText(text).assertDoesNotExist()

    @Test
    fun `without a folder it explains, offers to choose one and cannot sync`() {
        setContent(SyncUiState(deviceName = "Phone"))

        node(EXPLANATION).assertIsDisplayed()
        node("Choose folder").assertIsEnabled()
        node("Sync now").assertIsNotEnabled()
        assertAbsent("Change folder")
        assertAbsent("Undo last apply")
    }

    @Test
    fun `with a folder it shows its name and can change it or sync`() {
        setContent(SyncUiState(folderName = "Shared", deviceName = "Phone", questionCount = 3))

        node(EXPLANATION).assertIsDisplayed()
        node("Folder: Shared").assertIsDisplayed()
        node("Change folder").assertIsEnabled()
        node("Sync now").assertIsEnabled()
        node("Merge (3)").assertIsEnabled()
    }

    @Test
    fun `merge is disabled without questions`() {
        setContent(SyncUiState(folderName = "Shared"))

        node("Merge (0)").assertIsNotEnabled()
    }

    @Test
    fun `running disables every control`() {
        setContent(
            SyncUiState(
                folderName = "Shared",
                deviceName = "Phone",
                running = true,
                questionCount = 1,
                lastChanges = listOf(CHANGE),
            ),
        )

        node("Change folder").assertIsNotEnabled()
        node("Sync now").assertIsNotEnabled()
        node("Merge (1)").assertIsNotEnabled()
        node("Device name").assertIsNotEnabled()
        node("Undo last apply").assertIsNotEnabled()
    }

    @Test
    fun `the device name is saved only when asked`() {
        setContent(SyncUiState(deviceName = "Phone"))
        node("Save name").assertIsNotEnabled()

        composeRule.onNodeWithText("Phone").performTextReplacement("Tablet")

        assertEquals(emptyList(), names)
        node("Save name").performClick()
        assertEquals(listOf("Tablet"), names)
    }

    @Test
    fun `shows rejected files, skipped books, pending items and the error`() {
        setContent(
            SyncUiState(
                folderName = "Shared",
                error = "Could not open reader-sync-me-1.json",
                report = SyncReport(
                    rejected = listOf(RejectedFile("reader-sync-bad-2.json", "File is too large")),
                    skipped = listOf(SkippedBook("Emma", "Chapters don't match")),
                    questions = listOf(
                        Question(KEY, "Dracula", AskReason.BACKWARDS, emptyList(), Versioned(null)),
                    ),
                ),
            ),
        )

        node("Could not open reader-sync-me-1.json").assertIsDisplayed()
        node("reader-sync-bad-2.json: File is too large").assertIsDisplayed()
        node("Emma: Chapters don't match").assertIsDisplayed()
        node("Dracula: Reading position").assertIsDisplayed()
    }

    @Test
    fun `labels conflict copies, including one of this device`() {
        setContent(
            SyncUiState(
                folderName = "Shared",
                deviceId = "me",
                report = SyncReport(
                    devices = listOf(
                        DeviceInfo("peer", "Tablet", 0, conflictCopy = true),
                        DeviceInfo("me", "Phone", 0, conflictCopy = true),
                    ),
                ),
            ),
        )

        node("Tablet (conflict copy)").assertIsDisplayed()
        node("This device (another copy) (conflict copy)").assertIsDisplayed()
    }

    @Test
    fun `shows each applied change and its source`() {
        setContent(
            SyncUiState(
                folderName = "Shared",
                lastChanges = listOf(CHANGE),
                report = SyncReport(deviceNames = mapOf("peer" to "Tablet")),
            ),
        )

        node("Dune: Reading position").assertIsDisplayed()
        node("Was: Ch. 3, 10%").assertIsDisplayed()
        node("Now: Ch. 14, 62%").assertIsDisplayed()
        node("From Tablet").assertIsDisplayed()
    }

    @Test
    fun `undo asks first`() {
        setContent(SyncUiState(folderName = "Shared", lastChanges = listOf(CHANGE)))

        node("Undo last apply").performClick()
        assertEquals(0, undos)
        composeRule.onNodeWithText("Undo").performClick()
        assertEquals(1, undos)
    }

    @Test
    fun `describes values`() {
        assertEquals("Ch. 14, 62%", describeValue(PositionValue(13, 0.62f, false, 0)))
        assertEquals("Ch. 3, finished", describeValue(PositionValue(2, 1f, true, 0)))
        assertEquals("No position", describeValue(null))
        assertEquals("Reading, Favourites", describeValue(CategoriesValue(listOf("Reading", "Favourites"))))
        assertEquals("No categories", describeValue(CategoriesValue(emptyList())))
    }

    @Test
    fun `describes an undo result`() {
        val one = listOf(CHANGE)
        assertEquals("Restored: 1. Skipped (changed since): 0.", describeUndo(UndoResult(one, emptyList())))
        assertEquals(
            "Restored: 0. Skipped (changed since): 1. Not published: offline",
            describeUndo(UndoResult(emptyList(), one, "offline")),
        )
        assertEquals("Could not undo: gone", describeUndo(UndoResult(emptyList(), emptyList(), "gone")))
    }
}
