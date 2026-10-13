package dev.reader.app.ui.appearance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.reader.core.theme.ReaderTheme

@Composable
fun ReaderThemePresetPicker(
    presets: List<ReaderTheme>,
    activeName: String,
    onSelect: (String) -> Unit,
    onSaveAsNew: (name: String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newName by remember { mutableStateOf("") }

    LazyColumn(modifier = modifier) {
        items(presets, key = { it.name }) { preset ->
            ListItem(
                headlineContent = { Text(preset.name) },
                leadingContent = { RadioButton(selected = preset.name == activeName, onClick = null) },
                trailingContent = {
                    IconButton(onClick = { onDelete(preset.name) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${preset.name}")
                    }
                },
                modifier = Modifier.clickable { onSelect(preset.name) },
            )
        }
        item {
            OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("New preset name") })
            Button(
                onClick = {
                    onSaveAsNew(newName)
                    newName = ""
                },
                enabled = newName.isNotBlank(),
            ) {
                Text("Save as new preset")
            }
        }
    }
}
