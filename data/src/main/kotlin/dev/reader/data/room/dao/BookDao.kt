package dev.reader.data.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import dev.reader.data.room.entity.BookCategoryCrossRef
import dev.reader.data.room.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query(
        """SELECT * FROM books WHERE id IN
           (SELECT bookId FROM book_categories WHERE categoryId = :categoryId) ORDER BY addedAt DESC""",
    )
    fun observeByCategory(categoryId: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE sourcePath = :sourcePath LIMIT 1")
    suspend fun findBySourcePath(sourcePath: String): BookEntity?

    @Upsert
    suspend fun upsert(book: BookEntity)

    @Delete
    suspend fun delete(book: BookEntity)

    @Query("SELECT categoryId FROM book_categories WHERE bookId = :bookId")
    suspend fun categoryIdsFor(bookId: String): List<String>

    @Query("DELETE FROM book_categories WHERE bookId = :bookId")
    suspend fun clearCategories(bookId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategoryRefs(refs: List<BookCategoryCrossRef>)
}
