package dev.reader.app.ui.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.app.ui.MenuSubheader
import dev.reader.app.ui.RadioOptions
import dev.reader.app.ui.reader.bodyTextStyle
import dev.reader.core.theme.Justification
import dev.reader.core.theme.ReaderTheme

private const val MIN_FONT_SIZE = 10f
private const val MAX_FONT_SIZE = 36f
private const val MIN_LINE_SPACING = 1f
private const val MAX_LINE_SPACING = 3f
private val PREVIEW_MIN_HEIGHT = 72.dp

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

        MenuSubheader("Text")
        FontDropdown(theme.fontFamily, onSelect = { onChange(theme.copy(fontFamily = it)) })

        RadioOptions(
            options = Justification.entries,
            selected = theme.justification,
            label = ::justificationLabel,
            tag = { "justification-${it.name}" },
            onSelect = { onChange(theme.copy(justification = it)) },
        )

        MenuSubheader("Colours")
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

        MenuSubheader("Size and spacing")
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
            .heightIn(min = PREVIEW_MIN_HEIGHT)
            .background(Color(theme.backgroundColor), RoundedCornerShape(8.dp))
            .testTag("reader-theme-preview-box")
            .padding(12.dp),
    ) {
        Text(
            "The quick brown fox jumps over the lazy dog.",
            style = theme.bodyTextStyle(),
            modifier = Modifier.testTag("reader-theme-preview"),
        )
    }
}
