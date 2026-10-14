package dev.reader.app.ui.appearance

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import dev.reader.core.theme.AppPaletteOverrides
import dev.reader.core.theme.AppThemeMode
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val PURPLE = 0xFF6750A4L
private const val GREEN = 0xFF2E7D32L

@RunWith(RobolectricTestRunner::class)
class AppPaletteFactoryTest {

    private fun hue(color: Color): Float = FloatArray(3).also { ColorUtils.colorToHSL(color.toArgb(), it) }[0]

    @Test
    fun `amoled mode uses a pure black background`() {
        val scheme = AppPaletteFactory.colorScheme(PURPLE, AppThemeMode.AMOLED, systemInDarkTheme = false)
        assertEquals(Color.Black, scheme.background)
    }

    @Test
    fun `light mode does not use a black background`() {
        val scheme = AppPaletteFactory.colorScheme(PURPLE, AppThemeMode.LIGHT, systemInDarkTheme = true)
        assertNotEquals(Color.Black, scheme.background)
    }

    @Test
    fun `system mode follows the system dark flag`() {
        val lightScheme = AppPaletteFactory.colorScheme(PURPLE, AppThemeMode.SYSTEM, systemInDarkTheme = false)
        val darkScheme = AppPaletteFactory.colorScheme(PURPLE, AppThemeMode.SYSTEM, systemInDarkTheme = true)
        assertNotEquals(lightScheme.background, darkScheme.background)
    }

    @Test
    fun `every surface and container carries the seed hue and the bar matches the page`() {
        val purple = AppPaletteFactory.colorScheme(PURPLE, AppThemeMode.LIGHT, systemInDarkTheme = false)
        val green = AppPaletteFactory.colorScheme(GREEN, AppThemeMode.LIGHT, systemInDarkTheme = false)

        assertEquals(purple.background, purple.surface)
        listOf(
            purple.surfaceContainer,
            purple.surfaceContainerHigh,
            purple.secondaryContainer,
            purple.primaryContainer,
            purple.outlineVariant,
        ).forEach { assertTrue(abs(hue(it) - hue(purple.primary)) < 15f, "$it isn't tinted with the seed") }
        assertNotEquals(purple.surfaceContainer, green.surfaceContainer)
        assertNotEquals(purple.secondaryContainer, green.secondaryContainer)
    }

    @Test
    fun `text and accents contrast with their backgrounds in both modes`() {
        for (mode in listOf(AppThemeMode.LIGHT, AppThemeMode.DARK, AppThemeMode.AMOLED)) {
            val s = AppPaletteFactory.colorScheme(PURPLE, mode, systemInDarkTheme = false)
            assertTrue(abs(s.onSurface.luminance() - s.surface.luminance()) > 0.5f, "$mode text")
            assertTrue(abs(s.onPrimary.luminance() - s.primary.luminance()) > 0.3f, "$mode accent")
        }
    }

    @Test
    fun `overrides carry through to the roles derived from them`() {
        val overrides = AppPaletteOverrides(background = 0xFFFFF4E0L, accent = 0xFFFFEB3BL)
        val s = AppPaletteFactory.colorScheme(PURPLE, AppThemeMode.LIGHT, false, overrides)

        assertEquals(Color(0xFFFFF4E0), s.background)
        assertEquals(s.background, s.surface)
        assertNotEquals(s.surface, s.surfaceContainer)
        assertEquals(Color(0xFFFFEB3B), s.primary)
        assertEquals(Color.Black, s.onPrimary)
    }
}
