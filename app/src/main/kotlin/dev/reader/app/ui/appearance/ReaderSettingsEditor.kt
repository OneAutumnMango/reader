package dev.reader.app.ui.appearance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.core.reader.KeepScreenAwake
import dev.reader.core.reader.ReaderSettings
import dev.reader.core.reader.ReadingMode

private fun readingModeLabel(mode: ReadingMode): String = when (mode) {
    ReadingMode.CONTINUOUS_SCROLL -> "Continuous scroll"
    ReadingMode.PAGED_VERTICAL -> "Paged (vertical)"
    ReadingMode.PAGED_HORIZONTAL_LTR -> "Paged (left to right)"
    ReadingMode.PAGED_HORIZONTAL_RTL -> "Paged (right to left)"
}

private fun keepScreenAwakeLabel(option: KeepScreenAwake): String = when (option) {
    KeepScreenAwake.NEVER -> "Never"
    KeepScreenAwake.WHILE_READING -> "While reading"
    KeepScreenAwake.ALWAYS -> "Always"
}

/** Scrolling mode, selectable text, zoom, keep-awake and CSS-styling toggles. */
@Composable
fun ReaderSettingsEditor(settings: ReaderSettings, onChange: (ReaderSettings) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Reading mode")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ReadingMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .testTag("reading-mode-${mode.name}")
                        .clickable { onChange(settings.copy(readingMode = mode)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.readingMode == mode, onClick = null)
                    Text(readingModeLabel(mode))
                }
            }
        }

        Row(
            modifier = Modifier
                .testTag("selectable-text-toggle")
                .fillMaxWidth()
                .clickable { onChange(settings.copy(selectableText = !settings.selectableText)) },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Enable selectable text")
            Switch(checked = settings.selectableText, onCheckedChange = null)
        }

        Row(
            modifier = Modifier
                .testTag("zoom-toggle")
                .fillMaxWidth()
                .clickable { onChange(settings.copy(zoomEnabled = !settings.zoomEnabled)) },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Pinch to zoom")
            Switch(checked = settings.zoomEnabled, onCheckedChange = null)
        }

        Text("Keep screen awake")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KeepScreenAwake.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .testTag("keep-screen-awake-${option.name}")
                        .clickable { onChange(settings.copy(keepScreenAwake = option)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.keepScreenAwake == option, onClick = null)
                    Text(keepScreenAwakeLabel(option))
                }
            }
        }

        Row(
            modifier = Modifier
                .testTag("use-book-styling-toggle")
                .fillMaxWidth()
                .clickable { onChange(settings.copy(useBookStyling = !settings.useBookStyling)) },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Use book styling (CSS)")
            Switch(checked = settings.useBookStyling, onCheckedChange = null)
        }
    }
}
