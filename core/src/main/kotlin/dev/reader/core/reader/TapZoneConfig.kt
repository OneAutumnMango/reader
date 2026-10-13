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

/** A 3x3 grid of tap regions overlaid on the reader, row-major from top-left. */
@Serializable
data class TapZoneConfig(
    val enabled: Boolean = true,
    val zones: List<TapAction> = defaultZones,
) {
    init {
        require(zones.size == GRID_SIZE * GRID_SIZE) { "tap zone grid must be ${GRID_SIZE}x$GRID_SIZE" }
    }

    fun actionAt(row: Int, column: Int): TapAction = zones[row * GRID_SIZE + column]

    companion object {
        const val GRID_SIZE = 3

        // Classic reader layout: left column back, right column forward, centre opens the menu.
        val defaultZones = listOf(
            TapAction.PREVIOUS, TapAction.OPEN_MENU, TapAction.NEXT,
            TapAction.PREVIOUS, TapAction.OPEN_MENU, TapAction.NEXT,
            TapAction.PREVIOUS, TapAction.OPEN_MENU, TapAction.NEXT,
        )
    }
}
