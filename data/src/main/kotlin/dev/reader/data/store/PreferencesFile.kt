package dev.reader.data.store

import dev.reader.core.reader.ReaderSettings
import dev.reader.core.theme.AppTheme
import dev.reader.core.theme.ReaderTheme
import kotlinx.serialization.Serializable

/** Bumped when a stored shape changes in a way that needs migrating. */
const val STORE_VERSION: Int = 1

@Serializable
data class PreferencesFile(
    val version: Int = STORE_VERSION,
    val appTheme: AppTheme = AppTheme(),
    val readerSettings: ReaderSettings = ReaderSettings(),
    val themePresets: List<ReaderTheme> = ReaderTheme.builtIns,
    val activeThemePresetName: String = ReaderTheme.Default.name,
    val rootFolders: List<String> = emptyList(),
    val importedRootEntries: Set<String> = emptySet(),
)
