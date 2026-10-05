package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveDataCodecTest {
    @Test
    fun `portable save prefix remains legacy compatible`() {
        assertEquals("NETCRAWLER_SAVE_v1:", SaveDataCodec.PORTABLE_SAVE_PREFIX)
    }

    @Test
    fun `maze codec round trips cell types`() {
        val maze = arrayOf(
            arrayOf(CellType.SAFE_ZONE, CellType.PATH, CellType.WALL),
            arrayOf(CellType.DATA_STORE, CellType.ENCRYPTED_PORTAL, CellType.VIRUS_NODE)
        )
        val encoded = SaveDataCodec.serializeMaze(maze)
        val decoded = SaveDataCodec.deserializeMaze(encoded)
        assertEquals(maze.map { it.toList() }, decoded.map { it.toList() })
    }

    @Test
    fun `unknown legacy maze cells degrade to wall`() {
        val decoded = SaveDataCodec.deserializeMaze("SAFE_ZONE,UNKNOWN_CELL;PATH,WALL")
        assertEquals(CellType.SAFE_ZONE, decoded[0][0])
        assertEquals(CellType.WALL, decoded[0][1])
    }

    @Test
    fun `explored cell codec round trips coordinates`() {
        val cells = linkedSetOf(1 to 2, 5 to 8, -1 to 3)
        assertEquals(cells, SaveDataCodec.deserializeExploredCells(SaveDataCodec.serializeExploredCells(cells)))
    }

    @Test
    fun `floor and explored map codecs preserve keys and payloads`() {
        val floors = linkedMapOf(
            1 to arrayOf(arrayOf(CellType.SAFE_ZONE, CellType.PATH)),
            3 to arrayOf(arrayOf(CellType.DATA_STORE, CellType.ENCRYPTED_PORTAL))
        )
        val explored = linkedMapOf(1 to linkedSetOf(1 to 1, 2 to 1), 3 to linkedSetOf(4 to 7))

        val decodedFloors = SaveDataCodec.deserializeFloors(SaveDataCodec.serializeFloors(floors))
        val decodedExplored = SaveDataCodec.deserializeExploredMap(SaveDataCodec.serializeExploredMap(explored))

        assertEquals(floors.keys, decodedFloors.keys)
        floors.forEach { (key, maze) -> assertEquals(maze.map { it.toList() }, decodedFloors.getValue(key).map { it.toList() }) }
        assertEquals(explored, decodedExplored)
        assertTrue(SaveDataCodec.deserializeFloors("").isEmpty())
    }
}
