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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

class StatsRepository(private val database: ReaderDatabase) {

    /** [sessionStatsFrom] is the cutoff for session count, average and longest session. */
    fun observeSummary(sessionStatsFrom: Flow<Instant?> = flowOf(null)): Flow<StatsSummary> = combine(
        database.sessions().observeAll(),
        database.progress().observeFinishedCount(),
        database.books().observeAll(),
        sessionStatsFrom,
    ) { sessions, finished, books, from ->
        StatsCalculator.summarize(
            sessions = sessions.map { it.toEvent() },
            booksFinished = finished,
            bookTitles = books.associate { BookId(it.id) to it.title },
            sessionStatsFrom = from,
        )
    }

    suspend fun recordSession(session: ReadingSessionEvent) = database.sessions().insert(session.toEntity())

    fun observeAllSessions(): Flow<List<ReadingSessionEvent>> =
        database.sessions().observeAll().map { rows -> rows.map { it.toEvent() } }

    fun observeSessions(bookId: BookId): Flow<List<ReadingSessionEvent>> =
        database.sessions().observeForBook(bookId.value).map { rows -> rows.map { it.toEvent() } }

    suspend fun resetAll() = database.maintenance().deleteSessions()
}
