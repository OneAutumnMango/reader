package dev.reader.data.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.reader.data.room.entity.ReadingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingSessionDao {

    @Insert
    suspend fun insert(session: ReadingSessionEntity)

    @Query("SELECT * FROM reading_sessions ORDER BY startedAt, id")
    fun observeAll(): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions WHERE bookId = :bookId ORDER BY startedAt")
    fun observeForBook(bookId: String): Flow<List<ReadingSessionEntity>>
}
