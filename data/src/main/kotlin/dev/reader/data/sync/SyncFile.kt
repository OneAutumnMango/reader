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

private const val FILE_PREFIX = "reader-sync-"
private const val FILE_SUFFIX = ".json"
private const val MAX_DEVICE_NAME = 40
private const val SHORT_ID_LENGTH = 6
private const val MAX_ERROR_LENGTH = 200

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
        )
    }

    private fun validate(file: SyncFile) {
        require(file.format == SYNC_FORMAT) { "Unknown format version ${file.format}" }
        require(file.deviceId.isNotBlank()) { "Missing device ID" }
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
        require(item.version.values.all { it in 0..MAX_VERSION_COUNT }) { "Change count out of range" }
    }
}

/** A device name that is safe to display: no control characters, at most 40 characters, never blank. */
fun sanitizeDeviceName(raw: String): String {
    var name = raw.filterNot { it.isISOControl() }.trim().take(MAX_DEVICE_NAME)
    if (name.lastOrNull()?.isHighSurrogate() == true) name = name.dropLast(1)
    return name.trim().ifEmpty { "Device" }
}

fun syncFileName(deviceName: String, deviceId: String): String {
    val name = sanitizeDeviceName(deviceName).lowercase().replace(Regex("[^a-z0-9]+"), "-")
    return "$FILE_PREFIX$name-${deviceId.take(SHORT_ID_LENGTH)}$FILE_SUFFIX"
}

/** Includes Syncthing conflict copies, but not its temporary files. */
fun isSyncFileName(name: String): Boolean = name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX)
