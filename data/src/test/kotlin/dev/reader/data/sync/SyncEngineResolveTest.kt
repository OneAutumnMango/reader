package dev.reader.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.reader.core.model.Book
import dev.reader.core.model.BookFormat
import dev.reader.core.model.BookId
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
import kotlinx.coroutines.flow.first
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private class MemoryFolder : SyncFolder {
    val files = mutableMapOf<String, ByteArray>()
    var writeFails = false

    override suspend fun list() = files.map { SyncDocument(it.key, it.value.size.toLong()) }

    override suspend fun read(name: String) = files[name] ?: throw IOException("Gone")

    override suspend fun write(name: String, bytes: ByteArray) {
        if (writeFails) throw IOException("Write refused")
        files[name] = bytes
    }

    override suspend fun delete(name: String) {
        files.remove(name)
    }
}

private const val HASH = "hash-a"
private val POSITION = ItemKey(HASH, ItemKind.BOOK_POSITION)
private val CATEGORIES = ItemKey(HASH, ItemKind.CATEGORIES)

@RunWith(RobolectricTestRunner::class)
class SyncEngineResolveTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val devices = mutableListOf<Device>()

    private inner class Device(val id: String) {
        var oneCategory = false

        /** Runs after the library has been read and before it is written. */
        var duringMerge: suspend () -> Unit = {}
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ReaderDatabase::class.java,
        ).allowMainThreadQueries().build()
        private var ids = 0
        val store = SyncStateStore(File(temp.newFolder(), "sync.json"), id) { if (ids++ == 0) id else "$id-$ids" }
        private var categoryIds = 0
        val library = SyncLibrary(database) { "$id-category-${categoryIds++}" }
        val engine = SyncEngine(
            library,
            store,
            {
                duringMerge()
                oneCategory
            },
            { 1000L },
        )

        init {
            devices += this
            runBlocking {
                val chapters = (0..1).map { Chapter(ChapterId("a-$it"), BookId("a"), "$it", "", it, wordCount = 100) }
                LibraryRepository(database).upsertBook(
                    Book(
                        BookId("a"),
                        "Title",
                        format = BookFormat.EPUB,
                        sourcePath = "/a.epub",
                        addedAt = Instant.fromEpochMilliseconds(0),
                        contentHash = HASH,
                    ),
                    chapters,
                )
            }
        }

        suspend fun read(chapter: Int, fraction: Float, at: Long) =
            database.progress().upsert(ProgressEntity("a", "a-$chapter", fraction, at, false))

        suspend fun progress() = database.progress().observe("a").first()?.let { it.chapterId to it.position }

        suspend fun categories() = library.snapshot().books.getValue(HASH).categories?.names?.toSet()

        suspend fun setCategories(vararg names: String) =
            library.write(library.snapshot(), mapOf(CATEGORIES to CategoriesValue(names.toList())))

        fun record(key: ItemKey = POSITION) = store.load().records.single { it.key == key }.item
    }

    @After
    fun tearDown() = devices.forEach { it.database.close() }

    private val folder = MemoryFolder()

    /** Both at chapter 1, then [b] goes back, so that [a] has a question. */
    private suspend fun backwardsQuestion(a: Device, b: Device): Question {
        a.read(1, 0.8f, 10)
        a.engine.sync(folder)
        b.engine.sync(folder)
        b.read(0, 0.2f, 20)
        b.engine.sync(folder)
        return a.engine.check(folder).questions.single()
    }

    @Test
    fun `chosen value is not asked again on the other device when forward`() = runBlocking {
        val a = Device("aaaaaa")
        val b = Device("bbbbbb")
        a.read(0, 0.2f, 10)
        b.read(1, 0.5f, 20)
        a.engine.publish(folder)
        val question = b.engine.sync(folder).questions.single()
        val other = a.engine.sync(folder).questions.single()

        val result = a.engine.apply(folder, listOf(Pick(other, other.choices[1].item.value)))

        assertEquals(emptyList(), result.stale)
        assertNull(result.publishError)
        assertEquals("a-1" to 0.5f, a.progress())
        assertEquals(result.applied, a.store.load().lastChanges)
        assertEquals(0, a.store.load().questionCount)
        assertEquals(mapOf("aaaaaa" to 2L, "bbbbbb" to 1L), a.record().version)
        assertEquals(question.local.value, a.record().value)
        val again = b.engine.sync(folder)
        assertEquals(emptyList(), again.questions)
        assertEquals("a-1" to 0.5f, b.progress())
    }

    @Test
    fun `other device still asks before a choice moves it back`() = runBlocking {
        val a = Device("aaaaaa")
        val b = Device("bbbbbb")
        a.read(0, 0.2f, 10)
        b.read(1, 0.5f, 20)
        b.engine.publish(folder)
        val question = a.engine.sync(folder).questions.single()

        a.engine.apply(folder, listOf(Pick(question, question.choices[0].item.value)))

        assertEquals(AskReason.BACKWARDS, b.engine.sync(folder).questions.single().reason)
        assertEquals("a-1" to 0.5f, b.progress())
    }

    @Test
    fun `keep this is not asked again here`() = runBlocking {
        val a = Device("aaaaaa")
        val b = Device("bbbbbb")
        val question = backwardsQuestion(a, b)

        val result = a.engine.apply(folder, listOf(Pick(question, question.choices[0].item.value)))

        assertEquals(emptyList(), result.stale)
        assertEquals(emptyList(), result.applied) // nothing in the library changed, so there is nothing to undo
        assertEquals(emptyList(), a.engine.sync(folder).questions)
        assertEquals("a-1" to 0.8f, a.progress())
    }

    @Test
    fun `stale pick is not applied`() = runBlocking {
        val a = Device("aaaaaa")
        val b = Device("bbbbbb")
        val question = backwardsQuestion(a, b)
        val pick = Pick(question, question.choices[1].item.value)
        b.read(0, 0.4f, 30)
        b.engine.publish(folder)

        val peerChanged = a.engine.apply(folder, listOf(pick))

        assertEquals(listOf(question), peerChanged.stale)
        assertEquals(emptyList(), peerChanged.applied)
        assertEquals("a-1" to 0.8f, a.progress())

        val fresh = a.engine.check(folder).questions.single()
        a.read(1, 0.9f, 40)
        val localChanged = a.engine.apply(folder, listOf(Pick(fresh, fresh.choices[1].item.value)))

        assertEquals(listOf(fresh), localChanged.stale)
        assertEquals("a-1" to 0.9f, a.progress())
    }

    @Test
    fun `a change made while applying is not overwritten`() = runBlocking {
        val a = Device("aaaaaa")
        val b = Device("bbbbbb")
        val question = backwardsQuestion(a, b)
        a.duringMerge = { a.read(1, 0.9f, 40) }

        val result = a.engine.apply(folder, listOf(Pick(question, question.choices[1].item.value)))

        assertEquals(listOf(question), result.stale)
        assertEquals(emptyList(), result.applied)
        assertEquals("a-1" to 0.9f, a.progress())
    }

    private suspend fun categoryQuestion(a: Device, b: Device): Question {
        a.setCategories("One")
        b.setCategories("Two")
        b.engine.publish(folder)
        return a.engine.sync(folder).questions.single { it.key == CATEGORIES }
    }

    @Test
    fun `combine records both categories`() = runBlocking {
        val a = Device("aaaaaa")
        val b = Device("bbbbbb")
        val question = categoryQuestion(a, b)

        val result = a.engine.apply(folder, listOf(Pick(question, CategoriesValue(listOf("One", " Two", "two")))))

        assertEquals(emptyList(), result.stale)
        assertEquals(setOf("One", "Two"), a.categories())
        assertEquals(CategoriesValue(listOf("One", "Two")), a.record(CATEGORIES).value)
        assertEquals(emptyList(), b.engine.sync(folder).questions)
        assertEquals(setOf("One", "Two"), b.categories())
    }

    @Test
    fun `one category mode rejects two`() = runBlocking {
        val a = Device("aaaaaa")
        val b = Device("bbbbbb")
        val question = categoryQuestion(a, b)
        a.oneCategory = true

        val result = a.engine.apply(folder, listOf(Pick(question, CategoriesValue(listOf("One", "Two")))))

        assertEquals(listOf(question), result.stale)
        assertEquals(setOf("One"), a.categories())
    }

    @Test
    fun `a choice with too long a history is not applied`() = runBlocking {
        val a = Device("aaaaaa")
        a.read(0, 0.2f, 10)
        val signature = a.library.snapshot().books.getValue(HASH).signature
        for (peer in listOf("x", "y")) {
            val version = (1..40).associate { "$peer$it" to 1L }
            val position = Versioned(PositionValue(1, if (peer == "x") 0.5f else 0.7f, false, 1), version)
            folder.files["reader-sync-$peer-$peer.json"] = SyncFileCodec.encode(
                SyncFile(
                    deviceId = peer,
                    deviceName = peer,
                    devices = emptyMap(),
                    publishedAt = 5,
                    books = listOf(SyncBook(HASH, "T", signature, position, emptyList(), null)),
                ),
            )
        }
        val question = a.engine.check(folder).questions.single()

        val result = a.engine.apply(folder, listOf(Pick(question, question.choices[0].item.value)))

        assertEquals(listOf(question), result.stale)
        assertEquals(mapOf("aaaaaa" to 1L), a.record().version)
    }

    @Test
    fun `an apply that changes nothing keeps the undo data`() = runBlocking {
        val a = Device("aaaaaa")
        val b = Device("bbbbbb")
        a.read(1, 0.5f, 10)
        a.setCategories("One")
        b.setCategories("Two")
        a.engine.publish(folder)
        val report = b.engine.sync(folder)
        val synced = report.applied
        val question = report.questions.single()
        assertEquals(listOf(POSITION), synced.map { it.key })

        val stale = b.engine.apply(folder, listOf(Pick(question.copy(local = Versioned(null)), null)))

        assertEquals(1, stale.stale.size)
        assertEquals(synced, b.store.load().lastChanges)
        b.engine.apply(folder, emptyList())
        assertEquals(synced, b.store.load().lastChanges)

        val kept = b.engine.apply(folder, listOf(Pick(question, question.local.value)))

        assertEquals(emptyList(), kept.stale)
        assertEquals(synced, b.store.load().lastChanges)
        assertEquals(synced, b.engine.undo(folder).restored)
    }

    @Test
    fun `a keep that fails to write keeps the undo data`() = runBlocking {
        val a = Device("aaaaaa")
        val b = Device("bbbbbb")
        a.read(1, 0.5f, 10)
        a.setCategories("One")
        b.setCategories("Two")
        a.engine.publish(folder)
        val report = b.engine.sync(folder)
        val question = report.questions.single()
        // Dropping the table makes the library write throw after the intent is saved.
        b.database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail BEFORE INSERT ON book_categories BEGIN SELECT RAISE(ABORT, 'boom'); END",
        )

        assertTrue(runCatching { b.engine.apply(folder, listOf(Pick(question, question.local.value))) }.isFailure)

        assertEquals(report.applied, b.store.load().lastChanges)
    }

    @Test
    fun `the last of two picks for one item counts`() = runBlocking {
        val a = Device("aaaaaa")
        val b = Device("bbbbbb")
        val question = backwardsQuestion(a, b)

        val result = a.engine.apply(
            folder,
            listOf(Pick(question, question.choices[1].item.value), Pick(question, question.local.value)),
        )

        assertEquals(emptyList(), result.stale)
        assertEquals(emptyList(), result.applied)
        assertEquals(emptyList(), a.store.load().lastChanges.filter { it.after == question.choices[1].item.value })
        assertEquals("a-1" to 0.8f, a.progress())
    }

    @Test
    fun `a position that was not offered is not applied`() = runBlocking {
        val a = Device("aaaaaa")
        val b = Device("bbbbbb")
        val question = backwardsQuestion(a, b)
        for (value in listOf(PositionValue(1, 0.1f, false, 1), CategoriesValue(listOf("x")))) {
            val result = a.engine.apply(folder, listOf(Pick(question, value)))

            assertEquals(listOf(question), result.stale)
            assertEquals("a-1" to 0.8f, a.progress())
        }
    }

    @Test
    fun `undo restores values as a new version`() = runBlocking {
        val a = Device("aaaaaa")
        val b = Device("bbbbbb")
        val question = backwardsQuestion(a, b)
        val applied = a.engine.apply(folder, listOf(Pick(question, question.choices[1].item.value))).applied
        val version = a.record().version.getValue("aaaaaa")

        val result = a.engine.undo(folder)

        assertEquals(UndoResult(applied, emptyList()), result)
        assertEquals("a-1" to 0.8f, a.progress())
        assertEquals(version + 1, a.record().version.getValue("aaaaaa"))
        assertEquals(emptyList(), a.store.load().lastChanges)
        assertEquals(emptyList(), a.store.load().pending)
        val published = folder.files.getValue(syncFileName("aaaaaa", "aaaaaa"))
        assertEquals(a.record(), SyncFileCodec.decode(published).books.single().position)
    }

    @Test
    fun `undo skips items changed since`() = runBlocking {
        val a = Device("aaaaaa")
        val b = Device("bbbbbb")
        a.read(1, 0.5f, 10)
        a.engine.publish(folder)
        val applied = b.engine.sync(folder).applied
        b.read(1, 0.9f, 20)

        val result = b.engine.undo(folder)

        assertEquals(UndoResult(emptyList(), applied), result)
        assertEquals("a-1" to 0.9f, b.progress())
        assertEquals(emptyList(), b.store.load().lastChanges)
    }

    @Test
    fun `undo covers automatic changes from last sync`() = runBlocking {
        val a = Device("aaaaaa")
        val b = Device("bbbbbb")
        a.read(1, 0.5f, 10)
        a.setCategories("New")
        a.engine.publish(folder)
        val applied = b.engine.sync(folder).applied
        folder.writeFails = true

        val result = b.engine.undo(folder)

        assertEquals(2, applied.size)
        assertEquals(UndoResult(applied, emptyList(), "Write refused"), result)
        assertNull(b.progress())
        assertEquals(emptySet<String>(), b.categories())
        // Created categories remain.
        assertTrue(b.database.categories().all().any { it.name == "New" })
    }

    @Test
    fun `second undo does nothing`() = runBlocking {
        val a = Device("aaaaaa")
        val b = Device("bbbbbb")
        a.read(1, 0.5f, 10)
        a.engine.publish(folder)
        b.engine.sync(folder)
        b.engine.undo(folder)
        val state = b.store.load()

        assertEquals(UndoResult(emptyList(), emptyList()), b.engine.undo(folder))

        assertEquals(state, b.store.load())
        assertNull(b.progress())
    }
}
