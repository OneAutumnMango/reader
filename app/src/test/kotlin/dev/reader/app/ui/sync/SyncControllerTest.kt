package dev.reader.app.ui.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import dev.reader.core.sync.PositionValue
import dev.reader.core.sync.Versioned
import dev.reader.data.repo.LibraryRepository
import dev.reader.data.room.ReaderDatabase
import dev.reader.data.sync.ApplyResult
import dev.reader.data.sync.SyncBook
import dev.reader.data.sync.SyncDocument
import dev.reader.data.sync.SyncEngine
import dev.reader.data.sync.SyncFile
import dev.reader.data.sync.SyncFileCodec
import dev.reader.data.sync.SyncFolder
import dev.reader.data.sync.SyncLibrary
import dev.reader.data.sync.SyncStateStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private class FakeFolder : SyncFolder {
    val files = mutableMapOf<String, ByteArray>()
    var listFails = false
    var lists = 0
    var writes = 0
    var listGate: CompletableDeferred<Unit>? = null
    val listing = CompletableDeferred<Unit>()

    override suspend fun list(): List<SyncDocument> {
        lists++
        listing.complete(Unit)
        listGate?.await()
        if (listFails) throw IOException("Folder unavailable")
        return files.map { SyncDocument(it.key, it.value.size.toLong()) }
    }

    override suspend fun read(name: String) = files[name] ?: throw IOException("Gone")

    override suspend fun write(name: String, bytes: ByteArray) {
        writes++
        files[name] = bytes
    }

    override suspend fun delete(name: String) {
        files.remove(name)
    }
}

private const val HASH = "hash-a"
private const val PEER = "reader-sync-peer-peer-i.json"

@RunWith(RobolectricTestRunner::class)
class SyncControllerTest {

    @get:Rule val temp = TemporaryFolder()

    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        ReaderDatabase::class.java,
    ).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val folder = FakeFolder()
    private var available = true
    private val store by lazy { SyncStateStore(File(temp.root, "sync-state.json"), "Phone") }
    private val library = SyncLibrary(database)
    private val engine by lazy { SyncEngine(library, store, { false }) }
    private val controller by lazy {
        SyncController(engine, store, { folder.takeIf { available } }, { "Shared" }, scope)
    }

    @After fun tearDown() {
        scope.cancel()
        database.close()
    }

    private suspend fun until(condition: (SyncUiState) -> Boolean) =
        withTimeout(10_000) { controller.state.first(condition) }

    private suspend fun chooseFolder() {
        controller.setFolder("content://tree")
        until { !it.running && it.folderName != null }
    }

    private suspend fun finish(start: (((SyncUiState) -> Unit) -> Unit)): SyncUiState {
        val done = CompletableDeferred<SyncUiState>()
        start { done.complete(it) }
        return withTimeout(10_000) { done.await() }
    }

    /** A book in the library, and a peer file that has read further in it. */
    private suspend fun peerIsAhead() {
        val chapter = Chapter(ChapterId("c0"), BookId("b"), "One", "0.xhtml", 0, 100)
        val book = Book(
            id = BookId("b"),
            title = "Title",
            format = BookFormat.EPUB,
            sourcePath = "/b",
            addedAt = Instant.fromEpochMilliseconds(0),
            contentHash = HASH,
        )
        LibraryRepository(database).upsertBook(book, listOf(chapter))
        val signature = library.snapshot().books.getValue(HASH).signature
        val position = Versioned(PositionValue(0, 0.5f, false, 1), mapOf("peer-id" to 1L))
        val file = SyncFile(
            deviceId = "peer-id",
            deviceName = "Peer",
            devices = emptyMap(),
            publishedAt = 1,
            books = listOf(SyncBook(HASH, "Title", signature, position, emptyList(), null)),
        )
        folder.files[PEER] = SyncFileCodec.encode(file)
    }

    private suspend fun position() = library.snapshot().books.getValue(HASH).position

    @Test fun `second call while running is ignored`() = runBlocking<Unit> {
        chooseFolder()
        folder.listGate = CompletableDeferred()
        val done = CompletableDeferred<SyncUiState>()
        var ignored = true
        controller.syncNow { done.complete(it) }
        folder.listing.await()
        assertTrue(controller.state.value.running)

        controller.pullOnly { ignored = false }
        controller.setDeviceName("Other")
        folder.listGate!!.complete(Unit)
        withTimeout(10_000) { done.await() }
        delay(200)

        assertTrue(ignored)
        assertEquals("Phone", engine.state().deviceName)
        assertEquals(1, folder.writes)
    }

    @Test fun `unavailable folder sets error`() = runBlocking<Unit> {
        chooseFolder()
        available = false
        assertNotNull(finish { controller.syncNow(it) }.error)

        available = true
        folder.listFails = true
        val failed = finish { controller.syncNow(it) }
        assertEquals("Folder unavailable", failed.error)
        assertFalse(failed.running)
        assertEquals("Shared", failed.folderName)

        folder.listFails = false
        assertNull(finish { controller.syncNow(it) }.error)
    }

    @Test fun `failed apply and undo still call back`() = runBlocking<Unit> {
        chooseFolder()
        available = false
        val applied = CompletableDeferred<ApplyResult>()
        controller.apply(emptyList()) { applied.complete(it) }
        assertNotNull(withTimeout(10_000) { applied.await() }.publishError)
        val undone = CompletableDeferred<String?>()
        controller.undo { undone.complete(it.publishError) }
        assertNotNull(withTimeout(10_000) { undone.await() })
    }

    @Test fun `check without a folder is not an error`() = runBlocking<Unit> {
        controller.check()
        until { !it.running && it.deviceName.isNotEmpty() }
        delay(200)
        assertNull(controller.state.value.error)
    }

    @Test fun `pull only does not write a file`() = runBlocking<Unit> {
        peerIsAhead()
        chooseFolder()

        val result = finish { controller.pullOnly(it) }

        assertEquals(1, result.report.applied.size)
        assertEquals(1, result.lastChanges.size)
        assertEquals(0, folder.writes)
        assertNotNull(position())
    }

    @Test fun `push only leaves the library unchanged`() = runBlocking<Unit> {
        peerIsAhead()
        chooseFolder()

        assertNull(finish { controller.pushOnly(it) }.error)

        assertEquals(1, folder.writes)
        assertNull(position())
    }

    @Test fun `reset gives a new device id`() = runBlocking<Unit> {
        peerIsAhead()
        chooseFolder()
        finish { controller.syncNow(it) }
        val before = engine.state()

        controller.reset()

        val after = engine.state()
        assertNotEquals(before.deviceId, after.deviceId)
        assertTrue(after.records.isEmpty())
        assertEquals(before.folderUri, after.folderUri)
        assertTrue(controller.state.value.lastChanges.isEmpty())
        assertTrue(controller.state.value.report.applied.isEmpty())
    }

    @Test fun `reset during a running sync is not ignored`() = runBlocking<Unit> {
        peerIsAhead()
        chooseFolder()
        val before = engine.state().deviceId
        folder.listGate = CompletableDeferred()
        val done = CompletableDeferred<SyncUiState>()
        controller.syncNow { done.complete(it) }
        folder.listing.await()

        val reset = scope.async { controller.reset() }
        delay(200)
        assertFalse(reset.isCompleted)
        folder.listGate!!.complete(Unit)
        withTimeout(10_000) {
            reset.await()
            done.await()
        }

        val after = engine.state()
        assertNotEquals(before, after.deviceId)
        assertTrue(after.records.isEmpty())
        assertTrue(after.lastChanges.isEmpty())
        // The finished sync must not bring its pre-reset report back.
        assertTrue(controller.state.value.report.applied.isEmpty())
        assertTrue(controller.state.value.lastChanges.isEmpty())
        assertFalse(controller.state.value.running)
    }
}
