package dev.reader.app.ui.appearance

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.reader.app.ui.MenuSubheader
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
    0xFF00796BL,
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

private fun ColorScheme.effective(role: ColorRole): Color = when (role) {
    ColorRole.BACKGROUND -> background
    ColorRole.SURFACE -> surface
    ColorRole.ON_SURFACE -> onSurface
    ColorRole.OUTLINE -> outline
    ColorRole.ACCENT -> primary
}

private fun Color.toColorLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

@Composable
fun AppThemeEditor(theme: AppTheme, onChange: (AppTheme) -> Unit, modifier: Modifier = Modifier) {
    // Mirrors `theme` in a tracked MutableState so that changes applied from within this editor
    // (e.g. from the always-open CustomThemeDialog) are reflected immediately even if the caller's
    // own recomposition doesn't feed a fresh `theme` value straight back down on the same frame.
    var localTheme by remember { mutableStateOf(theme) }
    LaunchedEffect(theme) { localTheme = theme }
    val apply: (AppTheme) -> Unit = {
        localTheme = it
        onChange(it)
    }

    var showCustomDialog by remember { mutableStateOf(false) }
    val systemDark = isSystemInDarkTheme()

    Column(modifier = modifier) {
        MenuSubheader("Theme")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SEED_PRESETS.forEach { seed ->
                ThemePresetCard(
                    seedColor = seed,
                    mode = localTheme.mode,
                    systemInDarkTheme = systemDark,
                    selected = localTheme.seedColor == seed && localTheme.overrides == AppPaletteOverrides(),
                    onClick = { apply(localTheme.copy(seedColor = seed, overrides = AppPaletteOverrides())) },
                )
            }
            AddCustomThemeCard(onClick = { showCustomDialog = true })
        }

        MenuSubheader("Theme mode")
        RadioOptions(
            options = AppThemeMode.entries,
            selected = theme.mode,
            label = { mode -> mode.name.lowercase().replaceFirstChar { it.uppercase() } },
            tag = { "app-theme-mode-${it.name}" },
            onSelect = { onChange(theme.copy(mode = it)) },
        )
    }

    if (showCustomDialog) {
        CustomThemeDialog(localTheme, systemDark, apply, onDismiss = { showCustomDialog = false })
    }
}

@Composable
private fun CustomThemeDialog(
    theme: AppTheme,
    systemInDarkTheme: Boolean,
    onChange: (AppTheme) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = remember(theme.seedColor, theme.mode, systemInDarkTheme, theme.overrides) {
        AppPaletteFactory.colorScheme(theme.seedColor, theme.mode, systemInDarkTheme, theme.overrides)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom theme") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeColorRow(
                    label = "Seed colour",
                    color = theme.seedColor,
                    onPick = { onChange(theme.copy(seedColor = it)) },
                )
                HorizontalDivider()
                ColorRole.entries.forEach { role ->
                    ThemeColorRow(
                        label = role.label,
                        color = scheme.effective(role).toColorLong(),
                        onPick = { onChange(theme.copy(overrides = theme.overrides.with(role, it))) },
                        overridden = theme.overrides.get(role) != null,
                        onReset = { onChange(theme.copy(overrides = theme.overrides.with(role, null))) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun ThemeColorRow(
    label: String,
    color: Long,
    onPick: (Long) -> Unit,
    overridden: Boolean = false,
    onReset: (() -> Unit)? = null,
) {
    var picking by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        ColorSwatch(
            color,
            selected = false,
            onClick = { picking = true },
            modifier = Modifier.testTag("theme-color-$label"),
        )
        Text(label, modifier = Modifier.weight(1f).padding(start = 12.dp))
        if (onReset != null && overridden) {
            IconButton(onClick = onReset, modifier = Modifier.testTag("theme-color-reset-$label")) {
                Icon(Icons.Filled.Close, contentDescription = "Reset $label")
            }
        }
    }

    if (picking) {
        ColorPickerDialog(
            initial = color,
            label = label,
            onDismiss = { picking = false },
            onPicked = {
                onPick(it)
                picking = false
            },
        )
    }
}
