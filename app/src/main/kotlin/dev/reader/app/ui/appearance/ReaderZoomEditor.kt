package dev.reader.app.ui.appearance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.app.ui.reader.MAX_ZOOM
import dev.reader.core.reader.ReaderSettings

private const val MIN_DOUBLE_TAP_ZOOM = 1.5f

/** Pinch-to-zoom and double-tap-to-zoom toggles, plus how far a double tap zooms in. */
@Composable
fun ReaderZoomEditor(settings: ReaderSettings, onChange: (ReaderSettings) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .testTag("zoom-toggle")
                .fillMaxWidth()
                .clickable { onChange(settings.copy(zoomEnabled = !settings.zoomEnabled)) },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Pinch to zoom")
            Switch(checked = settings.zoomEnabled, onCheckedChange = null)
        }

        Row(
            modifier = Modifier
                .testTag("double-tap-zoom-toggle")
                .fillMaxWidth()
                .clickable { onChange(settings.copy(doubleTapZoomEnabled = !settings.doubleTapZoomEnabled)) },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Double tap to zoom")
            Switch(checked = settings.doubleTapZoomEnabled, onCheckedChange = null)
        }

        val roundedTenths = (settings.doubleTapZoomScale * 10).toInt()
        Text("Double tap zoom amount: ${roundedTenths / 10}.${roundedTenths % 10}x")
        Slider(
            value = settings.doubleTapZoomScale,
            onValueChange = { onChange(settings.copy(doubleTapZoomScale = it)) },
            valueRange = MIN_DOUBLE_TAP_ZOOM..MAX_ZOOM,
            enabled = settings.doubleTapZoomEnabled,
            modifier = Modifier.testTag("double-tap-zoom-amount-slider"),
        )
    }
}
