package com.example.data.svdag

import com.example.data.CellType
import com.example.data.ProceduralMatrixLevel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Cyberpunk Environment Types for Procedural Openworld Generation.
 */
enum class CyberEnvironmentDistrict {
    CORPORATE_CORE_DISTRICT,
    NEON_SLUMS_UNDERCITY,
    INDUSTRIAL_SUBGRID,
    CYBER_GRID_NEXUS
}

/**
 * World Building Configuration for Multi-Floor Openworld Sectors.
 */
data class MultiFloorWorldConfig(
    val district: CyberEnvironmentDistrict = CyberEnvironmentDistrict.CORPORATE_CORE_DISTRICT,
    val targetDepth: Int = 6, // 2^6 = 64x64x64 voxels volume (or 2^7 = 128x128x128)
    val sewerHeight: Int = 12,       // Z = 0 .. 11: Sewer canals, drainage & pipes
    val streetHeight: Int = 16,      // Z = 12 .. 27: Street level, plazas, checkpoints
    val buildingFloorHeight: Int = 8,// Z = 28+: Building stories (Lobby, Server Vault, Skywalks)
    val seed: Long = System.currentTimeMillis()
)

/**
 * Navigational Waypoint for First-Person Player / AI Navigation across Multi-Floor Sectors.
 */
data class FirstPersonWaypoint(
    val id: String,
    val name: String,
    val environmentLayer: String, // e.g. "Sub-surface Sewer Basin", "Street Level Plaza", "Tower Floor 3 Server Vault"
    val position: Triple<Int, Int, Int>,
    val isSpawnPoint: Boolean = false,
    val isElevator: Boolean = false,
    val isTerminal: Boolean = false,
    val isVault: Boolean = false
)

/**
 * World Generation Result containing Sparse Voxel DAG, telemetry stats, and First-Person nav metadata.
 */
data class ProceduralWorldResult(
    val dag: SparseVoxelDag,
    val stats: SvdagStats,
    val config: MultiFloorWorldConfig,
    val waypoints: List<FirstPersonWaypoint>,
    val defaultSpawnPosition: Triple<Int, Int, Int>,
    val environmentSummary: Map<String, Int>
)

/**
 * Procedural Multi-Floor Cyberpunk Openworld Generator combining SVDAG & LOD systems.
 * Generates interconnected explorable environments:
 * - Sub-surface Sewers, Drainage Canals, and Underground Hacker Refuges
 * - Ground Street Level Infrastructure, Roads, Plazas, Security Gates & Substations
 * - Multi-story Corporate Skyscrapers with Lobbies, Server Vaults, Penthouse Helipads
 * - Skywalks, Elevated Pedestrian Conduits, and Gravity Lifts
 */
object ProceduralCyberWorldGenerator {

    /**
     * Generates a complete 3D Multi-Floor Cyberpunk Openworld Sector integrated into a Sparse Voxel DAG.
     */
    fun generateMultiFloorWorld(config: MultiFloorWorldConfig): ProceduralWorldResult {
        val startMs = System.currentTimeMillis()
        val random = Random(config.seed)
        val dag = SparseVoxelDag(config.targetDepth)
        val N = dag.gridSize

        val voxels3D = Array(N) { Array(N) { Array(N) { VoxelType.EMPTY } } }
        val waypoints = mutableListOf<FirstPersonWaypoint>()
        val envSummary = mutableMapOf<String, Int>()

        // ----------------------------------------------------
        // LEVEL LAYER 1: Sub-Surface Sewers & Utility Tunnels (Z = 0 .. config.sewerHeight - 1)
        // ----------------------------------------------------
        val sewerZMax = config.sewerHeight.coerceAtMost(N / 4)
        generateSewerLayer(voxels3D, N, sewerZMax, random, waypoints, envSummary)

        // ----------------------------------------------------
        // LEVEL LAYER 2: Ground Street Level & Infrastructure (Z = sewerZMax .. sewerZMax + config.streetHeight - 1)
        // ----------------------------------------------------
        val streetZStart = sewerZMax
        val streetZMax = (streetZStart + config.streetHeight).coerceAtMost(N / 2)
        generateStreetLayer(voxels3D, N, streetZStart, streetZMax, random, waypoints, envSummary)

        // ----------------------------------------------------
        // LEVEL LAYER 3: Multi-Floor Corporate Towers & Skywalks (Z = streetZMax .. N - 1)
        // ----------------------------------------------------
        val towerZStart = streetZMax
        generateTowersAndSkywalksLayer(voxels3D, N, towerZStart, config.buildingFloorHeight, random, waypoints, envSummary)

        // ----------------------------------------------------
        // Vertical Connectors: Elevator Shafts & Stairwells spanning all layers
        // ----------------------------------------------------
        connectVerticalTransitShafts(voxels3D, N, sewerZMax, towerZStart, waypoints)

        // ----------------------------------------------------
        // Enforce Outer Perimeter Boundary Walls
        // ----------------------------------------------------
        for (z in 0 until N) {
            for (y in 0 until N) {
                for (x in 0 until N) {
                    if (x == 0 || x == N - 1 || y == 0 || y == N - 1 || z == 0 || z == N - 1) {
                        voxels3D[x][y][z] = VoxelType.SOLID_WALL
                    }
                }
            }
        }

        // Populate Sparse Voxel DAG
        dag.populateFrom3DArray(voxels3D)
        val buildTime = System.currentTimeMillis() - startMs

        // Benchmark Raycasting
        val raycastMicro = benchmarkRaycastPerformance(dag)
        val stats = dag.getStats(buildTime, raycastMicro)

        val defaultSpawn = waypoints.firstOrNull { it.isSpawnPoint }?.position ?: Triple(N / 2, N / 2, streetZStart + 1)

        return ProceduralWorldResult(
            dag = dag,
            stats = stats,
            config = config,
            waypoints = waypoints,
            defaultSpawnPosition = defaultSpawn,
            environmentSummary = envSummary
        )
    }

    /**
     * Sub-Surface Sewer, Drainage & Conduit Layer
     */
    private fun generateSewerLayer(
        voxels: Array<Array<Array<VoxelType>>>,
        N: Int,
        sewerZMax: Int,
        random: Random,
        waypoints: MutableList<FirstPersonWaypoint>,
        envSummary: MutableMap<String, Int>
    ) {
        var sewerBlocks = 0

        // Bedrock floor (z = 0)
        for (y in 0 until N) {
            for (x in 0 until N) {
                voxels[x][y][0] = VoxelType.SOLID_WALL
            }
        }

        // Sewer Ceiling Slab (z = sewerZMax - 1)
        for (y in 1 until N - 1) {
            for (x in 1 until N - 1) {
                voxels[x][y][sewerZMax - 1] = VoxelType.SOLID_WALL
            }
        }

        // Main Cross Drainage Canals
        val canalX = N / 2
        val canalY = N / 2
        val canalWidth = 4

        for (z in 1 until sewerZMax - 1) {
            for (y in 1 until N - 1) {
                for (x in 1 until N - 1) {
                    val inCanalX = abs(x - canalX) <= canalWidth / 2
                    val inCanalY = abs(y - canalY) <= canalWidth / 2

                    if (inCanalX || inCanalY) {
                        if (z == 1) {
                            voxels[x][y][z] = VoxelType.VENT_TUNNEL // Drainage channel bed
                        } else {
                            voxels[x][y][z] = VoxelType.PATH
                        }
                        sewerBlocks++
                    } else {
                        voxels[x][y][z] = VoxelType.SOLID_WALL
                    }
                }
            }
        }

        // Sub-surface Underground Hacker Refuge Basin (Quadrant 1)
        val refugeX = N / 4
        val refugeY = N / 4
        val refugeR = 5
        for (z in 1 until sewerZMax - 1) {
            for (dy in -refugeR..refugeR) {
                for (dx in -refugeR..refugeR) {
                    val px = (refugeX + dx).coerceIn(1, N - 2)
                    val py = (refugeY + dy).coerceIn(1, N - 2)

                    if (abs(dx) == refugeR || abs(dy) == refugeR) {
                        voxels[px][py][z] = VoxelType.SOLID_WALL
                    } else {
                        voxels[px][py][z] = when {
                            z == 1 && dx == 0 && dy == 0 -> VoxelType.SAFE_ZONE
                            z == 1 && abs(dx) == 2 && abs(dy) == 2 -> VoxelType.HACKABLE_TERMINAL
                            z == 1 && dx == 3 && dy == 0 -> VoxelType.LOOT_CACHE
                            else -> VoxelType.PATH
                        }
                        sewerBlocks++
                    }
                }
            }
        }

        // Connect Refuge to Main Canal via Tunnel
        for (x in refugeX..canalX) {
            voxels[x][refugeY][1] = VoxelType.PATH
            voxels[x][refugeY][2] = VoxelType.PATH
        }

        waypoints.add(
            FirstPersonWaypoint(
                id = "SEWER-01",
                name = "Underground Hacker Refuge",
                environmentLayer = "Sub-surface Sewers",
                position = Triple(refugeX, refugeY, 1),
                isSpawnPoint = true,
                isTerminal = true
            )
        )

        waypoints.add(
            FirstPersonWaypoint(
                id = "SEWER-VAULT",
                name = "Sub-drainage Encrypted Vault",
                environmentLayer = "Sub-surface Sewers",
                position = Triple(refugeX + 3, refugeY, 1),
                isVault = true
            )
        )

        envSummary["Sewer Canal Voxels"] = sewerBlocks
    }

    /**
     * Ground Street Level, Road Grid, Plazas & Security Infrastructure Layer
     */
    private fun generateStreetLayer(
        voxels: Array<Array<Array<VoxelType>>>,
        N: Int,
        zStart: Int,
        zMax: Int,
        random: Random,
        waypoints: MutableList<FirstPersonWaypoint>,
        envSummary: MutableMap<String, Int>
    ) {
        var streetBlocks = 0

        // Street Base Asphalt Floor (z = zStart)
        for (y in 1 until N - 1) {
            for (x in 1 until N - 1) {
                voxels[x][y][zStart] = VoxelType.PATH
            }
        }

        // Road Grid Corridors (2-lane avenues in X and Y)
        val roadStep = N / 3
        val roadWidth = 3

        for (z in zStart + 1 until zMax) {
            for (y in 1 until N - 1) {
                for (x in 1 until N - 1) {
                    val isRoadX = (x % roadStep) in 0..roadWidth
                    val isRoadY = (y % roadStep) in 0..roadWidth

                    if (isRoadX || isRoadY) {
                        voxels[x][y][z] = VoxelType.PATH
                        streetBlocks++
                    } else {
                        // Building plots & plazas
                        if (z == zStart + 1) {
                            voxels[x][y][z] = VoxelType.GRAND_HALL
                        } else if (z == zStart + 2 && (x % 5 == 0 || y % 5 == 0)) {
                            voxels[x][y][z] = VoxelType.HACKABLE_TERMINAL // Neon Street Terminal
                        } else {
                            voxels[x][y][z] = VoxelType.EMPTY
                        }
                    }
                }
            }
        }

        // Security Checkpoint Gate
        val gateX = N / 2
        val gateY = roadStep
        for (z in zStart + 1 until zStart + 4) {
            voxels[gateX][gateY][z] = VoxelType.TERMINAL_DOOR
            voxels[gateX + 1][gateY][z] = VoxelType.BLACK_ICE
            voxels[gateX - 1][gateY][z] = VoxelType.BLACK_ICE
        }

        waypoints.add(
            FirstPersonWaypoint(
                id = "STREET-PLAZA",
                name = "Central Street Promenade",
                environmentLayer = "Street Level",
                position = Triple(N / 2, N / 2, zStart + 1),
                isSpawnPoint = true
            )
        )

        waypoints.add(
            FirstPersonWaypoint(
                id = "CHECKPOINT-01",
                name = "North Security Checkpoint Gate",
                environmentLayer = "Street Level",
                position = Triple(gateX, gateY, zStart + 1),
                isTerminal = true
            )
        )

        envSummary["Street Infrastructure Voxels"] = streetBlocks
    }

    /**
     * Corporate Skyscraper Mega-Towers, Server Vaults, Penthouse & Skywalks Layer
     */
    private fun generateTowersAndSkywalksLayer(
        voxels: Array<Array<Array<VoxelType>>>,
        N: Int,
        zStart: Int,
        floorHeight: Int,
        random: Random,
        waypoints: MutableList<FirstPersonWaypoint>,
        envSummary: MutableMap<String, Int>
    ) {
        var towerBlocks = 0

        // Build 2 Major Corporate Mega-Towers in Quadrants
        val towerSize = N / 3
        val towers = listOf(
            Triple(N / 6, N / 6, "Arasaka Cybernetics Tower"),
            Triple(N - N / 3, N - N / 3, "Militech Core Spire")
        )

        towers.forEachIndexed { tIdx, (tx, ty, tName) ->
            val topZ = (N - 2).coerceAtLeast(zStart + floorHeight * 2)

            for (z in zStart until topZ) {
                val relZ = z - zStart
                val floorIdx = relZ / floorHeight
                val isFloorSlab = relZ % floorHeight == 0

                for (dy in 0 until towerSize) {
                    for (dx in 0 until towerSize) {
                        val px = (tx + dx).coerceIn(1, N - 2)
                        val py = (ty + dy).coerceIn(1, N - 2)

                        val isPerimeter = dx == 0 || dx == towerSize - 1 || dy == 0 || dy == towerSize - 1

                        if (isPerimeter) {
                            voxels[px][py][z] = VoxelType.SOLID_WALL
                            towerBlocks++
                        } else if (isFloorSlab) {
                            voxels[px][py][z] = VoxelType.SOLID_WALL
                            towerBlocks++
                        } else {
                            // Interior Room Content
                            val isCenter = dx == towerSize / 2 && dy == towerSize / 2
                            if (isCenter && relZ % floorHeight == 2) {
                                voxels[px][py][z] = if (floorIdx % 2 == 0) VoxelType.DATA_CORE else VoxelType.SCAN_CACHE
                            } else if (dx == 1 && dy == 1 && relZ % floorHeight == 1) {
                                voxels[px][py][z] = VoxelType.HACKABLE_TERMINAL
                            } else {
                                voxels[px][py][z] = VoxelType.PATH
                            }
                        }
                    }
                }
            }

            // Helipad / Penthouse Balcony on Roof
            val roofZ = topZ - 1
            for (dy in 0 until towerSize) {
                for (dx in 0 until towerSize) {
                    val px = (tx + dx).coerceIn(1, N - 2)
                    val py = (ty + dy).coerceIn(1, N - 2)
                    voxels[px][py][roofZ] = VoxelType.ELEVATED_BALCONY
                }
            }

            waypoints.add(
                FirstPersonWaypoint(
                    id = "TOWER-${tIdx + 1}-LOBBY",
                    name = "$tName Lobby",
                    environmentLayer = "Mega-Tower Floor 1",
                    position = Triple(tx + towerSize / 2, ty + 1, zStart + 1),
                    isTerminal = true
                )
            )

            waypoints.add(
                FirstPersonWaypoint(
                    id = "TOWER-${tIdx + 1}-VAULT",
                    name = "$tName Server Core Vault",
                    environmentLayer = "Mega-Tower Floor 2",
                    position = Triple(tx + towerSize / 2, ty + towerSize / 2, zStart + floorHeight + 2),
                    isVault = true
                )
            )

            waypoints.add(
                FirstPersonWaypoint(
                    id = "TOWER-${tIdx + 1}-ROOF",
                    name = "$tName Roof Helipad",
                    environmentLayer = "Penthouse Roof",
                    position = Triple(tx + towerSize / 2, ty + towerSize / 2, roofZ)
                )
            )
        }

        // Skywalk Pedestrian Bridge connecting the 2 towers at Mid-Height
        val midZ = zStart + floorHeight + 1
        val t1X = N / 6 + towerSize / 2
        val t1Y = N / 6 + towerSize / 2
        val t2X = N - N / 3 + towerSize / 2
        val t2Y = N - N / 3 + towerSize / 2

        // Draw Skywalk Corridor
        var currX = t1X
        var currY = t1Y
        while (currX != t2X || currY != t2Y) {
            if (currX < t2X) currX++ else if (currX > t2X) currX--
            if (currY < t2Y) currY++ else if (currY > t2Y) currY--

            val px = currX.coerceIn(1, N - 2)
            val py = currY.coerceIn(1, N - 2)

            voxels[px][py][midZ - 1] = VoxelType.ELEVATED_BALCONY // Skywalk Floor
            voxels[px][py][midZ] = VoxelType.PATH                // Walkway
            voxels[px][py][midZ + 1] = VoxelType.GRAVITY_SLOPE    // Gravity roof
        }

        waypoints.add(
            FirstPersonWaypoint(
                id = "SKYWALK-01",
                name = "High-Rise Skybridge Conduit",
                environmentLayer = "Mid-Tower Skywalk",
                position = Triple((t1X + t2X) / 2, (t1Y + t2Y) / 2, midZ)
            )
        )

        envSummary["MegaTower & Skywalk Voxels"] = towerBlocks
    }

    /**
     * Connects all multi-floor layers vertically using central elevator shafts and stairwells.
     */
    private fun connectVerticalTransitShafts(
        voxels: Array<Array<Array<VoxelType>>>,
        N: Int,
        sewerZMax: Int,
        towerZStart: Int,
        waypoints: MutableList<FirstPersonWaypoint>
    ) {
        val cx = N / 2
        val cy = N / 2

        // Central Multi-Floor Transit Elevator Shaft from Z = 1 to N - 2
        for (z in 1 until N - 1) {
            for (dx in -1..1) {
                for (dy in -1..1) {
                    val px = cx + dx
                    val py = cy + dy

                    if (dx == 0 && dy == 0) {
                        voxels[px][py][z] = VoxelType.ELEVATOR
                    } else {
                        voxels[px][py][z] = VoxelType.SOLID_WALL
                    }
                }
            }
            // Add elevator doors at key floor breaks
            if (z == 1 || z == sewerZMax || z == towerZStart || z % 8 == 0) {
                voxels[cx + 1][cy][z] = VoxelType.PATH
            }
        }

        waypoints.add(
            FirstPersonWaypoint(
                id = "TRANSIT-ELEVATOR-HUB",
                name = "Central Multi-Floor Elevator Hub",
                environmentLayer = "All Layers Connector",
                position = Triple(cx, cy, sewerZMax),
                isElevator = true
            )
        )
    }

    /**
     * Benchmark DDA Raycasting speed on the generated Sparse Voxel DAG.
     */
    private fun benchmarkRaycastPerformance(dag: SparseVoxelDag, iterations: Int = 300): Double {
        val random = Random(1337)
        val N = dag.gridSize.toDouble()
        val startNano = System.nanoTime()

        for (i in 0 until iterations) {
            val ox = random.nextDouble(2.0, N - 2.0)
            val oy = random.nextDouble(2.0, N - 2.0)
            val oz = random.nextDouble(2.0, N - 2.0)
            val dx = random.nextDouble(-1.0, 1.0)
            val dy = random.nextDouble(-1.0, 1.0)
            val dz = random.nextDouble(-1.0, 1.0)
            dag.raycast(ox, oy, oz, dx, dy, dz, maxDistance = N)
        }

        val elapsedNano = System.nanoTime() - startNano
        return (elapsedNano.toDouble() / iterations) / 1000.0
    }
}
