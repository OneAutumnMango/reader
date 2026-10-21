package dev.reader.core.theme

import kotlinx.serialization.Serializable

@Serializable
data class SavedCustomTheme(
    val id: String,
    val seedColor: Long,
    val overrides: AppPaletteOverrides = AppPaletteOverrides(),
)
