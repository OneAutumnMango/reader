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

private const val HEADING_SCALE = 1.2f

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

fun ReaderTheme.headingTextStyle(blockAlign: Justification? = null, useBookStyling: Boolean = false): TextStyle {
    val scaledSize = (fontSizeSp * HEADING_SCALE).sp
    return bodyTextStyle(blockAlign, useBookStyling).copy(fontWeight = FontWeight.Bold, fontSize = scaledSize)
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

fun List<Span>.toAnnotatedString(
    wordSpacingSp: Float = 0f,
    useBookStyling: Boolean = false,
): AnnotatedString = buildAnnotatedString {
    forEachIndexed { index, span ->
        val color = span.color.takeIf { useBookStyling }?.let { Color(it) } ?: Color.Unspecified
        withStyle(
            SpanStyle(
                color = color,
                fontWeight = if (span.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (span.italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = if (span.underline) TextDecoration.Underline else TextDecoration.None,
            ),
        ) {
            append(span.text)
        }
        if (wordSpacingSp > 0f && index != lastIndex) append(" ")
    }
}
