package dev.reader.core.reader

import kotlinx.serialization.Serializable

@Serializable
enum class TapAction {
    NONE,
    NEXT,
    PREVIOUS,
    OPEN_MENU,
    TOGGLE_BOOKMARK,
}

/** A rectangular tap region, normalized to the reader's size (0f..1f on both axes). */
@Serializable
data class TapZone(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val action: TapAction,
)

/** User-customizable tap regions overlaid on the reader. Later zones win on overlap. */
@Serializable
data class TapZoneConfig(
    val enabled: Boolean = true,
    val zones: List<TapZone> = defaultZones,
) {
    /** [x] and [y] are normalized (0f..1f) coordinates within the reader surface. */
    fun actionAt(x: Float, y: Float): TapAction =
        zones.lastOrNull { x in it.left..it.right && y in it.top..it.bottom }?.action ?: TapAction.NONE

    companion object {
        private const val THIRD = 1f / 3f
        private const val TWO_THIRDS = 2f / 3f

        // Classic reader layout: left third back, right third forward, centre opens the menu.
        val defaultZones = listOf(
            TapZone(0f, 0f, THIRD, 1f, TapAction.PREVIOUS),
            TapZone(THIRD, 0f, TWO_THIRDS, 1f, TapAction.OPEN_MENU),
            TapZone(TWO_THIRDS, 0f, 1f, 1f, TapAction.NEXT),
        )
    }
}
