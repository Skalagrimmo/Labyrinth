package com.example.data.svdag

import com.example.data.CellType
import com.example.data.ProceduralMatrixLevel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * World Building Engine leveraging Sparse Voxel DAG (SVDAG).
 * Generates ultra-large scale 3D Cyberspace Sectors (64^3, 128^3, 256^3 voxels) with zero memory bloat or performance drop.
 */
object SvdagWorldBuilder {

    /**
     * Converts a 2D matrix level layout into a full 3D Sparse Voxel DAG sector with multi-floor height levels.
     */
    fun buildSvdagFrom2DLevel(
        maze2d: Array<Array<CellType>>,
        heightLevels: Int = 16,
        targetDepth: Int = 6 // 2^6 = 64x64x64 volume
    ): Pair<SparseVoxelDag, SvdagStats> {
        val startMs = System.currentTimeMillis()
        val dag = SparseVoxelDag(targetDepth)
        val gridSize = dag.gridSize // e.g., 64

        val sizeX = maze2d[0].size
        val sizeY = maze2d.size

        val voxels3D = Array(gridSize) { Array(gridSize) { Array(gridSize) { VoxelType.EMPTY } } }

        // Populate 3D space from 2D maze template
        for (y in 0 until min(sizeY, gridSize)) {
            for (x in 0 until min(sizeX, gridSize)) {
                val cell = maze2d[y][x]
                val vType = VoxelType.fromCellType(cell)

                // Floor foundation (z = 0)
                voxels3D[x][y][0] = VoxelType.SOLID_WALL

                for (z in 1 until min(heightLevels, gridSize)) {
                    when (vType) {
                        VoxelType.SOLID_WALL -> {
                            // Extrude walls up to height
                            voxels3D[x][y][z] = VoxelType.SOLID_WALL
                        }
                        VoxelType.GRAND_HALL, VoxelType.ELEVATED_BALCONY -> {
                            if (z == 1) voxels3D[x][y][z] = vType
                            else if (z == heightLevels - 1) voxels3D[x][y][z] = VoxelType.SOLID_WALL
                            else if (z % 4 == 0 && (x % 3 == 0 || y % 3 == 0)) voxels3D[x][y][z] = VoxelType.ELEVATED_BALCONY
                            else voxels3D[x][y][z] = VoxelType.EMPTY
                        }
                        VoxelType.STAIRS -> {
                            // Create 3D diagonal voxel staircase step
                            val stepZ = 1 + (x + y) % (heightLevels - 2)
                            if (z == stepZ) voxels3D[x][y][z] = VoxelType.STAIRS
                            else voxels3D[x][y][z] = VoxelType.EMPTY
                        }
                        VoxelType.ELEVATOR -> {
                            // Vertical continuous elevator shaft
                            if (z % 2 == 0) voxels3D[x][y][z] = VoxelType.ELEVATOR
                            else voxels3D[x][y][z] = VoxelType.EMPTY
                        }
                        else -> {
                            if (z == 1) voxels3D[x][y][z] = vType
                            else if (z == heightLevels - 1) voxels3D[x][y][z] = VoxelType.SOLID_WALL
                            else voxels3D[x][y][z] = VoxelType.EMPTY
                        }
                    }
                }
            }
        }

        // Add ceiling boundary at heightLevels
        val ceilZ = min(heightLevels, gridSize - 1)
        for (y in 0 until min(sizeY, gridSize)) {
            for (x in 0 until min(sizeX, gridSize)) {
                if (voxels3D[x][y][ceilZ] == VoxelType.EMPTY) {
                    voxels3D[x][y][ceilZ] = VoxelType.SOLID_WALL
                }
            }
        }

        val populateMs = dag.populateFrom3DArray(voxels3D)
        val buildTime = System.currentTimeMillis() - startMs
        val benchMicro = benchmarkRaycastPerformance(dag)
        val stats = dag.getStats(buildTime, benchMicro)

        return Pair(dag, stats)
    }

    /**
     * Generates a massive multi-story 3D Cyberspace Corporate Mega-Tower Sector in Sparse Voxel DAG.
     * Can scale to 64^3, 128^3, 256^3 voxels!
     */
    fun generateCyberspaceMegaSector(
        targetDepth: Int = 7, // 2^7 = 128x128x128 = 2,097,152 voxels!
        seed: Long = System.currentTimeMillis()
    ): Pair<SparseVoxelDag, SvdagStats> {
        val startMs = System.currentTimeMillis()
        val random = Random(seed)
        val dag = SparseVoxelDag(targetDepth)
        val N = dag.gridSize

        val voxels3D = Array(N) { Array(N) { Array(N) { VoxelType.EMPTY } } }

        // 1. Build Perimeter Encryption Shields & Solid Outer Walls
        for (z in 0 until N) {
            for (y in 0 until N) {
                for (x in 0 until N) {
                    if (x == 0 || x == N - 1 || y == 0 || y == N - 1 || z == 0 || z == N - 1) {
                        voxels3D[x][y][z] = VoxelType.SOLID_WALL
                    }
                }
            }
        }

        // 2. Build Repeating Architectural Floors (Every 8 vertical voxels)
        val floorHeight = 8
        val totalFloors = N / floorHeight

        for (floor in 0 until totalFloors) {
            val baseZ = floor * floorHeight

            // Floor slab
            for (y in 1 until N - 1) {
                for (x in 1 until N - 1) {
                    voxels3D[x][y][baseZ] = VoxelType.SOLID_WALL
                }
            }

            // Central Core Uplink Elevator Column
            val cx = N / 2
            val cy = N / 2
            for (dz in 1 until floorHeight) {
                for (dx in -2..2) {
                    for (dy in -2..2) {
                        if (abs(dx) == 2 || abs(dy) == 2) {
                            voxels3D[cx + dx][cy + dy][baseZ + dz] = VoxelType.SOLID_WALL
                        } else {
                            voxels3D[cx + dx][cy + dy][baseZ + dz] = VoxelType.ELEVATOR
                        }
                    }
                }
            }

            // Quadrant Data Vaults & Corridors
            val quadSize = N / 4
            val roomCenters = listOf(
                Pair(quadSize, quadSize),
                Pair(N - quadSize, quadSize),
                Pair(quadSize, N - quadSize),
                Pair(N - quadSize, N - quadSize)
            )

            for ((rx, ry) in roomCenters) {
                val roomR = 4
                for (z in baseZ + 1 until baseZ + floorHeight - 1) {
                    for (dy in -roomR..roomR) {
                        for (dx in -roomR..roomR) {
                            val px = (rx + dx).coerceIn(1, N - 2)
                            val py = (ry + dy).coerceIn(1, N - 2)

                            if (abs(dx) == roomR || abs(dy) == roomR) {
                                voxels3D[px][py][z] = VoxelType.SOLID_WALL
                            } else {
                                voxels3D[px][py][z] = if (dx == 0 && dy == 0 && z == baseZ + 2) {
                                    VoxelType.DATA_CORE
                                } else if (dx % 2 == 0 && dy % 2 == 0 && z == baseZ + 1) {
                                    VoxelType.SAFE_ZONE
                                } else {
                                    VoxelType.PATH
                                }
                            }
                        }
                    }
                }
                // Add Doorway
                voxels3D[rx][ry - roomR][baseZ + 1] = VoxelType.PATH
                voxels3D[rx][ry + roomR][baseZ + 1] = VoxelType.PATH
            }

            // Connect Rooms via Orthogonal Corridors
            for (x in 2 until N - 2) {
                voxels3D[x][cy][baseZ + 1] = VoxelType.PATH
            }
            for (y in 2 until N - 2) {
                voxels3D[cx][y][baseZ + 1] = VoxelType.PATH
            }

            // Security ICE Barriers in higher floors
            if (floor >= 2) {
                val iceX = random.nextInt(4, N - 4)
                val iceY = random.nextInt(4, N - 4)
                for (z in baseZ + 1 until baseZ + 4) {
                    voxels3D[iceX][iceY][z] = VoxelType.BLACK_ICE
                }
            }
        }

        // 3. Populate into Sparse Voxel DAG
        val popMs = dag.populateFrom3DArray(voxels3D)
        val buildTime = System.currentTimeMillis() - startMs
        val benchMicro = benchmarkRaycastPerformance(dag)
        val stats = dag.getStats(buildTime, benchMicro)

        return Pair(dag, stats)
    }

    /**
     * Benchmark DDA Raycasting speed on the Sparse Voxel DAG.
     */
    fun benchmarkRaycastPerformance(dag: SparseVoxelDag, iterations: Int = 500): Double {
        val random = Random(42)
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
        return (elapsedNano.toDouble() / iterations) / 1000.0 // Microseconds per raycast
    }
}
