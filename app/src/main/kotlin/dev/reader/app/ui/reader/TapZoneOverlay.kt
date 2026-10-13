package dev.reader.app.ui.reader

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.core.reader.TapAction
import dev.reader.core.reader.TapZoneConfig

/**
 * Renders the tap-zone grid. In [showLabels] mode each region is outlined and labelled so the
 * user can preview their configuration before relying on it during normal reading.
 */
@Composable
fun TapZoneOverlay(
    config: TapZoneConfig,
    showLabels: Boolean,
    onAction: (TapAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!config.enabled) return

    Column(modifier = modifier.fillMaxSize()) {
        for (row in 0 until TapZoneConfig.GRID_SIZE) {
            Row(modifier = Modifier.weight(1f)) {
                for (column in 0 until TapZoneConfig.GRID_SIZE) {
                    val action = config.actionAt(row, column)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .testTag("tap-zone-$row-$column")
                            .then(if (showLabels) Modifier.border(1.dp, Color.Gray.copy(alpha = 0.5f)) else Modifier)
                            .clickable(enabled = action != TapAction.NONE) { onAction(action) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (showLabels) Text(action.name)
                    }
                }
            }
        }
    }
}
