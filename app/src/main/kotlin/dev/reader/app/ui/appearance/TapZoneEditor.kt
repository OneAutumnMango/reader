package dev.reader.app.ui.appearance

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

/** Tapping a cell cycles its action; the grid is always labelled so it previews live. */
@Composable
fun TapZoneEditor(config: TapZoneConfig, onChange: (TapZoneConfig) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        for (row in 0 until TapZoneConfig.GRID_SIZE) {
            Row(modifier = Modifier.weight(1f)) {
                for (column in 0 until TapZoneConfig.GRID_SIZE) {
                    val flatIndex = row * TapZoneConfig.GRID_SIZE + column
                    val action = config.zones[flatIndex]
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .testTag("tap-zone-editor-$row-$column")
                            .border(1.dp, Color.Gray.copy(alpha = 0.5f))
                            .clickable {
                                val nextIndex = (TapAction.entries.indexOf(action) + 1) % TapAction.entries.size
                                val next = TapAction.entries[nextIndex]
                                val zones = config.zones.toMutableList().also { it[flatIndex] = next }
                                onChange(config.copy(zones = zones))
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(action.name)
                    }
                }
            }
        }
    }
}
