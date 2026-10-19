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
import dev.reader.core.theme.AppTheme
import dev.reader.data.store.PreferencesFile
import kotlinx.coroutines.launch

@Composable
fun AppearanceRoute(graph: ReaderGraph, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val preferences by graph.settings.preferences.collectAsState(initial = PreferencesFile())

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
    }
}
