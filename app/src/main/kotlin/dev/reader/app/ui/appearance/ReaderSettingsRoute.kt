package dev.reader.app.ui.appearance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
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
    val activeTheme = preferences.themePresets.find { it.name == preferences.activeThemePresetName }
        ?: ReaderTheme.Default

    fun update(transform: (PreferencesFile) -> PreferencesFile) {
        scope.launch { graph.settings.update(transform) }
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MenuSection("Reader appearance") {
            ReaderThemeEditor(
                activeTheme,
                onChange = { theme ->
                    update { prefs ->
                        prefs.copy(themePresets = prefs.themePresets.map { if (it.name == theme.name) theme else it })
                    }
                },
                showPreview = true,
                presetPicker = {
                    ReaderThemePresetPicker(
                        presets = preferences.themePresets,
                        activeName = preferences.activeThemePresetName,
                        onSelect = { name -> update { it.copy(activeThemePresetName = name) } },
                        onSaveAsNew = { name ->
                            update {
                                it.copy(
                                    themePresets = it.themePresets + activeTheme.copy(name = name),
                                    activeThemePresetName = name,
                                )
                            }
                        },
                        onDelete = { name ->
                            update { prefs ->
                                val remaining = prefs.themePresets.filterNot { preset -> preset.name == name }
                                val active = prefs.activeThemePresetName.takeIf { it != name }
                                    ?: remaining.firstOrNull()?.name
                                    ?: ReaderTheme.Default.name
                                prefs.copy(themePresets = remaining, activeThemePresetName = active)
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
