package com.example.data

/**
 * Stable, Android-free codecs used by persistence.
 *
 * Keep these formats byte-for-byte compatible with the legacy PersistenceManager helpers.
 */
object SaveDataCodec {
    const val PORTABLE_SAVE_PREFIX = "NETCRAWLER_SAVE_v1:"

    fun serializeMaze(maze: Array<Array<CellType>>): String =
        maze.joinToString(";") { row -> row.joinToString(",") { it.name } }

    fun deserializeMaze(value: String): Array<Array<CellType>> {
        if (value.isEmpty()) return emptyArray()
        return value.split(";").map { row ->
            row.split(",").map { cellName ->
                try {
                    CellType.valueOf(cellName)
                } catch (_: Exception) {
                    CellType.WALL
                }
            }.toTypedArray()
        }.toTypedArray()
    }

    fun serializeExploredCells(cells: Set<Pair<Int, Int>>): String =
        cells.joinToString(";") { "${it.first},${it.second}" }

    fun deserializeExploredCells(value: String): Set<Pair<Int, Int>> {
        if (value.isEmpty()) return emptySet()
        return value.split(";").mapNotNull {
            val parts = it.split(",")
            if (parts.size != 2) return@mapNotNull null
            val x = parts[0].toIntOrNull()
            val y = parts[1].toIntOrNull()
            if (x != null && y != null) x to y else null
        }.toSet()
    }

    fun serializeFloors(floors: Map<Int, Array<Array<CellType>>>): String =
        floors.map { (floor, maze) -> "$floor:${serializeMaze(maze)}" }.joinToString("|")

    fun deserializeFloors(value: String): Map<Int, Array<Array<CellType>>> {
        if (value.isEmpty()) return emptyMap()
        val result = mutableMapOf<Int, Array<Array<CellType>>>()
        value.split("|").forEach { entry ->
            val parts = entry.split(":", limit = 2)
            val floor = parts.getOrNull(0)?.toIntOrNull()
            if (floor != null && parts.size == 2) result[floor] = deserializeMaze(parts[1])
        }
        return result
    }

    fun serializeExploredMap(explored: Map<Int, Set<Pair<Int, Int>>>): String =
        explored.map { (floor, cells) -> "$floor:${serializeExploredCells(cells)}" }.joinToString("|")

    fun deserializeExploredMap(value: String): Map<Int, Set<Pair<Int, Int>>> {
        if (value.isEmpty()) return emptyMap()
        val result = mutableMapOf<Int, Set<Pair<Int, Int>>>()
        value.split("|").forEach { entry ->
            val parts = entry.split(":", limit = 2)
            val floor = parts.getOrNull(0)?.toIntOrNull()
            if (floor != null && parts.size == 2) result[floor] = deserializeExploredCells(parts[1])
        }
        return result
    }
}
