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
private const val TOO_MANY = "Too many sync files"
private const val MAX_PULL_FILES = 50
private const val MAX_PULL_BYTES = 40L * 1024 * 1024

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

/** [value] is one of the question's choices, a combined set or a hand-ticked set. */
data class Pick(val question: Question, val value: ItemValue?)

/** [stale] picks were not applied. [applied] leaves out picks that kept this device's value. */
data class ApplyResult(val applied: List<Change>, val stale: List<Question>, val publishError: String? = null)

data class UndoResult(val restored: List<Change>, val skipped: List<Change>, val publishError: String? = null)

/** [files] hold only books in this library; the rest are listed in [skipped]. */
private class Pulled(
    val files: List<SyncFile>,
    val devices: List<DeviceInfo>,
    val rejected: List<RejectedFile>,
    val skipped: List<SkippedBook>,
)

/** Limits what one pull reads, since every decoded file is held in memory at once. */
private class PullLimit {
    private var files = 0
    private var bytes = 0L

    suspend fun read(folder: SyncFolder, document: SyncDocument): ByteArray {
        if (document.size > MAX_SYNC_FILE_BYTES) throw SyncFileException(TOO_LARGE)
        var content: ByteArray? = null
        if (files < MAX_PULL_FILES && bytes + document.size <= MAX_PULL_BYTES) {
            files++
            content = folder.read(document.name)
            // The listed size can be wrong, so the larger of the two counts.
            bytes += maxOf(document.size, content.size.toLong())
        }
        return content?.takeIf { bytes <= MAX_PULL_BYTES } ?: throw SyncFileException(TOO_MANY)
    }
}

/** The outcome of comparing the library with the pulled files; nothing has been written yet. */
private class Merge(
    val records: Map<ItemKey, Versioned>,
    val applied: Map<ItemKey, Versioned>,
    val changes: List<Change>,
    val questions: List<Question>,
    val skipped: List<SkippedBook>,
    val candidates: Map<ItemKey, List<Candidate>>,
) {
    /** What to record for [pick], or null if it is stale or cannot be applied. */
    fun chosen(pick: Pick, snapshot: LibrarySnapshot, deviceId: String, oneCategory: Boolean): Versioned? {
        val key = pick.question.key
        val question = questions.find { it.key == key }?.takeIf { unchanged(pick.question, it) } ?: return null
        val value = readable(key, snapshot.books.getValue(key.bookHash), Versioned(pick.value)) ?: return null
        if (oneCategory && ((value.value as? CategoriesValue)?.names?.size ?: 0) > 1) return null
        val all = listOf(question.local) + candidates.getValue(key).map { it.item }
        return resolve(value, all, deviceId, manual = true).takeIf { publishable(it, deviceId) }
    }

    /** Whether [now] still offers what [shown] did. */
    private fun unchanged(shown: Question, now: Question): Boolean {
        fun same(a: Versioned, b: Versioned) = a.version == b.version && sameValue(a.value, b.value)
        return same(shown.local, now.local) && shown.choices.size == now.choices.size &&
            shown.choices.all { old -> now.choices.any { same(old.item, it.item) } }
    }
}

private fun LibrarySnapshot.items(): Map<ItemKey, ItemValue?> = buildMap {
    for (book in books.values) {
        put(ItemKey(book.hash, ItemKind.BOOK_POSITION), book.position)
        book.chapters.forEach { (index, value) -> put(ItemKey(book.hash, ItemKind.CHAPTER, index), value) }
        book.categories?.let { put(ItemKey(book.hash, ItemKind.CATEGORIES), it) }
    }
}

private fun List<ItemRecord>.byKey() = associate { it.key to it.item }

private fun Map<ItemKey, Versioned>.toRecords() = map { ItemRecord(it.key, it.value) }

/**
 * Counts local changes since the last time. Records, and pending records, of items that are absent or hidden now
 * are kept.
 */
private fun recorded(state: SyncState, snapshot: LibrarySnapshot): SyncState {
    val records = state.records.byKey().toMutableMap()
    val pending = state.pending.byKey()
    val items = snapshot.items()
    for ((key, current) in items) {
        // An apply that reached the library before its records were saved is not a local change.
        val intended = pending[key]?.takeIf { sameValue(it.value, current) }
        records[key] = record(key.kind, intended ?: records[key], current, state.deviceId)
    }
    return state.copy(records = records.toRecords(), pending = state.pending.filter { it.key !in items })
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
        val book = snapshot.books[peer.contentHash] ?: continue // removed while the files were being read
        if (book.signature != peer.signature) {
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

/** Merged histories add up across files, and this install's own count must still fit after its next change. */
private fun publishable(item: Versioned, deviceId: String) = (item.version.keys + deviceId).size <= MAX_VERSION_ENTRIES

private fun historyTooLong(title: String) = SkippedBook(title, "Change history is too long")

private fun merge(state: SyncState, snapshot: LibrarySnapshot, files: List<SyncFile>, oneCategory: Boolean): Merge {
    val offers = offers(snapshot, files)
    val records = state.records.byKey().toMutableMap()
    val applied = mutableMapOf<ItemKey, Versioned>()
    val changes = mutableListOf<Change>()
    val questions = mutableListOf<Question>()
    val tooLong = linkedSetOf<SkippedBook>()
    for (key in snapshot.items().keys) {
        val others = offers.candidates[key] ?: continue
        val local = records.getValue(key)
        val title = snapshot.books.getValue(key.bookHash).title
        when (val decision = decide(state.deviceId, local, others, oneCategory)) {
            is Decision.Keep -> {
                val kept = local.copy(version = decision.item.version, rewound = decision.item.rewound)
                if (publishable(kept, state.deviceId)) records[key] = kept else tooLong += historyTooLong(title)
            }
            is Decision.Apply -> {
                val all = listOf(local) + others.map { it.item }
                val resolved = resolve(decision.item, all, state.deviceId, manual = false)
                if (publishable(resolved, state.deviceId)) {
                    applied[key] = resolved
                    changes += Change(key, title, local.value, decision.item.value, decision.sourceDeviceId)
                } else {
                    tooLong += historyTooLong(title)
                }
            }
            is Decision.Ask -> questions += Question(key, title, decision.reason, decision.choices, local)
        }
    }
    return Merge(records, applied, changes, questions, snapshot.skipped + offers.skipped + tooLong, offers.candidates)
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

    suspend fun state(): SyncState = locked { store.load() }

    suspend fun updateState(transform: (SyncState) -> SyncState) = locked { store.save(transform(store.load())) }

    suspend fun sync(folder: SyncFolder, publish: Boolean = true): SyncReport = locked {
        val pulled = pull(folder)
        // Read the library after the files, which can be slow, to keep the gap before writing short.
        val snapshot = library.snapshot()
        val state = recorded(store.load(), snapshot)
        val merge = merge(state, snapshot, pulled.files, oneCategory())

        val (applied, _) = write(state, merge, syncedAt = now())

        val publishError = if (publish) publishAfterWriting(folder, pulled.files) else null
        report(state, pulled, merge).copy(applied = applied, publishError = publishError)
    }

    /** Applies the choices that still hold, and publishes. */
    suspend fun apply(folder: SyncFolder, picks: List<Pick>): ApplyResult = locked {
        val pulled = pull(folder)
        val snapshot = library.snapshot()
        val state = recorded(store.load(), snapshot)
        val single = oneCategory()
        val merge = merge(state, snapshot, pulled.files, single)

        val resolved = mutableMapOf<ItemKey, Versioned>()
        val changes = mutableListOf<Change>()
        for (pick in picks) {
            val item = merge.chosen(pick, snapshot, state.deviceId, single) ?: continue
            val key = pick.question.key
            val before = pick.question.local.value
            resolved[key] = item
            if (!sameValue(before, item.value)) {
                // The first non-local choice holding the value, if any, is where it came from.
                val source = pick.question.choices.drop(1).find { sameValue(it.item.value, item.value) }
                changes += Change(key, pick.question.title, before, item.value, source?.deviceIds?.first())
            }
        }
        // Safe changes found while re-reading are not picks, so they wait for the next sync.
        val intended = Merge(merge.records, resolved, changes, merge.questions, merge.skipped, merge.candidates)
        val (applied, done) = write(state, intended, syncedAt = state.lastSyncAt)

        ApplyResult(
            applied = applied,
            stale = picks.map { it.question }.filter { it.key !in done },
            publishError = publishAfterWriting(folder, pulled.files),
        )
    }

    /** Puts back what the last sync or apply changed, where it has not been changed since, and publishes. */
    suspend fun undo(folder: SyncFolder): UndoResult = locked {
        val loaded = store.load()
        if (loaded.lastChanges.isEmpty()) return@locked UndoResult(emptyList(), emptyList())
        val snapshot = library.snapshot()
        val state = recorded(loaded, snapshot)
        val current = snapshot.items()
        val records = state.records.byKey()
        // A listed change may never have happened (a failed write), so only the current value counts.
        val due = loaded.lastChanges.filter { it.key in current && sameValue(current[it.key], it.after) }
        val restoring = due.associate {
            it.key to record(it.key.kind, records.getValue(it.key), it.before, state.deviceId)
        }
        val intended = Merge(records, restoring, due, emptyList(), emptyList(), emptyMap())

        val (intent, done) = library.transaction { writeUnchanged(state, intended) }
        store.save(
            intent.copy(records = (records + done).toRecords(), pending = state.pending, lastChanges = emptyList()),
        )

        val (restored, skipped) = loaded.lastChanges.partition { it.key in done }
        UndoResult(restored, skipped, publishAfterWriting(folder, emptyList()))
    }

    /** Returns why publishing failed, if it did. */
    private suspend fun publishAfterWriting(folder: SyncFolder, peers: List<SyncFile>): String? = try {
        publishFile(folder, peers.associate { it.deviceId to it.deviceName })
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) { // the changes are applied, so any failure here must still be reported with them
        e.message ?: "Could not publish"
    }

    /** Writes what [merge] applies, saves the outcome and returns the changes and items that took effect. */
    private suspend fun write(state: SyncState, merge: Merge, syncedAt: Long?): Pair<List<Change>, Set<ItemKey>> {
        // One transaction, so the user cannot change an item between the check that it is unchanged and the write.
        val (intent, done) = library.transaction { writeUnchanged(state, merge) }
        val changes = intent.lastChanges.filter { it.key in done }
        store.save(
            intent.copy(
                records = (merge.records + done).toRecords(),
                pending = state.pending,
                lastChanges = changes,
                lastSyncAt = syncedAt,
                questionCount = merge.questions.count { it.key !in done },
            ),
        )
        return changes to done.keys
    }

    /** Returns the state saved before writing and the records of the writes that took effect. */
    private suspend fun writeUnchanged(state: SyncState, merge: Merge): Pair<SyncState, Map<ItemKey, Versioned>> {
        // The user may have moved on since the merge was decided; those items wait for the next sync.
        val fresh = library.snapshot()
        val current = fresh.items()
        val decidedOn = state.records.byKey()
        val due = merge.applied.filterKeys { it in current && sameValue(current[it], decidedOn.getValue(it).value) }

        // Intended records are saved first so that dying mid-apply cannot turn them into local changes.
        val intent = state.copy(
            pending = state.pending + due.toRecords(),
            lastChanges = merge.changes.filter { it.key in due },
        )
        store.save(intent)
        if (due.isEmpty()) return intent to due
        library.write(fresh, due.mapValues { it.value.value })
        val after = library.snapshot().items()
        // The library can ignore a write; recording it anyway would look like the user undid it. An item
        // that is now hidden (categories turned ambiguous) was written, so it keeps the new record.
        return intent to due.filter { (key, item) -> key !in after || sameValue(after[key], item.value) }
    }

    /** Finds the open questions without changing the library, the saved state or the folder. */
    suspend fun check(folder: SyncFolder): SyncReport = locked {
        val pulled = pull(folder)
        val snapshot = library.snapshot()
        val state = recorded(store.load(), snapshot)
        report(state, pulled, merge(state, snapshot, pulled.files, oneCategory()))
    }

    suspend fun reset() = locked { store.reset() }

    private fun report(state: SyncState, pulled: Pulled, merge: Merge) = SyncReport(
        questions = merge.questions,
        skipped = merge.skipped + pulled.skipped,
        rejected = pulled.rejected,
        devices = pulled.devices,
        // A file's own name for itself wins over what other files call that device.
        deviceNames = pulled.files.fold(emptyMap<String, String>()) { names, file -> names + file.devices } +
            pulled.files.associate { it.deviceId to it.deviceName } +
            (state.deviceId to state.deviceName),
    )

    private suspend fun pull(folder: SyncFolder): Pulled {
        val state = store.load()
        val ownName = syncFileName(state.deviceName, state.deviceId)
        val known = library.snapshot().books.keys
        val files = mutableListOf<SyncFile>()
        val devices = mutableListOf<DeviceInfo>()
        val rejected = mutableListOf<RejectedFile>()
        val skipped = linkedSetOf<SkippedBook>()
        val limit = PullLimit()
        // Sorted so that, if the limits are reached, conflict copies are the first left out.
        val documents = folder.list()
            .filter { isSyncFileName(it.name) && it.name != ownName }
            .sortedWith(compareBy({ CONFLICT_MARKER in it.name }, { it.name }))
        for (document in documents) {
            try {
                val file = SyncFileCodec.decode(limit.read(folder, document))
                // Other books are dropped now so that they do not stay in memory.
                val (matched, unknown) = file.books.partition { it.contentHash in known }
                unknown.forEach { skipped += SkippedBook(it.title, "Not in this library") }
                files += file.copy(books = matched)
                val conflictCopy = CONFLICT_MARKER in document.name
                devices += DeviceInfo(file.deviceId, file.deviceName, file.publishedAt, conflictCopy)
            } catch (e: IOException) {
                rejected += RejectedFile(document.name, e.message ?: "File cannot be read")
            } catch (e: SyncFileException) {
                rejected += RejectedFile(document.name, e.message.orEmpty())
            }
        }
        return Pulled(files, devices, rejected, skipped.toList())
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
        // The new file is already written, so a failure here only leaves an old file for a later publish to remove.
        val documents = try {
            folder.list()
        } catch (_: IOException) {
            return
        }
        val candidates = documents.filter {
            it.name != ownName && isOwnNamingScheme(it.name, deviceId) && it.size <= MAX_SYNC_FILE_BYTES
        }
        candidates.forEach { deleteIfOwn(folder, it.name, deviceId) }
    }

    private suspend fun deleteIfOwn(folder: SyncFolder, name: String, deviceId: String): Boolean = try {
        val own = SyncFileCodec.decode(folder.read(name)).deviceId == deviceId
        if (own) folder.delete(name)
        own
    } catch (_: IOException) {
        false
    } catch (_: SyncFileException) {
        false
    }
}
