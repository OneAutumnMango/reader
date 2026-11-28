package dev.reader.app.ui.sync

import dev.reader.core.sync.AskReason
import dev.reader.core.sync.CategoriesValue
import dev.reader.core.sync.Choice
import dev.reader.core.sync.ItemKey
import dev.reader.core.sync.ItemKind
import dev.reader.core.sync.ItemValue
import dev.reader.core.sync.PositionValue
import dev.reader.core.sync.Versioned
import dev.reader.data.sync.ApplyResult
import dev.reader.data.sync.Pick
import dev.reader.data.sync.Question
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

internal fun position(chapter: Int, fraction: Float = 0f, finished: Boolean = false, updatedAt: Long = 0) =
    PositionValue(chapter, fraction, finished, updatedAt)

internal fun categories(vararg names: String) = CategoriesValue(names.toList())

/** [local] is held by "me"; each of [others] is a value and the devices holding it. */
internal fun question(
    title: String,
    local: ItemValue?,
    vararg others: Pair<ItemValue?, List<String>>,
    kind: ItemKind = if (local is CategoriesValue) ItemKind.CATEGORIES else ItemKind.BOOK_POSITION,
    chapter: Int = -1,
    hash: String = title,
) = Question(
    ItemKey(hash, kind, chapter),
    title,
    AskReason.SEPARATE,
    listOf(Choice(Versioned(local), listOf("me"))) + others.map { Choice(Versioned(it.first), it.second) },
    Versioned(local),
)

class MergeSelectionTest {

    @Test
    fun `useDevice skips items it lacks`() {
        val has = question("A", position(1), position(2) to listOf("tab"))
        val lacks = question("B", position(1), position(2) to listOf("other"))
        // A conflict copy puts one device under two values, so neither is "its" value.
        val twice = question("C", position(1), position(2) to listOf("tab"), position(3) to listOf("tab"))

        val picks = useDevice(listOf(has, lacks, twice), "tab")

        assertEquals(listOf(has), picks.map { it.question })
        assertSame(has.choices[1].item.value, picks.single().value)
    }

    @Test
    fun `useDevice takes this device's value when the device shares it`() {
        val shared = question("A", position(1), position(2) to listOf("other")).let {
            it.copy(choices = listOf(it.choices[0].copy(deviceIds = listOf("me", "tab")), it.choices[1]))
        }

        assertSame(shared.choices[0].item.value, useDevice(listOf(shared), "tab").single().value)
    }

    @Test
    fun `keepMine picks the first choice`() {
        val q = question("A", null, position(2) to listOf("tab"))

        assertEquals(listOf(Pick(q, null)), keepMine(listOf(q)))
    }

    @Test
    fun `keepFurthest ignores categories and picks the furthest`() {
        val later = question("A", position(1, 0.9f), position(2, 0.1f) to listOf("tab"), null to listOf("x"))
        val finished = question("B", position(2, 0.9f), position(2, 0.5f, finished = true) to listOf("tab"))
        val mine = question("C", position(5), position(2) to listOf("tab"))
        val sets = question("D", categories("a"), categories("b") to listOf("tab"))

        val picks = keepFurthest(listOf(later, finished, mine, sets))

        assertEquals(listOf(later, finished, mine), picks.map { it.question })
        assertSame(later.choices[1].item.value, picks[0].value)
        assertSame(finished.choices[1].item.value, picks[1].value)
        assertSame(mine.choices[0].item.value, picks[2].value)
    }

    @Test
    fun `combine unions case-insensitively`() {
        val q = question(
            "A",
            categories("Reading", "Sci-Fi"),
            categories("reading", "Classics") to listOf("tab"),
            categories("SCI-FI ", "classics", "New") to listOf("x"),
        )

        assertEquals(categories("Reading", "Sci-Fi", "Classics", "New"), combinedCategories(q))
    }

    @Test
    fun `summary counts backwards unfinished and lost categories`() {
        val picks = listOf(
            Pick(question("back", position(5)), position(2)),
            Pick(question("unfinish", position(2, 1f, finished = true)), position(2, 0.5f)),
            Pick(question("cleared", position(2)), null),
            Pick(question("forward", position(2)), position(3)),
            Pick(question("keep", position(2)), position(2)),
            Pick(question("lose", categories("a", "b")), categories("A")),
            Pick(question("add", categories("a")), categories("A", "b")),
        )

        assertEquals(ReviewSummary(total = 7, backwards = 3, unfinished = 1, losingCategory = 1), summarize(picks))
    }

    @Test
    fun `bulk acts only on filtered list`() {
        val dune = question("Dune", position(1), position(2) to listOf("tab"))
        val emma = question("Emma", position(1), position(2) to listOf("tab"))
        val duneSets = question("Dune", categories("a"), categories("b") to listOf("tab"))
        val all = listOf(dune, emma, duneSets)

        assertEquals(listOf(dune, duneSets), filterQuestions(all, MergeFilter.ALL, " dUn "))
        assertEquals(listOf(dune, emma), filterQuestions(all, MergeFilter.POSITIONS, ""))
        assertEquals(listOf(duneSets), filterQuestions(all, MergeFilter.CATEGORIES, ""))
        val filtered = filterQuestions(all, MergeFilter.POSITIONS, "dune")
        assertEquals(listOf(dune), useDevice(filtered, "tab").map { it.question })
        assertEquals(listOf(dune), keepMine(filtered).map { it.question })
        assertEquals(listOf(dune), keepFurthest(filtered).map { it.question })
    }

    @Test
    fun `one-category mode never offers two or more`() {
        assertEquals(false, isPickable(categories("a", "b"), oneCategory = true))
        assertEquals(true, isPickable(categories("a"), oneCategory = true))
        assertEquals(true, isPickable(categories("a", "b"), oneCategory = false))
        assertEquals(true, isPickable(null, oneCategory = true))
    }

    @Test
    fun `describes the outcome of an apply`() {
        val stale = question("A", position(1))
        assertEquals("Applied 2 choices.", describeApply(2, ApplyResult(emptyList(), emptyList())))
        assertEquals(
            "Applied 1 choice. Not published: offline",
            describeApply(1, ApplyResult(emptyList(), emptyList(), "offline")),
        )
        assertEquals(
            "Could not finish applying: gone. Check the list.",
            describeApply(1, ApplyResult(emptyList(), listOf(stale), "gone.")),
        )
        assertEquals(
            "Applied 1 choice. Not applied: 1. Any still waiting are listed; check them and choose again.",
            describeApply(2, ApplyResult(emptyList(), listOf(stale))),
        )
        assertEquals(DISCARDED, describeApply(2, ApplyResult(emptyList(), emptyList(), DISCARDED)))
    }
}
