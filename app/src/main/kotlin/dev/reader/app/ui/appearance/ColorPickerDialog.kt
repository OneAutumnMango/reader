package dev.reader.app.ui.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.graphics.Color as AndroidColor

private const val HUE_MAX = 360f
private const val HSV_SIZE = 3
private const val ALPHA_OPAQUE_MASK = 0xFF000000L
private const val ARGB_MASK = 0xFFFFFFFFL

/** A circle swatch that shows a selected state as a ring, not a checkmark glyph. */
@Composable
fun ColorSwatch(
    colorLong: Long,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = SWATCH_SIZE,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(colorLong))
            .border(
                width = if (selected) SELECTED_BORDER_WIDTH else UNSELECTED_BORDER_WIDTH,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

private val SWATCH_SIZE = 32.dp
private val SELECTED_BORDER_WIDTH = 3.dp
private val UNSELECTED_BORDER_WIDTH = 1.dp

/** Opens a colour picker dialog and reports the result as an ARGB [Long]. */
@Composable
fun PickColorButton(current: Long, label: String, modifier: Modifier = Modifier, onPicked: (Long) -> Unit) {
    var picking by remember { mutableStateOf(false) }

    TextButton(onClick = { picking = true }, modifier = modifier.testTag("pick-color-$label")) {
        Text("Pick")
    }

    if (picking) {
        ColorPickerDialog(
            initial = current,
            label = label,
            onDismiss = { picking = false },
            onPicked = {
                onPicked(it)
                picking = false
            },
        )
    }
}

private data class Hsv(val hue: Float, val saturation: Float, val value: Float)

private fun Long.toHsv(): Hsv {
    val hsv = FloatArray(HSV_SIZE)
    AndroidColor.colorToHSV(toInt(), hsv)
    return Hsv(hsv[0], hsv[1], hsv[2])
}

private fun Hsv.toArgb(alpha: Int): Long =
    AndroidColor.HSVToColor(alpha, floatArrayOf(hue, saturation, value)).toLong() and ARGB_MASK

@Composable
private fun ColorPickerDialog(initial: Long, label: String, onDismiss: () -> Unit, onPicked: (Long) -> Unit) {
    val alpha = remember(initial) { ((initial ushr 24) and 0xFF).toInt() }
    var hsv by remember(initial) { mutableStateOf(initial.toHsv()) }
    val picked = hsv.toArgb(alpha)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(Color(picked), RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                )
                Text("#%08X".format(picked), style = MaterialTheme.typography.bodyMedium)
                LabelledSlider("Hue", hsv.hue, 0f..HUE_MAX) { hsv = hsv.copy(hue = it) }
                LabelledSlider("Saturation", hsv.saturation, 0f..1f) { hsv = hsv.copy(saturation = it) }
                LabelledSlider("Brightness", hsv.value, 0f..1f) { hsv = hsv.copy(value = it) }
                HexField(picked) { hsv = it.toHsv() }
            }
        },
        confirmButton = { TextButton(onClick = { onPicked(picked) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LabelledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Slider(value = value, onValueChange = onChange, valueRange = range)
}

@Composable
private fun HexField(current: Long, onParsed: (Long) -> Unit) {
    var text by remember(current) { mutableStateOf("#%08X".format(current)) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            input.removePrefix("#").toLongOrNull(16)?.let { onParsed(it or ALPHA_OPAQUE_MASK) }
        },
        label = { Text("Hex") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
