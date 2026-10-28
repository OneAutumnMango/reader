package dev.reader.data.room

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.assertTrue

/**
 * Runs the real [ReaderDatabase] migrations in sequence against a database shaped like the
 * original version 1 schema, the way a device that installed the app long ago would see them.
 * `exportSchema` is false, so there's no historical schema file to drive Room's own
 * MigrationTestHelper against — this builds the version-1 tables by hand instead and checks the
 * specific columns each migration is supposed to add.
 */
@RunWith(RobolectricTestRunner::class)
class ReaderDatabaseMigrationTest {

    @Test
    fun `migrations 1 through 7 run in sequence and add every expected column`() {
        val dir = createTempDirectory("migration-test").toFile()
        try {
            val db = openVersion1Database(File(dir, "test.db"))
            listOf(
                ReaderDatabase.MIGRATION_1_2,
                ReaderDatabase.MIGRATION_2_3,
                ReaderDatabase.MIGRATION_3_4,
                ReaderDatabase.MIGRATION_4_5,
                ReaderDatabase.MIGRATION_5_6,
                ReaderDatabase.MIGRATION_6_7,
            ).forEach { it.migrate(db) }

            assertTrue(
                db.columnsOf("chapter_progress")
                    .containsAll(listOf("bookId", "chapterId", "position", "updatedAt", "finished")),
            )
            assertTrue(db.columnsOf("reading_sessions").contains("pagesTurned"))
            assertTrue(
                db.columnsOf("books")
                    .containsAll(listOf("contentHash", "chapterPattern", "chapterExcludedLines", "originUri")),
            )
            assertTrue(db.columnsOf("chapters").contains("isExtra"))
            db.close()
        } finally {
            dir.deleteRecursively()
        }
    }

    /** The version-1 schema (the tables that existed before any migration), minimal but accurate. */
    private fun openVersion1Database(file: File): SupportSQLiteDatabase {
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE books (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, " +
                        "author TEXT NOT NULL, coverPath TEXT, format TEXT NOT NULL, sourcePath TEXT NOT NULL, " +
                        "isLightNovel INTEGER NOT NULL, pdfRenderMode TEXT, addedAt INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE chapters (id TEXT NOT NULL PRIMARY KEY, bookId TEXT NOT NULL, " +
                        "title TEXT NOT NULL, sourcePath TEXT NOT NULL, orderIndex INTEGER NOT NULL, " +
                        "wordCount INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE reading_sessions (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "bookId TEXT NOT NULL, chapterId TEXT NOT NULL, startedAt INTEGER NOT NULL, " +
                        "endedAt INTEGER NOT NULL, wordsRead INTEGER NOT NULL)",
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
                .name(file.path)
                .callback(callback)
                .build(),
        )
        return helper.writableDatabase
    }

    private fun SupportSQLiteDatabase.columnsOf(table: String): List<String> {
        val names = mutableListOf<String>()
        query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) names += cursor.getString(nameIndex)
        }
        return names
    }
}
