package dev.reader.core.reader

import kotlinx.serialization.Serializable

@Serializable
enum class KeepScreenAwake {
    NEVER,
    WHILE_READING,
    ALWAYS,
}

@Serializable
data class ReaderSettings(
    val readingMode: ReadingMode = ReadingMode.CONTINUOUS_SCROLL,
    val tapZones: TapZoneConfig = TapZoneConfig(),
    val selectableText: Boolean = true,
    val keepScreenAwake: KeepScreenAwake = KeepScreenAwake.WHILE_READING,
    val useBookStyling: Boolean = true,
    val customCss: String = "",
    val zoomEnabled: Boolean = true,
    /** Visible gap between PDF/comic pages; off gives a seamless strip. */
    val pageGap: Boolean = true,
    val doubleTapZoomEnabled: Boolean = true,
    /** How far a double tap zooms in, from 1 (no zoom) up to the 5x pinch ceiling. */
    val doubleTapZoomScale: Float = 3f,
    /** Auto-scroll speed in dp per second (continuous scroll only). */
    val autoScrollSpeed: Float = 40f,
    /** Draws a guide line while auto-scrolling, as an eye anchor. */
    val readingLineEnabled: Boolean = false,
    /** Guide line's vertical position, 0f (top) to 1f (bottom). */
    val readingLinePosition: Float = 0.5f,
)
