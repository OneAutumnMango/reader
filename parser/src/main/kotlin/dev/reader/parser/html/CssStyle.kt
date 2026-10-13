package dev.reader.parser.html

import dev.reader.core.theme.Justification

/** A small, named subset of CSS declarations this reader understands. */
internal data class CssStyle(
    val color: Long? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underline: Boolean? = null,
    val textAlign: Justification? = null,
)

private val NAMED_COLORS = mapOf(
    "black" to 0xFF000000L,
    "white" to 0xFFFFFFFFL,
    "red" to 0xFFFF0000L,
    "green" to 0xFF008000L,
    "blue" to 0xFF0000FFL,
    "gray" to 0xFF808080L,
    "grey" to 0xFF808080L,
)

private const val BOLD_WEIGHT_THRESHOLD = 700
private val DECLARATION_PATTERN by lazy { Regex("([a-zA-Z-]+)\\s*:\\s*([^;]+)") }
private val CLASS_RULE_PATTERN by lazy { Regex("\\.([a-zA-Z0-9_-]+)\\s*\\{([^}]*)\\}") }

internal fun parseCssDeclarations(declarations: String): CssStyle = runCatching {
    var color: Long? = null
    var bold: Boolean? = null
    var italic: Boolean? = null
    var underline: Boolean? = null
    var textAlign: Justification? = null

    DECLARATION_PATTERN.findAll(declarations).forEach { match ->
        val value = match.groupValues[2].trim().lowercase()
        when (match.groupValues[1].trim().lowercase()) {
            "color" -> color = parseCssColor(value)
            "font-weight" -> bold = value == "bold" || (value.toIntOrNull() ?: 0) >= BOLD_WEIGHT_THRESHOLD
            "font-style" -> italic = value == "italic" || value == "oblique"
            "text-decoration", "text-decoration-line" -> underline = when {
                "underline" in value -> true
                value == "none" -> false
                else -> underline
            }
            "text-align" -> textAlign = if (value == "justify") Justification.JUSTIFY else Justification.START
        }
    }
    CssStyle(color, bold, italic, underline, textAlign)
}.getOrDefault(CssStyle())

private const val OPAQUE_ALPHA = 0xFF000000L
private const val SHORTHAND_HEX_LENGTH = 3
private const val FULL_HEX_LENGTH = 6
private const val HEX_RADIX = 16

private fun parseCssColor(value: String): Long? {
    if (value.startsWith("#")) {
        val hex = value.removePrefix("#")
        val expanded = if (hex.length == SHORTHAND_HEX_LENGTH) hex.map { "$it$it" }.joinToString("") else hex
        if (expanded.length != FULL_HEX_LENGTH) return null
        return expanded.toLongOrNull(HEX_RADIX)?.let { OPAQUE_ALPHA or it }
    }
    return NAMED_COLORS[value]
}

/** Declared properties on [this] win; anything unset falls back to [base]. */
internal fun CssStyle.mergeOver(base: CssStyle): CssStyle = CssStyle(
    color = color ?: base.color,
    bold = bold ?: base.bold,
    italic = italic ?: base.italic,
    underline = underline ?: base.underline,
    textAlign = textAlign ?: base.textAlign,
)

/** `.class { ... }` rules only — tag/id selectors, nesting and media queries are out of scope. */
internal fun parseStylesheetClassRules(document: org.jsoup.nodes.Document): Map<String, CssStyle> = runCatching {
    val rules = mutableMapOf<String, CssStyle>()
    document.select("style").forEach { styleTag ->
        CLASS_RULE_PATTERN.findAll(styleTag.data()).forEach { match ->
            rules[match.groupValues[1]] = parseCssDeclarations(match.groupValues[2])
        }
    }
    rules
}.getOrDefault(emptyMap())

internal fun styleOf(element: org.jsoup.nodes.Element, classRules: Map<String, CssStyle>): CssStyle = runCatching {
    val fromClasses = element.classNames()
        .mapNotNull { classRules[it] }
        .fold(CssStyle()) { acc, style -> style.mergeOver(acc) }
    val inline = parseCssDeclarations(element.attr("style"))
    inline.mergeOver(fromClasses)
}.getOrDefault(CssStyle())
