package dev.reader.core.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TapZoneConfigTest {

    @Test
    fun `default grid is back-menu-forward in every row`() {
        val config = TapZoneConfig()
        for (row in 0 until TapZoneConfig.GRID_SIZE) {
            assertEquals(TapAction.PREVIOUS, config.actionAt(row, 0))
            assertEquals(TapAction.OPEN_MENU, config.actionAt(row, 1))
            assertEquals(TapAction.NEXT, config.actionAt(row, 2))
        }
    }

    @Test
    fun `rejects a grid of the wrong size`() {
        assertFailsWith<IllegalArgumentException> {
            TapZoneConfig(zones = listOf(TapAction.NEXT))
        }
    }
}
