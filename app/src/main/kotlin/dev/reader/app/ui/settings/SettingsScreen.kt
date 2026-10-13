package dev.reader.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    status: String,
    onAddFiles: () -> Unit,
    onAddFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text("Library")
        Button(onClick = onAddFiles) { Text("Add files") }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onAddFolder) { Text("Add light novel folder") }
        if (status.isNotBlank()) Text(status)
    }
}
