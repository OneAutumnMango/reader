package dev.reader.app.ui.appearance

import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.documentfile.provider.DocumentFile
import dev.reader.app.data.AppLog
import dev.reader.app.ui.reader.CUSTOM_FONT_FILE_PREFIX
import dev.reader.app.ui.reader.resolveFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val CUSTOM_FONT_OPTION = "Other system font…"
private const val ADD_FONT_FILE_OPTION = "Add font file…"
private const val FONTS_DIR = "fonts"

// Many file managers report .ttf/.otf as octet-stream, so allow anything and validate after.
private val FONT_MIME_TYPES = arrayOf("font/*", "application/x-font-ttf", "application/octet-stream", "*/*")

/** Font families bundled with Android, keyed by the name [resolveFontFamily] understands. */
internal val SYSTEM_FONTS = linkedMapOf(
    "serif" to "Serif (Noto Serif)",
    "sans-serif" to "Sans serif (Roboto)",
    "sans-serif-light" to "Roboto Light",
    "sans-serif-thin" to "Roboto Thin",
    "sans-serif-medium" to "Roboto Medium",
    "sans-serif-black" to "Roboto Black",
    "sans-serif-condensed" to "Roboto Condensed",
    "sans-serif-condensed-light" to "Roboto Condensed Light",
    "sans-serif-smallcaps" to "Small caps",
    "monospace" to "Monospace",
    "serif-monospace" to "Typewriter (Cutive Mono)",
    "casual" to "Casual (Coming Soon)",
    "cursive" to "Cursive (Dancing Script)",
)

/** Human label for a stored font family value. */
internal fun fontLabel(fontFamily: String): String = when {
    fontFamily.startsWith(CUSTOM_FONT_FILE_PREFIX) ->
        File(fontFamily.removePrefix(CUSTOM_FONT_FILE_PREFIX)).nameWithoutExtension
    else -> SYSTEM_FONTS[fontFamily] ?: fontFamily
}

/**
 * Picks the reader font: Android's bundled families (each shown in its own typeface), any font
 * files the user has added, another system family by name, or a new .ttf/.otf file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontDropdown(fontFamily: String, onSelect: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var fontsVersion by remember { mutableIntStateOf(0) }
    val fontsDir = remember { File(context.filesDir, FONTS_DIR) }
    val addedFonts = remember(fontsVersion) {
        fontsDir.listFiles { f -> f.isFile }.orEmpty().sortedBy { it.name.lowercase() }
    }
    val isOtherSystemFont = !fontFamily.startsWith(CUSTOM_FONT_FILE_PREFIX) && fontFamily !in SYSTEM_FONTS
    var showOtherField by remember(fontFamily) { mutableStateOf(isOtherSystemFont) }
    var otherName by remember(fontFamily) { mutableStateOf(if (isOtherSystemFont) fontFamily else "") }

    val fontFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val added = withContext(Dispatchers.IO) {
                AppLog.debug("Starting font-file import", "FontImport")
                runCatching { importFont(context, uri, fontsDir) }
                    .onFailure { AppLog.error("Couldn't copy font file", "FontImport", it) }
                    .getOrNull()
            }
            if (added == null) {
                Toast.makeText(context, "That file isn't a font Android can read", Toast.LENGTH_SHORT).show()
            } else {
                AppLog.info("Imported font file", "FontImport")
                fontsVersion++
                onSelect("$CUSTOM_FONT_FILE_PREFIX${added.path}")
            }
        }
    }

    fun choose(value: String) {
        expanded = false
        showOtherField = false
        onSelect(value)
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = fontLabel(fontFamily),
            onValueChange = {},
            readOnly = true,
            label = { Text("Font") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag("font-dropdown"),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SYSTEM_FONTS.forEach { (key, label) ->
                FontOption(label, key, Modifier.testTag("font-option-$key")) { choose(key) }
            }
            if (addedFonts.isNotEmpty()) HorizontalDivider()
            addedFonts.forEach { file ->
                val value = "$CUSTOM_FONT_FILE_PREFIX${file.path}"
                FontOption(file.nameWithoutExtension, value, Modifier.testTag("font-option-file-${file.name}")) {
                    choose(value)
                }
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(CUSTOM_FONT_OPTION) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    showOtherField = true
                },
                modifier = Modifier.testTag("font-option-custom"),
            )
            DropdownMenuItem(
                text = { Text(ADD_FONT_FILE_OPTION) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = {
                    expanded = false
                    fontFileLauncher.launch(FONT_MIME_TYPES)
                },
                modifier = Modifier.testTag("font-option-add-file"),
            )
        }
    }

    if (showOtherField) {
        OutlinedTextField(
            value = otherName,
            onValueChange = {
                otherName = it
                if (it.isNotBlank()) onSelect(it.trim())
            },
            label = { Text("System font family name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("custom-font-field"),
        )
    }
}

@Composable
private fun FontOption(label: String, fontFamily: String, modifier: Modifier, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, fontFamily = resolveFontFamily(fontFamily)) },
        onClick = onClick,
        modifier = modifier,
    )
}

/** Copies the picked font into app storage under its own name; null if Android can't load it. */
private fun importFont(context: android.content.Context, uri: Uri, fontsDir: File): File? {
    // Sanitised to a plain file name (no separators, no leading dot) so it can't escape fontsDir.
    val name = DocumentFile.fromSingleUri(context, uri)?.name
        ?.replace(Regex("[^A-Za-z0-9._ -]"), "_")
        ?.trimStart('.')
        ?.ifBlank { null }
        ?: "font-${System.currentTimeMillis()}.ttf"
    fontsDir.mkdirs()
    val target = File(fontsDir, name)
    val input = context.contentResolver.openInputStream(uri)
    if (input == null) {
        AppLog.warn("Selected font file is unreadable", "FontImport")
        return null
    }
    input.use { source -> target.outputStream().use { source.copyTo(it) } }
    val valid = runCatching { Typeface.createFromFile(target) }
        .onFailure { AppLog.warn("Selected file is not a supported font", "FontImport", it) }
        .isSuccess
    if (!valid) target.delete()
    return target.takeIf { valid }
}
