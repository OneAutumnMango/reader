package dev.reader.app.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import dev.reader.app.data.ReaderGraph
import dev.reader.core.stats.StatsSummary
import kotlinx.coroutines.launch

@Composable
fun StatsRoute(graph: ReaderGraph, modifier: Modifier = Modifier) {
    val summary by graph.stats.observeSummary().collectAsState(initial = StatsSummary())
    val sessions by graph.stats.observeAllSessions().collectAsState(initial = emptyList())
    val books by graph.library.observeBooks().collectAsState(initial = emptyList())
    val chapters by graph.library.observeAllChapters().collectAsState(initial = emptyList())
    val preferences by graph.settings.preferences.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    StatsScreen(summary, modifier) {
        StatsGraphsSection(
            graphs = preferences?.statsGraphs.orEmpty(),
            sessions = sessions,
            books = books,
            chapters = chapters,
            onSave = { saved ->
                scope.launch {
                    graph.settings.update { current ->
                        val graphs = if (current.statsGraphs.any { it.id == saved.id }) {
                            current.statsGraphs.map { if (it.id == saved.id) saved else it }
                        } else {
                            current.statsGraphs + saved
                        }
                        current.copy(statsGraphs = graphs)
                    }
                }
            },
            onDelete = { deleted ->
                scope.launch {
                    graph.settings.update { current ->
                        current.copy(statsGraphs = current.statsGraphs.filterNot { it.id == deleted.id })
                    }
                }
            },
        )
    }
}
