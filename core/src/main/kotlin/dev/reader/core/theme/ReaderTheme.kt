package dev.reader.core.theme

import kotlinx.serialization.Serializable

@Serializable
enum class Justification {
    START,
    JUSTIFY,
    END,
}

/**
 * A saveable, loadable set of reader typography and spacing options. Only the colors differ
 * between day and night reading — font, sizes, spacing and justification are the same either way.
 */
@Serializable
data class ReaderTheme(
    val name: String,
    val fontFamily: String = "serif",
    val fontSizeSp: Float = 18f,
    val textColor: Long = 0xFF000000,
    val backgroundColor: Long = 0xFFFFFFFF,
    /** Used instead of [textColor]/[backgroundColor] when night mode is toggled on. Defaults to AMOLED black. */
    val nightTextColor: Long = 0xFFFFFFFF,
    val nightBackgroundColor: Long = 0xFF000000,
    val lineSpacing: Float = 1.4f,
    val wordSpacingSp: Float = 0f,
    val letterSpacingSp: Float = 0f,
    val paragraphSpacingSp: Float = 12f,
    val justification: Justification = Justification.START,
) {
    companion object {
        val Default = ReaderTheme(name = "Default")
        val Sepia = ReaderTheme(
            name = "Sepia",
            textColor = 0xFF5B4636,
            backgroundColor = 0xFFF4ECD8,
        )

        val builtIns = listOf(Default, Sepia)
    }
}
