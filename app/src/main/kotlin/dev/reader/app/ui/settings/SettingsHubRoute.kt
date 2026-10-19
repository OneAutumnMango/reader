package dev.reader.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SettingsRoute(
    onAppearanceClick: () -> Unit,
    onLibrarySourcesClick: () -> Unit,
    onCategoriesClick: () -> Unit,
    onDataClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScreen(
        onAppearanceClick = onAppearanceClick,
        onLibrarySourcesClick = onLibrarySourcesClick,
        onCategoriesClick = onCategoriesClick,
        onDataClick = onDataClick,
        modifier = modifier,
    )
}
