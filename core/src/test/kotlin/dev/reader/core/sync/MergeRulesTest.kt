package dev.reader.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MergeRulesTest {
    private fun pos(chapter: Int, position: Float, finished: Boolean = false, at: Long = 0) =
        PositionValue(chapter, position, finished, at)

    private fun v(vararg counts: Pair<String, Long>): Version = mapOf(*counts)

    private fun cats(vararg names: String) = CategoriesValue(names.toList())

    private fun item(value: ItemValue?, version: Version = emptyMap(), rewound: Boolean = false) =
        Versioned(value, version, rewound)

    private fun from(device: String, value: ItemValue?, version: Version) = Candidate(device, item(value, version))

    private fun decideA(local: Versioned, vararg others: Candidate, oneCategory: Boolean = false) =
        decide("A", local, others.toList(), oneCategory)

    private fun assertAsk(reason: AskReason, decision: Decision): Decision.Ask {
        val ask = assertIs<Decision.Ask>(decision)
        assertEquals(reason, ask.reason)
        assertEquals("A", ask.choices.first().deviceIds.first())
        return ask
    }

    private fun assertApply(device: String, value: ItemValue, decision: Decision) {
        val apply = assertIs<Decision.Apply>(decision)
        assertEquals(device, apply.sourceDeviceId)
        assertEquals(value, apply.item.value)
        assertFalse(apply.item.rewound)
    }

    @Test
    fun `equal values combine versions`() {
        val decision = decideA(item(pos(1, .5f), v("A" to 1L)), from("B", pos(1, .505f), v("B" to 1L)))
        assertEquals(v("A" to 1L, "B" to 1L), assertIs<Decision.Keep>(decision).item.version)
    }

    @Test
    fun `later forward value applies`() {
        val decision = decideA(item(pos(10, 0f), v("A" to 1L)), from("B", pos(14, 0f), v("A" to 1L, "B" to 1L)))
        assertApply("B", pos(14, 0f), decision)
    }

    @Test
    fun `later backward value asks`() {
        val decision = decideA(item(pos(14, 0f), v("A" to 1L)), from("B", pos(10, 0f), v("A" to 1L, "B" to 1L)))
        assertEquals(2, assertAsk(AskReason.BACKWARDS, decision).choices.size)
    }

    @Test
    fun `older value is ignored`() {
        val local = item(pos(14, 0f), v("A" to 2L))
        assertEquals(Decision.Keep(local), decideA(local, from("B", pos(10, 0f), v("A" to 1L))))
    }

    @Test
    fun `unfinishing asks`() {
        val decision = decideA(item(pos(3, 1f, true), v("A" to 1L)), from("B", pos(3, 1f), v("A" to 1L, "B" to 1L)))
        assertAsk(AskReason.BACKWARDS, decision)
    }

    @Test
    fun `newer and further separate position applies`() {
        val decision = decideA(
            item(pos(11, 0f, at = 1), v("A" to 2L, "B" to 1L)),
            from("B", pos(14, 0f, at = 2), v("A" to 1L, "B" to 2L)),
        )
        assertApply("B", pos(14, 0f, at = 2), decision)
    }

    @Test
    fun `newer but behind asks`() {
        val decision = decideA(
            item(pos(14, 0f, at = 1), v("A" to 2L, "B" to 1L)),
            from("B", pos(11, 0f, at = 2), v("A" to 1L, "B" to 2L)),
        )
        assertAsk(AskReason.SEPARATE, decision)
    }

    @Test
    fun `rewound blocks auto resolve`() {
        val decision = decideA(
            item(pos(11, 0f, at = 1), v("A" to 2L, "B" to 1L), rewound = true),
            from("B", pos(14, 0f, at = 2), v("A" to 1L, "B" to 2L)),
        )
        assertAsk(AskReason.SEPARATE, decision)
    }

    @Test
    fun `newer and further local position is kept`() {
        val decision = decideA(
            item(pos(14, 0f, at = 2), v("A" to 2L, "B" to 1L)),
            from("B", pos(11, 0f, at = 1), v("A" to 1L, "B" to 2L)),
        )
        assertEquals(v("A" to 2L, "B" to 2L), assertIs<Decision.Keep>(decision).item.version)
    }

    @Test
    fun `no shared history asks`() {
        val decision = decideA(item(pos(1, 0f, at = 1), v("A" to 1L)), from("B", pos(5, 0f, at = 2), v("B" to 1L)))
        assertAsk(AskReason.FIRST_CONTACT, decision)
    }

    @Test
    fun `untouched chapter takes remote`() {
        val decision = decideA(item(pos(0, 0f)), from("B", pos(0, .4f), v("B" to 1L)))
        assertApply("B", pos(0, .4f), decision)
    }

    @Test
    fun `same version different value asks`() {
        val decision = decideA(item(pos(1, 0f), v("B" to 1L)), from("B", pos(2, 0f), v("B" to 1L)))
        assertAsk(AskReason.SAME_VERSION, decision)
    }

    @Test
    fun `added category applies`() {
        val decision = decideA(
            item(cats("Reading"), v("A" to 1L)),
            from("B", cats("Reading", "Fav"), v("A" to 1L, "B" to 1L)),
        )
        assertApply("B", cats("Reading", "Fav"), decision)
    }

    @Test
    fun `category move asks`() {
        val decision = decideA(
            item(cats("Reading"), v("A" to 1L)),
            from("B", cats("Finished"), v("A" to 1L, "B" to 1L)),
        )
        assertAsk(AskReason.REMOVES_CATEGORY, decision)
    }

    @Test
    fun `one category mode asks for two`() {
        val decision = decideA(
            item(cats("Reading"), v("A" to 1L)),
            from("B", cats("Reading", "Fav"), v("A" to 1L, "B" to 1L)),
            oneCategory = true,
        )
        assertAsk(AskReason.ONE_CATEGORY, decision)
    }

    @Test
    fun `separate categories ask`() {
        val decision = decideA(
            item(cats("A"), v("A" to 2L, "B" to 1L)),
            from("B", cats("A", "B"), v("A" to 1L, "B" to 2L)),
        )
        assertAsk(AskReason.SEPARATE, decision)
    }

    @Test
    fun `three way choice groups devices`() {
        val decision = decideA(
            item(pos(5, 0f), v("A" to 1L)),
            from("B", pos(7, 0f), v("B" to 1L)),
            from("C", pos(7, 0f), v("C" to 1L)),
        )
        val choices = assertAsk(AskReason.FIRST_CONTACT, decision).choices
        assertEquals(listOf(listOf("A"), listOf("B", "C")), choices.map { it.deviceIds })
    }

    @Test
    fun `dropped local value is still the first choice`() {
        val decision = decideA(
            item(pos(1, 0f), v("A" to 1L)),
            from("B", pos(5, 0f), v("A" to 1L, "B" to 1L)),
            from("C", pos(6, 0f), v("C" to 1L)),
        )
        val choices = assertAsk(AskReason.FIRST_CONTACT, decision).choices
        assertEquals(pos(1, 0f), choices.first().item.value)
        assertEquals(3, choices.size)
    }

    @Test
    fun `separate winner behind a superseded local value asks`() {
        val decision = decideA(
            item(pos(20, 0f), v("A" to 1L)),
            from("B", pos(10, 0f, at = 2), v("A" to 1L, "B" to 1L)),
            from("C", pos(5, 0f, at = 1), v("A" to 1L, "C" to 1L)),
        )
        assertAsk(AskReason.BACKWARDS, decision)
    }

    @Test
    fun `unversioned defaults keep local`() {
        val local = item(null)
        assertEquals(Decision.Keep(local), decideA(local, from("B", pos(2, 0f), v())))
    }

    @Test
    fun `only one choice takes the superseded local value`() {
        val decision = decideA(
            item(pos(1, .506f), v("A" to 1L)),
            from("B", pos(1, .500f), v("A" to 1L, "B" to 1L)),
            from("C", pos(1, .512f), v("A" to 1L, "C" to 1L)),
        )
        val values = assertIs<Decision.Ask>(decision).choices.map { it.item.value }
        assertEquals(listOf<ItemValue?>(pos(1, .506f), pos(1, .512f)), values)
    }

    @Test
    fun `leaving a finished chapter for a later one is forward`() {
        assertFalse(isBackward(pos(3, 1f, true), pos(4, 0f)))
    }

    @Test
    fun `record ignores jitter`() {
        val previous = item(pos(1, .500f), v("A" to 1L))
        assertEquals(previous, record(ItemKind.CHAPTER, previous, pos(1, .505f, at = 9), "A"))
    }

    @Test
    fun `record marks rewound`() {
        val result = record(ItemKind.BOOK_POSITION, item(pos(10, 0f), v("A" to 1L)), pos(5, 0f), "A")
        assertEquals(v("A" to 2L), result.version)
        assertTrue(result.rewound)
    }

    @Test
    fun `record starts defaults unversioned`() {
        assertEquals(v(), record(ItemKind.CHAPTER, null, pos(2, 0f), "A").version)
        assertEquals(v(), record(ItemKind.CATEGORIES, null, cats(), "A").version)
        assertEquals(v("A" to 1L), record(ItemKind.BOOK_POSITION, null, pos(0, 0f), "A").version)
    }

    @Test
    fun `category names ignore case spaces and order`() {
        assertTrue(sameValue(cats(" Fav", "reading"), cats("Reading", "FAV ")))
    }

    @Test
    fun `manual resolve dominates all`() {
        val inputs = listOf(item(pos(1, 0f), v("A" to 3L), rewound = true), item(pos(2, 0f), v("B" to 2L)))
        val result = resolve(inputs[1], inputs, "A", manual = true)
        assertEquals(pos(2, 0f), result.value)
        assertEquals(v("A" to 4L, "B" to 2L), result.version)
        assertFalse(result.rewound)
        inputs.forEach { older ->
            assertIs<Decision.Apply>(decide("Z", older.copy(value = pos(0, 0f)), listOf(Candidate("A", result)), false))
        }
    }

    @Test
    fun `converges across three devices`() {
        val start = record(ItemKind.BOOK_POSITION, null, pos(1, 0f), "A")
        val advanced = record(ItemKind.BOOK_POSITION, start, pos(4, 0f), "A")
        val b = assertIs<Decision.Apply>(decide("B", start, listOf(Candidate("A", advanced)), false)).item
        val c = decide("C", start, listOf(Candidate("B", b), Candidate("A", start)), false)
        assertEquals(advanced.value, assertIs<Decision.Apply>(c).item.value)
    }
}
