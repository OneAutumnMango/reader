package dev.reader.data.store

import dev.reader.core.reader.ReaderSettings
import dev.reader.core.stats.StatsGraph
import dev.reader.core.theme.AppTheme
import dev.reader.core.theme.LibraryLayout
import dev.reader.core.theme.ReaderTheme
import kotlinx.serialization.Serializable

/** Bumped when a stored shape changes in a way that needs migrating. */
const val STORE_VERSION: Int = 1

/** How a library folder's contents map to books. */
@Serializable
enum class CollectionKind {
    /** Every book file inside, including in subfolders, is its own book. */
    FILES,

    /** Every subfolder is one book, its files being the chapters. */
    FOLDERS,
}

@Serializable
enum class LibrarySort { TITLE, AUTHOR, LAST_READ, ADDED, PROGRESS }

@Serializable
data class PreferencesFile(
    val version: Int = STORE_VERSION,
    val appTheme: AppTheme = AppTheme(),
    val readerSettings: ReaderSettings = ReaderSettings(),
    val themePresets: List<ReaderTheme> = ReaderTheme.builtIns,
    val activeThemePresetName: String = ReaderTheme.Default.name,
    val rootFolders: List<String> = emptyList(),
    /** Library folder URI -> its [CollectionKind]; folders added before kinds existed import both. */
    val rootFolderKinds: Map<String, CollectionKind> = emptyMap(),
    /** Stable tree URI -> user-facing collection name, independent of provider metadata. */
    val rootFolderNames: Map<String, String> = emptyMap(),
    /** Root-folder entry URI -> sourcePath of the book imported from it. */
    val rootEntryBooks: Map<String, String> = emptyMap(),
    val libraryLayout: LibraryLayout = LibraryLayout.MEDIUM,
    val showAllCategory: Boolean = true,
    val librarySort: LibrarySort = LibrarySort.ADDED,
    val librarySortDescending: Boolean = true,
    val statsGraphs: List<StatsGraph> = emptyList(),
)
