package dev.reader.data.room.dao

import androidx.room.Dao
import androidx.room.Query
import dev.reader.data.room.entity.ChapterFtsEntity

/** How much text one book has in the index. */
data class BookIndexSizeRow(val bookId: String, val chapters: Int, val bytes: Long)

@Dao
interface SearchDao {

    @Query("INSERT INTO chapter_fts(chapterId, bookId, body) VALUES (:chapterId, :bookId, :body)")
    suspend fun insert(chapterId: String, bookId: String, body: String)

    @Query("DELETE FROM chapter_fts WHERE bookId = :bookId")
    suspend fun removeBook(bookId: String)

    /** The next [limit] indexed chapters after row [after], so the whole index can be read in small pages. */
    @Query("SELECT rowid, chapterId, bookId, body FROM chapter_fts WHERE rowid > :after ORDER BY rowid LIMIT :limit")
    suspend fun page(after: Int, limit: Int): List<ChapterFtsEntity>

    @Query("DELETE FROM chapter_fts")
    suspend fun clear()

    @Query(
        """
        SELECT bookId, COUNT(*) AS chapters, SUM(LENGTH(CAST(body AS BLOB))) AS bytes
        FROM chapter_fts GROUP BY bookId ORDER BY bytes DESC
        """,
    )
    suspend fun sizes(): List<BookIndexSizeRow>
}
