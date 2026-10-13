package dev.reader.data.repo

import dev.reader.data.store.PreferencesFile
import dev.reader.data.store.ReaderStores
import kotlinx.coroutines.flow.Flow

class SettingsRepository(private val stores: ReaderStores) {

    val preferences: Flow<PreferencesFile> = stores.preferences.data

    suspend fun update(transform: (PreferencesFile) -> PreferencesFile) {
        stores.preferences.updateData { transform(it) }
    }
}
