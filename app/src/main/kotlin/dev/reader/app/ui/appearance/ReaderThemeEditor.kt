package dev.reader.app.ui.appearance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.reader.core.theme.Justification
import dev.reader.core.theme.ReaderTheme

private const val MIN_FONT_SIZE = 10f
private const val MAX_FONT_SIZE = 36f
private const val MIN_LINE_SPACING = 1f
private const val MAX_LINE_SPACING = 3f

/** Editable typography and spacing controls for a [ReaderTheme]. */
@Composable
fun ReaderThemeEditor(theme: ReaderTheme, onChange: (ReaderTheme) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = theme.fontFamily,
            onValueChange = { onChange(theme.copy(fontFamily = it)) },
            label = { Text("Font") },
        )

        Row {
            Justification.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .testTag("justification-${option.name}")
                        .clickable { onChange(theme.copy(justification = option)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = theme.justification == option, onClick = null)
                    Text(option.name)
                }
            }
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
