package dev.reader.app.ui.sync

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.reader.data.sync.SyncReport

/** Top-bar sync button: tap to sync, press and hold for more. Absent until a sync folder is chosen. */
@Composable
fun QuickSyncButton(
    state: SyncUiState,
    onSync: () -> Unit,
    onPullOnly: () -> Unit,
    onPushOnly: () -> Unit,
    onMerge: () -> Unit,
    onSettings: () -> Unit,
) {
    if (state.folderName == null) return
    var open by remember { mutableStateOf(false) }
    Box {
        // Not an IconButton, which has no long press. It stays enabled while running so the menu still opens.
        Box(
            modifier = Modifier
                .size(48.dp)
                .combinedClickable(
                    // A round ripple without clipping, which would cut off the badge.
                    interactionSource = null,
                    indication = ripple(bounded = false, radius = 24.dp),
                    role = Role.Button,
                    onLongClickLabel = "Sync options",
                    onLongClick = { open = true },
                    onClick = { if (!state.running) onSync() },
                )
                .testTag("library-sync")
                .semantics {
                    contentDescription =
                        if (state.questionCount > 0) "Sync, ${state.questionCount} to merge" else "Sync"
                    if (state.running) stateDescription = "Syncing"
                },
            contentAlignment = Alignment.Center,
        ) {
            BadgedBox(badge = { MergeBadge(state.questionCount) }) {
                if (state.running) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Sync, contentDescription = null)
                }
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            fun close(then: () -> Unit): () -> Unit = {
                open = false
                then()
            }
            DropdownMenuItem(text = { Text("Pull only") }, onClick = close(onPullOnly), enabled = !state.running)
            DropdownMenuItem(text = { Text("Push only") }, onClick = close(onPushOnly), enabled = !state.running)
            DropdownMenuItem(
                text = { Text("Merge (${state.questionCount})") },
                onClick = close(onMerge),
                enabled = state.questionCount > 0,
            )
            DropdownMenuItem(text = { Text("Online Sync settings") }, onClick = close(onSettings))
        }
    }
}

private const val MAX_BADGE_COUNT = 99

@Composable
private fun MergeBadge(count: Int) {
    if (count > 0) Badge { Text(if (count > MAX_BADGE_COUNT) "$MAX_BADGE_COUNT+" else "$count") }
}

/** The result of a finished sync or pull. */
fun quickSyncMessage(state: SyncUiState): String {
    val report = state.report
    // A failed publish is also the error, so only another error hides what was applied.
    val unpublished = report.publishError != null && report.applied.isNotEmpty()
    if (state.error != null && !(unpublished && state.error == report.publishError)) return state.error
    if (unpublished) return "Changes applied but not published"
    val result = when (val count = report.applied.size) {
        0 -> "Already up to date"
        1 -> "1 change applied"
        else -> "$count changes applied"
    }
    return listOfNotNull(
        result,
        when (val count = state.questionCount) {
            0 -> null
            1 -> "1 question"
            else -> "$count questions"
        },
        "some files or books skipped".takeIf { leftOut(report) },
    ).joinToString(" · ")
}

/** The message's action, "Merge" or "Details", if any. A failed pull leaves old results, so it has none. */
internal fun quickSyncAction(state: SyncUiState): String? = when {
    state.error != null && state.error != state.report.publishError -> null
    state.questionCount > 0 -> MERGE_ACTION
    leftOut(state.report) -> "Details"
    else -> null
}

internal const val MERGE_ACTION = "Merge"

private fun leftOut(report: SyncReport) =
    report.skipped.isNotEmpty() || report.rejected.isNotEmpty()
