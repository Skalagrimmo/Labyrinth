package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NameGeneratorTest {

    @Test
    fun `randomName never returns blank`() {
        repeat(50) {
            assertTrue(NameGenerator.randomName().isNotBlank())
        }
    }

    @Test
    fun `randomName contains only alphanumerics, underscores and spaces`() {
        repeat(50) {
            val name = NameGenerator.randomName()
            assertTrue(name.all { it.isLetterOrDigit() || it == '_' || it == ' ' })
        }
    }

    @Test
    fun `suggestions returns distinct names up to count`() {
        val suggestions = NameGenerator.suggestions(5, Random(7))
        assertEquals(5, suggestions.size)
        assertEquals(5, suggestions.distinct().size)
        assertTrue(suggestions.all { it.isNotBlank() })
    }

    @Test
    fun `suggestions differ across runs`() {
        val a = NameGenerator.suggestions(3, Random(1))
        val b = NameGenerator.suggestions(3, Random(2))
        assertNotEquals(a, b)
    }

    @Test
    fun `names have no formatting artifacts`() {
        val name = NameGenerator.randomName(Random(42))
        assertFalse(name.startsWith(" ") || name.endsWith(" "))
        assertFalse(name.contains("  "))
    }
}
