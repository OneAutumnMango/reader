package dev.reader.app.ui.appearance

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import dev.reader.core.theme.AppPaletteOverrides
import dev.reader.core.theme.AppThemeMode

private const val AMOLED_SURFACE_LIGHTNESS = 0.06f
private const val DARK_BACKGROUND_LIGHTNESS = 0.1f
private const val DARK_SURFACE_LIGHTNESS = 0.16f
private const val DARK_PRIMARY_LIGHTNESS = 0.75f
private const val DARK_ON_BACKGROUND_LIGHTNESS = 0.92f
private const val LIGHT_BACKGROUND_LIGHTNESS = 0.98f
private const val LIGHT_SURFACE_LIGHTNESS = 0.94f
private const val LIGHT_PRIMARY_LIGHTNESS = 0.42f
private const val LIGHT_ON_BACKGROUND_LIGHTNESS = 0.12f
private const val NEUTRAL_SATURATION = 0.06f
private const val MIN_ACCENT_SATURATION = 0.3f
private const val MAX_ACCENT_SATURATION = 0.6f
private const val HSL_COMPONENT_COUNT = 3

/** Derives a Material3 [ColorScheme] from one seed colour, without a dynamic-colour dependency. */
object AppPaletteFactory {

    @Suppress("CyclomaticComplexMethod")
    fun colorScheme(
        seedColor: Long,
        mode: AppThemeMode,
        systemInDarkTheme: Boolean,
        overrides: AppPaletteOverrides = AppPaletteOverrides(),
    ): ColorScheme {
        val dark = isDark(mode, systemInDarkTheme)
        val hsl = FloatArray(HSL_COMPONENT_COUNT)
        ColorUtils.colorToHSL(seedColor.toInt(), hsl)
        val hue = hsl[0]
        val accentSaturation = hsl[1].coerceIn(MIN_ACCENT_SATURATION, MAX_ACCENT_SATURATION)

        fun tone(lightness: Float, saturation: Float = NEUTRAL_SATURATION) =
            Color(ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness)))

        val background = overrides.background?.let { Color(it) } ?: defaultBackground(mode, dark, ::tone)
        val surface = overrides.surface?.let { Color(it) } ?: defaultSurface(mode, dark, ::tone)
        val primary = overrides.accent?.let { Color(it) }
            ?: tone(if (dark) DARK_PRIMARY_LIGHTNESS else LIGHT_PRIMARY_LIGHTNESS, accentSaturation)
        val onPrimary = if (dark) Color.Black else Color.White
        val onBackground = overrides.onSurface?.let { Color(it) }
            ?: tone(if (dark) DARK_ON_BACKGROUND_LIGHTNESS else LIGHT_ON_BACKGROUND_LIGHTNESS)
        val outline = overrides.outline?.let { Color(it) }

        val base = if (dark) {
            darkColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = onBackground,
            )
        } else {
            lightColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = onBackground,
            )
        }
        return if (outline != null) base.copy(outline = outline) else base
    }

    private fun isDark(mode: AppThemeMode, systemInDarkTheme: Boolean): Boolean = when (mode) {
        AppThemeMode.SYSTEM -> systemInDarkTheme
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK, AppThemeMode.AMOLED -> true
    }

    private fun defaultBackground(mode: AppThemeMode, dark: Boolean, tone: (Float, Float) -> Color): Color =
        if (mode == AppThemeMode.AMOLED) {
            Color.Black
        } else {
            tone(if (dark) DARK_BACKGROUND_LIGHTNESS else LIGHT_BACKGROUND_LIGHTNESS, NEUTRAL_SATURATION)
        }

    private fun defaultSurface(mode: AppThemeMode, dark: Boolean, tone: (Float, Float) -> Color): Color =
        if (mode == AppThemeMode.AMOLED) {
            tone(AMOLED_SURFACE_LIGHTNESS, NEUTRAL_SATURATION)
        } else {
            tone(if (dark) DARK_SURFACE_LIGHTNESS else LIGHT_SURFACE_LIGHTNESS, NEUTRAL_SATURATION)
        }
}
