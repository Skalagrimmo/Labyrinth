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

    fun spawnEnemy(layer: Int): Enemy =
        EnemyFactory.create(
            layer = layer,
            archetypes = GameContentCatalog.enemyArchetypes + EnemyArchetypeRegistry.registeredArchetypes()
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
