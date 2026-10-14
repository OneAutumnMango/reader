package dev.reader.data.room.dao

import androidx.room.Dao
import androidx.room.Query

/** Bulk wipes used by restore-from-backup and the danger-zone actions. */
@Dao
interface MaintenanceDao {

    @Query("DELETE FROM books")
    suspend fun deleteBooks()

    @Query("DELETE FROM chapters")
    suspend fun deleteChapters()

    @Query("DELETE FROM book_categories")
    suspend fun deleteBookCategories()

    @Query("DELETE FROM bookmarks")
    suspend fun deleteBookmarks()

    @Query("DELETE FROM progress")
    suspend fun deleteProgress()

    @Query("DELETE FROM chapter_progress")
    suspend fun deleteChapterProgress()

    @Query("DELETE FROM chapter_fts")
    suspend fun deleteSearchIndex()

    @Query("DELETE FROM categories")
    suspend fun deleteCategories()

    @Query("DELETE FROM reading_sessions")
    suspend fun deleteSessions()
}
