package dev.reader.data.repo

import dev.reader.core.model.BookId
import dev.reader.core.stats.ReadingSessionEvent
import dev.reader.core.stats.StatsCalculator
import dev.reader.core.stats.StatsSummary
import dev.reader.data.room.ReaderDatabase
import dev.reader.data.room.toEntity
import dev.reader.data.room.toEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class StatsRepository(private val database: ReaderDatabase) {

    fun observeSummary(): Flow<StatsSummary> = combine(
        database.sessions().observeAll(),
        database.progress().observeFinishedCount(),
        database.books().observeAll(),
    ) { sessions, finished, books ->
        StatsCalculator.summarize(
            sessions = sessions.map { it.toEvent() },
            booksFinished = finished,
            bookTitles = books.associate { BookId(it.id) to it.title },
        )
    }

    suspend fun recordSession(session: ReadingSessionEvent) = database.sessions().insert(session.toEntity())
}
