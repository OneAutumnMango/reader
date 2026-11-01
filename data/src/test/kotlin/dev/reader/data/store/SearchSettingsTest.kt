package dev.reader.data.store

import dev.reader.core.model.BookId
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchSettingsTest {

    @Test
    fun `a settings file from before search settings existed still loads, with the defaults`() {
        val json = Json { ignoreUnknownKeys = true }

        val prefs = json.decodeFromString(PreferencesFile.serializer(), """{"version":1}""")

        assertTrue(prefs.searchSettings.indexEnabled)
        assertTrue(prefs.searchSettings.indexOnImport)
        assertEquals(emptySet(), prefs.searchSettings.indexExcludedBooks)
    }

    @Test
    fun `a book is indexed only when indexing is on and it isn't left out`() {
        val settings = SearchSettings(indexExcludedBooks = setOf("left-out"))

        assertTrue(settings.indexes(BookId("kept")))
        assertFalse(settings.indexes(BookId("left-out")))
        assertFalse(settings.copy(indexEnabled = false).indexes(BookId("kept")))
    }

    @Test
    fun `a job knows whether it is running, paused or finished`() {
        assertFalse(SearchIndexJob().running)
        assertFalse(SearchIndexJob().finished)

        val running = SearchIndexJob(pending = listOf("a", "b"), total = 4)
        assertTrue(running.running)
        assertEquals(2, running.done)
        assertTrue(running.copy(paused = true).isPaused)
        assertFalse(running.copy(paused = true).running)

        assertTrue(SearchIndexJob(total = 4).finished)
    }
}
