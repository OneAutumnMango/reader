package dev.reader.app.ui.stats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dev.reader.app.data.ReaderGraph
import dev.reader.core.stats.StatsSummary

@Composable
fun StatsRoute(graph: ReaderGraph, modifier: Modifier = Modifier) {
    val summary by graph.stats.observeSummary().collectAsState(initial = StatsSummary())
    StatsScreen(summary, modifier)
}
