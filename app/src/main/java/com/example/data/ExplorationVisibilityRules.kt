package com.example.data

object ExplorationVisibilityRules {
    fun revealAround(
        maze: Array<Array<CellType>>,
        originX: Int,
        originY: Int,
        radius: Int = 3
    ): Set<Pair<Int, Int>> {
        if (maze.isEmpty()) return emptySet()

        val revealed = mutableSetOf(originX to originY)
        val width = maze[0].size

        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val x = originX + dx
                val y = originY + dy
                if (y !in maze.indices || x !in 0 until width) continue
                if (dx * dx + dy * dy <= radius * radius + 1) {
                    revealed.add(x to y)
                }
            }
        }

        return revealed
    }
}
