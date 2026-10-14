package dev.reader.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.reader.app.ui.MenuSection
import dev.reader.core.stats.DailyReading
import dev.reader.core.stats.StatsSummary
import java.text.NumberFormat
import kotlin.math.roundToInt
import kotlin.time.Duration

private val BAR_MAX_HEIGHT = 96.dp
private val MIN_BAR_HEIGHT = 4.dp
private const val INACTIVE_BAR_ALPHA = 0.5f

@Composable
fun StatsScreen(summary: StatsSummary, modifier: Modifier = Modifier) {
    if (summary.sessionCount == 0 && summary.booksFinished == 0) {
        EmptyStats(modifier)
        return
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroCard(
                icon = Icons.Filled.LocalFireDepartment,
                value = "${summary.currentStreakDays} ${if (summary.currentStreakDays == 1) "day" else "days"}",
                label = "Current streak",
                caption = "Best: ${summary.longestStreakDays}",
                modifier = Modifier.weight(1f),
            )
            HeroCard(
                icon = Icons.Filled.Schedule,
                value = summary.todayReadingTime.toReadableDuration(),
                label = "Read today",
                caption = "This week: ${summary.thisWeekReadingTime.toReadableDuration()}",
                modifier = Modifier.weight(1f),
            )
        }

        MenuSection("Last 7 days") { WeekChart(summary.last7Days) }

        val insights = statsInsights(summary)
        if (insights.isNotEmpty()) {
            MenuSection("Insights") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    insights.forEach { InsightRow(it) }
                }
            }
        }

        MenuSection("All time") { StatGrid(statTiles(summary)) }

        if (summary.topBooks.isNotEmpty()) {
            MenuSection("Most read") { TopBooks(summary) }
        }
    }
}

private fun statTiles(summary: StatsSummary): List<Pair<String, String>> {
    val numbers = NumberFormat.getIntegerInstance()
    return listOf(
        "Reading time" to summary.totalReadingTime.toReadableDuration(),
        "Reading speed" to "${summary.averageWpm.roundToInt()} wpm",
        "Words read" to numbers.format(summary.totalWordsRead),
        "Pages turned" to numbers.format(summary.totalPagesTurned),
        "Books finished" to summary.booksFinished.toString(),
        "Books started" to summary.booksStarted.toString(),
        "Sessions" to summary.sessionCount.toString(),
        "Days active" to summary.activeDays.toString(),
        "Avg session" to summary.averageSession.toReadableDuration(),
        "Longest session" to summary.longestSession.toReadableDuration(),
    )
}

@Composable
private fun EmptyStats(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.AutoStories, contentDescription = null, modifier = Modifier.size(64.dp))
        Text("No reading yet", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
        Text(
            "Open a book and your streaks, reading speed and habits will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun HeroCard(icon: ImageVector, value: String, label: String, caption: String, modifier: Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekChart(days: List<DailyReading>) {
    val max = days.maxOfOrNull { it.time } ?: Duration.ZERO
    if (max <= Duration.ZERO) {
        Text("Nothing read in the last week yet.")
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().testTag("week-chart"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        days.forEachIndexed { index, day ->
            val isToday = index == days.lastIndex
            val color = MaterialTheme.colorScheme.primary
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(maxOf(BAR_MAX_HEIGHT * (day.time / max).toFloat(), MIN_BAR_HEIGHT))
                        .background(
                            if (isToday) color else color.copy(alpha = INACTIVE_BAR_ALPHA),
                            RoundedCornerShape(4.dp),
                        ),
                )
                Text(
                    day.date.dayOfWeek.displayName().take(3),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isToday) FontWeight.Bold else null,
                )
            }
        }
    }
}

@Composable
private fun InsightRow(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            Icons.Filled.Lightbulb,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(18.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StatGrid(tiles: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, value) ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBooks(summary: StatsSummary) {
    val top = summary.topBooks.first().time
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        summary.topBooks.forEach { book ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        book.title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(book.time.toReadableDuration(), style = MaterialTheme.typography.labelLarge)
                }
                LinearProgressIndicator(
                    progress = { if (top > Duration.ZERO) (book.time / top).toFloat() else 0f },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}
