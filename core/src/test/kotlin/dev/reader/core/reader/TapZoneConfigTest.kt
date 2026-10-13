package dev.reader.core.reader

import kotlin.test.Test
import kotlin.test.assertEquals

class TapZoneConfigTest {

    @Test
    fun `default zones are back-menu-forward thirds`() {
        val config = TapZoneConfig()

        assertEquals(TapAction.PREVIOUS, config.actionAt(0.1f, 0.5f))
        assertEquals(TapAction.OPEN_MENU, config.actionAt(0.5f, 0.5f))
        assertEquals(TapAction.NEXT, config.actionAt(0.9f, 0.5f))
    }

    @Test
    fun `outside every zone returns none`() {
        val config = TapZoneConfig(zones = emptyList())

        assertEquals(TapAction.NONE, config.actionAt(0.5f, 0.5f))
    }

    @Test
    fun `later zones win on overlap`() {
        val config = TapZoneConfig(
            zones = listOf(
                TapZone(0f, 0f, 1f, 1f, TapAction.NEXT),
                TapZone(0f, 0f, 0.5f, 0.5f, TapAction.PREVIOUS),
            ),
        )

        assertEquals(TapAction.PREVIOUS, config.actionAt(0.1f, 0.1f))
        assertEquals(TapAction.NEXT, config.actionAt(0.9f, 0.9f))
    }
}
