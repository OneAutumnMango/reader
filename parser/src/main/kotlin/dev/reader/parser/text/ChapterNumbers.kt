package dev.reader.parser.text

/** Pulls the chapter number out of a heading like "Chapter 12", "CHAPTER IV", "Part Twenty-One" or "第十二章". */
internal object ChapterNumbers {

    private const val TEN = 10

    private val ARABIC = Regex("\\d{1,5}")
    private val ROMAN_TOKEN = Regex("(?i)\\b[ivxlcdm]{1,8}\\b")
    private val CJK_NUMBER = Regex("[一二三四五六七八九十百千零〇两]+")

    private val UNITS = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val TENS = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90,
    )
    private val ROMAN = mapOf('i' to 1, 'v' to 5, 'x' to 10, 'l' to 50, 'c' to 100, 'd' to 500, 'm' to 1000)
    private val CJK_DIGITS = mapOf(
        '零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
    )
    private val CJK_UNITS = mapOf('十' to 10, '百' to 100, '千' to 1000)

    fun of(heading: String): Int? =
        ARABIC.find(heading)?.value?.toIntOrNull()
            ?: words(heading)
            ?: CJK_NUMBER.find(heading)?.value?.let(::cjk)
            ?: ROMAN_TOKEN.find(heading)?.value?.let(::roman)

    private fun words(heading: String): Int? {
        val tokens = heading.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
        for ((i, token) in tokens.withIndex()) {
            val unit = UNITS.indexOf(token)
            if (unit >= 0) return unit
            val tens = TENS[token] ?: continue
            val next = tokens.getOrNull(i + 1)?.let { UNITS.indexOf(it) }?.takeIf { it in 1 until TEN }
            return tens + (next ?: 0)
        }
        return null
    }

    private fun roman(token: String): Int? {
        val values = token.lowercase().map { ROMAN[it] ?: return null }
        return values.indices.sumOf { i ->
            if (i + 1 < values.size && values[i] < values[i + 1]) -values[i] else values[i]
        }
    }

    private fun cjk(text: String): Int {
        var total = 0
        var digit = 0
        for (ch in text) {
            val unit = CJK_UNITS[ch]
            if (unit != null) {
                total += (if (digit == 0) 1 else digit) * unit
                digit = 0
            } else {
                digit = CJK_DIGITS[ch] ?: 0
            }
        }
        return total + digit
    }
}
