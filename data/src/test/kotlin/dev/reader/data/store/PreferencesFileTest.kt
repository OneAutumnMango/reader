package dev.reader.data.store

import dev.reader.core.theme.AppPaletteOverrides
import dev.reader.core.theme.ReaderTheme
import dev.reader.core.theme.SavedCustomTheme
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals

/** Mirrors [ReaderStores]'s own JSON config, so round-trip tests here match real persisted behavior. */
private val STORE_JSON = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

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

    @Test
    fun `customAppThemes is empty by default`() {
        assertEquals(emptyList(), PreferencesFile().customAppThemes)
    }

    @Test
    fun `a non-empty customAppThemes list round-trips through serialization`() {
        val prefs = PreferencesFile(
            customAppThemes = listOf(
                SavedCustomTheme(id = "abc", seedColor = 0xFF6750A4L),
                SavedCustomTheme(
                    id = "def",
                    seedColor = 0xFF2E7D32L,
                    overrides = AppPaletteOverrides(accent = 0xFFFFEB3BL),
                ),
            ),
        )

        val encoded = STORE_JSON.encodeToString(PreferencesFile.serializer(), prefs)
        val decoded = STORE_JSON.decodeFromString(PreferencesFile.serializer(), encoded)

        assertEquals(prefs.customAppThemes, decoded.customAppThemes)
    }
}
