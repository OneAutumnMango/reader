package dev.reader.data.store

import dev.reader.core.theme.ReaderTheme
import org.junit.Test
import kotlin.test.assertEquals

class PreferencesFileTest {

    @Test
    fun `resolves the active preset's day colors by default`() {
        val prefs = PreferencesFile()

        assertEquals(ReaderTheme.Default, prefs.activeReaderTheme)
    }

    @Test
    fun `swaps in the active preset's night colors once night theme is enabled, unchanged otherwise`() {
        val preset = ReaderTheme.Default.copy(
            fontSizeSp = 22f,
            textColor = 0xFF111111,
            backgroundColor = 0xFFEEEEEE,
            nightTextColor = 0xFF00FF00,
            nightBackgroundColor = 0xFF330033,
        )
        val prefs = PreferencesFile(themePresets = listOf(preset), nightThemeEnabled = true)

        val resolved = prefs.activeReaderTheme

        assertEquals(0xFF00FF00, resolved.textColor)
        assertEquals(0xFF330033, resolved.backgroundColor)
        // Everything else (font, size, spacing) stays the same regardless of day/night.
        assertEquals(22f, resolved.fontSizeSp)
    }

    @Test
    fun `falls back to Default when the active preset no longer exists`() {
        val prefs = PreferencesFile(
            themePresets = listOf(ReaderTheme.Sepia),
            activeThemePresetName = "Deleted preset",
        )

        assertEquals(ReaderTheme.Default, prefs.activeReaderTheme)
    }
}
