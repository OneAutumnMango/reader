package dev.reader.core.theme

import kotlinx.serialization.Serializable

@Serializable
enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED,
}

/** Manual overrides for individual palette roles, layered on top of the seed-derived colours. */
@Serializable
data class AppPaletteOverrides(
    val background: Long? = null,
    val surface: Long? = null,
    val onSurface: Long? = null,
    val outline: Long? = null,
    val accent: Long? = null,
)

/** App-wide UI appearance, independent of the in-book reader theme. */
@Serializable
data class AppTheme(
    val seedColor: Long = 0xFF6750A4,
    val mode: AppThemeMode = AppThemeMode.SYSTEM,
    val overrides: AppPaletteOverrides = AppPaletteOverrides(),
)
