package dev.reader.app.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import dev.reader.core.richtext.Span
import dev.reader.core.theme.Justification
import dev.reader.core.theme.ReaderTheme

private const val HEADING_SCALE = 1.2f

fun ReaderTheme.bodyTextStyle(): TextStyle = TextStyle(
    color = Color(textColor),
    fontSize = fontSizeSp.sp,
    lineHeight = (fontSizeSp * lineSpacing).sp,
    letterSpacing = letterSpacingSp.sp,
    textAlign = if (justification == Justification.JUSTIFY) TextAlign.Justify else TextAlign.Start,
)

fun ReaderTheme.headingTextStyle(): TextStyle =
    bodyTextStyle().copy(fontWeight = FontWeight.Bold, fontSize = (fontSizeSp * HEADING_SCALE).sp)

fun ReaderTheme.backgroundColor(): Color = Color(backgroundColor)

fun List<Span>.toAnnotatedString(wordSpacingSp: Float = 0f): AnnotatedString = buildAnnotatedString {
    forEachIndexed { index, span ->
        withStyle(
            SpanStyle(
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
