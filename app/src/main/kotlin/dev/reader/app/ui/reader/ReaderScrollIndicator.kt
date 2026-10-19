package dev.reader.app.ui.reader

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private const val THUMB_ALPHA = 0.55f

internal fun Modifier.readerScrollIndicator(
    color: Color,
    metrics: () -> Pair<Float, Float>,
): Modifier = drawWithContent {
    drawContent()
    val (position, visibleFraction) = metrics()
    if (visibleFraction >= 1f || size.height <= 0f) return@drawWithContent
    val width = 3.dp.toPx()
    val inset = 3.dp.toPx()
    val trackHeight = (size.height - inset * 2).coerceAtLeast(0f)
    val minimumHeight = 18.dp.toPx().coerceAtMost(trackHeight)
    val thumbHeight = (trackHeight * visibleFraction).coerceIn(minimumHeight, trackHeight)
    drawRoundRect(
        color = color.copy(alpha = THUMB_ALPHA),
        topLeft = Offset(size.width - width - inset, inset + (trackHeight - thumbHeight) * position.coerceIn(0f, 1f)),
        size = Size(width, thumbHeight),
        cornerRadius = CornerRadius(width),
    )
}
