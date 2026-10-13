package dev.reader.data.store

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

/** The one file the app persists outside Room: themes, tap zones, and other app settings. */
class ReaderStores(
    fileSystem: FileSystem,
    directory: Path,
    scope: CoroutineScope,
    json: Json = STORE_JSON,
) {
    val preferences: DataStore<PreferencesFile> = run {
        val jsonSerializer = JsonSerializer(PreferencesFile.serializer(), PreferencesFile(), json)
        DataStoreFactory.create(
            storage = OkioStorage(fileSystem, jsonSerializer) { directory / PREFERENCES_FILE },
            corruptionHandler = ReplaceFileCorruptionHandler { PreferencesFile() },
            scope = scope,
        )
    }

    private companion object {
        const val PREFERENCES_FILE = "preferences.json"

        /** Not pretty-printed: this is a machine file, unlike an export. */
        val STORE_JSON = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }
}
