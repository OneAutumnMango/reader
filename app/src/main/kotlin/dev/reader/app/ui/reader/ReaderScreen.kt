package dev.reader.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuDropdownProvider
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.unit.dp
import dev.reader.core.reader.ReaderSettings
import dev.reader.core.reader.ReadingMode
import dev.reader.core.reader.TapAction
import dev.reader.core.richtext.RichDocument
import dev.reader.core.theme.ReaderTheme

private const val READING_LINE_ALPHA = 0.6f

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
    autoScrollMode: AutoScrollMode = AutoScrollMode.OFF,
    onAutoScrollDown: () -> Unit = {},
    onAutoScrollTap: (Long) -> Unit = {},
) {
    KeepScreenAwakeEffect(settings.keepScreenAwake)
    val selectionToolbar = remember { ReaderSelectionToolbar() }
    val blockAnchors = remember(document) { BlockAnchorRegistry() }
    var selectionContainerCoordinates by remember(document) { mutableStateOf<LayoutCoordinates?>(null) }
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
    val inAutoScroll = autoScrollMode != AutoScrollMode.OFF &&
        settings.readingMode == ReadingMode.CONTINUOUS_SCROLL
    var holding by remember { mutableStateOf(false) }
    // A gesture cut short by leaving the mode must not leave the hold stuck on.
    LaunchedEffect(inAutoScroll) { if (!inAutoScroll) holding = false }

    Box(
        modifier = modifier.fillMaxSize().background(theme.backgroundColor())
            .autoScrollTouch(inAutoScroll, onAutoScrollDown, { holding = it }, onAutoScrollTap)
            .onGloballyPositioned { selectionToolbar.coordinates = it },
    ) {
        CompositionLocalProvider(
            LocalTextToolbar provides selectionToolbar,
            LocalClipboard provides selectionClipboard,
            LocalTextContextMenuToolbarProvider provides selectionToolbar,
            LocalTextContextMenuDropdownProvider provides selectionToolbar,
        ) {
            TapZoneOverlay(settings.tapZones, showTapZoneLabels, onTapAction, Modifier.fillMaxSize()) {
                Zoomable(
                    settings.zoomEnabled,
                    Modifier.fillMaxSize(),
                    settings.doubleTapZoomEnabled && !inAutoScroll, // double tap resumes auto-scroll
                    settings.doubleTapZoomScale,
                ) {
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
                            onBlockDisposed = blockAnchors::remove,
                            onSelectionContainerLayout = { selectionContainerCoordinates = it },
                            autoScrollSpeedDp = settings.autoScrollSpeed,
                            autoScrolling = inAutoScroll && autoScrollMode == AutoScrollMode.RUNNING && !holding,
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
        if (inAutoScroll && settings.readingLineEnabled) {
            val lineColor = Color(theme.textColor).copy(alpha = READING_LINE_ALPHA)
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val y = size.height * settings.readingLinePosition
                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
                },
            )
        }
        ReaderSelectionToolbarOverlay(selectionToolbar) { action, text, rect ->
            // rect is relative to SelectionContainer's own coordinates, not selectionToolbar's
            // (which tracks this outer Box, used only for positioning the toolbar popup) — using
            // the wrong frame here silently resolved to the wrong, nearby character.
            val anchorFraction = selectionContainerCoordinates?.let { root ->
                blockAnchors.resolveFraction(document, root, rect)
            }
            onSelectionAction(action, text, anchorFraction)
        }
    }
}
