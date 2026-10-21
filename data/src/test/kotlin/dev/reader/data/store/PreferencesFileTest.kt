package dev.reader.data.store

import dev.reader.core.theme.ReaderTheme
import org.junit.Test
import kotlin.test.assertEquals

class PreferencesFileTest {

    @Test
    fun `resolves the day preset by default`() {
        val prefs = PreferencesFile()

        assertEquals(ReaderTheme.Default, prefs.activeReaderTheme)
    }

    @Test
    fun `resolves the night preset once night theme is enabled`() {
        val prefs = PreferencesFile(nightThemeEnabled = true)

        assertEquals(ReaderTheme.Night, prefs.activeReaderTheme)
    }

    @Test
    fun `day and night can point at independently customized presets`() {
        val customDay = ReaderTheme.Default.copy(name = "My Day", fontSizeSp = 22f)
        val customNight = ReaderTheme.Night.copy(name = "My Night", fontSizeSp = 16f)
        val prefs = PreferencesFile(
            themePresets = listOf(customDay, customNight),
            activeThemePresetName = customDay.name,
            nightThemePresetName = customNight.name,
        )

        assertEquals(customDay, prefs.activeReaderTheme)
        assertEquals(customNight, prefs.copy(nightThemeEnabled = true).activeReaderTheme)
    }

    @Test
    fun `falls back to Default when the pointed-at preset no longer exists`() {
        val prefs = PreferencesFile(
            themePresets = listOf(ReaderTheme.Default),
            nightThemePresetName = "Deleted preset",
            nightThemeEnabled = true,
        )

        assertEquals(ReaderTheme.Default, prefs.activeReaderTheme)
    }
}
