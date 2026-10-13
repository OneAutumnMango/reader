package dev.reader.core.util

import kotlin.test.Test
import kotlin.test.assertTrue

class NaturalSortComparatorTest {

    @Test
    fun `sorts embedded chapter numbers numerically`() {
        val input = listOf("Chapter 10", "Chapter 2", "Chapter 1")
        val sorted = input.sortedWith(NaturalSortComparator)
        assertTrue(sorted == listOf("Chapter 1", "Chapter 2", "Chapter 10"))
    }

    @Test
    fun `falls back to lexical order when no digits differ`() {
        val input = listOf("Prologue", "Afterword")
        val sorted = input.sortedWith(NaturalSortComparator)
        assertTrue(sorted == listOf("Afterword", "Prologue"))
    }

    @Test
    fun `shorter prefix sorts first when otherwise equal`() {
        val input = listOf("Chapter 1 Part", "Chapter 1")
        val sorted = input.sortedWith(NaturalSortComparator)
        assertTrue(sorted == listOf("Chapter 1", "Chapter 1 Part"))
    }
}
