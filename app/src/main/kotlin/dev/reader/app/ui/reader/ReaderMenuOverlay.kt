package dev.reader.app.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
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
private const val SWIPE_EXPAND_THRESHOLD_PX = 60f

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
 * controls (reusing the same editors as the Appearance screen). Can also be expanded/collapsed
 * by swiping up/down on the bar itself when `swipeGestureEnabled` is true.
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
) {
    val swipeModifier = if (swipeGestureEnabled) {
        Modifier.pointerInput(Unit) {
            var totalDrag = 0f
            detectVerticalDragGestures(
                onDragStart = { totalDrag = 0f },
                onDragEnd = {
                    when {
                        totalDrag <= -SWIPE_EXPAND_THRESHOLD_PX -> onExpandedChange(true)
                        totalDrag >= SWIPE_EXPAND_THRESHOLD_PX -> onExpandedChange(false)
                    }
                },
            ) { change, dragAmount ->
                change.consume()
                totalDrag += dragAmount
            }
        }
    } else {
        Modifier
    }

    Column(modifier = modifier.fillMaxWidth().shadow(MENU_SHADOW_ELEVATION).then(swipeModifier)) {
        AnimatedVisibility(visible = expanded) {
            Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .heightIn(max = EXPANDED_PANEL_MAX_HEIGHT)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
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

        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
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
