package dev.reader.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SettingsRoute(
    onAppearanceClick: () -> Unit,
    onReaderClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onDataClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScreen(
        onAppearanceClick = onAppearanceClick,
        onReaderClick = onReaderClick,
        onLibraryClick = onLibraryClick,
        onDataClick = onDataClick,
        modifier = modifier,
    )
}
