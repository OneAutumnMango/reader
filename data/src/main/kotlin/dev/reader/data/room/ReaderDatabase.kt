package dev.reader.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.reader.data.room.dao.BookDao
import dev.reader.data.room.dao.BookmarkDao
import dev.reader.data.room.dao.CategoryDao
import dev.reader.data.room.dao.ChapterDao
import dev.reader.data.room.dao.ChapterProgressDao
import dev.reader.data.room.dao.ProgressDao
import dev.reader.data.room.dao.ReadingSessionDao
import dev.reader.data.room.dao.SearchDao
import dev.reader.data.room.entity.BookCategoryCrossRef
import dev.reader.data.room.entity.BookEntity
import dev.reader.data.room.entity.BookmarkEntity
import dev.reader.data.room.entity.CategoryEntity
import dev.reader.data.room.entity.ChapterEntity
import dev.reader.data.room.entity.ChapterFtsEntity
import dev.reader.data.room.entity.ChapterProgressEntity
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
        ChapterProgressEntity::class,
        ReadingSessionEntity::class,
        ChapterFtsEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun books(): BookDao
    abstract fun chapters(): ChapterDao
    abstract fun categories(): CategoryDao
    abstract fun bookmarks(): BookmarkDao
    abstract fun progress(): ProgressDao
    abstract fun chapterProgress(): ChapterProgressDao
    abstract fun sessions(): ReadingSessionDao
    abstract fun search(): SearchDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reading_sessions ADD COLUMN pagesTurned INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN contentHash TEXT")
                db.execSQL("ALTER TABLE chapters ADD COLUMN isExtra INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN chapterPattern TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN chapterExcludedLines TEXT")
            }
        }
    }
}
