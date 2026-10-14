package dev.reader.app.ui.appearance

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.reader.core.theme.ReaderTheme

/** Picks the active reader theme preset from a dropdown, like the font picker, with save-as-new and delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderThemePresetPicker(
    presets: List<ReaderTheme>,
    activeName: String,
    onSelect: (String) -> Unit,
    onSaveAsNew: (name: String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = activeName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Preset") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag("theme-preset-dropdown"),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            presets.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.name) },
                    leadingIcon = {
                        if (preset.name == activeName) Icon(Icons.Filled.Check, contentDescription = "Active")
                    },
                    trailingIcon = {
                        if (presets.size > 1) {
                            IconButton(
                                onClick = { onDelete(preset.name) },
                                modifier = Modifier.testTag("theme-preset-delete-${preset.name}"),
                            ) { Icon(Icons.Filled.Delete, contentDescription = "Delete ${preset.name}") }
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(preset.name)
                    },
                    modifier = Modifier.testTag("theme-preset-${preset.name}"),
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Save current as new preset\u2026") },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = {
                    expanded = false
                    naming = true
                },
                modifier = Modifier.testTag("theme-preset-add"),
            )
        }
    }

    if (naming) {
        NewPresetDialog(
            taken = presets.map { it.name }.toSet(),
            onConfirm = { name ->
                naming = false
                onSaveAsNew(name)
            },
            onDismiss = { naming = false },
        )
    }
}

@Composable
private fun NewPresetDialog(taken: Set<String>, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val trimmed = name.trim()
    val duplicate = trimmed in taken
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New preset") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") },
                isError = duplicate,
                supportingText = if (duplicate) ({ Text("A preset with that name already exists") }) else null,
                modifier = Modifier.testTag("theme-preset-name"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotEmpty() && !duplicate,
                modifier = Modifier.testTag("theme-preset-save"),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
