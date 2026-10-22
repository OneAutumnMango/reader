package dev.reader.core.theme

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SavedCustomThemeTest {

    @Test
    fun `round-trips through JSON with no overrides set`() {
        val original = SavedCustomTheme(id = "abc", seedColor = 0xFF6750A4L)

        val encoded = Json.encodeToString(SavedCustomTheme.serializer(), original)
        val decoded = Json.decodeFromString(SavedCustomTheme.serializer(), encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `round-trips through JSON with overrides set`() {
        val original = SavedCustomTheme(
            id = "def",
            seedColor = 0xFF2E7D32L,
            overrides = AppPaletteOverrides(
                background = 0xFFFFF4E0L,
                surface = 0xFFEFEFEFL,
                onSurface = 0xFF111111L,
                outline = 0xFF222222L,
                accent = 0xFFFFEB3BL,
            ),
        )

        val encoded = Json.encodeToString(SavedCustomTheme.serializer(), original)
        val decoded = Json.decodeFromString(SavedCustomTheme.serializer(), encoded)

        assertEquals(original, decoded)
    }
}
