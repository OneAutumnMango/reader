package dev.reader.app.ui.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.app.ui.reader.AUTO_SCROLL_MAX_SPEED
import dev.reader.app.ui.reader.AUTO_SCROLL_MIN_SPEED
import dev.reader.app.ui.reader.AUTO_SCROLL_STEP
import dev.reader.core.reader.ReaderSettings
import kotlin.math.roundToInt

private val LINE_PREVIEW_HEIGHT = 200.dp
private const val LINE_MIN = 0.05f
private const val LINE_MAX = 0.95f
private const val PERCENT = 100
private const val DISABLED_ALPHA = 0.4f

/** Auto-scroll speed, plus an optional guide line placed by tapping or dragging on a mini screen. */
@Composable
fun AutoScrollEditor(settings: ReaderSettings, onChange: (ReaderSettings) -> Unit, modifier: Modifier = Modifier) {
    val currentSettings by rememberUpdatedState(settings)
    val currentOnChange by rememberUpdatedState(onChange)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Auto-scroll speed: ${settings.autoScrollSpeed.roundToInt()} dp/s")
        Slider(
            value = settings.autoScrollSpeed,
            onValueChange = {
                onChange(settings.copy(autoScrollSpeed = (it / AUTO_SCROLL_STEP).roundToInt() * AUTO_SCROLL_STEP))
            },
            valueRange = AUTO_SCROLL_MIN_SPEED..AUTO_SCROLL_MAX_SPEED,
            modifier = Modifier.testTag("auto-scroll-speed-slider"),
        )

        Row(
            modifier = Modifier
                .testTag("reading-line-toggle")
                .fillMaxWidth()
                .clickable { onChange(settings.copy(readingLineEnabled = !settings.readingLineEnabled)) },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Show a reading line while auto-scrolling")
            Switch(checked = settings.readingLineEnabled, onCheckedChange = null)
        }

        Text(
            "Tap or drag on the screen below to place the line " +
                "(${(settings.readingLinePosition * PERCENT).roundToInt()}% from the top)",
            style = MaterialTheme.typography.bodyMedium,
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(LINE_PREVIEW_HEIGHT)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                // Stable keys: every change makes new settings, which would restart a keyed gesture mid-drag.
                .pointerInput(Unit) {
                    fun place(y: Float) = currentOnChange(
                        currentSettings.copy(readingLinePosition = (y / size.height).coerceIn(LINE_MIN, LINE_MAX)),
                    )
                    detectTapGestures { place(it.y) }
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, _ ->
                        currentOnChange(
                            currentSettings.copy(
                                readingLinePosition = (change.position.y / size.height).coerceIn(LINE_MIN, LINE_MAX),
                            ),
                        )
                    }
                }
                .testTag("reading-line-preview"),
        ) {
            Column(Modifier.fillMaxWidth().alpha(DISABLED_ALPHA).padding(12.dp), Arrangement.spacedBy(10.dp)) {
                repeat(PREVIEW_TEXT_LINES) {
                    val width = if (it % 4 == 3) 0.6f else 1f
                    Box(
                        Modifier.fillMaxWidth(width).height(4.dp).background(MaterialTheme.colorScheme.onSurface),
                    )
                }
            }
            Box(
                Modifier
                    .offset(y = maxHeight * settings.readingLinePosition - 1.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .alpha(if (settings.readingLineEnabled) 1f else DISABLED_ALPHA)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

private const val PREVIEW_TEXT_LINES = 14
