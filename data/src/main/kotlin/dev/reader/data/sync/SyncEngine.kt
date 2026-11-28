package dev.reader.data.sync

import dev.reader.core.sync.AskReason
import dev.reader.core.sync.Candidate
import dev.reader.core.sync.CategoriesValue
import dev.reader.core.sync.Choice
import dev.reader.core.sync.Decision
import dev.reader.core.sync.ItemKey
import dev.reader.core.sync.ItemKind
import dev.reader.core.sync.ItemValue
import dev.reader.core.sync.PositionValue
import dev.reader.core.sync.Versioned
import dev.reader.core.sync.decide
import dev.reader.core.sync.record
import dev.reader.core.sync.resolve
import dev.reader.core.sync.sameValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

private const val CONFLICT_MARKER = ".sync-conflict-"
private const val TOO_LARGE = "File is too large"

data class SyncDocument(val name: String, val size: Long)

interface SyncFolder {
    /** Throws [IOException] when the folder is unavailable. */
    suspend fun list(): List<SyncDocument>
    suspend fun read(name: String): ByteArray
    suspend fun write(name: String, bytes: ByteArray)
    suspend fun delete(name: String)
}

data class DeviceInfo(val id: String, val name: String, val publishedAt: Long, val conflictCopy: Boolean)

data class Question(
    val key: ItemKey,
    val title: String,
    val reason: AskReason,
    val choices: List<Choice>,
    val local: Versioned,
)

data class RejectedFile(val name: String, val reason: String)

data class SyncReport(
    val applied: List<Change> = emptyList(),
    val questions: List<Question> = emptyList(),
    val skipped: List<SkippedBook> = emptyList(),
    val rejected: List<RejectedFile> = emptyList(),
    val devices: List<DeviceInfo> = emptyList(),
    val deviceNames: Map<String, String> = emptyMap(),
    val publishError: String? = null,
)

private class Pulled(val files: List<SyncFile>, val devices: List<DeviceInfo>, val rejected: List<RejectedFile>)

/** The outcome of comparing the library with the pulled files; nothing has been written yet. */
private class Merge(
    val records: Map<ItemKey, Versioned>,
    val applied: Map<ItemKey, Versioned>,
    val changes: List<Change>,
    val questions: List<Question>,
    val skipped: List<SkippedBook>,
)

private fun LibrarySnapshot.items(): Map<ItemKey, ItemValue?> = buildMap {
    for (book in books.values) {
        put(ItemKey(book.hash, ItemKind.BOOK_POSITION), book.position)
        book.chapters.forEach { (index, value) -> put(ItemKey(book.hash, ItemKind.CHAPTER, index), value) }
        book.categories?.let { put(ItemKey(book.hash, ItemKind.CATEGORIES), it) }
    }
}

private fun List<ItemRecord>.byKey() = associate { it.key to it.item }

private fun Map<ItemKey, Versioned>.toRecords() = map { ItemRecord(it.key, it.value) }

/** Counts local changes since the last time. Records of books that are absent or skipped now are kept. */
private fun recorded(state: SyncState, snapshot: LibrarySnapshot): SyncState {
    val records = state.records.byKey().toMutableMap()
    val pending = state.pending.byKey()
    for ((key, current) in snapshot.items()) {
        // An apply that reached the library before its records were saved is not a local change.
        val intended = pending[key]?.takeIf { sameValue(it.value, current) }
        records[key] = record(key.kind, intended ?: records[key], current, state.deviceId)
    }
    return state.copy(records = records.toRecords(), pending = emptyList())
}

/**
 * A peer's value in the form the library would read it back, or null if the library cannot hold it.
 * Anything else would be counted as a local change after applying.
 */
private fun readable(key: ItemKey, book: LocalBook, item: Versioned): Versioned? = when (key.kind) {
    ItemKind.BOOK_POSITION -> {
        val value = item.value as? PositionValue
        item.takeIf { value == null || value.chapter in book.chapterIds }
    }
    ItemKind.CHAPTER -> {
        val value = item.value as? PositionValue ?: PositionValue(key.chapter, 0f, false, 0)
        item.copy(value = value.copy(chapter = key.chapter))
    }
    ItemKind.CATEGORIES -> {
        val names = (item.value as? CategoriesValue)?.names.orEmpty()
        item.copy(value = CategoriesValue(normalizeCategoryNames(names)))
    }
}

private class Offers(val candidates: Map<ItemKey, List<Candidate>>, val skipped: Set<SkippedBook>)

/** What the [files] hold for each item of the books that match this library. */
private fun offers(snapshot: LibrarySnapshot, files: List<SyncFile>): Offers {
    val skipped = linkedSetOf<SkippedBook>()
    val candidates = mutableMapOf<ItemKey, MutableList<Candidate>>()
    for ((deviceId, peer) in files.flatMap { file -> file.books.map { file.deviceId to it } }) {
        val book = snapshot.books[peer.contentHash]
        if (book == null) {
            skipped += SkippedBook(peer.title, "Not in this library")
        } else if (book.signature != peer.signature) {
            skipped += SkippedBook(book.title, "Chapters don't match")
        } else {
            for ((key, item) in peer.items()) {
                val value = readable(key, book, item) ?: continue
                candidates.getOrPut(key) { mutableListOf() } += Candidate(deviceId, value)
            }
        }
    }
    return Offers(candidates, skipped)
}

private fun SyncBook.items(): List<Pair<ItemKey, Versioned>> = buildList {
    add(ItemKey(contentHash, ItemKind.BOOK_POSITION) to position)
    chapters.forEach { add(ItemKey(contentHash, ItemKind.CHAPTER, it.index) to it.item) }
    categories?.let { add(ItemKey(contentHash, ItemKind.CATEGORIES) to it) }
}

private fun merge(state: SyncState, snapshot: LibrarySnapshot, files: List<SyncFile>, oneCategory: Boolean): Merge {
    val offers = offers(snapshot, files)
    val records = state.records.byKey().toMutableMap()
    val applied = mutableMapOf<ItemKey, Versioned>()
    val changes = mutableListOf<Change>()
    val questions = mutableListOf<Question>()
    for (key in snapshot.items().keys) {
        val others = offers.candidates[key] ?: continue
        val local = records.getValue(key)
        val title = snapshot.books.getValue(key.bookHash).title
        when (val decision = decide(state.deviceId, local, others, oneCategory)) {
            is Decision.Keep ->
                records[key] = local.copy(version = decision.item.version, rewound = decision.item.rewound)
            is Decision.Apply -> {
                val all = listOf(local) + others.map { it.item }
                applied[key] = resolve(decision.item, all, state.deviceId, manual = false)
                changes += Change(key, title, local.value, decision.item.value, decision.sourceDeviceId)
            }
            is Decision.Ask -> questions += Question(key, title, decision.reason, decision.choices, local)
        }
    }
    return Merge(records, applied, changes, questions, snapshot.skipped + offers.skipped)
}

/** Publishes this install's sync file and merges the others into the library. */
class SyncEngine(
    private val library: SyncLibrary,
    private val store: SyncStateStore,
    private val oneCategory: suspend () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    private suspend fun <T> locked(block: suspend () -> T): T =
        mutex.withLock { withContext(Dispatchers.IO) { block() } }

    suspend fun publish(folder: SyncFolder) = locked { publishFile(folder, emptyMap()) }

    suspend fun sync(folder: SyncFolder, publish: Boolean = true): SyncReport = locked {
        val pulled = pull(folder, store.load())
        // Read the library after the files, which can be slow, to keep the gap before writing short.
        val snapshot = library.snapshot()
        val state = recorded(store.load(), snapshot)
        val merge = merge(state, snapshot, pulled.files, oneCategory())

        val applied = write(state, merge)

        var publishError: String? = null
        if (publish) {
            try {
                publishFile(folder, pulled.files.associate { it.deviceId to it.deviceName })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { // the changes are applied, so any failure here must still be reported with them
                publishError = e.message ?: "Could not publish"
            }
        }
        report(state, pulled, merge).copy(applied = applied, publishError = publishError)
    }

    /** Writes what [merge] decided to apply, saves the outcome and returns the changes that took effect. */
    private suspend fun write(state: SyncState, merge: Merge): List<Change> {
        // The user may have moved on since the merge was decided; those items wait for the next sync.
        val fresh = library.snapshot()
        val current = fresh.items()
        val decidedOn = state.records.byKey()
        val due = merge.applied.filterKeys { it in current && sameValue(current[it], decidedOn.getValue(it).value) }
        val dueChanges = merge.changes.filter { it.key in due }

        // Intended records are saved first so that dying mid-apply cannot turn them into local changes.
        val intent = state.copy(pending = due.toRecords(), lastChanges = dueChanges)
        store.save(intent)
        var done = due
        if (due.isNotEmpty()) {
            library.write(fresh, due.mapValues { it.value.value })
            val after = library.snapshot().items()
            // The library can ignore a write; recording it anyway would look like the user undid it. An item
            // that is now hidden (categories turned ambiguous) was written, so it keeps the new record.
            done = due.filter { (key, item) -> key !in after || sameValue(after[key], item.value) }
        }
        val changes = dueChanges.filter { it.key in done }
        store.save(
            intent.copy(
                records = (merge.records + done).toRecords(),
                pending = emptyList(),
                lastChanges = changes,
                lastSyncAt = now(),
                questionCount = merge.questions.size,
            ),
        )
        return changes
    }

    /** Finds the open questions without changing the library, the saved state or the folder. */
    suspend fun check(folder: SyncFolder): SyncReport = locked {
        val pulled = pull(folder, store.load())
        val snapshot = library.snapshot()
        val state = recorded(store.load(), snapshot)
        report(state, pulled, merge(state, snapshot, pulled.files, oneCategory()))
    }

    suspend fun reset() = locked { store.reset() }

    private fun report(state: SyncState, pulled: Pulled, merge: Merge) = SyncReport(
        questions = merge.questions,
        skipped = merge.skipped,
        rejected = pulled.rejected,
        devices = pulled.devices,
        // A file's own name for itself wins over what other files call that device.
        deviceNames = pulled.files.fold(emptyMap<String, String>()) { names, file -> names + file.devices } +
            pulled.files.associate { it.deviceId to it.deviceName } +
            (state.deviceId to state.deviceName),
    )

    private suspend fun pull(folder: SyncFolder, state: SyncState): Pulled {
        val ownName = syncFileName(state.deviceName, state.deviceId)
        val files = mutableListOf<SyncFile>()
        val devices = mutableListOf<DeviceInfo>()
        val rejected = mutableListOf<RejectedFile>()
        for (document in folder.list().filter { isSyncFileName(it.name) && it.name != ownName }) {
            if (document.size > MAX_SYNC_FILE_BYTES) {
                rejected += RejectedFile(document.name, TOO_LARGE)
                continue
            }
            try {
                val file = SyncFileCodec.decode(folder.read(document.name))
                files += file
                val conflictCopy = CONFLICT_MARKER in document.name
                devices += DeviceInfo(file.deviceId, file.deviceName, file.publishedAt, conflictCopy)
            } catch (e: IOException) {
                rejected += RejectedFile(document.name, e.message ?: "File cannot be read")
            } catch (e: SyncFileException) {
                rejected += RejectedFile(document.name, e.message.orEmpty())
            }
        }
        return Pulled(files, devices, rejected)
    }

    /** Must be called under the lock. */
    private suspend fun publishFile(folder: SyncFolder, peerNames: Map<String, String>) {
        val snapshot = library.snapshot()
        val state = recorded(store.load(), snapshot)
        store.save(state)

        val records = state.records.byKey()
        val books = snapshot.books.values.map { book ->
            SyncBook(
                contentHash = book.hash,
                title = book.title,
                signature = book.signature,
                position = records.getValue(ItemKey(book.hash, ItemKind.BOOK_POSITION)),
                chapters = book.chapters.keys.sorted().mapNotNull { index ->
                    val item = records.getValue(ItemKey(book.hash, ItemKind.CHAPTER, index))
                    if (item.version.isEmpty()) null else SyncChapter(index, item)
                },
                categories = book.categories?.let { records.getValue(ItemKey(book.hash, ItemKind.CATEGORIES)) },
            )
        }
        val bytes = SyncFileCodec.encode(
            SyncFile(
                deviceId = state.deviceId,
                deviceName = state.deviceName,
                devices = peerNames + (state.deviceId to state.deviceName),
                publishedAt = now(),
                books = books,
            ),
        )
        // Other devices would reject it, so keep the previous file instead.
        if (bytes.size > MAX_SYNC_FILE_BYTES) throw IOException("Sync file is too large to publish")
        val ownName = syncFileName(state.deviceName, state.deviceId)
        folder.write(ownName, bytes)
        deleteOldFiles(folder, ownName, state.deviceId)
    }

    /** Removes files this install wrote under an earlier name. Anything not provably its own is kept. */
    private suspend fun deleteOldFiles(folder: SyncFolder, ownName: String, deviceId: String) {
        // Every name this install has used ends the same way as its current one.
        val ending = ownName.substring(ownName.lastIndexOf('-'))
        for (document in folder.list()) {
            val name = document.name
            val candidate = name != ownName && isSyncFileName(name) && name.endsWith(ending) &&
                CONFLICT_MARKER !in name && document.size <= MAX_SYNC_FILE_BYTES
            if (!candidate) continue
            val owner = try {
                SyncFileCodec.decode(folder.read(name)).deviceId
            } catch (_: IOException) {
                null
            } catch (_: SyncFileException) {
                null
            }
            if (owner == deviceId) folder.delete(name)
        }
    }
}
