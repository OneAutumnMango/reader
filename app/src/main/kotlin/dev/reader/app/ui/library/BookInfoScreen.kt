package dev.reader.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.reader.app.data.BookLocation
import dev.reader.app.ui.MenuSection
import dev.reader.app.ui.stats.toReadableDuration
import dev.reader.core.model.Book
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId
import dev.reader.core.model.Progress
import dev.reader.core.stats.ReadingSessionEvent
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private const val WORDS_PER_MINUTE = 250
private const val PERCENT = 100
private const val KILOBYTE = 1024.0

/** Stats and facts about one book: its size, how much of it has been read, and where it lives on the device. */
@Composable
fun BookInfoScreen(
    book: Book,
    chapters: List<Chapter>,
    chapterProgress: Map<ChapterId, Progress>,
    sessions: List<ReadingSessionEvent>,
    modifier: Modifier = Modifier,
    location: BookLocation? = null,
    sizeBytes: Long? = null,
    onOpenFolder: () -> Unit = {},
    onOpenItem: () -> Unit = {},
    onCopyLocation: () -> Unit = {},
) {
    val real = chapters.filterNot { it.isExtra }.ifEmpty { chapters }
    val extras = chapters.size - real.size
    val words = chapters.sumOf { it.wordCount }
    val read = real.count { chapterProgress[it.id]?.finished == true }
    val fraction = if (real.isEmpty()) {
        0f
    } else {
        real.sumOf { chapter ->
            val progress = chapterProgress[chapter.id]
            (if (progress?.finished == true) 1f else progress?.position ?: 0f).toDouble()
        }.toFloat() / real.size
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MenuSection("Overview") {
            InfoRow("Format", book.format.name)
            InfoRow("Chapters", if (extras > 0) "${real.size} (+$extras book info)" else "${real.size}")
            InfoRow("Words", "%,d".format(words), Modifier.testTag("book-info-words"))
            InfoRow("Reading time", "about ${(words / WORDS_PER_MINUTE).minutes.toReadableDuration()}")
            sizeBytes?.let { InfoRow("Size", it.toReadableSize()) }
            InfoRow("Added", book.addedAt.toDateLabel())
        }

        MenuSection("Your reading") {
            InfoRow("Progress", "${(fraction * PERCENT).toInt()}%")
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
            InfoRow("Chapters read", "$read of ${real.size}")
            InfoRow("Time spent", sessions.totalTime().toReadableDuration())
            InfoRow("Sessions", "${sessions.size}")
            InfoRow("Words read", "%,d".format(sessions.sumOf { it.wordsRead }))
            sessions.maxOfOrNull { it.endedAt }?.let { InfoRow("Last read", it.toDateLabel()) }
        }

        MenuSection("Location") {
            LocationContent(location, book.sourcePath, onOpenFolder, onOpenItem, onCopyLocation)
        }

        if (real.size > 1) {
            MenuSection("Chapters") { ChapterBreakdown(real, chapterProgress) }
        }
    }
}

@Composable
private fun LocationContent(
    location: BookLocation?,
    sourcePath: String,
    onOpenFolder: () -> Unit,
    onOpenItem: () -> Unit,
    onCopyLocation: () -> Unit,
) {
    if (location == null) {
        Text(
            "Where this was imported from wasn't recorded. Re-adding it will remember its location.",
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        SelectionContainer {
            Text(
                location.label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("book-location"),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            if (location.folder != null) {
                LocationButton("Open folder", Icons.Filled.FolderOpen, "book-open-folder", onOpenFolder)
            }
            LocationButton("Open file", Icons.AutoMirrored.Filled.OpenInNew, "book-open-item", onOpenItem)
            LocationButton("Copy", Icons.Filled.ContentCopy, "book-copy-location", onCopyLocation)
        }
    }
    Text(
        "App copy: $sourcePath",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun LocationButton(label: String, icon: ImageVector, tag: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.testTag(tag)) {
        Icon(icon, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
        Text(label, modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
    }
}

/** Per-chapter word counts, collapsed by default so it doesn't crowd the page. */
@Composable
private fun ChapterBreakdown(chapters: List<Chapter>, chapterProgress: Map<ChapterId, Progress>) {
    var expanded by remember { mutableStateOf(false) }
    val longest = chapters.maxOf { it.wordCount }.coerceAtLeast(1)
    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("book-info-breakdown")) {
        Text(if (expanded) "Hide word count per chapter" else "Show word count per chapter")
        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
    }
    if (!expanded) return
    chapters.forEachIndexed { index, chapter ->
        Column(modifier = Modifier.padding(vertical = 4.dp).testTag("book-info-chapter-${chapter.id.value}")) {
            Row {
                Text(
                    "${index + 1}. ${chapter.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("%,d".format(chapter.wordCount), style = MaterialTheme.typography.labelMedium)
            }
            LinearProgressIndicator(
                progress = { chapter.wordCount.toFloat() / longest },
                color = if (chapterProgress[chapter.id]?.finished == true) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondary
                },
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

private fun List<ReadingSessionEvent>.totalTime(): Duration =
    fold(Duration.ZERO) { total, session -> total + (session.endedAt - session.startedAt) }

private fun Instant.toDateLabel(): String = toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

private fun Long.toReadableSize(): String {
    val units = listOf("B", "KB", "MB", "GB")
    var size = toDouble()
    var unit = 0
    while (size >= KILOBYTE && unit < units.lastIndex) {
        size /= KILOBYTE
        unit++
    }
    return if (unit == 0) "$this B" else "%.1f %s".format(size, units[unit])
}
