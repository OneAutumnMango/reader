package dev.reader.app.ui.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.reader.core.reader.TapAction
import dev.reader.core.reader.TapZone

/** Full-screen overlay: drag out a rectangle to define a brand new tap zone. */
@Composable
fun ZoneRegionPicker(onConfirm: (TapZone) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var start by remember { mutableStateOf<Offset?>(null) }
        var current by remember { mutableStateOf<Offset?>(null) }
        val density = LocalDensity.current

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .testTag("zone-region-picker")
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            start = offset
                            current = offset
                        },
                        onDrag = { change, _ -> current = change.position },
                    )
                },
        ) {
            Text(
                "Drag to draw your new zone",
                color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).padding(24.dp),
            )

            val containerWidthPx = with(density) { maxWidth.toPx() }
            val containerHeightPx = with(density) { maxHeight.toPx() }
            val startOffset = start
            val currentOffset = current
            if (startOffset != null && currentOffset != null) {
                val leftPx = minOf(startOffset.x, currentOffset.x)
                val topPx = minOf(startOffset.y, currentOffset.y)
                val widthPx = kotlin.math.abs(currentOffset.x - startOffset.x)
                val heightPx = kotlin.math.abs(currentOffset.y - startOffset.y)
                val toDp: (Float) -> Dp = { with(density) { it.toDp() } }
                Box(
                    modifier = Modifier
                        .offset(x = toDp(leftPx), y = toDp(topPx))
                        .size(toDp(widthPx), toDp(heightPx))
                        .background(Color.White.copy(alpha = 0.3f))
                        .border(2.dp, Color.White),
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) }
                Button(
                    enabled = startOffset != null && currentOffset != null,
                    onClick = {
                        val left = minOf(startOffset!!.x, currentOffset!!.x) / containerWidthPx
                        val top = minOf(startOffset.y, currentOffset.y) / containerHeightPx
                        val right = maxOf(startOffset.x, currentOffset.x) / containerWidthPx
                        val bottom = maxOf(startOffset.y, currentOffset.y) / containerHeightPx
                        onConfirm(TapZone(left, top, right, bottom, TapAction.NONE))
                    },
                ) { Text("Confirm zone") }
            }
        }
    }
}
