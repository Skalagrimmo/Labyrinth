package com.example.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Security intensity levels assigned to matrix sectors.
 */
enum class MatrixSecurityIntensity(val title: String, val threatMultiplier: Float, val traceSpeed: Float) {
    DEFCON_5_LOW("DEFCON 5: LOW SECURITY", 0.8f, 1.0f),
    DEFCON_4_MODERATE("DEFCON 4: ELEVATED FIREWALLS", 1.0f, 1.25f),
    DEFCON_3_HIGH("DEFCON 3: HEAVY MILITARY ICE", 1.3f, 1.5f),
    DEFCON_2_SEVERE("DEFCON 2: BLACK-ICE SENTINELS", 1.6f, 1.85f),
    DEFCON_1_MAXIMUM("DEFCON 1: ARASAKA CENTRAL CORE", 2.0f, 2.5f)
}

/**
 * Node types placed procedurally on the matrix grid map.
 */
enum class MatrixNodeType(val label: String, val cellType: CellType) {
    ENTRY_GATE("Neural Gateway", CellType.SAFE_ZONE),
    DATA_NODE("Data Terminal", CellType.DATA_STORE),
    SECURITY_ICE("Black-ICE Barrier", CellType.VIRUS_NODE),
    LOOT_VAULT("Encrypted Cache", CellType.SECRET_CACHE),
    GRAND_CORE("Mainframe Core", CellType.GRAND_HALL),
    EXIT_PORTAL("Sub-Sector Uplink", CellType.ENCRYPTED_PORTAL)
}

/**
 * Loot item contained inside procedural matrix loot caches.
 */
data class MatrixLootCache(
    val id: String = Random.nextInt(100000, 999999).toString(),
    val gridX: Int,
    val gridY: Int,
    val cacheType: String, // e.g. "Classified Data-Vault", "Corrupt Memory Bank"
    val creditsReward: Int,
    val ramReward: Int,
    val itemReward: String? = null,
    val isLocked: Boolean = true,
    val hackDifficulty: Int = 1
)

/**
 * Node instance positioned on matrix grid map.
 */
data class MatrixNodePosition(
    val nodeType: MatrixNodeType,
    val x: Int,
    val y: Int,
    val securityLevel: Int, // 1..5
    val description: String
)

/**
 * Complete procedural matrix level structure.
 */
data class ProceduralMatrixLevel(
    val levelNumber: Int,
    val sectorName: String,
    val width: Int,
    val height: Int,
    val securityIntensity: MatrixSecurityIntensity,
    val grid: Array<Array<CellType>>,
    val nodes: List<MatrixNodePosition>,
    val lootCaches: List<MatrixLootCache>,
    val entryPosition: Pair<Int, Int>,
    val exitPosition: Pair<Int, Int>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ProceduralMatrixLevel
        return levelNumber == other.levelNumber && sectorName == other.sectorName
    }

    override fun hashCode(): Int {
        return levelNumber.hashCode() * 31 + sectorName.hashCode()
    }
}

/**
 * Procedural Matrix Level Generator.
 * Creates randomized matrix layout maps with rooms, corridors, security nodes, and loot caches.
 */
object ProceduralMatrixLevelGenerator {

    private val SECTOR_PREFIXES = listOf("Sub-Net", "Quantum", "Cyberspace", "Arasaka", "Militech", "Gibson", "Zeta", "Neural")
    private val SECTOR_SUFFIXES = listOf("Vault", "Matrix Core", "Grid-Zone 9", "Data Corridor", "Sub-Layer", "Deep Web Node", "Sanctum")

    /**
     * Generates a unique procedural matrix level based on depth and optional random seed.
     */
    fun generateLevel(
        levelNumber: Int,
        width: Int = 12,
        height: Int = 12,
        seed: Long = System.currentTimeMillis() + levelNumber * 1000L
    ): ProceduralMatrixLevel {
        val random = Random(seed)

        val sectorName = "${SECTOR_PREFIXES.random(random)}-${SECTOR_SUFFIXES.random(random)} [0x${random.nextInt(0x1000, 0xFFFF).toString(16).uppercase()}]"

        val securityIntensity = when {
            levelNumber <= 2 -> MatrixSecurityIntensity.DEFCON_5_LOW
            levelNumber <= 4 -> MatrixSecurityIntensity.DEFCON_4_MODERATE
            levelNumber <= 7 -> MatrixSecurityIntensity.DEFCON_3_HIGH
            levelNumber <= 9 -> MatrixSecurityIntensity.DEFCON_2_SEVERE
            else -> MatrixSecurityIntensity.DEFCON_1_MAXIMUM
        }

        // Initialize grid filled with WALLs
        val grid = Array(height) { Array(width) { CellType.WALL } }

        // Carve primary room chambers
        val roomCount = random.nextInt(3, 6)
        val rooms = mutableListOf<Room>()

        for (r in 0 until roomCount) {
            val rw = random.nextInt(3, 5)
            val rh = random.nextInt(3, 5)
            val rx = random.nextInt(1, max(2, width - rw - 1))
            val ry = random.nextInt(1, max(2, height - rh - 1))
            val room = Room(rx, ry, rw, rh)

            // Carve room internal space
            for (y in ry until ry + rh) {
                for (x in rx until rx + rw) {
                    grid[y][x] = CellType.PATH
                }
            }

            // Connect room to previous room via corridor
            if (rooms.isNotEmpty()) {
                val prevCenter = rooms.last().center()
                val currCenter = room.center()

                // Horizontal corridor
                val startX = min(prevCenter.first, currCenter.first)
                val endX = max(prevCenter.first, currCenter.first)
                for (x in startX..endX) {
                    grid[prevCenter.second][x] = CellType.PATH
                }

                // Vertical corridor
                val startY = min(prevCenter.second, currCenter.second)
                val endY = max(prevCenter.second, currCenter.second)
                for (y in startY..endY) {
                    grid[y][currCenter.first] = CellType.PATH
                }
            }

            rooms.add(room)
        }

        // Guarantee entry position at first room center
        val entryPos = rooms.firstOrNull()?.center() ?: Pair(1, 1)
        grid[entryPos.second][entryPos.first] = CellType.SAFE_ZONE

        // Guarantee exit portal at last room center
        val exitPos = rooms.lastOrNull()?.center() ?: Pair(width - 2, height - 2)
        grid[exitPos.second][exitPos.first] = CellType.ENCRYPTED_PORTAL

        // Procedural Node and Loot Placement
        val nodes = mutableListOf<MatrixNodePosition>()
        val lootCaches = mutableListOf<MatrixLootCache>()

        nodes.add(
            MatrixNodePosition(
                nodeType = MatrixNodeType.ENTRY_GATE,
                x = entryPos.first,
                y = entryPos.second,
                securityLevel = 1,
                description = "Primary Access Gateway"
            )
        )

        nodes.add(
            MatrixNodePosition(
                nodeType = MatrixNodeType.EXIT_PORTAL,
                x = exitPos.first,
                y = exitPos.second,
                securityLevel = levelNumber,
                description = "Encrypted Level Uplink Portal"
            )
        )

        // Populate open PATH cells with randomized node types and loot caches
        var nodeCount = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                if (grid[y][x] == CellType.PATH && Pair(x, y) != entryPos && Pair(x, y) != exitPos) {
                    val roll = random.nextInt(100)

                    when {
                        roll < 12 -> {
                            grid[y][x] = CellType.DATA_STORE
                            nodes.add(
                                MatrixNodePosition(
                                    nodeType = MatrixNodeType.DATA_NODE,
                                    x = x,
                                    y = y,
                                    securityLevel = random.nextInt(1, levelNumber + 1),
                                    description = "Encrypted Corporate File Terminal"
                                )
                            )
                            lootCaches.add(
                                MatrixLootCache(
                                    gridX = x,
                                    gridY = y,
                                    cacheType = "Corrupt Data Store",
                                    creditsReward = random.nextInt(40, 120) * levelNumber,
                                    ramReward = random.nextInt(1, 3),
                                    itemReward = if (random.nextBoolean()) "RAMBoost.exe" else "NanoMed.sys",
                                    hackDifficulty = random.nextInt(1, 4)
                                )
                            )
                            nodeCount++
                        }
                        roll < 22 -> {
                            grid[y][x] = CellType.VIRUS_NODE
                            nodes.add(
                                MatrixNodePosition(
                                    nodeType = MatrixNodeType.SECURITY_ICE,
                                    x = x,
                                    y = y,
                                    securityLevel = levelNumber + 1,
                                    description = "Active Security Black-ICE Process"
                                )
                            )
                        }
                        roll < 28 -> {
                            grid[y][x] = CellType.SECRET_CACHE
                            nodes.add(
                                MatrixNodePosition(
                                    nodeType = MatrixNodeType.LOOT_VAULT,
                                    x = x,
                                    y = y,
                                    securityLevel = levelNumber + 2,
                                    description = "Classified Vault Crypt-Cache"
                                )
                            )
                            lootCaches.add(
                                MatrixLootCache(
                                    gridX = x,
                                    gridY = y,
                                    cacheType = "Classified Vault Crypt-Cache",
                                    creditsReward = random.nextInt(150, 350) * levelNumber,
                                    ramReward = random.nextInt(2, 5),
                                    itemReward = "OverclockDaemon.dll",
                                    hackDifficulty = random.nextInt(2, 5)
                                )
                            )
                        }
                        roll < 32 -> {
                            grid[y][x] = CellType.GRAVITY_SLOPE
                        }
                    }
                }
            }
        }

        return ProceduralMatrixLevel(
            levelNumber = levelNumber,
            sectorName = sectorName,
            width = width,
            height = height,
            securityIntensity = securityIntensity,
            grid = grid,
            nodes = nodes,
            lootCaches = lootCaches,
            entryPosition = entryPos,
            exitPosition = exitPos
        )
    }

    private data class Room(val x: Int, val y: Int, val w: Int, val h: Int) {
        fun center(): Pair<Int, Int> = Pair(x + w / 2, y + h / 2)
    }
}
