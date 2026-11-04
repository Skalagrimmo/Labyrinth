package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorationScanRulesTest {

    @Test
    fun `scan classifies hostile and loot cells inside radius`() {
        val maze = openMaze(9, 9).apply {
            this[4][5] = CellType.VIRUS_NODE
            this[3][3] = CellType.SECRET_CACHE
            this[4][2] = CellType.STAIRS_UP
            this[7][7] = CellType.VIRUS_NODE
        }

        val result = ExplorationScanRules.scan(
            maze = maze,
            originX = 4,
            originY = 4,
            radius = 3
        )

        assertTrue((5 to 4) in result.scannedCells)
        assertTrue((5 to 4) in result.foundEnemies)
        assertEquals(
            setOf(3 to 3, 2 to 4),
            result.foundLoot
        )
        assertTrue((7 to 7) !in result.scannedCells)
        assertTrue((7 to 7) !in result.foundEnemies)
    }

    @Test
    fun `scan uses circular radius rather than square bounds`() {
        val maze = openMaze(9, 9).apply {
            this[4][7] = CellType.VIRUS_NODE
            this[6][6] = CellType.DATA_STORE
            this[4][6] = CellType.SECRET_CACHE
        }

        val result = ExplorationScanRules.scan(
            maze = maze,
            originX = 4,
            originY = 4,
            radius = 2
        )

        assertTrue((4 to 6) in result.scannedCells)
        assertTrue((4 to 7) !in result.scannedCells)
        assertTrue((6 to 6) !in result.scannedCells)
        assertTrue((6 to 6) !in result.foundLoot)
        assertTrue((4 to 7) !in result.foundEnemies)
    }

    @Test
    fun `scan clips to maze bounds`() {
        val maze = openMaze(5, 5).apply {
            this[0][0] = CellType.VIRUS_NODE
            this[4][4] = CellType.DATA_STORE
        }

        val result = ExplorationScanRules.scan(
            maze = maze,
            originX = 0,
            originY = 0,
            radius = 8
        )

        assertEquals(setOf(0 to 0), result.foundEnemies)
        assertEquals(
            setOf(4 to 4),
            result.foundLoot
        )
        assertTrue(result.scannedCells.all { (x, y) ->
            x in 0 until 5 && y in 0 until 5
        })
    }

    @Test
    fun `empty maze produces empty scan result`() {
        val result = ExplorationScanRules.scan(
            maze = emptyArray(),
            originX = 0,
            originY = 0
        )

        assertEquals(emptySet<Pair<Int, Int>>(), result.scannedCells)
        assertEquals(emptySet<Pair<Int, Int>>(), result.foundEnemies)
        assertEquals(emptySet<Pair<Int, Int>>(), result.foundLoot)
    }

    private fun openMaze(width: Int, height: Int): Array<Array<CellType>> =
        Array(height) { Array(width) { CellType.PATH } }
}
