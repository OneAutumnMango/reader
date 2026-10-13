package dev.reader.data.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/** Indexed chapter text for in-book search; [chapterId]/[bookId] are unindexed lookup keys. */
@Fts4
@Entity(tableName = "chapter_fts")
data class ChapterFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Int,
    val chapterId: String,
    val bookId: String,
    val body: String,
)
