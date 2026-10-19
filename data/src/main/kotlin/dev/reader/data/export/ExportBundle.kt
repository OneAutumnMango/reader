package dev.reader.data.export

import dev.reader.core.model.Book
import dev.reader.core.model.Bookmark
import dev.reader.core.model.Category
import dev.reader.core.model.Chapter
import dev.reader.core.model.Progress
import dev.reader.core.stats.ReadingSessionEvent
import dev.reader.data.store.PreferencesFile
import kotlinx.serialization.Serializable

/** What a bundle contains; importing only touches the scopes that were exported. */
@Serializable
enum class ExportScope {
    /** Appearance, reader settings, themes, library layout and root folders. */
    SETTINGS,

    /** Books, chapters, categories, bookmarks and reading progress. */
    LIBRARY,

    /** Reading sessions behind the stats page. */
    STATS,
}

/** Everything the app persists outside the source book files themselves. */
@Serializable
data class ExportBundle(
    val version: Int = 2,
    // Version-1 bundles predate scopes and always held everything.
    val scopes: Set<ExportScope> = ExportScope.entries.toSet(),
    val books: List<Book> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val categories: List<Category> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val progress: List<Progress> = emptyList(),
    val chapterProgress: List<Progress> = emptyList(),
    val sessions: List<ReadingSessionEvent> = emptyList(),
    val preferences: PreferencesFile? = null,
    val coverAssets: Map<String, String> = emptyMap(),
)
