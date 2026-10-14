package dev.reader.parser.text

import kotlin.math.max

/**
 * Finds chapter headings in plain text/markdown by trying many independent strategies — markdown
 * headings, "Chapter N"-style keywords (several languages), CJK `第N章`, bracketed titles, a
 * repeated prefix inferred from the book itself, numbered/bold/all-caps lines and finally short
 * standalone lines with no closing punctuation — then scoring each by how chapter-like its
 * matches are: base confidence, whether the numbers run in sequence, and whether the resulting
 * chapters have sensible lengths.
 */
internal object ChapterDetector {

    private data class Strategy(val name: String, val pattern: String, val weight: Double, val minMatches: Int)

    private class Scored(val strategy: Strategy, val starts: List<Int>, val score: Double)

    private const val MAX_SUGGESTIONS = 5
    private const val MIN_SCORE = 1.5
    private const val SEQUENCE_WEIGHT = 2.0

    // Chapter-length heuristics only kick in for real books; tiny samples would always look "too short".
    private const val MIN_WORDS_FOR_LENGTH_CHECKS = 1000
    private const val HEALTHY_MEDIAN_WORDS = 300
    private const val TINY_CHAPTER_WORDS = 30
    private const val TOO_SHORT_MEDIAN_WORDS = 60
    private const val MAX_TINY_FRACTION = 0.3
    private const val TINY_CHAPTERS_PENALTY = 1.5
    private const val MIN_WORDS_PER_CHAPTER = 80

    private const val PREFIX_MAX_LENGTH = 30
    private const val PREFIX_MAX_WORDS = 4
    private const val MAX_INFERRED_PREFIXES = 3
    private const val INFERRED_MIN_MATCHES = 3

    private const val NUMBER_WORDS = "one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|" +
        "fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety"
    private const val CHAPTER_NUMBER = "#?\\s*(?:\\d+|[ivxlcdm]+\\b|(?:$NUMBER_WORDS)\\b|[一二三四五六七八九十百]+)"

    // "Chapter 12", "CHAPTER IV: The Return", "Part One", "Ch. 3", "Глава 5", "Capítulo 2", "Prologue".
    private const val KEYWORD_PATTERN = "(?i)^(?:#{1,6}\\s*)?(?:\\*\\*|__)?(?:" +
        "(?:chapter|chap\\.?|ch\\.|part|book|volume|vol\\.|act|episode|ep\\.|section|arc|chapitre|cap[ií]tulo|" +
        "capitolo|kapitel|hoofdstuk|rozdzia[łl]|глава|часть|том)\\s*$CHAPTER_NUMBER" +
        "|(?:prologue|epilogue|interlude|intermission|afterword|foreword|preface|introduction|side story|" +
        "bonus chapter|пролог|эпилог)\\b)"

    // 第12章 / 第十二章 / 第3回 — Chinese & Japanese web novels.
    private const val CJK_PATTERN = "^(?:#{1,6}\\s*)?第\\s*[0-9０-９一二三四五六七八九十百千零〇两]+\\s*[章节節回话話卷部篇幕]"

    // [Chapter 3], 【第3話】, (Ep. 4)
    private const val BRACKET_PATTERN =
        "(?i)^[\\[【〔(（]\\s*(?:chapter|chap\\.?|ch\\.?|episode|ep\\.?|part|第)[^\\]】〕)）]{0,60}[\\]】〕)）]"

    // "12", "12.", "IV", "IV. The Return", "3 - Into the Dark"
    private const val NUMBERED_PATTERN = "^(?:\\d{1,4}|[IVXLCDM]{1,7})(?:[.:)]|\\s+[-–—:])?(?:\\s+\\S.{0,80})?$"

    // **The Return** / __The Return__
    private const val BOLD_PATTERN = "^(?:\\*\\*|__)[^*_]{1,80}(?:\\*\\*|__)$"

    // "THE RETURN" — at least three capitals, no lowercase letters.
    private const val ALL_CAPS_PATTERN = "^(?=(?:[^a-z]*[A-Z]){3})[^a-z]+$"

    // A short line opening with a capital or digit, under ten words, not ending in sentence punctuation.
    private const val STANDALONE_TITLE_PATTERN =
        "^(?=.*\\p{L})(?!(?:\\S+\\s+){10})(?!.*[.,;:!?\"'”’)\\]…\\-]$)[\\p{Lu}\\p{N}].{0,59}$"

    private val LEADING_NUMBER = Regex("^(.*?)(?:#\\s*)?(\\d+|\\b[IVXLCDM]+\\b)")
    private val SEPARATOR = Regex("^[\\s*\\-_=~#•·.]+$")

    fun suggest(lines: List<String>, markdown: Boolean): List<ChapterSuggestion> {
        val wordPrefix = wordPrefixSums(lines)
        return strategies(lines, markdown)
            .mapNotNull { strategy -> score(strategy, lines, wordPrefix) }
            .sortedByDescending { it.score }
            .distinctBy { it.starts }
            .take(MAX_SUGGESTIONS)
            .map { ChapterSuggestion(it.strategy.name, it.strategy.pattern, it.starts.size) }
    }

    private fun strategies(lines: List<String>, markdown: Boolean): List<Strategy> = buildList {
        if (markdown) {
            add(Strategy("# headings", "^#\\s+\\S", weight = 3.0, minMatches = 2))
            add(Strategy("## headings", "^##\\s+\\S", weight = 2.8, minMatches = 2))
            add(Strategy("### headings", "^###\\s+\\S", weight = 2.0, minMatches = 3))
        }
        add(Strategy("Chapter / Part / Prologue headings", KEYWORD_PATTERN, weight = 3.0, minMatches = 2))
        add(Strategy("第N章 headings", CJK_PATTERN, weight = 3.0, minMatches = 2))
        add(Strategy("[Bracketed] headings", BRACKET_PATTERN, weight = 2.8, minMatches = 2))
        addAll(inferredPrefixes(lines))
        add(Strategy("Numbered lines", NUMBERED_PATTERN, weight = 2.0, minMatches = 3))
        add(Strategy("Bold lines", BOLD_PATTERN, weight = 1.8, minMatches = 3))
        add(
            Strategy(
                "ALL-CAPS lines",
                ChapterSplitter.STANDALONE_PREFIX + ALL_CAPS_PATTERN,
                weight = 1.4,
                minMatches = 3,
            ),
        )
        add(
            Strategy(
                "Short standalone lines",
                ChapterSplitter.STANDALONE_PREFIX + STANDALONE_TITLE_PATTERN,
                weight = 1.0,
                minMatches = 3,
            ),
        )
    }

    /**
     * Learns this book's own heading prefix ("Episode 4", "Log Entry #12", "Day 3") from short lines
     * that follow a blank line and contain a number, keeping any prefix that recurs.
     */
    private fun inferredPrefixes(lines: List<String>): List<Strategy> = lines.indices
        .asSequence()
        .filter { i -> i == 0 || lines[i - 1].isBlank() }
        .map { lines[it].trim() }
        .filter { it.isNotEmpty() && it.length <= MAX_HEADING_LENGTH && !SEPARATOR.matches(it) }
        .mapNotNull { line -> LEADING_NUMBER.find(line)?.groupValues?.get(1)?.trim()?.trimEnd(':', '-', '–', '—') }
        .map { it.trim() }
        .filter { prefix ->
            prefix.length in 1..PREFIX_MAX_LENGTH &&
                prefix.any(Char::isLetter) &&
                prefix.split(Regex("\\s+")).size <= PREFIX_MAX_WORDS
        }
        .groupingBy { it.lowercase() }
        .eachCount()
        .filterValues { it >= INFERRED_MIN_MATCHES }
        .entries
        .sortedByDescending { it.value }
        .take(MAX_INFERRED_PREFIXES)
        .map { (prefix, _) ->
            Strategy(
                name = "Lines starting “$prefix”",
                pattern = "(?i)^${Regex.escape(prefix)}\\s*[#:.\\-–—]?\\s*(?:\\d|[ivxlcdm]+\\b)",
                weight = 2.6,
                minMatches = INFERRED_MIN_MATCHES,
            )
        }
        .toList()

    private fun score(strategy: Strategy, lines: List<String>, wordPrefix: IntArray): Scored? {
        val starts = runCatching { ChapterSplitter.headingLines(lines, HeadingRule(strategy.pattern)) }
            .getOrNull()
            ?.filterNot { SEPARATOR.matches(lines[it].trim()) }
            ?: return null
        if (starts.size < strategy.minMatches) return null
        val total = strategy.weight + SEQUENCE_WEIGHT * sequenceScore(starts.map { lines[it] }) +
            lengthScore(starts, wordPrefix)
        return Scored(strategy, starts, total).takeIf { total >= MIN_SCORE }
    }

    /** Fraction of consecutive numbered headings that count up by one (resets to 1 are forgiven). */
    private fun sequenceScore(headings: List<String>): Double {
        val numbers = headings.mapNotNull { ChapterNumbers.of(it) }
        if (numbers.size < 2) return 0.0
        val steps = numbers.zipWithNext()
        return steps.count { (a, b) -> b == a + 1 || (b == 1 && a > 1) }.toDouble() / steps.size
    }

    /** Rewards chapters of a believable length and penalises splits that shred the text into scraps. */
    private fun lengthScore(starts: List<Int>, wordPrefix: IntArray): Double {
        val totalWords = wordPrefix.last()
        if (totalWords < MIN_WORDS_FOR_LENGTH_CHECKS) return 0.0
        val bounds = starts + (wordPrefix.size - 1)
        val sizes = bounds.zipWithNext { a, b -> wordPrefix[b] - wordPrefix[a] }.sorted()
        val median = sizes[sizes.size / 2]
        var score = 0.0
        if (median >= HEALTHY_MEDIAN_WORDS) score += 1.0
        if (median < TOO_SHORT_MEDIAN_WORDS) score -= 2.0
        if (sizes.count { it < TINY_CHAPTER_WORDS }.toDouble() / sizes.size > MAX_TINY_FRACTION) {
            score -= TINY_CHAPTERS_PENALTY
        }
        if (starts.size > max(1, totalWords / MIN_WORDS_PER_CHAPTER)) score -= 2.0
        return score
    }

    /** wordPrefix[i] = words in lines[0 until i], so any chunk's word count is a subtraction. */
    private fun wordPrefixSums(lines: List<String>): IntArray {
        val sums = IntArray(lines.size + 1)
        lines.forEachIndexed { i, line ->
            sums[i + 1] = sums[i] + line.split(' ', '\t').count { it.isNotBlank() }
        }
        return sums
    }

    private const val MAX_HEADING_LENGTH = 100
}
