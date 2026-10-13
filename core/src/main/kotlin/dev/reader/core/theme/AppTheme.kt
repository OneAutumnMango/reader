package dev.reader.core.theme

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED,
}

/** App-wide UI appearance, independent of the in-book reader theme. */
data class AppTheme(
    val seedColor: Long = 0xFF6750A4,
    val mode: AppThemeMode = AppThemeMode.SYSTEM,
)
