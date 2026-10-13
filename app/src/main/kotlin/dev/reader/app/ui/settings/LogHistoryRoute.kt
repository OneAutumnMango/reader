package dev.reader.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.reader.app.data.AppLog

@Composable
fun LogHistoryRoute(modifier: Modifier = Modifier) {
    LogHistoryScreen(entries = AppLog.entries, onClear = { AppLog.clear() }, modifier = modifier)
}
