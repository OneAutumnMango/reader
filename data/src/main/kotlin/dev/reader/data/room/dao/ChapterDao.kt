package dev.reader.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.reader.data.room.entity.ChapterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY orderIndex")
    fun observeByBook(bookId: String): Flow<List<ChapterEntity>>

    @Upsert
    suspend fun upsertAll(chapters: List<ChapterEntity>)

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: String)
}
