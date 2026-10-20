package dev.reader.app.data

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class CoverSearchServiceTest {

    private val service = CoverSearchService()

    @Test
    fun `parses docs with a cover id into candidates, skipping those without one`() {
        val json = """
            {
                "docs": [
                    {"title": "My Book", "cover_i": 12345},
                    {"title": "No Cover Edition"},
                    {"title": "My Book (reprint)", "cover_i": 67890}
                ]
            }
        """.trimIndent()

        val candidates = service.parseCoverCandidates(json, fallbackTitle = "Fallback")

        assertEquals(2, candidates.size)
        assertEquals("https://covers.openlibrary.org/b/id/12345-M.jpg", candidates[0].previewUrl)
        assertEquals("https://covers.openlibrary.org/b/id/12345-L.jpg", candidates[0].fullUrl)
        assertEquals("My Book", candidates[0].label)
        assertEquals("My Book (reprint)", candidates[1].label)
    }

    @Test
    fun `falls back to the search title when a doc has no title field`() {
        val json = """{"docs": [{"cover_i": 1}]}"""

        val candidates = service.parseCoverCandidates(json, fallbackTitle = "Fallback Title")

        assertEquals("Fallback Title", candidates.single().label)
    }

    @Test
    fun `drops duplicate cover ids`() {
        val json = """
            {"docs": [{"title": "A", "cover_i": 1}, {"title": "B", "cover_i": 1}]}
        """.trimIndent()

        val candidates = service.parseCoverCandidates(json, fallbackTitle = "Fallback")

        assertEquals(1, candidates.size)
    }

    @Test
    fun `missing docs array yields no candidates instead of throwing`() {
        val candidates = service.parseCoverCandidates("{}", fallbackTitle = "Fallback")

        assertTrue(candidates.isEmpty())
    }
}
