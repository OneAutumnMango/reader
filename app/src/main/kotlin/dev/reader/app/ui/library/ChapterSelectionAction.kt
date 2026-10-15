package dev.reader.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.core.model.Chapter
import dev.reader.core.model.ChapterId

/** What can be done to the selected chapters; each is an icon whose long-press tooltip says what it does. */
enum class ChapterSelectionAction(val icon: ImageVector, val label: String) {
    SELECT_ALL(Icons.Filled.SelectAll, "Select all"),
    INVERT(Icons.Filled.FlipToBack, "Invert selection"),
    MARK_READ(Icons.Filled.DoneAll, "Mark selected as read"),
    MARK_UNREAD(Icons.Filled.RemoveDone, "Mark selected as unread"),
    READ_UP_TO(Icons.Filled.VerticalAlignTop, "Mark selected and everything before as read"),
    UNREAD_FROM(Icons.Filled.VerticalAlignBottom, "Mark selected and everything after as unread"),
}

@Composable
fun ChapterSelectionBar(
    selectedCount: Int,
    onAction: (ChapterSelectionAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().testTag("chapter-selection-bar"),
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WithTooltip("Stop selecting") {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("chapter-selection-close")) {
                        Icon(Icons.Filled.Close, contentDescription = "Stop selecting")
                    }
                }
                Text(
                    "$selectedCount selected",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("chapter-selection-count"),
                )
            }
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                ChapterSelectionAction.entries.forEach { action ->
                    WithTooltip(action.label) {
                        IconButton(
                            onClick = { onAction(action) },
                            modifier = Modifier.testTag("chapter-selection-${action.name}"),
                        ) { Icon(action.icon, contentDescription = action.label) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WithTooltip(text: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState(),
        content = content,
    )
}

/** The chapters an [action] marks, in book order; null for actions that only change the selection. */
fun ChapterSelectionAction.targets(chapters: List<Chapter>, selected: Set<ChapterId>): List<Chapter>? =
    when (this) {
        ChapterSelectionAction.MARK_READ, ChapterSelectionAction.MARK_UNREAD -> chapters.filter { it.id in selected }
        ChapterSelectionAction.READ_UP_TO -> {
            val last = chapters.indexOfLast { it.id in selected }
            chapters.take(last + 1)
        }
        ChapterSelectionAction.UNREAD_FROM -> {
            val first = chapters.indexOfFirst { it.id in selected }
            if (first < 0) emptyList() else chapters.drop(first)
        }
        ChapterSelectionAction.SELECT_ALL, ChapterSelectionAction.INVERT -> null
    }
