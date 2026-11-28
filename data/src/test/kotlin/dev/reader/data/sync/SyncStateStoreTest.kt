package dev.reader.data.sync

import dev.reader.core.sync.CategoriesValue
import dev.reader.core.sync.ItemKey
import dev.reader.core.sync.ItemKind
import dev.reader.core.sync.PositionValue
import dev.reader.core.sync.Versioned
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SyncStateStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var ids = 0

    private fun store(file: File) = SyncStateStore(file, "Phone") { "id-${++ids}" }

    private fun full(state: SyncState): SyncState {
        val key = ItemKey("hash", ItemKind.BOOK_POSITION)
        val record = ItemRecord(key, Versioned(PositionValue(1, 0.5f, false, 7), mapOf("id-1" to 2L), rewound = true))
        return state.copy(
            deviceName = "Tablet",
            folderUri = "content://folder",
            records = listOf(record),
            pending = listOf(record),
            lastChanges = listOf(Change(key, "Book", null, CategoriesValue(listOf("A")), "other")),
            lastSyncAt = 5,
            questionCount = 3,
        )
    }

    @Test
    fun `first load creates and saves a state`() {
        val file = File(folder.root, "sync/state.json")

        assertEquals(SyncState("id-1", "Phone"), store(file).load())
        assertEquals("id-1", store(file).load().deviceId)
    }

    @Test
    fun `state survives reload`() {
        val file = File(folder.root, "state.json")
        val saved = full(store(file).load())

        store(file).save(saved)

        assertEquals(saved, store(file).load())
        assertEquals(listOf("state.json"), folder.root.list()?.toList())
    }

    @Test
    fun `reset changes id and clears records but keeps folder`() {
        val file = File(folder.root, "state.json")
        val store = store(file)
        store.save(full(store.load()))

        store.reset()

        val expected = SyncState("id-2", "Tablet", folderUri = "content://folder", lastSyncAt = 5)
        assertEquals(expected, store(file).load())
    }

    @Test
    fun `corrupt state file gives a fresh state with a new id`() {
        val file = File(folder.root, "state.json")
        store(file).load()
        file.writeText("{\"deviceId\":")

        assertEquals(SyncState("id-2", "Phone"), store(file).load())
        assertEquals("id-2", store(file).load().deviceId)
    }

    @Test
    fun `an unreadable state file keeps its identity`() {
        val file = folder.newFolder("state.json")

        assertFailsWith<IOException> { store(file).load() }

        assertEquals(0, ids)
        assertTrue(file.isDirectory)
    }

    @Test
    fun `a failed save leaves the old file intact`() {
        val file = File(folder.root, "state.json")
        val saved = store(file).load()
        folder.newFolder("state.json.tmp")

        assertFailsWith<IOException> { store(file).save(saved.copy(deviceName = "Other")) }

        assertEquals(saved, store(file).load())
    }
}
