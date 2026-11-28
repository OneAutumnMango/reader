package dev.reader.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
import dev.reader.core.model.Category
import dev.reader.core.model.CategoryId
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import dev.reader.core.sync.AskReason
import dev.reader.core.sync.CategoriesValue
import dev.reader.core.sync.ItemKey
import dev.reader.core.sync.ItemKind
import dev.reader.core.sync.PositionValue
import dev.reader.core.sync.Versioned
import dev.reader.data.repo.LibraryRepository
import dev.reader.data.room.ReaderDatabase
import dev.reader.data.room.entity.ProgressEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private class FakeSyncFolder : SyncFolder {
    val files = mutableMapOf<String, ByteArray>()
    val sizes = mutableMapOf<String, Long>()
    val reads = mutableListOf<String>()
    var listFails = false
    var writeFails = false
    var writes = 0
    var writeGate: CompletableDeferred<Unit>? = null
    val writing = CompletableDeferred<Unit>()

    override suspend fun list(): List<SyncDocument> {
        if (listFails) throw IOException("Folder unavailable")
        return files.map { SyncDocument(it.key, sizes[it.key] ?: it.value.size.toLong()) }
    }

    override suspend fun read(name: String): ByteArray {
        reads += name
        return files[name] ?: throw IOException("Gone")
    }

    override suspend fun write(name: String, bytes: ByteArray) {
        writing.complete(Unit)
        writeGate?.await()
        if (writeFails) throw IOException("Write refused")
        writes++
        files[name] = bytes
    }

    override suspend fun delete(name: String) {
        files.remove(name)
    }
}

private const val HASH = "hash-a"
private val POSITION = ItemKey(HASH, ItemKind.BOOK_POSITION)

@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val devices = mutableListOf<Device>()

    private inner class Device(val id: String, withBook: Boolean = true) {
        /** Runs after a merge has read the library and before it writes. */
        var duringMerge: suspend () -> Unit = {}
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ReaderDatabase::class.java,
        ).allowMainThreadQueries().build()
        val repository = LibraryRepository(database)
        private var ids = 0
        val store = SyncStateStore(File(temp.newFolder(), "sync.json"), id) { if (ids++ == 0) id else "$id-$ids" }
        val engine = SyncEngine(
            SyncLibrary(database),
            store,
            {
                duringMerge()
                false
            },
            { 1000L },
        )
        val fileName get() = syncFileName(store.load().deviceName, store.load().deviceId)

        init {
            devices += this
            if (withBook) runBlocking { addBook("a") }
        }

        suspend fun addBook(book: String, hash: String = "hash-$book", words: Int = 100, title: String = "Title") {
            val chapters = (0..1).map {
                Chapter(ChapterId("$book-$it"), BookId(book), "$it", "", it, wordCount = words)
            }
            repository.upsertBook(
                Book(
                    BookId(book),
                    title,
                    format = BookFormat.EPUB,
                    sourcePath = "/$book.epub",
                    addedAt = Instant.fromEpochMilliseconds(0),
                    contentHash = hash,
                ),
                chapters,
            )
        }

        suspend fun read(chapter: Int, fraction: Float, at: Long) =
            database.progress().upsert(ProgressEntity("a", "a-$chapter", fraction, at, false))

        suspend fun progress() = database.progress().observe("a").first()?.let { it.chapterId to it.position }

        suspend fun categories() = SyncLibrary(database).snapshot().books.getValue(HASH).categories

        fun record(key: ItemKey = POSITION) = store.load().records.single { it.key == key }.item
    }

    @After
    fun tearDown() = devices.forEach { it.database.close() }

    private fun peerFile(
        position: Versioned = Versioned(null),
        chapters: List<SyncChapter> = emptyList(),
        categories: Versioned? = null,
        signature: String,
    ) = SyncFileCodec.encode(
        SyncFile(
            deviceId = "peer",
            deviceName = "Peer",
            devices = emptyMap(),
            publishedAt = 5,
            books = listOf(SyncBook(HASH, "Peer title", signature, position, chapters, categories)),
        ),
    )

    private suspend fun signature(device: Device) =
        SyncLibrary(device.database).snapshot().books.getValue(HASH).signature

    @Test
    fun `forward progress travels between two devices`() = runBlocking {
        val folder = FakeSyncFolder()
        val a = Device("aaaaaa")
        val b = Device("bbbbbb")
        a.read(1, 0.5f, 10)
        a.engine.sync(folder)

        val report = b.engine.sync(folder)

        assertEquals("a-1" to 0.5f, b.progress())
        assertEquals(
            listOf(Change(POSITION, "Title", null, PositionValue(1, 0.5f, false, 10), "aaaaaa")),
            report.applied,
        )
        assertEquals(report.applied, b.store.load().lastChanges)
        assertEquals(1000L, b.store.load().lastSyncAt)
        assertEquals(emptyList(), b.store.load().pending)
        assertEquals(mapOf("aaaaaa" to 1L), b.record().version)
        assertEquals(listOf(DeviceInfo("aaaaaa", "aaaaaa", 1000, false)), report.devices)
        assertEquals(mapOf("aaaaaa" to "aaaaaa", "bbbbbb" to "bbbbbb"), report.deviceNames)
        assertEquals(emptyList(), a.engine.sync(folder).questions)
    }

    @Test
    fun `backward progress is a question and is not applied`() = runBlocking {
        val folder = FakeSyncFolder()
        val a = Device("aaaaaa")
        val b = Device("bbbbbb")
        a.read(1, 0.8f, 10)
        a.engine.sync(folder)
        b.engine.sync(folder)
        b.read(0, 0.2f, 20)
        b.engine.sync(folder)

        val report = a.engine.sync(folder)

        assertEquals("a-1" to 0.8f, a.progress())
        val question = report.questions.single()
        assertEquals(AskReason.BACKWARDS, question.reason)
        assertEquals(POSITION, question.key)
        assertEquals(question.local.value, question.choices[0].item.value)
        assertEquals(a.record(), question.local)
        assertEquals(1, a.store.load().questionCount)
    }

    @Test
    fun `three devices converge in either order`() = runBlocking {
        for (reversed in listOf(false, true)) {
            val folder = FakeSyncFolder()
            val all = listOf(Device("aaaaaa"), Device("bbbbbb"), Device("cccccc"))
            all[0].read(0, 0.2f, 10)
            all.forEach { it.engine.sync(folder) }
            all[1].read(1, 0.5f, 20)
            all[1].engine.sync(folder)
            val others = listOf(all[0], all[2]).let { if (reversed) it.reversed() else it }

            others.forEach { assertEquals(emptyList(), it.engine.sync(folder).questions) }

            all.forEach {
                val report = it.engine.sync(folder)
                assertEquals(emptyList(), report.questions)
                assertEquals(emptyList(), report.applied)
                assertEquals("a-1" to 0.5f, it.progress())
                assertEquals(mapOf("aaaaaa" to 1L, "bbbbbb" to 1L), it.record().version)
            }
        }
    }

    @Test
    fun `bad file is rejected and valid files still sync`() = runBlocking {
        val folder = FakeSyncFolder()
        val a = Device("aaaaaa")
        val b = Device("bbbbbb")
        a.read(1, 0.5f, 10)
        a.engine.publish(folder)
        folder.files["reader-sync-bad-123456.json"] = "{".encodeToByteArray()
        folder.files["notes.txt"] = "x".encodeToByteArray()

        val report = b.engine.sync(folder)

        assertEquals(listOf("reader-sync-bad-123456.json"), report.rejected.map { it.name })
        assertEquals("a-1" to 0.5f, b.progress())
        assertFalse("notes.txt" in folder.reads)
        assertNull(report.publishError)
        assertTrue(b.fileName in folder.files)
    }

    @Test
    fun `conflict copy is read`() = runBlocking {
        val folder = FakeSyncFolder()
        val a = Device("aaaaaa")
        val b = Device("bbbbbb")
        a.read(1, 0.5f, 10)
        a.engine.publish(folder)
        val copy = "reader-sync-aaaaaa-aaaaaa.sync-conflict-20261128-1.json"
        folder.files[copy] = folder.files.remove(a.fileName)!!

        val report = b.engine.sync(folder)

        assertEquals("a-1" to 0.5f, b.progress())
        assertTrue(report.devices.single().conflictCopy)
    }

    @Test
    fun `oversized file is not read`() = runBlocking {
        val folder = FakeSyncFolder()
        val a = Device("aaaaaa")
        val name = "reader-sync-big-123456.json"
        folder.files[name] = ByteArray(1)
        folder.sizes[name] = MAX_SYNC_FILE_BYTES + 1L

        val report = a.engine.sync(folder)

        assertEquals(listOf(RejectedFile(name, "File is too large")), report.rejected)
        assertFalse(name in folder.reads)
    }

    @Test
    fun `a file too large to publish is not written`() = runBlocking {
        val folder = FakeSyncFolder()
        val a = Device("aaaaaa", withBook = false)
        repeat(6) { a.addBook("big$it", title = "x".repeat(1024 * 1024)) }

        assertFailsWith<IOException> { a.engine.publish(folder) }
        val report = a.engine.sync(folder)

        assertEquals("Sync file is too large to publish", report.publishError)
        assertEquals(0, folder.writes)
    }

    @Test
    fun `list failure publishes nothing`() = runBlocking {
        val folder = FakeSyncFolder().apply { listFails = true }
        val a = Device("aaaaaa")

        assertFailsWith<IOException> { a.engine.sync(folder) }

        assertEquals(0, folder.writes)
        assertNull(a.store.load().lastSyncAt)
    }

    @Test
    fun `write failure reports publishError after applying`() = runBlocking {
        val folder = FakeSyncFolder()
        val a = Device("aaaaaa")
        val b = Device("bbbbbb")
        a.read(1, 0.5f, 10)
        a.engine.publish(folder)
        folder.writeFails = true

        val report = b.engine.sync(folder)

        assertEquals("Write refused", report.publishError)
        assertEquals(1, report.applied.size)
        assertEquals("a-1" to 0.5f, b.progress())
    }

    @Test
    fun `publish never changes the database`() = runBlocking {
        val folder = FakeSyncFolder()
        val a = Device("aaaaaa")
        val b = Device("bbbbbb")
        a.read(1, 0.5f, 10)
        a.engine.publish(folder)

        b.engine.publish(folder)
        assertNull(b.progress())
        b.engine.sync(folder, publish = false)

        assertEquals("a-1" to 0.5f, b.progress())
        assertEquals(2, folder.writes)
    }

    @Test
    fun `untouched chapters are not published`() = runBlocking {
        val folder = FakeSyncFolder()
        val a = Device("aaaaaa")

        a.engine.publish(folder)

        assertEquals(emptyList(), SyncFileCodec.decode(folder.files.getValue(a.fileName)).books.single().chapters)
    }

    @Test
    fun `rename removes only the old own file`() = runBlocking {
        val folder = FakeSyncFolder()
        val a = Device("aaaaaa")
        val b = Device("bbbbbb")
        a.engine.publish(folder)
        b.engine.publish(folder)
        val old = a.fileName
        val copy = old.removeSuffix(".json") + ".sync-conflict-20261128-1.json"
        val copyWithSuffix = "reader-sync-x.sync-conflict-1-aaaaaa.json"
        val otherScheme = "reader-sync-handmade.json"
        listOf(copy, copyWithSuffix, otherScheme).forEach { folder.files[it] = folder.files.getValue(old) }
        a.store.save(a.store.load().copy(deviceName = "Renamed"))

        a.engine.publish(folder)

        assertEquals(setOf(a.fileName, b.fileName, copy, copyWithSuffix, otherScheme), folder.files.keys)
    }

    @Test
    fun `other devices' files are never deleted`() = runBlocking {
        val folder = FakeSyncFolder()
        val a = Device("aaaaaa")
        // Named as if it were this install's, but another install's content or unreadable.
        val lookalike = "reader-sync-other-aaaaaa.json"
        folder.files[lookalike] = peerFile(signature = "x")
        folder.files["reader-sync-broken-aaaaaa.json"] = ByteArray(3)
        val before = folder.files.keys.toSet()

        a.engine.sync(folder)

        assertEquals(before + a.fileName, folder.files.keys)
    }

    @Test
    fun `pending apply is not counted as a local edit`() = runBlocking {
        val folder = FakeSyncFolder()
        val a = Device("aaaaaa")
        val b = Device("bbbbbb")
        a.read(1, 0.5f, 10)
        a.engine.publish(folder)
        b.engine.publish(folder)
        val before = b.store.load()
        b.engine.sync(folder)
        val applied = b.store.load().records.single { it.key == POSITION }
        b.store.save(before.copy(pending = listOf(applied)))

        b.engine.sync(folder)

        assertEquals(applied.item, b.record())
        assertEquals(emptyList(), b.store.load().pending)
    }

    @Test
    fun `records survive a skipped book`() = runBlocking {
        val folder = FakeSyncFolder()
        val a = Device("aaaaaa")
        a.read(1, 0.5f, 10)
        a.engine.sync(folder)
        val before = a.store.load().records
        a.addBook("copy", HASH)

        val report = a.engine.sync(folder)
        assertEquals(before, a.store.load().records)
        assertEquals(2, report.skipped.size)
        a.repository.deleteBook(BookId("copy"))
        a.engine.sync(folder)

        assertEquals(before, a.store.load().records)
    }

    @Test
    fun `signature mismatch is skipped with reason`() = runBlocking {
        val folder = FakeSyncFolder()
        val a = Device("aaaaaa", withBook = false)
        val b = Device("bbbbbb")
        a.addBook("a", words = 7)
        a.addBook("only", title = "Only A")
        a.read(1, 0.5f, 10)
        a.engine.publish(folder)

        val report = b.engine.sync(folder)

        assertEquals(
            setOf(SkippedBook("Title", "Chapters don't match"), SkippedBook("Only A", "Not in this library")),
            report.skipped.toSet(),
        )
        assertNull(b.progress())
    }

    @Test
    fun `peer values the library cannot hold cause no local change or question`() = runBlocking {
        val folder = FakeSyncFolder()
        val a = Device("aaaaaa")
        a.repository.upsertCategory(Category(CategoryId("c"), "Reading", 0))
        folder.files["reader-sync-peer-peer.json"] = peerFile(
            position = Versioned(PositionValue(9, 0.5f, false, 1), mapOf("peer" to 1L)),
            chapters = listOf(
                SyncChapter(0, Versioned(null, mapOf("peer" to 1L))),
                SyncChapter(1, Versioned(PositionValue(7, 0.5f, false, 1), mapOf("peer" to 1L))),
            ),
            categories = Versioned(CategoriesValue(listOf(" ", "​", " Reading", "reading")), mapOf("peer" to 1L)),
            signature = signature(a),
        )

        a.engine.sync(folder)
        val records = a.store.load().records
        val again = a.engine.sync(folder)

        assertEquals(records, a.store.load().records)
        assertEquals(emptyList(), again.applied)
        assertEquals(emptyList(), again.questions)
        assertNull(a.progress())
        assertEquals(CategoriesValue(listOf("Reading")), a.categories())
        assertEquals(mapOf("peer" to 1L), a.record(ItemKey(HASH, ItemKind.CATEGORIES)).version)
    }

    @Test
    fun `an item the user changed during a sync is not overwritten`() = runBlocking {
        val folder = FakeSyncFolder()
        val a = Device("aaaaaa")
        val b = Device("bbbbbb")
        a.read(1, 0.5f, 10)
        a.engine.publish(folder)
        b.duringMerge = { b.read(0, 0.3f, 20) }

        val report = b.engine.sync(folder, publish = false)

        assertEquals(emptyList(), report.applied)
        assertEquals(emptyList(), b.store.load().lastChanges)
        assertEquals("a-0" to 0.3f, b.progress())
        assertEquals(emptyMap(), b.record().version)
    }

    @Test
    fun `categories that turn ambiguous when applied are not recorded as removed`() = runBlocking {
        val folder = FakeSyncFolder()
        val a = Device("aaaaaa")
        a.repository.upsertCategory(Category(CategoryId("c1"), "Dup", 0))
        a.repository.upsertCategory(Category(CategoryId("c2"), "dup", 1))
        folder.files["reader-sync-peer-peer.json"] = peerFile(
            categories = Versioned(CategoriesValue(listOf("Dup")), mapOf("peer" to 1L)),
            signature = signature(a),
        )
        val key = ItemKey(HASH, ItemKind.CATEGORIES)

        a.engine.sync(folder)
        assertNull(a.categories())
        a.engine.sync(folder)
        a.repository.deleteCategory(Category(CategoryId("c2"), "dup", 1))
        a.engine.sync(folder)

        assertEquals(Versioned(CategoriesValue(listOf("Dup")), mapOf("peer" to 1L)), a.record(key))
    }

    @Test
    fun `check changes nothing`() = runBlocking {
        val folder = FakeSyncFolder()
        val a = Device("aaaaaa")
        val b = Device("bbbbbb")
        a.read(1, 0.8f, 10)
        a.engine.publish(folder)
        b.read(0, 0.2f, 5)
        b.engine.publish(folder)
        a.read(1, 0.9f, 30)
        val state = a.store.load()
        val files = folder.files.toMap()
        val writes = folder.writes

        val report = a.engine.check(folder)

        assertEquals(listOf(AskReason.FIRST_CONTACT), report.questions.map { it.reason })
        assertEquals(emptyList(), b.engine.check(folder).applied)
        assertEquals(state, a.store.load())
        assertEquals(files, folder.files)
        assertEquals(writes, folder.writes)
        assertEquals("a-0" to 0.2f, b.progress())
    }

    @Test
    fun `reset waits for a running publish and is not overwritten by it`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val folder = FakeSyncFolder().apply { writeGate = gate }
        val a = Device("aaaaaa")
        a.read(1, 0.5f, 10)
        val publishing = launch { a.engine.publish(folder) }
        folder.writing.await()
        val resetting = launch { a.engine.reset() }
        delay(200)
        assertFalse(resetting.isCompleted)

        gate.complete(Unit)
        publishing.join()
        resetting.join()

        assertNotEquals("aaaaaa", a.store.load().deviceId)
        assertEquals(emptyList(), a.store.load().records)
    }
}
