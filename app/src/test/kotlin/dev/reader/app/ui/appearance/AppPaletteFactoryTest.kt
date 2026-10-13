package dev.reader.app.ui.appearance

import androidx.compose.ui.graphics.Color
import dev.reader.core.theme.AppThemeMode
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@RunWith(RobolectricTestRunner::class)
class AppPaletteFactoryTest {

    @Test
    fun `amoled mode uses a pure black background`() {
        val scheme = AppPaletteFactory.colorScheme(0xFF6750A4L, AppThemeMode.AMOLED, systemInDarkTheme = false)
        assertEquals(Color.Black, scheme.background)
    }

    @Test
    fun `light mode does not use a black background`() {
        val scheme = AppPaletteFactory.colorScheme(0xFF6750A4L, AppThemeMode.LIGHT, systemInDarkTheme = true)
        assertNotEquals(Color.Black, scheme.background)
    }

    @Test
    fun `system mode follows the system dark flag`() {
        val lightScheme = AppPaletteFactory.colorScheme(0xFF6750A4L, AppThemeMode.SYSTEM, systemInDarkTheme = false)
        val darkScheme = AppPaletteFactory.colorScheme(0xFF6750A4L, AppThemeMode.SYSTEM, systemInDarkTheme = true)
        assertNotEquals(lightScheme.background, darkScheme.background)
    }
}
