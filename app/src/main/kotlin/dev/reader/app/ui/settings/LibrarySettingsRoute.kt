package dev.reader.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.app.data.ReaderGraph
import dev.reader.app.ui.MenuSection
import dev.reader.app.ui.MenuSubheader
import dev.reader.app.ui.RadioOptions
import dev.reader.app.ui.categories.CategoriesRoute
import dev.reader.core.theme.LibraryLayout
import dev.reader.data.store.PreferencesFile
import kotlinx.coroutines.launch

private val LIBRARY_SECTIONS = listOf("Sources", "Categories", "Display")

@Composable
fun LibrarySettingsRoute(graph: ReaderGraph, modifier: Modifier = Modifier) {
    var selectedSection by remember { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        LibrarySettingsTabs(selectedSection, onSelect = { selectedSection = it })
        when (selectedSection) {
            0 -> LibrarySourcesRoute(graph, Modifier.weight(1f))
            1 -> CategoriesRoute(graph, Modifier.weight(1f))
            else -> LibraryDisplaySettingsRoute(graph, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun LibrarySettingsTabs(selectedSection: Int, onSelect: (Int) -> Unit) {
    PrimaryTabRow(selectedTabIndex = selectedSection, modifier = Modifier.testTag("library-settings-tabs")) {
        LIBRARY_SECTIONS.forEachIndexed { index, title ->
            Tab(
                selected = selectedSection == index,
                onClick = { onSelect(index) },
                text = { Text(title) },
                modifier = Modifier.testTag("library-settings-tab-$index"),
            )
        }
    }
}

@Composable
fun LibraryDisplaySettingsRoute(graph: ReaderGraph, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val preferences by graph.settings.preferences.collectAsState(initial = PreferencesFile())
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
    ) {
        MenuSection("Library appearance") {
            MenuSubheader("Cover size", Modifier.padding(top = 0.dp))
            RadioOptions(
                options = LibraryLayout.entries,
                selected = preferences.libraryLayout,
                label = { layout -> layout.name.lowercase().replaceFirstChar { it.uppercase() } },
                tag = { "library-layout-${it.name}" },
                onSelect = { layout -> scope.launch { graph.settings.update { it.copy(libraryLayout = layout) } } },
            )
        }
    }
}
