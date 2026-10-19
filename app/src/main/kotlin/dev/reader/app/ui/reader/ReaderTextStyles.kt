package dev.reader.app.ui.reader

import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import dev.reader.core.richtext.Span
import dev.reader.core.theme.Justification
import dev.reader.core.theme.ReaderTheme
import java.io.File

private val HEADING_SCALES = listOf(1.75f, 1.5f, 1.3f, 1.15f)

/** Prefix used to mark a [ReaderTheme.fontFamily] value as a path to a user-imported font file. */
const val CUSTOM_FONT_FILE_PREFIX = "file:"

/** Resolves a theme's stored font family name (generic, system, or a `file:` custom font path). */
fun resolveFontFamily(fontFamily: String): FontFamily = when (fontFamily) {
    "sans-serif" -> FontFamily.SansSerif
    "serif" -> FontFamily.Serif
    "monospace" -> FontFamily.Monospace
    "cursive" -> FontFamily.Cursive
    else -> if (fontFamily.startsWith(CUSTOM_FONT_FILE_PREFIX)) {
        runCatching { FontFamily(Font(File(fontFamily.removePrefix(CUSTOM_FONT_FILE_PREFIX)))) }
            .getOrDefault(FontFamily.Default)
    } else {
        runCatching { FontFamily(Typeface.create(fontFamily, Typeface.NORMAL)) }.getOrDefault(FontFamily.Default)
    }
}

fun ReaderTheme.bodyTextStyle(
    blockAlign: Justification? = null,
    useBookStyling: Boolean = false,
): TextStyle = TextStyle(
    color = Color(textColor),
    fontFamily = resolveFontFamily(fontFamily),
    fontSize = fontSizeSp.sp,
    lineHeight = (fontSizeSp * lineSpacing).sp,
    letterSpacing = letterSpacingSp.sp,
    textAlign = resolveTextAlign(blockAlign, useBookStyling),
)

fun ReaderTheme.headingTextStyle(
    level: Int = 1,
    blockAlign: Justification? = null,
    useBookStyling: Boolean = false,
): TextStyle {
    val scale = HEADING_SCALES[(level - 1).coerceIn(0, HEADING_SCALES.lastIndex)]
    return bodyTextStyle(blockAlign, useBookStyling).copy(
        fontWeight = FontWeight.Bold,
        fontSize = (fontSizeSp * scale).sp,
        lineHeight = (fontSizeSp * scale * lineSpacing).sp,
        textAlign = if (useBookStyling && blockAlign != null) resolveTextAlign(blockAlign, true) else TextAlign.Center,
    )
}

fun ReaderTheme.backgroundColor(): Color = Color(backgroundColor)

private fun ReaderTheme.resolveTextAlign(blockAlign: Justification?, useBookStyling: Boolean): TextAlign {
    val effective = if (useBookStyling) blockAlign ?: justification else justification
    return when (effective) {
        Justification.JUSTIFY -> TextAlign.Justify
        Justification.END -> TextAlign.End
        Justification.START -> TextAlign.Start
    }
}

private val SEARCH_HIGHLIGHT = Color(0x80FFC107)

/** [this] with every non-empty match of [regex] shaded, for search results. */
fun AnnotatedString.highlighted(regex: Regex?): AnnotatedString {
    regex ?: return this
    val matches = regex.findAll(text).filterNot { it.range.isEmpty() }.toList()
    if (matches.isEmpty()) return this
    return buildAnnotatedString {
        append(this@highlighted)
        matches.forEach { addStyle(SpanStyle(background = SEARCH_HIGHLIGHT), it.range.first, it.range.last + 1) }
    }
}

fun List<Span>.toAnnotatedString(
    wordSpacingSp: Float = 0f,
    useBookStyling: Boolean = false,
): AnnotatedString = buildAnnotatedString {
    forEach { span ->
        val color = span.color.takeIf { useBookStyling }?.let { Color(it) } ?: Color.Unspecified
        // Only set what the span actually changes, so a heading's bold/size isn't reset to normal.
        withStyle(
            SpanStyle(
                color = color,
                background = if (span.highlighted) SEARCH_HIGHLIGHT else Color.Unspecified,
                fontWeight = if (span.bold) FontWeight.Bold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                textDecoration = if (span.underline) TextDecoration.Underline else null,
            ),
        ) {
            append(span.text)
        }
    }
    if (wordSpacingSp != 0f) {
        toAnnotatedString().text.forEachIndexed { index, character ->
            if (character == ' ') addStyle(SpanStyle(letterSpacing = wordSpacingSp.sp), index, index + 1)
        }
    }
}
