package dev.reader.data.sync

import dev.reader.core.sync.CategoriesValue
import dev.reader.core.sync.MAX_VERSION_COUNT
import dev.reader.core.sync.PositionValue
import dev.reader.core.sync.Versioned
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Required
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val SYNC_FORMAT = 1
const val MAX_SYNC_FILE_BYTES = 5 * 1024 * 1024

/** Change histories are copied into this install's own file, so their size is limited too. */
internal const val MAX_VERSION_ENTRIES = 64
private const val MAX_ID_LENGTH = 64
private const val MAX_TITLE = 300

private const val FILE_PREFIX = "reader-sync-"
private const val FILE_SUFFIX = ".json"
private const val MAX_DEVICE_NAME = 40
private const val SHORT_ID_LENGTH = 6
private const val MAX_ERROR_LENGTH = 200
private const val MAX_CATEGORY_NAME = 100

@Serializable
data class SyncChapter(val index: Int, val item: Versioned)

/** A null [categories] means category sync was skipped for this book. */
@Serializable
data class SyncBook(
    val contentHash: String,
    val title: String,
    val signature: String,
    val position: Versioned,
    val chapters: List<SyncChapter>,
    val categories: Versioned?,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SyncFile(
    @Required val format: Int = SYNC_FORMAT,
    val deviceId: String,
    val deviceName: String,
    val devices: Map<String, String>,
    val publishedAt: Long,
    val books: List<SyncBook>,
)

class SyncFileException(message: String) : Exception(message)

/** Sync files come from a shared folder, so [decode] treats every byte as untrusted. */
object SyncFileCodec {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = " "
    }

    fun encode(file: SyncFile): ByteArray = json.encodeToString(SyncFile.serializer(), file).encodeToByteArray()

    @Suppress("ThrowsCount") // every failure at this boundary becomes a SyncFileException
    fun decode(bytes: ByteArray): SyncFile {
        if (bytes.size > MAX_SYNC_FILE_BYTES) throw SyncFileException("File is too large")
        val file = try {
            json.decodeFromString(SyncFile.serializer(), bytes.decodeToString(throwOnInvalidSequence = true))
                .also(::validate)
        } catch (e: Exception) {
            val message = "File cannot be read: ${e.message}".filterNot { it.isISOControl() }
            throw SyncFileException(message.take(MAX_ERROR_LENGTH))
        } catch (e: StackOverflowError) {
            throw SyncFileException("File is nested too deeply")
        } catch (e: OutOfMemoryError) {
            throw SyncFileException("File needs too much memory to read")
        }
        return file.copy(
            deviceName = sanitizeDeviceName(file.deviceName),
            devices = file.devices.mapValues { sanitizeDeviceName(it.value) },
            books = file.books.map { normalizeCategories(it).copy(title = cleanText(it.title, MAX_TITLE)) },
        )
    }

    private fun normalizeCategories(book: SyncBook): SyncBook {
        val categories = book.categories ?: return book
        val value = categories.value as? CategoriesValue ?: return book
        return book.copy(categories = categories.copy(value = CategoriesValue(normalizeCategoryNames(value.names))))
    }

    private fun validate(file: SyncFile) {
        require(file.format == SYNC_FORMAT) { "Unknown format version ${file.format}" }
        require(isId(file.deviceId)) { "Bad device ID" }
        require(file.publishedAt >= 0) { "Negative publish time" }
        require(file.books.distinctBy { it.contentHash }.size == file.books.size) { "Duplicate book" }
        for (book in file.books) {
            checkPosition(book.position)
            require(book.chapters.distinctBy { it.index }.size == book.chapters.size) { "Duplicate chapter" }
            for (chapter in book.chapters) {
                require(chapter.index >= 0) { "Negative chapter index" }
                checkPosition(chapter.item)
            }
            val categories = book.categories ?: continue
            require(categories.value == null || categories.value is CategoriesValue) { "Expected categories" }
            checkVersion(categories)
        }
    }

    private fun checkPosition(item: Versioned) {
        checkVersion(item)
        val value = item.value ?: return
        require(value is PositionValue) { "Expected a position" }
        require(value.chapter >= 0) { "Negative chapter" }
        require(value.position in 0f..1f) { "Position out of range" } // also false for NaN
        require(value.updatedAt >= 0) { "Negative change time" }
    }

    private fun checkVersion(item: Versioned) {
        require(item.version.size <= MAX_VERSION_ENTRIES) { "Change history is too long" }
        require(item.version.keys.all(::isId)) { "Bad device ID in change history" }
        require(item.version.values.all { it in 0..MAX_VERSION_COUNT }) { "Change count out of range" }
    }

    private fun isId(id: String) = id.isNotBlank() && id.length <= MAX_ID_LENGTH
}

/** [raw] without control characters or outer spaces, at most [max] characters. */
private fun cleanText(raw: String, max: Int): String {
    var text = raw.filterNot { it.isISOControl() }.trim().take(max)
    if (text.lastOrNull()?.isHighSurrogate() == true) text = text.dropLast(1)
    return text.trim()
}

internal fun cleanCategoryName(raw: String) = cleanText(raw, MAX_CATEGORY_NAME)

/** False for names that would show as empty: only spaces, control or invisible format characters. */
internal fun isVisibleName(name: String) =
    name.any { !it.isWhitespace() && !it.isISOControl() && it.category != CharCategory.FORMAT }

/** The visible [names], cleaned, with only the first of those differing by case. */
internal fun normalizeCategoryNames(names: List<String>): List<String> =
    names.map(::cleanCategoryName).filter(::isVisibleName).distinctBy { it.lowercase() }

/** A device name that is safe to display: no control characters, at most 40 characters, never blank. */
fun sanitizeDeviceName(raw: String): String = cleanText(raw, MAX_DEVICE_NAME).ifEmpty { "Device" }

fun syncFileName(deviceName: String, deviceId: String): String {
    val name = sanitizeDeviceName(deviceName).lowercase().replace(Regex("[^a-z0-9]+"), "-")
    return "$FILE_PREFIX$name-${deviceId.take(SHORT_ID_LENGTH)}$FILE_SUFFIX"
}

/** Whether [syncFileName] could have made [name] for this ID, under any device name. */
internal fun isOwnNamingScheme(name: String, deviceId: String): Boolean {
    val shortId = Regex.escape(deviceId.take(SHORT_ID_LENGTH))
    return Regex("$FILE_PREFIX[a-z0-9-]+-$shortId${Regex.escape(FILE_SUFFIX)}").matches(name)
}

/** Includes Syncthing conflict copies, but not its temporary files. */
fun isSyncFileName(name: String): Boolean = name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX)
