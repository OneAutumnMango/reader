package dev.reader.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.reader.data.room.entity.ChapterProgressEntity
import kotlinx.coroutines.flow.Flow

/** How far through a book is, averaged over its real (non-extra) chapters; finished chapters count fully. */
data class BookFractionRow(val bookId: String, val fraction: Float)

@Dao
interface ChapterProgressDao {

    @Query("SELECT * FROM chapter_progress WHERE bookId = :bookId AND chapterId = :chapterId")
    fun observe(bookId: String, chapterId: String): Flow<ChapterProgressEntity?>

    @Query("SELECT * FROM chapter_progress WHERE bookId = :bookId")
    fun observeForBook(bookId: String): Flow<List<ChapterProgressEntity>>

    @Query(
        """
        SELECT c.bookId AS bookId,
            AVG(CASE WHEN p.finished THEN 1.0 ELSE COALESCE(p.position, 0.0) END) AS fraction
        FROM chapters c LEFT JOIN chapter_progress p ON p.bookId = c.bookId AND p.chapterId = c.id
        WHERE c.isExtra = 0
        GROUP BY c.bookId
        """,
    )
    fun observeBookFractions(): Flow<List<BookFractionRow>>

    @Upsert
    suspend fun upsert(progress: ChapterProgressEntity)

    @Upsert
    suspend fun upsertAll(progress: List<ChapterProgressEntity>)

    @Query("SELECT * FROM chapter_progress")
    suspend fun all(): List<ChapterProgressEntity>
}
