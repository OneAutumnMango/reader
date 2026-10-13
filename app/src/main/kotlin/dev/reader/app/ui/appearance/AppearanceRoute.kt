package dev.reader.app.ui.appearance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.reader.app.data.ReaderGraph
import dev.reader.core.theme.AppTheme
import dev.reader.core.theme.ReaderTheme
import dev.reader.data.store.PreferencesFile
import kotlinx.coroutines.launch

private val SECTION_HEIGHT = 300.dp

@Composable
fun AppearanceRoute(graph: ReaderGraph, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val preferences by graph.settings.preferences.collectAsState(initial = PreferencesFile())
    val activeTheme = preferences.themePresets.find { it.name == preferences.activeThemePresetName }
        ?: ReaderTheme.Default

    fun update(transform: (PreferencesFile) -> PreferencesFile) {
        scope.launch { graph.settings.update(transform) }
    }

    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        Text("App theme")
        AppThemeEditor(preferences.appTheme, onChange = { theme: AppTheme -> update { it.copy(appTheme = theme) } })

        HorizontalDivider()
        Text("Reader theme")
        ReaderThemeEditor(
            activeTheme,
            onChange = { theme ->
                update { prefs ->
                    val updated = prefs.themePresets.map { preset -> if (preset.name == theme.name) theme else preset }
                    prefs.copy(themePresets = updated)
                }
            },
        )
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
            modifier = Modifier.height(SECTION_HEIGHT),
        )

        HorizontalDivider()
        Text("Reader settings")
        ReaderSettingsEditor(
            preferences.readerSettings,
            onChange = { settings -> update { it.copy(readerSettings = settings) } },
        )

        HorizontalDivider()
        Text("Tap zones")
        TapZoneEditor(
            preferences.readerSettings.tapZones,
            onChange = { config ->
                update { it.copy(readerSettings = it.readerSettings.copy(tapZones = config)) }
            },
            modifier = Modifier.height(SECTION_HEIGHT),
        )
    }
}
