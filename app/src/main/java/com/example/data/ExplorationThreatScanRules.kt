package com.example.data

object ExplorationThreatScanRules {
    fun findFirstHostile(
        maze: Array<Array<CellType>>,
        originX: Int,
        originY: Int,
        radius: Int = 2
    ): Pair<Int, Int>? {
        if (maze.isEmpty()) return null
        val width = maze[0].size

        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val x = originX + dx
                val y = originY + dy
                if (y !in maze.indices || x !in 0 until width) continue
                if (maze[y][x] == CellType.VIRUS_NODE) {
                    return x to y
                }
            }
        }

        return null
    }
}
