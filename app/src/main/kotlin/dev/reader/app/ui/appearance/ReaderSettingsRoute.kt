package dev.reader.app.ui.appearance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.app.data.ReaderGraph
import dev.reader.app.ui.MenuSection
import dev.reader.app.ui.MenuSubheader
import dev.reader.core.theme.ReaderTheme
import dev.reader.data.store.PreferencesFile
import kotlinx.coroutines.launch

@Composable
fun ReaderSettingsRoute(graph: ReaderGraph, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val preferences by graph.settings.preferences.collectAsState(initial = PreferencesFile())
    // Which of the two themes the editor below is currently editing — independent of which one
    // is actually active while reading (that's the night toggle in the reader's own menu).
    var editingNight by remember { mutableStateOf(false) }
    val editingPresetName = if (editingNight) preferences.nightThemePresetName else preferences.activeThemePresetName
    val editingTheme = preferences.themePresets.find { it.name == editingPresetName } ?: ReaderTheme.Default

    fun update(transform: (PreferencesFile) -> PreferencesFile) {
        scope.launch { graph.settings.update(transform) }
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MenuSection("Reader appearance") {
            DayNightEditorToggle(editingNight, onChange = { editingNight = it })
            ReaderThemeEditor(
                editingTheme,
                onChange = { theme ->
                    update { prefs ->
                        prefs.copy(themePresets = prefs.themePresets.map { if (it.name == theme.name) theme else it })
                    }
                },
                showPreview = true,
                presetPicker = {
                    ReaderThemePresetPicker(
                        presets = preferences.themePresets,
                        activeName = editingPresetName,
                        onSelect = { name ->
                            update {
                                if (editingNight) it.copy(nightThemePresetName = name) else it.copy(activeThemePresetName = name)
                            }
                        },
                        onSaveAsNew = { name ->
                            update {
                                val saved = it.copy(themePresets = it.themePresets + editingTheme.copy(name = name))
                                if (editingNight) saved.copy(nightThemePresetName = name) else saved.copy(activeThemePresetName = name)
                            }
                        },
                        onDelete = { name ->
                            update { prefs ->
                                val remaining = prefs.themePresets.filterNot { preset -> preset.name == name }
                                val fallback = remaining.firstOrNull()?.name ?: ReaderTheme.Default.name
                                prefs.copy(
                                    themePresets = remaining,
                                    activeThemePresetName = prefs.activeThemePresetName.takeIf { it != name } ?: fallback,
                                    nightThemePresetName = prefs.nightThemePresetName.takeIf { it != name } ?: fallback,
                                )
                            }
                        },
                    )
                },
            )
        }

        MenuSection("Reading") {
            ReaderSettingsEditor(
                preferences.readerSettings,
                onChange = { settings -> update { it.copy(readerSettings = settings) } },
            )
        }

        MenuSection("Tap zones") {
            MenuSubheader("Reader gestures", Modifier.padding(top = 0.dp))
            TapZoneEditor(
                preferences.readerSettings.tapZones,
                onChange = { config ->
                    update { it.copy(readerSettings = it.readerSettings.copy(tapZones = config)) }
                },
            )
        }
    }
}

/** Picks which of the two reader themes (day/night) the editor above it edits. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayNightEditorToggle(editingNight: Boolean, onChange: (Boolean) -> Unit) {
    val options = listOf(false to "Day", true to "Night")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = editingNight == value,
                onClick = { onChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                modifier = Modifier.testTag("theme-editing-${label.lowercase()}"),
            ) { Text(label) }
        }
    }
}
