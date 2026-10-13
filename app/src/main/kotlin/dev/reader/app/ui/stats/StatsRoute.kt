package dev.reader.app.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.reader.app.data.ReaderGraph
import dev.reader.core.stats.StatsSummary
import kotlin.time.Duration

@Composable
fun StatsRoute(graph: ReaderGraph, modifier: Modifier = Modifier) {
    val initial = StatsSummary(
        booksFinished = 0,
        totalReadingTime = Duration.ZERO,
        totalWordsRead = 0,
        averageWpm = 0.0,
        currentStreakDays = 0,
    )
    val summary by graph.stats.observeSummary().collectAsState(initial = initial)
    StatsScreen(summary, modifier)
}
