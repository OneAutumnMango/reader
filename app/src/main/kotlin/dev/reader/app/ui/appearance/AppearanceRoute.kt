package dev.reader.app.ui.appearance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.reader.app.data.ReaderGraph
import dev.reader.app.ui.MenuSection
import dev.reader.app.ui.RadioOptions
import dev.reader.core.theme.AppTheme
import dev.reader.core.theme.LibraryLayout
import dev.reader.core.theme.ReaderTheme
import dev.reader.data.store.PreferencesFile
import kotlinx.coroutines.launch

private val PRESET_PICKER_HEIGHT = 220.dp

@Composable
fun AppearanceRoute(graph: ReaderGraph, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val preferences by graph.settings.preferences.collectAsState(initial = PreferencesFile())
    val activeTheme = preferences.themePresets.find { it.name == preferences.activeThemePresetName }
        ?: ReaderTheme.Default

    fun update(transform: (PreferencesFile) -> PreferencesFile) {
        scope.launch { graph.settings.update(transform) }
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MenuSection("App theme") {
            AppThemeEditor(preferences.appTheme, onChange = { theme: AppTheme -> update { it.copy(appTheme = theme) } })
        }

        MenuSection("Library") {
            Text("Cover size")
            RadioOptions(
                options = LibraryLayout.entries,
                selected = preferences.libraryLayout,
                label = { layout -> layout.name.lowercase().replaceFirstChar { it.uppercase() } },
                tag = { "library-layout-${it.name}" },
                onSelect = { layout -> update { it.copy(libraryLayout = layout) } },
            )
        }

        MenuSection("Reader theme") {
            ReaderThemeEditor(
                activeTheme,
                onChange = { theme ->
                    update { prefs ->
                        val updated = prefs.themePresets.map { preset ->
                            if (preset.name == theme.name) theme else preset
                        }
                        prefs.copy(themePresets = updated)
                    }
                },
                showPreview = true,
            )
            Spacer(modifier = Modifier.height(16.dp))
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
                    update { it.copy(themePresets = it.themePresets.filterNot { preset -> preset.name == name }) }
                },
                modifier = Modifier.height(PRESET_PICKER_HEIGHT),
            )
        }

        MenuSection("Reader settings") {
            ReaderSettingsEditor(
                preferences.readerSettings,
                onChange = { settings -> update { it.copy(readerSettings = settings) } },
            )
        }

        MenuSection("Tap zones") {
            TapZoneEditor(
                preferences.readerSettings.tapZones,
                onChange = { config ->
                    update { it.copy(readerSettings = it.readerSettings.copy(tapZones = config)) }
                },
            )
        }
    }
}
