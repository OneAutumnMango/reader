package dev.reader.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.reader.data.room.entity.ProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {

    @Query("SELECT * FROM progress WHERE bookId = :bookId")
    fun observe(bookId: String): Flow<ProgressEntity?>

    @Query("SELECT * FROM progress")
    fun observeAll(): Flow<List<ProgressEntity>>

    @Upsert
    suspend fun upsert(progress: ProgressEntity)

    @Query("SELECT COUNT(*) FROM progress WHERE finished = 1")
    fun observeFinishedCount(): Flow<Int>
}
