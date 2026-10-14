package dev.reader.app.ui.appearance

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.core.graphics.ColorUtils
import dev.reader.core.theme.AppPaletteOverrides
import dev.reader.core.theme.AppThemeMode

private const val HSL_COMPONENT_COUNT = 3
private const val MIN_ACCENT_SATURATION = 0.35f
private const val MAX_ACCENT_SATURATION = 0.65f
private const val SECONDARY_SATURATION_RATIO = 0.45f
private const val TERTIARY_HUE_SHIFT = 60f
private const val FULL_CIRCLE = 360f
private const val NEUTRAL_SATURATION_RATIO = 0.35f
private const val MIN_NEUTRAL_SATURATION = 0.03f
private const val MAX_NEUTRAL_SATURATION = 0.16f
private const val VARIANT_SATURATION_RATIO = 1.6f
private const val MAX_VARIANT_SATURATION = 0.25f
private const val ERROR_HUE = 4f
private const val ERROR_SATURATION = 0.7f
private const val HALF = 0.5f

/** HSL lightness for each colour role, so light, dark and AMOLED share one scheme builder. */
private data class Tones(
    val accent: Float,
    val onAccent: Float,
    val container: Float,
    val onContainer: Float,
    val inverseAccent: Float,
    val background: Float,
    val onBackground: Float,
    val variant: Float,
    val onVariant: Float,
    val outline: Float,
    val outlineVariant: Float,
    val inverseSurface: Float,
    val inverseOnSurface: Float,
    val lowest: Float,
    val low: Float,
    val mid: Float,
    val high: Float,
    val highest: Float,
    val dim: Float,
    val bright: Float,
)

private val LIGHT = Tones(
    accent = 0.40f, onAccent = 1f, container = 0.88f, onContainer = 0.14f, inverseAccent = 0.80f,
    background = 0.975f, onBackground = 0.11f, variant = 0.89f, onVariant = 0.30f,
    outline = 0.50f, outlineVariant = 0.80f, inverseSurface = 0.20f, inverseOnSurface = 0.95f,
    lowest = 1f, low = 0.955f, mid = 0.935f, high = 0.915f, highest = 0.895f, dim = 0.86f, bright = 0.975f,
)

private val DARK = Tones(
    accent = 0.76f, onAccent = 0.18f, container = 0.30f, onContainer = 0.90f, inverseAccent = 0.40f,
    background = 0.085f, onBackground = 0.91f, variant = 0.28f, onVariant = 0.80f,
    outline = 0.58f, outlineVariant = 0.30f, inverseSurface = 0.90f, inverseOnSurface = 0.20f,
    lowest = 0.05f, low = 0.11f, mid = 0.13f, high = 0.165f, highest = 0.205f, dim = 0.085f, bright = 0.24f,
)

private val AMOLED = DARK.copy(
    background = 0f, lowest = 0f, low = 0.04f, mid = 0.065f, high = 0.095f, highest = 0.13f, dim = 0f, bright = 0.17f,
)

/**
 * Derives a full Material3 [ColorScheme] from one seed colour, without a dynamic-colour dependency.
 * Every surface, container, outline and accent is tinted with the seed's hue so the app reads as one theme.
 */
object AppPaletteFactory {

    fun colorScheme(
        seedColor: Long,
        mode: AppThemeMode,
        systemInDarkTheme: Boolean,
        overrides: AppPaletteOverrides = AppPaletteOverrides(),
    ): ColorScheme {
        val tones = when {
            mode == AppThemeMode.AMOLED -> AMOLED
            mode == AppThemeMode.DARK || (mode == AppThemeMode.SYSTEM && systemInDarkTheme) -> DARK
            else -> LIGHT
        }
        return Palettes(seedColor).scheme(tones).withOverrides(overrides)
    }
}

private class Palettes(seed: Long) {
    private val hue: Float
    private val accentSaturation: Float
    private val neutralSaturation: Float

    init {
        val hsl = FloatArray(HSL_COMPONENT_COUNT)
        ColorUtils.colorToHSL(seed.toInt(), hsl)
        hue = hsl[0]
        accentSaturation = hsl[1].coerceIn(MIN_ACCENT_SATURATION, MAX_ACCENT_SATURATION)
        neutralSaturation = (hsl[1] * NEUTRAL_SATURATION_RATIO).coerceIn(MIN_NEUTRAL_SATURATION, MAX_NEUTRAL_SATURATION)
    }

    private fun hsl(hue: Float, saturation: Float, lightness: Float) =
        Color(ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness)))

    fun primary(l: Float) = hsl(hue, accentSaturation, l)
    fun secondary(l: Float) = hsl(hue, accentSaturation * SECONDARY_SATURATION_RATIO, l)
    fun tertiary(l: Float) = hsl((hue + TERTIARY_HUE_SHIFT) % FULL_CIRCLE, accentSaturation, l)
    fun neutral(l: Float) = hsl(hue, neutralSaturation, l)
    fun variant(l: Float) =
        hsl(hue, (neutralSaturation * VARIANT_SATURATION_RATIO).coerceAtMost(MAX_VARIANT_SATURATION), l)
    fun error(l: Float) = hsl(ERROR_HUE, ERROR_SATURATION, l)

    // lightColorScheme only differs from darkColorScheme in its defaults, and every role is set here.
    fun scheme(t: Tones): ColorScheme = lightColorScheme(
        primary = primary(t.accent),
        onPrimary = primary(t.onAccent),
        primaryContainer = primary(t.container),
        onPrimaryContainer = primary(t.onContainer),
        inversePrimary = primary(t.inverseAccent),
        secondary = secondary(t.accent),
        onSecondary = secondary(t.onAccent),
        secondaryContainer = secondary(t.container),
        onSecondaryContainer = secondary(t.onContainer),
        tertiary = tertiary(t.accent),
        onTertiary = tertiary(t.onAccent),
        tertiaryContainer = tertiary(t.container),
        onTertiaryContainer = tertiary(t.onContainer),
        background = neutral(t.background),
        onBackground = neutral(t.onBackground),
        surface = neutral(t.background),
        onSurface = neutral(t.onBackground),
        surfaceVariant = variant(t.variant),
        onSurfaceVariant = variant(t.onVariant),
        surfaceTint = primary(t.accent),
        inverseSurface = neutral(t.inverseSurface),
        inverseOnSurface = neutral(t.inverseOnSurface),
        error = error(t.accent),
        onError = error(t.onAccent),
        errorContainer = error(t.container),
        onErrorContainer = error(t.onContainer),
        outline = variant(t.outline),
        outlineVariant = variant(t.outlineVariant),
        scrim = Color.Black,
        surfaceBright = neutral(t.bright),
        surfaceDim = neutral(t.dim),
        surfaceContainerLowest = neutral(t.lowest),
        surfaceContainerLow = neutral(t.low),
        surfaceContainer = neutral(t.mid),
        surfaceContainerHigh = neutral(t.high),
        surfaceContainerHighest = neutral(t.highest),
    )
}

private const val LOW_STEP = 0.03f
private const val MID_STEP = 0.05f
private const val HIGH_STEP = 0.08f
private const val HIGHEST_STEP = 0.11f
private const val VARIANT_STEP = 0.14f
private const val ON_VARIANT_FADE = 0.3f
private const val CONTAINER_TINT = 0.3f
private const val INVERSE_FADE = 0.4f

/** Layers manual role overrides on top, re-deriving the roles that depend on them so they still match. */
private fun ColorScheme.withOverrides(o: AppPaletteOverrides): ColorScheme {
    var s = this
    o.onSurface?.let { c ->
        val text = Color(c)
        s = s.copy(onSurface = text, onBackground = text, onSurfaceVariant = lerp(text, s.surface, ON_VARIANT_FADE))
    }
    val backdrop = o.surface ?: o.background
    o.background?.let { s = s.copy(background = Color(it)) }
    backdrop?.let { s = s.withSurfaces(Color(it)) }
    o.outline?.let { c ->
        val line = Color(c)
        s = s.copy(outline = line, outlineVariant = lerp(line, s.surface, HALF))
    }
    o.accent?.let { c ->
        val accent = Color(c)
        s = s.copy(
            primary = accent,
            onPrimary = if (accent.luminance() > HALF) Color.Black else Color.White,
            primaryContainer = lerp(s.surface, accent, CONTAINER_TINT),
            onPrimaryContainer = s.onSurface,
            inversePrimary = lerp(accent, s.surface, INVERSE_FADE),
            surfaceTint = accent,
        )
    }
    return s
}

private fun ColorScheme.withSurfaces(surface: Color): ColorScheme = copy(
    surface = surface,
    surfaceBright = surface,
    surfaceContainerLowest = surface,
    surfaceContainerLow = lerp(surface, onSurface, LOW_STEP),
    surfaceContainer = lerp(surface, onSurface, MID_STEP),
    surfaceContainerHigh = lerp(surface, onSurface, HIGH_STEP),
    surfaceContainerHighest = lerp(surface, onSurface, HIGHEST_STEP),
    surfaceVariant = lerp(surface, onSurface, VARIANT_STEP),
    surfaceDim = lerp(surface, onSurface, HIGH_STEP),
)
