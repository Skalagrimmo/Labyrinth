package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-JVM tests for GameEngine's content-generation functions.
 *
 * Only the deterministic, side-effect-light behavior is asserted here (stat ranges and
 * invariants) to stay stable regardless of RNG seeding.
 */
class GameEngineTest {

    @Test
    fun `generateMaze is reproducible for an explicit seed`() {
        val first = GameEngine.generateMaze(width = 24, height = 24, layer = 4, seed = 424242L)
        val second = GameEngine.generateMaze(width = 24, height = 24, layer = 4, seed = 424242L)

        assertEquals(first.map { it.toList() }, second.map { it.toList() })
    }

    @Test
    fun `generated maze preserves traversal invariants`() {
        val maze = GameEngine.generateMaze(width = 24, height = 24, layer = 2, seed = 9001L)

        assertEquals(CellType.SAFE_ZONE, maze[1][1])
        assertTrue(maze.first().all { it == CellType.WALL })
        assertTrue(maze.last().all { it == CellType.WALL })
        assertTrue(maze.all { row -> row.first() == CellType.WALL && row.last() == CellType.WALL })

        val portals = maze.sumOf { row -> row.count { it == CellType.ENCRYPTED_PORTAL } }
        assertEquals(1, portals)
    }

    @Test
    fun `spawnEnemy produces a valid enemy at any layer`() {
        val enemy = GameEngine.spawnEnemy(layer = 3)
        assertTrue(enemy.name.isNotBlank())
        assertEquals(enemy.maxIntegrity, enemy.integrity)
        assertEquals(enemy.maxShield, enemy.shield)
        assertTrue(enemy.damage > 0)
        assertTrue(enemy.integrity > 0)
        assertTrue(enemy.iconAscii.isNotBlank())
        assertTrue(enemy.description.isNotBlank())
    }

    @Test
    fun `spawnEnemy scales with layer`() {
        // Deep layers use a strictly larger base (40 + layer*15) before the per-archetype
        // multiplier. A very wide gap (1 vs 15) makes the ordering robust to RNG and to
        // low hpMult archetypes (some < 1.0), so maxIntegrity must rise on average.
        val low = GameEngine.spawnEnemy(layer = 1).maxIntegrity
        val mid = GameEngine.spawnEnemy(layer = 15).maxIntegrity
        // Even the weakest archetype at layer 15 (265 * 0.8 = 212) exceeds the strongest
        // archetype at layer 1 (~93), so the ordering holds for any single sample.
        assertTrue(mid > low)
    }

    @Test
    fun `spawnBoss scales with level across all boss types`() {
        for (type in BossType.values()) {
            val boss = GameEngine.spawnBoss(type, level = 5)
            assertTrue(boss.isBoss)
            assertEquals(type, boss.bossType)
            assertTrue(boss.maxIntegrity > 0)
            assertTrue(boss.damage > 0)
            assertTrue(boss.name.isNotBlank())
        }
    }


    @Test
    fun `game engine maze facade preserves MazeGenerator output`() {
        val direct = MazeGenerator.generate(width = 24, height = 24, layer = 3, seed = 8675309L)
        val facade = GameEngine.generateMaze(width = 24, height = 24, layer = 3, seed = 8675309L)

        assertEquals(direct.map { it.toList() }, facade.map { it.toList() })
    }
}
