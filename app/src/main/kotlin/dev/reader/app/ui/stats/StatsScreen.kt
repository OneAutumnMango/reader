package dev.reader.app.ui.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.reader.core.stats.StatsSummary

@Composable
fun StatsScreen(summary: StatsSummary, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text("Books finished: ${summary.booksFinished}")
        Text("Reading time: ${summary.totalReadingTime.toReadableDuration()}")
        Text("Words read: ${summary.totalWordsRead}")
        Text("Average WPM: ${summary.averageWpm.toInt()}")
        Text("Current streak: ${summary.currentStreakDays} days")
    }
}
