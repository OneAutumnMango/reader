package dev.reader.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.reader.data.room.entity.ChapterProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterProgressDao {

    @Query("SELECT * FROM chapter_progress WHERE bookId = :bookId AND chapterId = :chapterId")
    fun observe(bookId: String, chapterId: String): Flow<ChapterProgressEntity?>

    @Upsert
    suspend fun upsert(progress: ChapterProgressEntity)
}
