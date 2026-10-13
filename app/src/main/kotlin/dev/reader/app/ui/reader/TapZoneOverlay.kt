package dev.reader.app.ui.reader

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.core.reader.TapAction
import dev.reader.core.reader.TapZoneConfig

/**
 * Renders the user's custom tap zones. Only genuine taps (no movement) trigger an action — a
 * single [detectTapGestures] spans the whole area so drags pass through untouched and the
 * scrollable/pager behind this overlay is free to handle them. In [showLabels] mode each region
 * is outlined and labelled so the user can preview their configuration before relying on it.
 */
@Composable
fun TapZoneOverlay(
    config: TapZoneConfig,
    showLabels: Boolean,
    onAction: (TapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!config.enabled) return

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().testTag("tap-zone-overlay").pointerInput(config) {
            detectTapGestures(
                onTap = { offset ->
                    val action = config.actionAt(offset.x / size.width, offset.y / size.height)
                    if (action != TapAction.NONE) onAction(action)
                },
            )
        },
    ) {
        if (showLabels) {
            config.zones.forEachIndexed { index, zone ->
                Box(
                    modifier = Modifier
                        .offset(x = maxWidth * zone.left, y = maxHeight * zone.top)
                        .size(maxWidth * (zone.right - zone.left), maxHeight * (zone.bottom - zone.top))
                        .testTag("tap-zone-$index")
                        .border(1.dp, Color.Gray.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(zone.action.name)
                }
            }
        }
    }
}
