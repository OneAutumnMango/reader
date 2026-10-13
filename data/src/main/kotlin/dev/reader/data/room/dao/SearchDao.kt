package dev.reader.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.reader.data.room.entity.ChapterFtsEntity

@Dao
interface SearchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun index(entry: ChapterFtsEntity)

    @Query("DELETE FROM chapter_fts WHERE chapterId = :chapterId")
    suspend fun removeFromIndex(chapterId: String)

    @Query("SELECT rowid, chapterId, bookId, body FROM chapter_fts WHERE chapter_fts MATCH :query")
    suspend fun search(query: String): List<ChapterFtsEntity>
}
