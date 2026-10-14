package dev.reader.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

private val BLANK_LINE_HEIGHT = 8.dp

/**
 * The book's text laid out like the reader: tap any line to make a chapter start there (it stays
 * highlighted) and tap it again to undo. [onDone] returns to the chapter list.
 */
@Composable
fun ChapterLinePicker(text: String, picked: Set<Int>, onToggle: (Int) -> Unit, onDone: () -> Unit) {
    val lines = remember(text) { text.lines() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = picked.minOrNull() ?: 0)
    val scope = rememberCoroutineScope()
    var find by remember { mutableStateOf("") }

    fun findNext() {
        if (find.isBlank()) return
        val start = listState.firstVisibleItemIndex + 1
        val next = (lines.indices.drop(start) + lines.indices.take(start))
            .firstOrNull { lines[it].contains(find, ignoreCase = true) } ?: return
        scope.launch { listState.scrollToItem(next) }
    }

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize().testTag("chapter-line-picker")) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Pick chapter starts", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tap a line to start a chapter there \u00b7 ${picked.size} picked",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onDone, modifier = Modifier.testTag("chapter-picker-done")) { Text("Done") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = find,
                        onValueChange = { find = it },
                        singleLine = true,
                        placeholder = { Text("Find text") },
                        modifier = Modifier.weight(1f).testTag("chapter-picker-find"),
                    )
                    IconButton(onClick = ::findNext, modifier = Modifier.testTag("chapter-picker-find-next")) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match")
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("chapter-picker-lines")) {
                    itemsIndexed(lines) { index, line ->
                        if (line.isBlank()) {
                            Spacer(Modifier.height(BLANK_LINE_HEIGHT))
                        } else {
                            PickableLine(line, index, index in picked) { onToggle(index) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickableLine(line: String, index: Int, picked: Boolean, onClick: () -> Unit) {
    Text(
        line.trim(),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = if (picked) FontWeight.Bold else null,
        color = if (picked) MaterialTheme.colorScheme.onPrimaryContainer else Color.Unspecified,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (picked) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .testTag("chapter-line-$index")
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}
