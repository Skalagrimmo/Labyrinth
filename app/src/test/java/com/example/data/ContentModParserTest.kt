package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the reusable Markdown content parser (ContentModParser).
 *
 * These are pure host-JVM tests — no Android / Robolectric needed — covering the only
 * logic in the mod system that is non-trivial: parsing `## Kind: Name` blocks into
 * typed [ContentModParser.ModEntity] records.
 */
class ContentModParserTest {

    @Test
    fun `parses enemy heading with name`() {
        val entities = ContentModParser.parse(
            """
            # Header
            ## Enemy: HoneyBadger.exe
            tier: 4
            hp: 1.5
            portrait:
            ```
              /\/\
             ( (0) )
            ```
            """.trimIndent()
        )
        assertEquals(1, entities.size)
        val e = entities.first()
        assertEquals("Enemy", e.kind)
        assertEquals("HoneyBadger.exe", e.name)
        assertEquals("4", e.fields["tier"])
        assertEquals("1.5", e.fields["hp"])
        assertEquals("/\\/\\\n ( (0) )", e.portrait.trim())
    }

    @Test
    fun `parses multiple content blocks in one document`() {
        val markdown = """
            # Mod

            ## Enemy: A.exe
            tier: 2

            ## Item: B.pkg
            category: CONSUMABLE

            ## Program: C.bin
            ram: 3
        """.trimIndent()
        val entities = ContentModParser.parse(markdown)
        assertEquals(3, entities.size)
        assertEquals(listOf("Enemy", "Item", "Program"), entities.map { it.kind })
    }

    @Test
    fun `field keys are lowercased, values preserved`() {
        val e = ContentModParser.parse(
            """
            ## Item: Serum
            RARITY: EPIC
            status: BUFFED:4:true
            """.trimIndent()
        ).first()
        assertEquals("EPIC", e.fields["rarity"])
        assertEquals("BUFFED:4:true", e.fields["status"])
    }

    @Test
    fun `fenced ascii portrait is captured and fences are excluded`() {
        val e = ContentModParser.parse(
            """
            ## Enemy: Ghost
            desc: A ghost.
            ```
            line one
            line two
            ```
            """.trimIndent()
        ).first()
        assertTrue(e.portrait.contains("line one"))
        assertTrue(e.portrait.contains("line two"))
        assertEquals("A ghost.", e.fields["desc"])
    }

    @Test
    fun `markdown horizontal rules are not leaked into body description`() {
        val markdown = """
            # Header

            ## Item: Thing
            value: 10

            some real description text

            ---

            ## Item: Other
            value: 20
        """.trimIndent()
        val entities = ContentModParser.parse(markdown)
        assertEquals(2, entities.size)
        assertTrue(entities[0].body.isNotEmpty())
        assertTrue(!entities[0].body.contains("---"))
        assertEquals("Thing", entities[0].name)
    }

    @Test
    fun `kind without a name falls back gracefully`() {
        val e = ContentModParser.parse("## Enemy\n tier: 1\n").first()
        assertEquals("", e.name)
        assertEquals("1", e.fields["tier"])
    }

    @Test
    fun `plain prose becomes body text for descriptions`() {
        val e = ContentModParser.parse(
            """
            ## Program: shell
            ram: 2
            A useful program that **helps** you *today*.
            """.trimIndent()
        ).first()
        assertTrue(e.body.contains("A useful program"))
        assertTrue(!e.body.contains("**"))
    }
}
