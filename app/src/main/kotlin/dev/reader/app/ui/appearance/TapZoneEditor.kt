package dev.reader.app.ui.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.core.reader.TapAction
import dev.reader.core.reader.TapZoneConfig

private val ZONE_PREVIEW_HEIGHT = 200.dp
private val ZONE_BORDER_WIDTH = 1.dp
private val ZONE_SELECTED_BORDER_WIDTH = 3.dp

@Suppress("MagicNumber")
private fun tapActionColor(action: TapAction): Color = when (action) {
    TapAction.NONE -> Color(0xFF9E9E9E)
    TapAction.NEXT -> Color(0xFF2E7D32)
    TapAction.PREVIOUS -> Color(0xFF1565C0)
    TapAction.OPEN_MENU -> Color(0xFF6A1B9A)
    TapAction.TOGGLE_BOOKMARK -> Color(0xFFD84315)
}

private fun tapActionLabel(action: TapAction): String = when (action) {
    TapAction.NONE -> "Nothing"
    TapAction.NEXT -> "Next page"
    TapAction.PREVIOUS -> "Previous page"
    TapAction.OPEN_MENU -> "Open menu"
    TapAction.TOGGLE_BOOKMARK -> "Toggle bookmark"
}

/**
 * Tap a zone on the mini screen preview to select it, then pick what it should do from the
 * action list below — no more guessing what a cell in a rigid grid means.
 */
@Composable
fun TapZoneEditor(config: TapZoneConfig, onChange: (TapZoneConfig) -> Unit, modifier: Modifier = Modifier) {
    var selectedZone by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier) {
        Text(
            "Tap a zone below, then choose what it does",
            style = MaterialTheme.typography.bodyMedium,
        )

        Box(
            modifier = Modifier
                .padding(vertical = 12.dp)
                .fillMaxWidth()
                .height(ZONE_PREVIEW_HEIGHT)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                for (row in 0 until TapZoneConfig.GRID_SIZE) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        for (column in 0 until TapZoneConfig.GRID_SIZE) {
                            val flatIndex = row * TapZoneConfig.GRID_SIZE + column
                            ZoneCell(
                                action = config.zones[flatIndex],
                                selected = selectedZone == flatIndex,
                                onClick = { selectedZone = flatIndex },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tap-zone-editor-$row-$column"),
                            )
                        }
                    }
                }
            }
        }

        Legend()

        val zone = selectedZone
        if (zone != null) {
            Text(
                "This zone does:",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp),
            )
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TapAction.entries.forEach { action ->
                    ActionChip(
                        action = action,
                        selected = config.zones[zone] == action,
                        onClick = {
                            val zones = config.zones.toMutableList().also { it[zone] = action }
                            onChange(config.copy(zones = zones))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoneCell(action: TapAction, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(
                if (selected) ZONE_SELECTED_BORDER_WIDTH else ZONE_BORDER_WIDTH,
                if (selected) Color.White else Color.White.copy(alpha = 0.4f),
            )
            .background(tapActionColor(action).copy(alpha = if (selected) 0.95f else 0.6f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(tapActionLabel(action), color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ActionChip(action: TapAction, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .testTag("tap-zone-action-${action.name}")
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) tapActionColor(action) else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            tapActionLabel(action),
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun Legend() {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TapAction.entries.forEach { action ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(tapActionColor(action)),
                )
                Text(
                    tapActionLabel(action),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}
