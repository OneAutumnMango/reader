package dev.reader.app.ui.sync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import dev.reader.core.sync.CategoriesValue
import dev.reader.core.sync.ItemKind
import dev.reader.data.sync.ApplyResult
import dev.reader.data.sync.Pick
import dev.reader.data.sync.SyncReport
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w400dp-h2000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real text layout, to see whether text is cut off
class MergeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val applied = mutableListOf<List<Pick>>()
    private var onDone: ((ApplyResult) -> Unit)? = null
    private var checks = 0
    private var checksAgain = 0
    private var state by mutableStateOf(SyncUiState())

    private val dune = question("Dune", position(4, 0.5f), position(1, 0.25f) to listOf("tab"))

    private fun stateOf(vararg questions: dev.reader.data.sync.Question) = SyncUiState(
        folderName = "Shared",
        deviceId = "me",
        report = SyncReport(
            questions = questions.toList(),
            deviceNames = mapOf("me" to "Phone", "tab" to "Tablet", "pc" to "Laptop"),
        ),
        questionCount = questions.size,
    )

    private fun setContent(initial: SyncUiState, oneCategory: Boolean = false, checked: Boolean = true) {
        state = initial
        composeRule.setContent {
            MergeScreen(
                state,
                oneCategory = oneCategory,
                categoryNames = listOf("Local only"),
                checked = checked,
                loadBook = { null },
                onApply = { picks, done ->
                    applied += picks
                    onDone = done
                },
                onCheck = { checks++ },
                onCheckAgain = { checksAgain++ },
            )
        }
    }

    private fun node(text: String) = composeRule.run {
        onNodeWithTag("merge-list").performScrollToNode(hasText(text))
        onNodeWithText(text)
    }

    private fun click(text: String) {
        composeRule.onNodeWithText(text).performClick()
    }

    private fun absent(text: String) = composeRule.onNodeWithText(text).assertDoesNotExist()

    /** A row is one node holding each of its lines. */
    private fun row(vararg lines: String) = composeRule.run {
        val matcher = lines.map { hasText(it) }.reduce { all, next -> all and next }
        onNodeWithTag("merge-list").performScrollToNode(matcher)
        onNode(matcher)
    }

    private fun tabletChoice() = row("Tablet", "Ch. 2, 25%, not finished")

    @Test
    fun `nothing applies before Apply`() {
        setContent(stateOf(dune))
        click("Review and apply (0)")
        absent("Apply")

        tabletChoice().performClick()
        click("Review and apply (1)")

        composeRule.onNodeWithText("Go backwards: 1").assertExists()
        assertEquals(emptyList(), applied)
        click("Apply")
        assertEquals(listOf(listOf(Pick(dune, dune.choices[1].item.value))), applied)
    }

    @Test
    fun `decide later leaves item unpicked`() {
        setContent(stateOf(dune))
        node("Decide later").assertIsSelected()

        tabletChoice().performClick().assertIsSelected()
        node("Decide later").assertIsNotSelected().performClick()

        composeRule.onNodeWithText("Review and apply (0)").assertIsNotEnabled()
    }

    @Test
    fun `combine hidden in one category mode`() {
        setContent(
            stateOf(question("Dune", categories("a"), categories("b") to listOf("tab"))),
            oneCategory = true,
        )

        node("Choose manually").performClick()
        absent("Combine")
        composeRule.onNodeWithText("Local only").performClick()
        composeRule.onNode(hasText("b") and isToggleable()).performClick()
        click("Use these")
        click("Review and apply (1)")
        click("Apply")

        assertEquals(categories("b"), applied.single().single().value)
    }

    @Test
    fun `combine is offered otherwise and removals are marked`() {
        setContent(stateOf(question("Dune", categories("a"), categories("b") to listOf("tab"))))

        row("Tablet", "b", "Adds: b", "Removes: a").assertIsEnabled()
        row("Combine", "a, b", "Adds: b").performClick()

        composeRule.onNodeWithText("Review and apply (1)").assertIsEnabled()
    }

    @Test
    fun `a set of two cannot be picked in one category mode`() {
        setContent(
            stateOf(question("Dune", categories("a"), categories("b", "c") to listOf("tab"))),
            oneCategory = true,
        )

        row("Tablet", "b, c", "Adds: b, c", "Removes: a", "Not allowed: one category per book is on")
            .assertIsNotEnabled()
    }

    @Test
    fun `three choices are all shown with device names`() {
        setContent(
            stateOf(
                question(
                    "Dune",
                    position(4, 0.5f),
                    position(1, 0.25f) to listOf("tab"),
                    position(7, 1f, finished = true) to listOf("pc", "me"),
                ),
            ),
        )

        row("This device", "Ch. 5, 50%, not finished").assertExists()
        tabletChoice().assertExists()
        row("Laptop, This device (another copy)", "Ch. 8, 100%, finished").assertExists()
    }

    @Test
    fun `a group is answered at once or expanded`() {
        val chapter = question(
            "Dune",
            position(2),
            position(2, 0.5f) to listOf("tab"),
            kind = ItemKind.CHAPTER,
            chapter = 2,
        )
        setContent(stateOf(dune, chapter))
        absent("Tablet")

        node("Use Tablet for this book").performClick()
        composeRule.onNodeWithText("Review and apply (2)").assertIsEnabled()
        node("Show each item").performClick()

        tabletChoice().assertIsSelected()
    }

    @Test
    fun `apply is disabled while running and picks stay`() {
        setContent(stateOf(dune))
        tabletChoice().performClick()
        click("Review and apply (1)")

        state = state.copy(running = true)

        composeRule.onNodeWithText("Apply").assertIsNotEnabled()
    }

    @Test
    fun `a failed apply keeps the picks and shows the reason`() {
        setContent(stateOf(dune))
        tabletChoice().performClick()
        click("Review and apply (1)")
        click("Apply")

        composeRule.runOnIdle { onDone!!(ApplyResult(emptyList(), listOf(dune), "offline")) }

        composeRule.onNodeWithText("Could not finish applying: offline. Check the list.").assertExists()
        click("OK")
        composeRule.onNodeWithText("Review and apply (1)").assertIsEnabled()
        assertEquals(1, checks)
    }

    @Test
    fun `a stale pick is dropped and listed again without retrying`() {
        setContent(stateOf(dune))
        tabletChoice().performClick()
        click("Review and apply (1)")
        click("Apply")

        composeRule.runOnIdle { onDone!!(ApplyResult(emptyList(), listOf(dune))) }
        click("OK")

        node("Decide later").assertIsSelected()
        assertEquals(1, applied.size)
        assertEquals(1, checks)
    }

    @Test
    fun `picks are dropped when the item changes or this install is reset`() {
        setContent(stateOf(dune))
        tabletChoice().performClick()

        state = state.copy(deviceId = "new")
        composeRule.onNodeWithText("Review and apply (0)").assertExists()

        tabletChoice().performClick()
        state = state.copy(
            report = state.report.copy(
                questions = listOf(question("Dune", position(6), position(1, 0.25f) to listOf("tab"))),
            ),
        )
        composeRule.onNodeWithText("Review and apply (0)").assertExists()
        composeRule.onNodeWithText("1 pick was dropped because the item changed.").assertExists()
    }

    @Test
    fun `an apply discarded by a reset still says why`() {
        setContent(stateOf(dune))
        tabletChoice().performClick()
        click("Review and apply (1)")
        click("Apply")

        composeRule.runOnIdle {
            state = state.copy(deviceId = "new", report = SyncReport())
            onDone!!(ApplyResult(emptyList(), listOf(dune), DISCARDED))
        }

        composeRule.onNodeWithText(DISCARDED).assertExists()
    }

    @Test
    fun `a peer cannot pose as this device`() {
        val posing = stateOf(dune).let {
            it.copy(report = it.report.copy(deviceNames = mapOf("tab" to "This device")))
        }
        setContent(posing)

        row("[tab] This device", "Ch. 2, 25%, not finished").assertExists()
    }

    private fun assertShownInFull(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNode(hasText(text), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.single().lineCount > 2, "expected a long text")
        assertFalse(layouts.single().hasVisualOverflow, "cut off: $text")
    }

    @Test
    fun `no removal is cut off however many and long the names`() {
        val kept = List(25) { "Shelf number $it ${"long ".repeat(12)}" }.map { it.trim() }
        val local = CategoriesValue(kept + "Last one")
        setContent(stateOf(question("Dune", local, CategoriesValue(listOf("New")) to listOf("tab"))))
        val everything = local.names.joinToString()
        val removes = "Removes: $everything"

        row("Tablet", "New", "Adds: New", removes).performClick()
        assertShownInFull(removes)

        click("Review and apply (1)")
        composeRule.onNode(hasText(removes)).assertExists()
        composeRule.onNode(hasText("Was: $everything", substring = true)).assertExists()
    }

    @Test
    fun `the manual dialog shows every removal`() {
        val local = CategoriesValue(List(25) { "c$it" })
        setContent(stateOf(question("Dune", local, categories("New") to listOf("tab"))))

        node("Choose manually").performClick()
        composeRule.onNode(isToggleable() and hasText("c0")).performClick()
        composeRule.onNodeWithTag("manual-list").performScrollToNode(hasText("Removes: c0"))
        composeRule.onNodeWithText("Chosen: ${local.names.drop(1).joinToString()}").assertExists()
    }

    @Test
    fun `ticking does not move the manual dialog's checkboxes`() {
        setContent(stateOf(question("Dune", categories("a"), categories("b") to listOf("tab"))))
        node("Choose manually").performClick()
        val box = composeRule.onNode(isToggleable() and hasText("b"))
        val before = box.getUnclippedBoundsInRoot().top

        box.performClick()

        assertEquals(before, box.getUnclippedBoundsInRoot().top)
    }

    @Test
    fun `an unpickable starting set must be changed before it is used`() {
        setContent(
            stateOf(question("Dune", categories("a", "b"), categories("c") to listOf("tab"))),
            oneCategory = true,
        )

        node("Choose manually").performClick()
        composeRule.onNodeWithText("Use these").assertIsNotEnabled()
        composeRule.onNode(isToggleable() and hasText("a")).performClick()
        composeRule.onNodeWithText("Use these").assertIsEnabled()
    }

    @Test
    fun `bulk buttons act only on the filtered list`() {
        val emma = question("Emma", position(4, 0.5f), position(1, 0.25f) to listOf("tab"))
        setContent(stateOf(dune, emma))
        composeRule.onNodeWithText("Search by title").performTextInput("emm")

        node("Use all from Tablet").performClick()

        composeRule.onNodeWithText("Picked 1 of 1 listed.").assertExists()
        click("Review and apply (1)")
        click("Apply")
        assertEquals(listOf(emma), applied.single().map { it.question })
    }

    @Test
    fun `the bulk notice is announced and cleared by later changes`() {
        setContent(stateOf(dune))
        val notice = "Picked 1 of 1 listed."
        fun bulk() {
            node("Keep all from this device").performClick()
            composeRule.onNodeWithText(notice)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        }

        bulk()
        node("Positions").performClick()
        absent(notice)
        bulk()
        node("Search by title").performTextInput("D")
        absent(notice)
        bulk()
        node("Decide later").performClick()
        absent(notice)
    }

    @Test
    fun `a collapsed group says what differs`() {
        fun chapter(index: Int) = question(
            "Dune",
            position(index),
            position(index, 0.5f) to listOf("tab"),
            kind = ItemKind.CHAPTER,
            chapter = index,
        )
        setContent(stateOf(dune, chapter(2), chapter(6)))

        node("Reading position: Ch. 5, 50% / Ch. 2, 25%. Chapters: 3, 7")
    }

    @Test
    fun `a failed check shows the error instead of nothing to merge`() {
        setContent(SyncUiState(folderName = "Shared", error = "Folder is missing"))

        node("Folder is missing")
        absent("Nothing to merge.")
        node("Check again").performClick()
        assertEquals(1, checksAgain)
        assertEquals(0, checks)
    }

    @Test
    fun `says it is checking until the check ends`() {
        setContent(SyncUiState(folderName = "Shared"), checked = false)

        node("Checking for differences…")
        absent("Nothing to merge.")
    }
}
