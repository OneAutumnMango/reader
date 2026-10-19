package dev.reader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuDropdownProvider
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
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
    initialPositionFraction: Float = 0f,
    onTapAction: (TapAction) -> Unit = {},
    onPageChanged: (index: Int, pageCount: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    highlight: Regex? = null,
    jump: BlockJump? = null,
    onSelectionAction: (SelectionAction, String, Float?) -> Unit = { _, _, _ -> },
    onBookmarkTap: ((String) -> Unit)? = null,
) {
    KeepScreenAwakeEffect(settings.keepScreenAwake)
    val selectionToolbar = remember { ReaderSelectionToolbar() }
    val blockAnchors = remember(document) { BlockAnchorRegistry() }
    val systemClipboard = LocalClipboard.current
    val context = LocalContext.current
    val selectionClipboard = remember(selectionToolbar, systemClipboard, context) {
        object : Clipboard by systemClipboard {
            override suspend fun setClipEntry(clipEntry: ClipEntry?) {
                val text = clipEntry?.clipData?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                if (!selectionToolbar.captureSelection(text)) systemClipboard.setClipEntry(clipEntry)
            }
        }
    }
    DisposableEffect(selectionToolbar) { onDispose { selectionToolbar.dismiss() } }

    Box(
        modifier = modifier.fillMaxSize().background(theme.backgroundColor())
            .onGloballyPositioned { selectionToolbar.coordinates = it },
    ) {
        CompositionLocalProvider(
            LocalTextToolbar provides selectionToolbar,
            LocalClipboard provides selectionClipboard,
            LocalTextContextMenuToolbarProvider provides selectionToolbar,
            LocalTextContextMenuDropdownProvider provides selectionToolbar,
        ) {
            TapZoneOverlay(settings.tapZones, showTapZoneLabels, onTapAction, Modifier.fillMaxSize()) {
                Zoomable(settings.zoomEnabled, Modifier.fillMaxSize()) {
                    when (settings.readingMode) {
                        ReadingMode.CONTINUOUS_SCROLL -> ContinuousReader(
                            document,
                            theme,
                            settings,
                            Modifier.fillMaxSize(),
                            initialPositionFraction,
                            onPageChanged,
                            highlight,
                            jump,
                            onBookmarkTap,
                            onBlockLayout = blockAnchors::record,
                        )
                        else -> PagedReader(
                            document,
                            theme,
                            settings,
                            Modifier.fillMaxSize(),
                            initialPositionFraction,
                            onPageChanged,
                            highlight,
                            jump,
                            onBookmarkTap,
                        )
                    }
                }
            }
        }
        ReaderSelectionToolbarOverlay(selectionToolbar) { action, text, rect ->
            val anchorFraction = selectionToolbar.coordinates?.let { root ->
                blockAnchors.resolveFraction(document, root, rect)
            }
            onSelectionAction(action, text, anchorFraction)
        }
    }
}
