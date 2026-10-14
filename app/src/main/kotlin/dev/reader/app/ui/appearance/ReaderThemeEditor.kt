package dev.reader.app.ui.appearance

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.app.ui.reader.CUSTOM_FONT_FILE_PREFIX
import dev.reader.app.ui.reader.bodyTextStyle
import dev.reader.core.theme.Justification
import dev.reader.core.theme.ReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MIN_FONT_SIZE = 10f
private const val MAX_FONT_SIZE = 36f
private const val MIN_LINE_SPACING = 1f
private const val MAX_LINE_SPACING = 3f
private const val CUSTOM_FONT_OPTION = "Custom…"
private const val ADD_FONT_FILE_OPTION = "+ Add font file…"
private val FONT_PRESETS = listOf(
    "sans-serif",
    "serif",
    "monospace",
    "cursive",
    "sans-serif-condensed",
    "sans-serif-light",
    "sans-serif-medium",
)

private fun justificationLabel(option: Justification): String = when (option) {
    Justification.START -> "Left"
    Justification.JUSTIFY -> "Justify"
    Justification.END -> "Right"
}

/**
 * Editable typography and spacing controls for a [ReaderTheme]. [showPreview] is only shown on
 * the Appearance screen.
 */
@Composable
fun ReaderThemeEditor(
    theme: ReaderTheme,
    onChange: (ReaderTheme) -> Unit,
    modifier: Modifier = Modifier,
    showPreview: Boolean = false,
) {
    Column(modifier = modifier) {
        if (showPreview) ThemePreview(theme)

        FontDropdown(theme.fontFamily, onSelect = { onChange(theme.copy(fontFamily = it)) })

        Row {
            Justification.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .testTag("justification-${option.name}")
                        .clickable { onChange(theme.copy(justification = option)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = theme.justification == option, onClick = null)
                    Text(justificationLabel(option))
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Text colour", modifier = Modifier.weight(1f))
            ColorSwatch(theme.textColor, selected = false, onClick = {})
            PickColorButton(theme.textColor, "Text colour") { onChange(theme.copy(textColor = it)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Background colour", modifier = Modifier.weight(1f))
            ColorSwatch(theme.backgroundColor, selected = false, onClick = {})
            PickColorButton(theme.backgroundColor, "Background colour") { onChange(theme.copy(backgroundColor = it)) }
        }

        Text("Font size: ${theme.fontSizeSp.toInt()}sp")
        Slider(
            value = theme.fontSizeSp,
            onValueChange = { onChange(theme.copy(fontSizeSp = it)) },
            valueRange = MIN_FONT_SIZE..MAX_FONT_SIZE,
        )

        Text("Line spacing: ${"%.1f".format(theme.lineSpacing)}")
        Slider(
            value = theme.lineSpacing,
            onValueChange = { onChange(theme.copy(lineSpacing = it)) },
            valueRange = MIN_LINE_SPACING..MAX_LINE_SPACING,
        )

        Text("Word spacing: ${theme.wordSpacingSp.toInt()}sp")
        Slider(
            value = theme.wordSpacingSp,
            onValueChange = { onChange(theme.copy(wordSpacingSp = it)) },
            valueRange = 0f..20f,
        )

        Text("Letter spacing: ${theme.letterSpacingSp.toInt()}sp")
        Slider(
            value = theme.letterSpacingSp,
            onValueChange = { onChange(theme.copy(letterSpacingSp = it)) },
            valueRange = 0f..10f,
        )

        Text("Paragraph spacing: ${theme.paragraphSpacingSp.toInt()}sp")
        Slider(
            value = theme.paragraphSpacingSp,
            onValueChange = { onChange(theme.copy(paragraphSpacingSp = it)) },
            valueRange = 0f..40f,
        )
    }
}

@Composable
private fun ThemePreview(theme: ReaderTheme) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Color(theme.backgroundColor), RoundedCornerShape(8.dp))
            .padding(12.dp),
    ) {
        Text(
            "The quick brown fox jumps over the lazy dog.",
            style = theme.bodyTextStyle(),
            modifier = Modifier.testTag("reader-theme-preview"),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontDropdown(fontFamily: String, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    val isCustomFile = fontFamily.startsWith(CUSTOM_FONT_FILE_PREFIX)
    var customText by remember(fontFamily) {
        mutableStateOf(fontFamily.takeIf { it !in FONT_PRESETS && !isCustomFile } ?: "")
    }
    val showCustomField = fontFamily !in FONT_PRESETS && !isCustomFile

    val fontFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                val fontsDir = File(context.filesDir, "fonts").apply { mkdirs() }
                val target = File(fontsDir, "${System.currentTimeMillis()}.ttf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { input.copyTo(it) }
                }
                target.path
            }
            onSelect("$CUSTOM_FONT_FILE_PREFIX$path")
        }
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = when {
                isCustomFile -> File(fontFamily.removePrefix(CUSTOM_FONT_FILE_PREFIX)).name
                showCustomField -> CUSTOM_FONT_OPTION
                else -> fontFamily
            },
            onValueChange = {},
            readOnly = true,
            label = { Text("Font") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().testTag("font-dropdown"),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            FONT_PRESETS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(CUSTOM_FONT_OPTION) },
                onClick = {
                    onSelect(customText.ifBlank { fontFamily })
                    expanded = false
                },
                modifier = Modifier.testTag("font-option-custom"),
            )
            DropdownMenuItem(
                text = { Text(ADD_FONT_FILE_OPTION) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = {
                    expanded = false
                    fontFileLauncher.launch(arrayOf("*/*"))
                },
                modifier = Modifier.testTag("font-option-add-file"),
            )
        }
    }

    if (showCustomField) {
        OutlinedTextField(
            value = customText,
            onValueChange = {
                customText = it
                onSelect(it)
            },
            label = { Text("Custom font family") },
            modifier = Modifier.fillMaxWidth().testTag("custom-font-field"),
        )
    }
}
