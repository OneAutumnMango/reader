package dev.reader.app.ui.appearance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import dev.reader.app.ui.RadioOptions
import dev.reader.core.theme.AppPaletteOverrides
import dev.reader.core.theme.AppTheme
import dev.reader.core.theme.AppThemeMode

private val SEED_PRESETS = listOf(
    0xFF6750A4L,
    0xFF2E7D32L,
    0xFF1565C0L,
    0xFFB71C1CL,
    0xFFEF6C00L,
)

private enum class ColorRole(val label: String) {
    BACKGROUND("Background"),
    SURFACE("Surface"),
    ON_SURFACE("Text"),
    OUTLINE("Outline"),
    ACCENT("Accent"),
}

private fun AppPaletteOverrides.get(role: ColorRole): Long? = when (role) {
    ColorRole.BACKGROUND -> background
    ColorRole.SURFACE -> surface
    ColorRole.ON_SURFACE -> onSurface
    ColorRole.OUTLINE -> outline
    ColorRole.ACCENT -> accent
}

private fun AppPaletteOverrides.with(role: ColorRole, value: Long?): AppPaletteOverrides = when (role) {
    ColorRole.BACKGROUND -> copy(background = value)
    ColorRole.SURFACE -> copy(surface = value)
    ColorRole.ON_SURFACE -> copy(onSurface = value)
    ColorRole.OUTLINE -> copy(outline = value)
    ColorRole.ACCENT -> copy(accent = value)
}

@Composable
fun AppThemeEditor(theme: AppTheme, onChange: (AppTheme) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row {
            SEED_PRESETS.forEach { seed ->
                ColorSwatch(
                    seed,
                    selected = theme.seedColor == seed,
                    onClick = { onChange(theme.copy(seedColor = seed)) },
                    modifier = Modifier.testTag("seed-swatch-${"%08X".format(seed)}"),
                )
            }
            PickColorButton(theme.seedColor, "Seed colour") { onChange(theme.copy(seedColor = it)) }
        }

        OutlinedTextField(
            value = "#%08X".format(theme.seedColor),
            onValueChange = { text ->
                text.removePrefix("#").toLongOrNull(16)?.let { onChange(theme.copy(seedColor = it)) }
            },
            label = { Text("Seed colour") },
        )

        Text("Theme mode")
        RadioOptions(
            options = AppThemeMode.entries,
            selected = theme.mode,
            label = { mode -> mode.name.lowercase().replaceFirstChar { it.uppercase() } },
            tag = { "app-theme-mode-${it.name}" },
            onSelect = { onChange(theme.copy(mode = it)) },
        )

        Text("Individual colours")
        ColorRole.entries.forEach { role ->
            val current = theme.overrides.get(role)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(role.label, modifier = Modifier.weight(1f))
                if (current != null) {
                    ColorSwatch(current, selected = false, onClick = {})
                    PickColorButton(current, role.label) {
                        onChange(theme.copy(overrides = theme.overrides.with(role, it)))
                    }
                    TextButton(onClick = { onChange(theme.copy(overrides = theme.overrides.with(role, null))) }) {
                        Text("Reset")
                    }
                } else {
                    PickColorButton(theme.seedColor, role.label) {
                        onChange(theme.copy(overrides = theme.overrides.with(role, it)))
                    }
                }
            }
        }
    }
}
