package dev.reader.core.util

/**
 * Compares strings the way a reader expects chapter files to sort: embedded
 * digit runs compare by numeric value, so "Chapter 2" sorts before "Chapter 10".
 */
object NaturalSortComparator : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val (numA, nextI) = readNumber(a, i)
                val (numB, nextJ) = readNumber(b, j)
                if (numA != numB) return numA.compareTo(numB)
                i = nextI
                j = nextJ
            } else {
                if (ca != cb) return ca.compareTo(cb)
                i++
                j++
            }
        }
        return (a.length - i).compareTo(b.length - j)
    }

    private fun readNumber(s: String, start: Int): Pair<Long, Int> {
        var end = start
        while (end < s.length && s[end].isDigit()) end++
        return s.substring(start, end).toLong() to end
    }
}
