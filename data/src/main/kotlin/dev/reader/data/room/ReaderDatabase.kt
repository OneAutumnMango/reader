package dev.reader.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.reader.data.room.dao.BookDao
import dev.reader.data.room.dao.BookmarkDao
import dev.reader.data.room.dao.CategoryDao
import dev.reader.data.room.dao.ChapterDao
import dev.reader.data.room.dao.ProgressDao
import dev.reader.data.room.dao.ReadingSessionDao
import dev.reader.data.room.dao.SearchDao
import dev.reader.data.room.entity.BookCategoryCrossRef
import dev.reader.data.room.entity.BookEntity
import dev.reader.data.room.entity.BookmarkEntity
import dev.reader.data.room.entity.CategoryEntity
import dev.reader.data.room.entity.ChapterEntity
import dev.reader.data.room.entity.ChapterFtsEntity
import dev.reader.data.room.entity.ProgressEntity
import dev.reader.data.room.entity.ReadingSessionEntity

@Database(
    entities = [
        BookEntity::class,
        ChapterEntity::class,
        CategoryEntity::class,
        BookCategoryCrossRef::class,
        BookmarkEntity::class,
        ProgressEntity::class,
        ReadingSessionEntity::class,
        ChapterFtsEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun books(): BookDao
    abstract fun chapters(): ChapterDao
    abstract fun categories(): CategoryDao
    abstract fun bookmarks(): BookmarkDao
    abstract fun progress(): ProgressDao
    abstract fun sessions(): ReadingSessionDao
    abstract fun search(): SearchDao
}
