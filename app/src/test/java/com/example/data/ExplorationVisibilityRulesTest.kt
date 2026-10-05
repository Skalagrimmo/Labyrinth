package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorationVisibilityRulesTest {

    @Test
    fun `revealAround includes origin and circular radius with legacy margin`() {
        val maze = openMaze(9, 9)

        val revealed = ExplorationVisibilityRules.revealAround(
            maze = maze,
            originX = 4,
            originY = 4,
            radius = 3
        )

        assertTrue((4 to 4) in revealed)
        assertTrue((7 to 4) in revealed)
        assertTrue((7 to 5) in revealed)
        assertTrue((8 to 4) !in revealed)
    }

    @Test
    fun `revealAround clips at map boundaries`() {
        val revealed = ExplorationVisibilityRules.revealAround(
            maze = openMaze(5, 5),
            originX = 0,
            originY = 0,
            radius = 3
        )

        assertTrue(revealed.all { (x, y) ->
            x in 0 until 5 && y in 0 until 5
        })
        assertEquals(1, revealed.count { it == 0 to 0 })
    }

    @Test
    fun `empty maze reveals nothing`() {
        assertEquals(
            emptySet<Pair<Int, Int>>(),
            ExplorationVisibilityRules.revealAround(
                maze = emptyArray(),
                originX = 0,
                originY = 0
            )
        )
    }

    private fun openMaze(width: Int, height: Int): Array<Array<CellType>> =
        Array(height) { Array(width) { CellType.PATH } }
}
