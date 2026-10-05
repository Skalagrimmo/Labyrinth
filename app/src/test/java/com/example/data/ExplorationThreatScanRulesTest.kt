package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExplorationThreatScanRulesTest {

    @Test
    fun `finds first hostile using legacy row-major-radius order`() {
        val maze = openMaze(7, 7).apply {
            this[1][3] = CellType.VIRUS_NODE
            this[3][1] = CellType.VIRUS_NODE
        }

        assertEquals(
            3 to 1,
            ExplorationThreatScanRules.findFirstHostile(
                maze = maze,
                originX = 2,
                originY = 2,
                radius = 2
            )
        )
    }

    @Test
    fun `ignores hostile cells outside radius and clips bounds`() {
        val maze = openMaze(3, 3).apply {
            this[0][0] = CellType.VIRUS_NODE
            this[2][2] = CellType.VIRUS_NODE
        }

        assertNull(
            ExplorationThreatScanRules.findFirstHostile(
                maze = maze,
                originX = 1,
                originY = 1,
                radius = 0
            )
        )
        assertEquals(
            0 to 0,
            ExplorationThreatScanRules.findFirstHostile(
                maze = maze,
                originX = 0,
                originY = 0,
                radius = 2
            )
        )
    }

    @Test
    fun `empty maze returns no hostile`() {
        assertNull(
            ExplorationThreatScanRules.findFirstHostile(
                maze = emptyArray(),
                originX = 0,
                originY = 0
            )
        )
    }

    private fun openMaze(width: Int, height: Int): Array<Array<CellType>> =
        Array(height) { Array(width) { CellType.PATH } }
}
