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
import dev.reader.data.sync.Pick
import dev.reader.data.sync.SyncBook
import dev.reader.data.sync.SyncDocument
import dev.reader.data.sync.SyncEngine
import dev.reader.data.sync.SyncFile
import dev.reader.data.sync.SyncFileCodec
import dev.reader.data.sync.SyncFolder
import dev.reader.data.sync.SyncLibrary
import dev.reader.data.sync.SyncReport
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private class FakeFolder : SyncFolder {
    val files = mutableMapOf<String, ByteArray>()
    var listFails = false
    var writeFails = false
    var lists = 0
    var writes = 0
    var listGate: CompletableDeferred<Unit>? = null
    var listing = CompletableDeferred<Unit>()

    override suspend fun list(): List<SyncDocument> {
        lists++
        listing.complete(Unit)
        listGate?.await()
        if (listFails) throw IOException("Folder unavailable")
        return files.map { SyncDocument(it.key, it.value.size.toLong()) }
    }

    override suspend fun read(name: String) = files[name] ?: throw IOException("Gone")

    override suspend fun write(name: String, bytes: ByteArray) {
        if (writeFails) throw IOException("Write refused")
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
        folder.files[PEER] = peerFile("peer-id", 0.5f, signature)
    }

    private fun peerFile(id: String, fraction: Float, signature: String): ByteArray {
        val position = Versioned(PositionValue(0, fraction, false, 1), mapOf(id to 1L))
        return SyncFileCodec.encode(
            SyncFile(
                deviceId = id,
                deviceName = id,
                devices = emptyMap(),
                publishedAt = 1,
                books = listOf(SyncBook(HASH, "Title", signature, position, emptyList(), null)),
            ),
        )
    }

    /** Two unrelated peers disagree about a book never opened here, which stays so across a reset. */
    private suspend fun question(): Pick {
        peerIsAhead()
        val signature = library.snapshot().books.getValue(HASH).signature
        folder.files["reader-sync-other-other-.json"] = peerFile("other-id", 0.8f, signature)
        chooseFolder()
        val asked = finish { controller.syncNow(it) }.report.questions.single()
        return Pick(asked, asked.choices.last().item.value)
    }

    @Test fun `opening the app syncs`() = runBlocking<Unit> {
        peerIsAhead()
        chooseFolder()

        controller.appOpened()

        assertEquals(1, until { !it.running && it.lastSyncAt != null }.report.applied.size)
        assertEquals(1, folder.writes)
    }

    @Test fun `opening the app does not sync when that is switched off`() = runBlocking<Unit> {
        peerIsAhead()
        chooseFolder()
        controller.setSyncOnOpen(false)
        until { !it.running && !it.syncOnOpen }

        controller.appOpened()
        delay(500)

        assertEquals(0, folder.lists)
    }

    @Test fun `the automatic sync waits until the book is closed`() = runBlocking<Unit> {
        peerIsAhead()
        chooseFolder()
        controller.setReading(true)

        controller.appOpened()
        delay(500)
        assertEquals(0, folder.lists)

        controller.setReading(false)
        assertEquals(1, until { !it.running && it.lastSyncAt != null }.report.applied.size)
    }

    @Test fun `a book opened during a sync holds the changes until it is closed`() = runBlocking<Unit> {
        peerIsAhead()
        chooseFolder()
        val gate = CompletableDeferred<Unit>()
        folder.listGate = gate
        controller.appOpened()
        withTimeout(10_000) { folder.listing.await() }

        controller.setReading(true)
        gate.complete(Unit)
        assertTrue(until { !it.running && it.lastSyncAt != null }.report.applied.isEmpty())
        assertTrue(database.progress().observeAll().first().isEmpty())

        controller.setReading(false)
        until { !it.running && it.report.applied.size == 1 }
    }

    @Test fun `the automatic sync settings are saved and survive a reset`() = runBlocking<Unit> {
        controller.setSyncOnOpen(false)
        until { !it.running && !it.syncOnOpen }
        controller.setPublishOnClose(false)
        until { !it.running && !it.publishOnClose }

        controller.reset()

        assertFalse(store.load().syncOnOpen)
        assertFalse(store.load().publishOnClose)
    }

    @Test fun `a failed sync, pull or push drops the earlier results`() = runBlocking<Unit> {
        peerIsAhead()
        chooseFolder()
        folder.writeFails = true
        val first = finish { controller.syncNow(it) }
        assertEquals("Write refused", first.report.publishError)
        assertEquals(1, first.report.applied.size)

        folder.listFails = true
        for (start in listOf(controller::syncNow, controller::pullOnly)) {
            val failed = finish { start(it) }
            assertEquals("Folder unavailable", failed.error)
            assertNull(failed.report.publishError)
            assertTrue(failed.report.applied.isEmpty())
            assertEquals(1, failed.lastChanges.size)
        }

        folder.listFails = false
        finish { controller.syncNow(it) }
        val pushed = finish { controller.pushOnly(it) }
        assertEquals("Write refused", pushed.error)
        assertNull(pushed.report.publishError)
        assertTrue(pushed.report.applied.isEmpty())
    }

    @Test fun `a failed check, apply or undo keeps changes and drops the old publish error`() = runBlocking<Unit> {
        peerIsAhead()
        chooseFolder()
        folder.writeFails = true
        val starts = listOf<() -> Unit>(
            { controller.check() },
            { controller.apply(emptyList()) {} },
            { controller.undo {} },
        )
        for (start in starts) {
            folder.listFails = false
            available = true
            finish { controller.syncNow(it) }
            val before = controller.state.value
            assertEquals("Write refused", before.report.publishError)
            available = false
            start()
            val after = until { !it.running }
            assertEquals("Sync folder is missing or permission was lost", after.error)
            assertNull(after.report.publishError)
            assertEquals(before.report.applied, after.report.applied)
            assertEquals(before.lastChanges, after.lastChanges)
        }
    }

    @Test fun `a successful check keeps the last error`() = runBlocking<Unit> {
        peerIsAhead()
        chooseFolder()
        folder.writeFails = true
        finish { controller.syncNow(it) }

        val lists = folder.lists
        controller.check()
        val checked = until { !it.running }

        assertTrue(folder.lists > lists)
        assertEquals("Write refused", checked.error)
        assertEquals("Write refused", checked.report.publishError)
    }

    @Test fun `check again clears a stale error but keeps a publish error`() = runBlocking<Unit> {
        peerIsAhead()
        chooseFolder()
        folder.listFails = true
        finish { controller.syncNow(it) }
        folder.listFails = false
        controller.check(clearError = true)
        assertNull(until { !it.running }.error)

        folder.writeFails = true
        finish { controller.syncNow(it) }
        controller.check(clearError = true)
        val checked = until { !it.running }
        assertEquals("Write refused", checked.error)
        assertEquals("Write refused", checked.report.publishError)
    }

    @Test fun `nothing syncs while a restore runs`() = runBlocking<Unit> {
        chooseFolder()
        val before = engine.state().deviceId
        val restoring = CompletableDeferred<Unit>()
        val finishRestore = CompletableDeferred<Unit>()
        val restore = scope.async {
            controller.resetAndRun {
                restoring.complete(Unit)
                finishRestore.await()
                "restored"
            }
        }
        restoring.await()
        val done = CompletableDeferred<SyncUiState>()
        controller.syncNow { done.complete(it) }
        delay(200)
        assertEquals(0, folder.lists)

        finishRestore.complete(Unit)

        assertEquals("restored", withTimeout(10_000) { restore.await() })
        // Started before the restore finished resetting, or after it: either way it ran after the restore.
        withTimeout(10_000) { done.await() }
        assertNotEquals(before, engine.state().deviceId)
    }

    @Test fun `picks queued behind a restore are not applied`() = runBlocking<Unit> {
        val pick = question()
        val restoring = CompletableDeferred<Unit>()
        val finishRestore = CompletableDeferred<Unit>()
        val restore = scope.async {
            controller.resetAndRun {
                restoring.complete(Unit)
                finishRestore.await()
            }
        }
        restoring.await()
        val done = CompletableDeferred<ApplyResult>()
        controller.apply(listOf(pick)) { done.complete(it) }
        delay(200)

        finishRestore.complete(Unit)
        withTimeout(10_000) { restore.await() }
        val result = withTimeout(10_000) { done.await() }

        assertTrue(result.applied.isEmpty())
        assertNotNull(result.publishError)
        assertNull(position())
    }

    @Test fun `a failed restore throws and still shows the reset`() = runBlocking<Unit> {
        peerIsAhead()
        chooseFolder()
        finish { controller.syncNow(it) }

        assertFailsWith<IOException> { controller.resetAndRun { throw IOException("Bad backup") } }

        assertTrue(controller.state.value.lastChanges.isEmpty())
        assertTrue(controller.state.value.report.applied.isEmpty())
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
        finish { controller.syncNow(it) }
        val before = engine.state().deviceId
        folder.listing = CompletableDeferred()
        folder.listGate = CompletableDeferred()
        val done = CompletableDeferred<SyncUiState>()
        controller.syncNow { done.complete(it) }
        folder.listing.await()

        val reset = scope.async { controller.reset() }
        delay(200)
        assertFalse(reset.isCompleted)
        folder.listGate!!.complete(Unit)
        val finished = withTimeout(10_000) {
            reset.await()
            done.await()
        }
        assertNotNull(finished.error)
        assertEquals(SyncReport(), finished.report)

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
