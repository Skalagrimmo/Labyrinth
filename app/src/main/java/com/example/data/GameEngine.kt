package com.example.data

import kotlin.math.abs
import kotlin.random.Random

object GameEngine {

    // Procedural Maze Generator
    // Returns a 2D Array of CellType of size width x height
    fun generateMaze(
        width: Int = 10,
        height: Int = 10,
        layer: Int = 1,
        seed: Long = System.currentTimeMillis() + layer * 123L
    ): Array<Array<CellType>> =
        MazeGenerator.generate(width = width, height = height, layer = layer, seed = seed)

    // Specialized procedural environments. Kept as compatibility facades during rebuild.
    fun generateBuildingFloor(
        floor: Int,
        seed: Long = System.currentTimeMillis()
    ): Array<Array<CellType>> =
        EnvironmentGenerator.generateBuildingFloor(floor = floor, seed = seed)

    fun generateCollectorTunnels(
        level: Int,
        seed: Long = System.currentTimeMillis()
    ): Array<Array<CellType>> =
        EnvironmentGenerator.generateCollectorTunnels(level = level, seed = seed)

    fun generateCitySector(
        districtIndex: Int,
        seed: Long = System.currentTimeMillis()
    ): Array<Array<CellType>> =
        EnvironmentGenerator.generateCitySector(districtIndex = districtIndex, seed = seed)

    private fun generateFallbackMaze(width: Int, height: Int): Array<Array<CellType>> {
        val grid = Array(height) { Array(width) { CellType.WALL } }
        val roomsCount = 4
        
        // Define centers of rooms along the diagonal
        val centers = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until roomsCount) {
            val t = i.toFloat() / (roomsCount - 1)
            val cx = (1 + t * (width - 3)).toInt().coerceIn(1, width - 2)
            val cy = (1 + t * (height - 3)).toInt().coerceIn(1, height - 2)
            centers.add(Pair(cx, cy))
        }
        
        // Carve rooms (9x9 size to require 9 steps to cross)
        for ((cx, cy) in centers) {
            for (dy in -4..4) {
                for (dx in -4..4) {
                    val rx = cx + dx
                    val ry = cy + dy
                    if (rx in 1 until width - 1 && ry in 1 until height - 1) {
                        grid[ry][rx] = CellType.PATH
                    }
                }
            }
        }
        
        // Connect rooms linearly
        for (i in 0 until centers.size - 1) {
            val (x1, y1) = centers[i]
            val (x2, y2) = centers[i + 1]
            var cx = x1
            var cy = y1
            while (cx != x2) {
                if (cx in 1 until width - 1 && cy in 1 until height - 1) {
                    grid[cy][cx] = CellType.PATH
                }
                cx += if (x2 > cx) 1 else -1
            }
            while (cy != y2) {
                if (cx in 1 until width - 1 && cy in 1 until height - 1) {
                    grid[cy][cx] = CellType.PATH
                }
                cy += if (y2 > cy) 1 else -1
            }
        }
        
        // Place critical elements
        grid[1][1] = CellType.SAFE_ZONE
        
        val exitX = centers.last().first
        val exitY = centers.last().second
        grid[exitY][exitX] = CellType.ENCRYPTED_PORTAL
        
        // Place some items in other room centers
        if (centers.size > 2) {
            val (dx1, dy1) = centers[1]
            grid[dy1][dx1] = CellType.DATA_STORE
            
            val (dx2, dy2) = centers[2]
            grid[dy2][dx2] = CellType.VIRUS_NODE
        }
        
        return grid
    }

    // Canvas-style 3D ASCII Perspective Wireframe Drawer
    fun render3DPerspective(
        grid: Array<Array<CellType>>,
        px: Int,
        py: Int,
        dir: Direction,
        activeWeather: CyberWeather = CyberWeather.CLEAR
    ): String =
        AsciiPerspectiveRenderer.render(
            grid = grid,
            px = px,
            py = py,
            dir = dir,
            activeWeather = activeWeather
        )

    fun getStoreCyberware(): List<Cyberware> = GameContentCatalog.getStoreCyberware()

    fun getStartingPrograms(runnerClass: NetrunnerClass): List<Program> =
        GameContentCatalog.getStartingPrograms(runnerClass)

    // Generates a fully loaded enemy depending on the cyberspace layer
    /**
     * Enemy archetype catalog. Each archetype defines a distinct combat profile
     * (tank, glass-cannon, shield-heavy, healer, debuffer...) with stat multipliers,
     * optional starting status effects, and a unique ASCII portrait.
     */
    data class EnemyArchetype(
        val name: String,
        val description: String,
        val asciiArt: String,
        val minTier: Int,          // minimum depth/layer at which this enemy can appear
        val hpMult: Float = 1.0f,
        val shieldMult: Float = 1.0f,
        val dmgMult: Float = 1.0f,
        val armorBonus: Int = 0,
        val bountyMult: Float = 1.0f,
        val statusEffects: List<Pair<StatusEffectType, Int>> = emptyList() // (type, turns)
    )

    /**
     * Mod-registered enemy archetypes dynamically added at runtime.
     * Populated by [ContentRegistry] when a mod document is loaded.
     */
    val registeredEnemyArchetypes = mutableListOf<EnemyArchetype>()

    /** Registers additional enemy archetypes from mods. */
    fun registerEnemyArchetypes(archetypes: List<EnemyArchetype>) {
        registeredEnemyArchetypes.addAll(archetypes)
    }

    /**
     * Spawns a procedurally chosen enemy scaled to the current depth/layer.
     * Higher tiers (and tougher archetypes) unlock as the player descends.
     * Mod-registered archetypes (via [registerEnemyArchetypes]) are merged in.
     */
    fun spawnEnemy(layer: Int): Enemy =
        EnemyFactory.create(
            layer = layer,
            archetypes = GameContentCatalog.enemyArchetypes + registeredEnemyArchetypes
        )

    fun spawnBoss(bossType: BossType, level: Int): Enemy =
        BossFactory.create(bossType = bossType, level = level)

    // Hacking puzzle matrix generator
    fun generateHackingPuzzle(difficulty: Int): HackingPuzzle =
        HackingPuzzleGenerator.generate(
            difficulty = difficulty,
            random = Random(System.currentTimeMillis())
        )
}

class CharCanvas(val rows: Int, val cols: Int) {
    private val buffer = Array(rows) { CharArray(cols) { ' ' } }

    fun set(r: Int, c: Int, ch: Char) {
        if (r in 0 until rows && c in 0 until cols) {
            buffer[r][c] = ch
        }
    }

    fun drawLine(r1: Int, c1: Int, r2: Int, c2: Int, ch: Char) {
        val dr = abs(r2 - r1)
        val dc = abs(c2 - c1)
        val sr = if (r1 < r2) 1 else -1
        val sc = if (c1 < c2) 1 else -1
        var err = dr - dc
        var r = r1
        var c = c1
        while (true) {
            set(r, c, ch)
            if (r == r2 && c == c2) break
            val e2 = 2 * err
            if (e2 > -dc) {
                err -= dc
                r += sr
            }
            if (e2 < dr) {
                err += dr
                c += sc
            }
        }
    }

    fun render(): String {
        return buffer.joinToString("\n") { String(it) }
    }
}

data class HackingPuzzle(
    val grid: Array<Array<String>>,
    val targetSequence: List<String>,
    val bufferLimit: Int,
    val selectedIndices: List<Pair<Int, Int>> = emptyList(),
    val currentBuffer: List<String> = emptyList(),
    var isSolved: Boolean = false,
    var isFailed: Boolean = false,
    var highlightedRow: Int? = 0, // Starts at row 0 highlighted
    var highlightedCol: Int? = null
)
