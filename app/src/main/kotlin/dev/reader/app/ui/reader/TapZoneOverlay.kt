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
 * Wraps [content] with the user's custom tap zones. Only genuine taps (no movement) trigger an
 * action; the gesture detector sits on this wrapping [Box] while [content] is composed as its
 * child, so the child's own scrollable/pager gets first claim on drag motion during dispatch and
 * is always free to scroll — only taps that nothing else consumed reach [onAction]. In
 * [showLabels] mode each region is also outlined and labelled so the user can preview their
 * configuration before relying on it.
 */
@Composable
fun TapZoneOverlay(
    config: TapZoneConfig,
    showLabels: Boolean,
    onAction: (TapAction) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    if (!config.enabled) {
        Box(modifier = modifier.fillMaxSize()) { content() }
        return
    }

    val gestureModifier = Modifier.pointerInput(config) {
        detectTapGestures(
            onTap = { offset ->
                val action = config.actionAt(offset.x / size.width, offset.y / size.height)
                if (action != TapAction.NONE) onAction(action)
            },
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().testTag("tap-zone-overlay").then(gestureModifier)) {
        content()

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
