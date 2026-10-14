package dev.reader.app.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import dev.reader.app.ui.appearance.ReaderSettingsEditor
import dev.reader.app.ui.appearance.ReaderThemeEditor
import dev.reader.core.reader.PdfRenderMode
import dev.reader.core.reader.ReaderSettings
import dev.reader.core.reader.ReadingMode
import dev.reader.core.theme.ReaderTheme

private val EXPANDED_PANEL_MAX_HEIGHT = 420.dp
private val MENU_SHADOW_ELEVATION = 8.dp
private const val PERCENT_MAX = 100
private const val SWIPE_THRESHOLD_PX = 60f
private const val FLING_THRESHOLD = 1000f

/**
 * Collapses the expanded panel when it's dragged down while already scrolled to the top. A gesture
 * that scrolled the panel's content at any point never collapses it.
 */
private class PullDownToCollapse(
    private val scrollState: ScrollState,
    private val onCollapse: () -> Unit,
) : NestedScrollConnection {
    private var pulled = 0f
    private var scrolledContent = false

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput) {
            if (consumed.y != 0f) {
                scrolledContent = true
            } else if (!scrolledContent && available.y > 0f && scrollState.value == 0) {
                pulled += available.y
            }
        }
        return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (pulled >= SWIPE_THRESHOLD_PX) onCollapse()
        pulled = 0f
        scrolledContent = false
        return Velocity.Zero
    }
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
 * Bottom-anchored reader menu: never shifts the reader content. The collapsed bar shows
 * chapter progress; the "^" expands it upward into font/theme/reading-mode/selectable-text
 * controls (reusing the same editors as the Appearance screen). Swiping up/down on the bar, or
 * pulling the panel down from its top, also expands/collapses it when `swipeGestureEnabled`.
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
    var barDrag by remember { mutableFloatStateOf(0f) }
    val barSwipe = if (swipeGestureEnabled) {
        Modifier.draggable(
            state = rememberDraggableState { barDrag += it },
            orientation = Orientation.Vertical,
            onDragStarted = { barDrag = 0f },
            onDragStopped = { velocity ->
                when {
                    barDrag <= -SWIPE_THRESHOLD_PX || velocity < -FLING_THRESHOLD -> currentOnExpandedChange(true)
                    barDrag >= SWIPE_THRESHOLD_PX || velocity > FLING_THRESHOLD -> currentOnExpandedChange(false)
                }
            },
        )
    } else {
        Modifier
    }

    val panelScroll = rememberScrollState()
    val pullDownToCollapse = remember(panelScroll) {
        PullDownToCollapse(panelScroll) { currentOnExpandedChange(false) }
    }

    Column(modifier = modifier.fillMaxWidth().shadow(MENU_SHADOW_ELEVATION)) {
        AnimatedVisibility(visible = expanded) {
            Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                Column {
                    HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
                    Column(
                        modifier = Modifier
                            .heightIn(max = EXPANDED_PANEL_MAX_HEIGHT)
                            .then(if (swipeGestureEnabled) Modifier.nestedScroll(pullDownToCollapse) else Modifier)
                            .verticalScroll(panelScroll)
                            .padding(16.dp)
                            .testTag("reader-menu-panel"),
                    ) {
                        if (pdfRenderMode != null) {
                            Text("PDF display", style = MaterialTheme.typography.titleSmall)
                            PdfModeSelector(pdfRenderMode, onPdfRenderModeChange)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        }
                        Text("Appearance", style = MaterialTheme.typography.titleSmall)
                        ReaderThemeEditor(theme, onThemeChange)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        Text("Reading", style = MaterialTheme.typography.titleSmall)
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
            Column {
                HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outline)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("reader-menu-back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(progressLabel, style = MaterialTheme.typography.labelLarge)
                    if (pdfRenderMode != null) {
                        val showingPages = pdfRenderMode == PdfRenderMode.FIXED_PAGE
                        val nextMode = if (showingPages) PdfRenderMode.REFLOW_TEXT else PdfRenderMode.FIXED_PAGE
                        AssistChip(
                            onClick = { onPdfRenderModeChange(nextMode) },
                            label = { Text(if (showingPages) "Text view" else "Page view") },
                            leadingIcon = {
                                Icon(
                                    if (showingPages) Icons.Filled.TextFields else Icons.Filled.PictureAsPdf,
                                    contentDescription = if (showingPages) "Show extracted text" else "Show PDF pages",
                                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                                )
                            },
                            modifier = Modifier.testTag("reader-menu-pdf-mode"),
                        )
                    }
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
