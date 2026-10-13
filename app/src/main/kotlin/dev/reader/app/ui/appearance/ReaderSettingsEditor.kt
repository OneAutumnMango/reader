package dev.reader.app.ui.appearance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.reader.core.reader.KeepScreenAwake
import dev.reader.core.reader.ReaderSettings
import dev.reader.core.reader.ReadingMode

/** Scrolling mode, selectable text, keep-awake and CSS-styling toggles. */
@Composable
fun ReaderSettingsEditor(settings: ReaderSettings, onChange: (ReaderSettings) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text("Reading mode")
        Row {
            ReadingMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .testTag("reading-mode-${mode.name}")
                        .clickable { onChange(settings.copy(readingMode = mode)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.readingMode == mode, onClick = null)
                    Text(mode.name)
                }
            }
        }

        Row(
            modifier = Modifier
                .testTag("selectable-text-toggle")
                .clickable { onChange(settings.copy(selectableText = !settings.selectableText)) },
        ) {
            Text("Enable selectable text")
            Switch(checked = settings.selectableText, onCheckedChange = null)
        }

        Text("Keep screen awake")
        Row {
            KeepScreenAwake.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .testTag("keep-screen-awake-${option.name}")
                        .clickable { onChange(settings.copy(keepScreenAwake = option)) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.keepScreenAwake == option, onClick = null)
                    Text(option.name)
                }
            }
        }

        Row(
            modifier = Modifier
                .testTag("use-book-styling-toggle")
                .clickable { onChange(settings.copy(useBookStyling = !settings.useBookStyling)) },
        ) {
            Text("Use book styling (CSS)")
            Switch(checked = settings.useBookStyling, onCheckedChange = null)
        }
    }
}
