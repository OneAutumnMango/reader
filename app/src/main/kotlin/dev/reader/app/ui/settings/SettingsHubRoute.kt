package dev.reader.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SettingsRoute(
    onAppearanceClick: () -> Unit,
    onReaderClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onSearchClick: () -> Unit,
    onDataClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScreen(
        onAppearanceClick = onAppearanceClick,
        onReaderClick = onReaderClick,
        onLibraryClick = onLibraryClick,
        onSearchClick = onSearchClick,
        onDataClick = onDataClick,
        modifier = modifier,
    )
}
