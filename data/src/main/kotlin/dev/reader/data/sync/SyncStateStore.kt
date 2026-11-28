package dev.reader.data.sync

import dev.reader.core.sync.ItemKey
import dev.reader.core.sync.ItemValue
import dev.reader.core.sync.Versioned
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

@Serializable
data class ItemRecord(val key: ItemKey, val item: Versioned)

@Serializable
data class Change(
    val key: ItemKey,
    val title: String,
    val before: ItemValue?,
    val after: ItemValue?,
    val sourceDeviceId: String?,
)

@Serializable
data class SyncState(
    val deviceId: String,
    val deviceName: String,
    val folderUri: String? = null,
    val records: List<ItemRecord> = emptyList(),
    val pending: List<ItemRecord> = emptyList(),
    val lastChanges: List<Change> = emptyList(),
    val lastSyncAt: Long? = null,
    val questionCount: Int = 0,
)

/** This install's sync state. [file] belongs in noBackupFilesDir so that a backup never copies the device ID. */
class SyncStateStore(
    private val file: File,
    private val defaultName: String,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /** A missing or unparsable file gives a fresh identity; a read error is thrown so nothing is overwritten. */
    fun load(): SyncState {
        if (file.exists()) {
            try {
                return Json.decodeFromString(SyncState.serializer(), file.readText())
            } catch (_: SerializationException) {
                // Replaced below.
            } catch (_: IllegalArgumentException) {
                // Replaced below.
            }
        }
        return SyncState(newId(), sanitizeDeviceName(defaultName)).also(::save)
    }

    fun save(state: SyncState) {
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        FileOutputStream(temp).use {
            it.write(Json.encodeToString(SyncState.serializer(), state).encodeToByteArray())
            it.fd.sync()
        }
        if (!temp.renameTo(file)) throw IOException("Could not save sync state")
    }

    fun reset() = save(
        load().copy(
            deviceId = newId(),
            records = emptyList(),
            pending = emptyList(),
            lastChanges = emptyList(),
            questionCount = 0,
        ),
    )
}
