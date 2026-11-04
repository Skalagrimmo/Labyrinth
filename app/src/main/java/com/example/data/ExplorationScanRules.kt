package com.example.data

data class ExplorationScanResult(
    val scannedCells: Set<Pair<Int, Int>>,
    val foundEnemies: Set<Pair<Int, Int>>,
    val foundLoot: Set<Pair<Int, Int>>
)

object ExplorationScanRules {
    private val lootCellTypes = setOf(
        CellType.DATA_STORE,
        CellType.SECRET_CACHE,
        CellType.ENCRYPTED_PORTAL,
        CellType.ELEVATOR,
        CellType.STAIRS_UP,
        CellType.STAIRS_DOWN
    )

    fun scan(
        maze: Array<Array<CellType>>,
        originX: Int,
        originY: Int,
        radius: Int = 8
    ): ExplorationScanResult {
        if (maze.isEmpty()) {
            return ExplorationScanResult(
                scannedCells = emptySet(),
                foundEnemies = emptySet(),
                foundLoot = emptySet()
            )
        }

        val foundEnemies = mutableSetOf<Pair<Int, Int>>()
        val foundLoot = mutableSetOf<Pair<Int, Int>>()
        val scannedCells = mutableSetOf<Pair<Int, Int>>()
        val rowCount = maze.size
        val colCount = maze[0].size

        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val x = originX + dx
                val y = originY + dy
                if (x !in 0 until colCount || y !in 0 until rowCount) continue
                if (dx * dx + dy * dy > radius * radius) continue

                val position = x to y
                scannedCells.add(position)
                when (maze[y][x]) {
                    CellType.VIRUS_NODE -> foundEnemies.add(position)
                    in lootCellTypes -> foundLoot.add(position)
                    else -> Unit
                }
            }
        }

        return ExplorationScanResult(
            scannedCells = scannedCells,
            foundEnemies = foundEnemies,
            foundLoot = foundLoot
        )
    }
}
