package dev.reader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.reader.core.reader.ReaderSettings
import dev.reader.core.reader.ReadingMode
import dev.reader.core.reader.TapAction
import dev.reader.core.richtext.RichDocument
import dev.reader.core.theme.ReaderTheme

/** Renders a text chapter with the reader's theme, scrolling mode and tap zones. */
@Composable
fun ReaderScreen(
    document: RichDocument,
    theme: ReaderTheme,
    settings: ReaderSettings,
    showTapZoneLabels: Boolean = false,
    onTapAction: (TapAction) -> Unit = {},
    onPageChanged: (index: Int, pageCount: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    KeepScreenAwakeEffect(settings.keepScreenAwake)

    Box(modifier = modifier.fillMaxSize().background(theme.backgroundColor())) {
        when (settings.readingMode) {
            ReadingMode.CONTINUOUS_SCROLL -> ContinuousReader(
                document,
                theme,
                settings,
                Modifier.fillMaxSize(),
                onPageChanged,
            )
            else -> PagedReader(document, theme, settings, Modifier.fillMaxSize(), onPageChanged)
        }
        TapZoneOverlay(settings.tapZones, showTapZoneLabels, onTapAction, Modifier.fillMaxSize())
    }
}
