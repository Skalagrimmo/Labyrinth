package com.example.data

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
