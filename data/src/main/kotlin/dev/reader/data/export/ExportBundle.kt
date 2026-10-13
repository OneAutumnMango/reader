package dev.reader.data.export

import dev.reader.core.model.Book
import dev.reader.core.model.Bookmark
import dev.reader.core.model.Category
import dev.reader.core.model.Chapter
import dev.reader.core.model.Progress
import dev.reader.data.store.PreferencesFile
import kotlinx.serialization.Serializable

/** Everything the app persists outside the source book files themselves. */
@Serializable
data class ExportBundle(
    val version: Int = 1,
    val books: List<Book> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
    val categories: List<Category> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val progress: List<Progress> = emptyList(),
    val preferences: PreferencesFile = PreferencesFile(),
)
