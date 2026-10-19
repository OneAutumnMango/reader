package dev.reader.app.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.app.ui.MenuSubheader
import dev.reader.app.ui.appearance.ReaderSettingsEditor
import dev.reader.app.ui.appearance.ReaderThemeEditor
import dev.reader.core.reader.PdfRenderMode
import dev.reader.core.reader.ReaderSettings
import dev.reader.core.reader.ReadingMode
import dev.reader.core.theme.ReaderTheme

private val PANEL_MAX = 420.dp
private val MENU_SHADOW_ELEVATION = 8.dp
private val HANDLE_HEIGHT = 28.dp
private val HANDLE_PILL_WIDTH = 40.dp
private val HANDLE_PILL_HEIGHT = 4.dp
private const val HANDLE_PILL_ALPHA = 0.5f
private const val PERCENT_MAX = 100
private const val SWIPE_THRESHOLD_PX = 60f
private const val FLING_THRESHOLD = 1000f

/** Vertical swipe detection reporting up/down once a drag or fling passes the thresholds. */
@Composable
private fun Modifier.verticalSwipe(enabled: Boolean, onUp: () -> Unit, onDown: () -> Unit): Modifier {
    if (!enabled) return this
    val up by rememberUpdatedState(onUp)
    val down by rememberUpdatedState(onDown)
    var drag by remember { mutableFloatStateOf(0f) }
    return draggable(
        state = rememberDraggableState { drag += it },
        orientation = Orientation.Vertical,
        onDragStarted = { drag = 0f },
        onDragStopped = { velocity ->
            when {
                drag <= -SWIPE_THRESHOLD_PX || velocity < -FLING_THRESHOLD -> up()
                drag >= SWIPE_THRESHOLD_PX || velocity > FLING_THRESHOLD -> down()
            }
        },
    )
}

/**
 * Formats chapter position as "Page X of Y" for paged modes and page-image content (PDF pages,
 * comics), or a percentage for continuously scrolled text.
 */
fun readerProgressLabel(readingMode: ReadingMode, index: Int, total: Int, pageBased: Boolean = false): String {
    if (total <= 0) return ""
    return if (readingMode == ReadingMode.CONTINUOUS_SCROLL && !pageBased) {
        "${((index + 1) * PERCENT_MAX / total).coerceIn(0, PERCENT_MAX)}%"
    } else {
        "Page ${index + 1} of $total"
    }
}

/**
 * Bottom-anchored reader menu: never shifts the reader content. The collapsed bar shows back,
 * chapter progress (centred), search and "^". Expanded, a grab strip across the top of the panel
 * takes the swipes, so they never fight the panel's own scrolling: up makes it full screen, down
 * closes it back to the bar. Swiping up on the bar itself also expands it.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ReaderMenuOverlay(
    progressLabel: String,
    theme: ReaderTheme,
    onThemeChange: (ReaderTheme) -> Unit,
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    onBack: () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    swipeGestureEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    pdfRenderMode: PdfRenderMode? = null,
    onPdfRenderModeChange: (PdfRenderMode) -> Unit = {},
    onSearch: (() -> Unit)? = null,
    searchPanel: (@Composable () -> Unit)? = null,
) {
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    var fullScreen by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) { if (!expanded) fullScreen = false }
    val barSwipe = Modifier.verticalSwipe(
        swipeGestureEnabled,
        onUp = { currentOnExpandedChange(true) },
        onDown = { currentOnExpandedChange(false) },
    )
    val handleSwipe = Modifier.verticalSwipe(
        swipeGestureEnabled,
        onUp = { fullScreen = true },
        onDown = { currentOnExpandedChange(false) },
    )
    val panelScroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fullScreen) Modifier.fillMaxHeight() else Modifier)
            .shadow(MENU_SHADOW_ELEVATION),
    ) {
        AnimatedVisibility(visible = expanded, modifier = if (fullScreen) Modifier.weight(1f) else Modifier) {
            Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = if (fullScreen) Modifier.statusBarsPadding() else Modifier) {
                    HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(HANDLE_HEIGHT)
                            .then(handleSwipe)
                            .testTag("reader-menu-handle"),
                    ) {
                        Box(
                            Modifier
                                .size(HANDLE_PILL_WIDTH, HANDLE_PILL_HEIGHT)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = HANDLE_PILL_ALPHA),
                                    RoundedCornerShape(50),
                                ),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .then(if (fullScreen) Modifier.fillMaxHeight() else Modifier.heightIn(max = PANEL_MAX))
                            .readerScrollIndicator(MaterialTheme.colorScheme.onSurfaceVariant) {
                                val total = panelScroll.maxValue.toFloat() + panelScroll.viewportSize
                                val visible = if (total > 0f) panelScroll.viewportSize / total else 1f
                                panelScroll.value.toFloat() / panelScroll.maxValue.coerceAtLeast(1) to visible
                            }
                            .verticalScroll(panelScroll)
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                            .testTag("reader-menu-panel"),
                    ) {
                        if (pdfRenderMode != null) {
                            MenuSubheader("PDF display", Modifier.padding(top = 0.dp))
                            PdfModeSelector(pdfRenderMode, onPdfRenderModeChange)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        }
                        MenuSubheader("Appearance", Modifier.padding(top = 0.dp))
                        ReaderThemeEditor(theme, onThemeChange)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        MenuSubheader("Reading", Modifier.padding(top = 0.dp))
                        ReaderSettingsEditor(settings, onSettingsChange)
                    }
                }
            }
        }

        if (searchPanel != null) {
            Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth().testTag("reader-search-panel")) {
                Column {
                    HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
                    searchPanel()
                }
            }
        }

        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth().testTag("reader-menu-bar").then(barSwipe)) {
            Column(modifier = Modifier.navigationBarsPadding()) {
                HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.CenterStart).testTag("reader-menu-back"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        progressLabel,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.align(Alignment.Center).testTag("reader-menu-progress"),
                    )
                    Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                        if (onSearch != null) {
                            IconButton(onClick = onSearch, modifier = Modifier.testTag("reader-menu-search")) {
                                Icon(Icons.Filled.Search, contentDescription = "Search")
                            }
                        }
                        IconButton(
                            onClick = { onExpandedChange(!expanded) },
                            modifier = Modifier.testTag("reader-menu-expand"),
                        ) {
                            Icon(
                                if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                                contentDescription = if (expanded) "Collapse" else "Expand",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PdfModeSelector(mode: PdfRenderMode, onChange: (PdfRenderMode) -> Unit) {
    val options = listOf(PdfRenderMode.FIXED_PAGE to "Original pages", PdfRenderMode.REFLOW_TEXT to "Extracted text")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        options.forEachIndexed { index, (option, label) ->
            SegmentedButton(
                selected = mode == option,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                modifier = Modifier.testTag("pdf-mode-${option.name}"),
            ) {
                Text(label)
            }
        }
    }
}
