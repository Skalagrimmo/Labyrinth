package com.example.data.svdag

import com.example.data.CellType
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Voxel Types for 3D Cyber World Building in Sparse Voxel DAG (SVDAG).
 */
enum class VoxelType(
    val id: Int,
    val displayName: String,
    val colorHex: Long,
    val isSolid: Boolean,
    val symbol: Char
) {
    EMPTY(0, "Vacuum / Air", 0x00000000, false, ' '),
    SOLID_WALL(1, "Solid Firewall Block", 0xFF00FFCC, true, '#'),
    PATH(2, "Data Channel", 0xFF1E293B, false, '.'),
    DATA_CORE(3, "Data Stream Core", 0xFF3B82F6, false, 'D'),
    BLACK_ICE(4, "Black-ICE Barrier", 0xFFEF4444, true, 'V'),
    ENCRYPTED_PORTAL(5, "Sub-Sector Gate", 0xFFA855F7, false, 'P'),
    SAFE_ZONE(6, "Access Point", 0xFF10B981, false, 'S'),
    LOOT_CACHE(7, "Encrypted Vault", 0xFFF59E0B, true, 'C'),
    GRAND_HALL(8, "Grand Core Floor", 0xFF06B6D4, false, 'H'),
    STAIRS(9, "Voxel Staircase", 0xFF6366F1, false, 'U'),
    GRAVITY_SLOPE(10, "Gravity Slope", 0xFFEC4899, false, 'L'),
    ELEVATOR(11, "Elevator Column", 0xFF14B8A6, false, 'X'),
    VENT_TUNNEL(12, "Service Conduit", 0xFF64748B, false, 'T'),
    ELEVATED_BALCONY(13, "High-Level Balcony", 0xFF8B5CF6, false, 'B'),
    SECRET_WALL(14, "Illusory Wall", 0xFF475569, true, 'W'),
    HACKABLE_TERMINAL(15, "Security Terminal", 0xFF00E5FF, true, 'K'),
    TERMINAL_DOOR(16, "Security Door", 0xFF334155, true, 'G'),
    SCAN_CACHE(17, "Quantum Stealth Cache", 0xFFFFD700, true, 'M'),
    ALTERNATIVE_VENT(18, "Sub-Conduit Vent", 0xFF38BDF8, false, 'Q'),
    ICE_PATROL(19, "ICE Patrol Unit", 0xFFFF0055, false, 'I');

    companion object {
        private val VALUES = values()
        fun fromId(id: Int): VoxelType = VALUES.getOrElse(id) { EMPTY }

        fun fromCellType(cellType: CellType): VoxelType = when (cellType) {
            CellType.WALL -> SOLID_WALL
            CellType.PATH -> PATH
            CellType.DATA_STORE -> DATA_CORE
            CellType.ENCRYPTED_PORTAL -> ENCRYPTED_PORTAL
            CellType.VIRUS_NODE -> BLACK_ICE
            CellType.SAFE_ZONE -> SAFE_ZONE
            CellType.SECRET_CACHE -> LOOT_CACHE
            CellType.GRAND_HALL -> GRAND_HALL
            CellType.DOME_CHAMBER -> GRAND_HALL
            CellType.VENT_TUNNEL -> VENT_TUNNEL
            CellType.ELEVATED_BALCONY -> ELEVATED_BALCONY
            CellType.STAIRS_UP, CellType.STAIRS_DOWN -> STAIRS
            CellType.GRAVITY_SLOPE -> GRAVITY_SLOPE
            CellType.ECHO -> DATA_CORE
            CellType.ELEVATOR -> ELEVATOR
            CellType.SECRET_WALL -> SECRET_WALL
            CellType.HACKABLE_TERMINAL -> HACKABLE_TERMINAL
            CellType.TERMINAL_DOOR -> TERMINAL_DOOR
            CellType.SCAN_CACHE -> SCAN_CACHE
            CellType.ALTERNATIVE_VENT -> ALTERNATIVE_VENT
        }
    }
}

/**
 * Representation of a Node in Sparse Voxel Directed Acyclic Graph.
 */
sealed class SvdagNode {
    /** Leaf node representing a single uniform voxel material */
    data class LeafNode(val voxelType: VoxelType) : SvdagNode() {
        val dominantVoxel: VoxelType get() = voxelType
    }

    /** Internal octree node pointing to 8 child node IDs in pool */
    data class InternalNode(
        val children: IntArray,
        val dominantVoxel: VoxelType = VoxelType.EMPTY
    ) : SvdagNode() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is InternalNode) return false
            return children.contentEquals(other.children) && dominantVoxel == other.dominantVoxel
        }

        override fun hashCode(): Int {
            var result = children.contentHashCode()
            result = 31 * result + dominantVoxel.hashCode()
            return result
        }
    }
}

/**
 * Performance & Memory Telemetry for Sparse Voxel DAG.
 */
data class SvdagStats(
    val maxDepth: Int,
    val gridSize: Int,
    val totalVoxels: Long,
    val totalNodes: Int,
    val leafCount: Int,
    val internalCount: Int,
    val rawMemoryBytes: Long,
    val dagMemoryBytes: Long,
    val compressionRatio: Float,
    val buildTimeMs: Long,
    val averageRaycastMicros: Double,
    val currentLodLevel: Int = 0,
    val lodCellSize: Int = 1
)

/**
 * Raycast hit result from traversing Sparse Voxel DAG.
 */
data class SvdagRaycastResult(
    val hit: Boolean,
    val hitX: Double,
    val hitY: Double,
    val hitZ: Double,
    val voxelX: Int,
    val voxelY: Int,
    val voxelZ: Int,
    val voxelType: VoxelType,
    val stepsTaken: Int,
    val distance: Double
)

/**
 * High Performance Sparse Voxel Directed Acyclic Graph (SVDAG).
 * Compresses 3D voxel spaces (64^3, 128^3, 256^3+) by deduplicating identical subtrees into shared node indices.
 * Supports O(log N) point lookup, fast DDA raycasting, copy-on-write sub-tree modifications, and telemetry.
 */
class SparseVoxelDag(val maxDepth: Int) {

    val gridSize: Int = 1 shl maxDepth // N = 2^maxDepth (e.g., 64, 128, 256)
    val totalVoxelVolume: Long = gridSize.toLong() * gridSize.toLong() * gridSize.toLong()

    private val nodesList = mutableListOf<SvdagNode>()
    private val nodeKeyToIdMap = HashMap<Any, Int>()

    var rootId: Int = -1
        private set

    // Cached leaf node IDs for each VoxelType
    private val leafIdsMap = IntArray(VoxelType.entries.size) { -1 }

    init {
        // Pre-create canonical leaf nodes for all voxel types
        for (vType in VoxelType.entries) {
            val leaf = SvdagNode.LeafNode(vType)
            val id = nodesList.size
            nodesList.add(leaf)
            nodeKeyToIdMap[leaf] = id
            leafIdsMap[vType.id] = id
        }
        // Default empty DAG root (entire volume is EMPTY)
        rootId = buildEmptyRoot(maxDepth)
    }

    private fun getLeafId(type: VoxelType): Int {
        return leafIdsMap[type.id]
    }

    private fun buildEmptyRoot(depth: Int): Int {
        val emptyLeafId = getLeafId(VoxelType.EMPTY)
        var currentId = emptyLeafId
        for (d in 1..depth) {
            val children = IntArray(8) { currentId }
            currentId = getOrInsertInternal(children)
        }
        return currentId
    }

    /**
     * Computes the representative/dominant voxel type across 8 child node IDs for LOD filtering.
     * Non-empty solid/interactive voxels are prioritized over EMPTY.
     */
    private fun computeDominantVoxel(children: IntArray): VoxelType {
        val counts = HashMap<VoxelType, Int>()
        for (childId in children) {
            val vType = when (val node = nodesList[childId]) {
                is SvdagNode.LeafNode -> node.voxelType
                is SvdagNode.InternalNode -> node.dominantVoxel
            }
            counts[vType] = (counts[vType] ?: 0) + 1
        }
        // Prioritize non-empty voxels first
        val nonEmpty = counts.filterKeys { it != VoxelType.EMPTY }
        if (nonEmpty.isNotEmpty()) {
            return nonEmpty.maxByOrNull { entry ->
                // Higher priority weight for interactive / solid voxels
                var weight = entry.value * 10
                if (entry.key.isSolid) weight += 5
                if (entry.key != VoxelType.SOLID_WALL && entry.key != VoxelType.PATH) weight += 15
                weight
            }?.key ?: VoxelType.EMPTY
        }
        return VoxelType.EMPTY
    }

    /**
     * Deduplicates internal octree node. If all 8 children are identical and refer to a leaf, fold it into that leaf.
     */
    fun getOrInsertInternal(children: IntArray): Int {
        // Homogenous octant optimization: check if all 8 children are the exact same leaf node
        val firstChild = children[0]
        var allSame = true
        for (i in 1 until 8) {
            if (children[i] != firstChild) {
                allSame = false
                break
            }
        }
        if (allSame && nodesList[firstChild] is SvdagNode.LeafNode) {
            return firstChild
        }

        val dominant = computeDominantVoxel(children)
        val key = SvdagNode.InternalNode(children.clone(), dominant)
        val existingId = nodeKeyToIdMap[key]
        if (existingId != null) {
            return existingId
        }

        val newId = nodesList.size
        nodesList.add(key)
        nodeKeyToIdMap[key] = newId
        return newId
    }

    /**
     * Query voxel at 3D integer coordinates (x, y, z) with a specific Level of Detail (LOD).
     * @param lod Level of Detail step (0 = Full Resolution, 1 = 2x2x2 downsample, 2 = 4x4x4 downsample, etc.).
     * Traversing down to (maxDepth - lod) steps returns filtered dominant voxels at higher tree levels.
     */
    fun getVoxelAtLod(x: Int, y: Int, z: Int, lod: Int = 0): VoxelType {
        if (x !in 0 until gridSize || y !in 0 until gridSize || z !in 0 until gridSize) {
            return VoxelType.EMPTY
        }

        val targetDepth = (maxDepth - lod).coerceAtLeast(0)
        var currentId = rootId
        var currentDepth = maxDepth

        while (currentDepth > targetDepth) {
            val node = nodesList[currentId]
            if (node is SvdagNode.LeafNode) {
                return node.voxelType
            }
            val internal = node as SvdagNode.InternalNode

            val shift = currentDepth - 1
            val bitX = (x shr shift) and 1
            val bitY = (y shr shift) and 1
            val bitZ = (z shr shift) and 1

            val childIndex = (bitZ shl 2) or (bitY shl 1) or bitX
            currentId = internal.children[childIndex]
            currentDepth--
        }

        return when (val node = nodesList[currentId]) {
            is SvdagNode.LeafNode -> node.voxelType
            is SvdagNode.InternalNode -> node.dominantVoxel
        }
    }

    /**
     * Get voxel at 3D integer coordinates (x, y, z).
     * O(log N) DAG hierarchy traversal.
     */
    fun getVoxel(x: Int, y: Int, z: Int): VoxelType {
        if (x !in 0 until gridSize || y !in 0 until gridSize || z !in 0 until gridSize) {
            return VoxelType.EMPTY
        }

        var currentId = rootId
        var currentDepth = maxDepth

        while (currentDepth > 0) {
            val node = nodesList[currentId]
            if (node is SvdagNode.LeafNode) {
                return node.voxelType
            }
            val internal = node as SvdagNode.InternalNode

            val shift = currentDepth - 1
            val bitX = (x shr shift) and 1
            val bitY = (y shr shift) and 1
            val bitZ = (z shr shift) and 1

            val childIndex = (bitZ shl 2) or (bitY shl 1) or bitX
            currentId = internal.children[childIndex]
            currentDepth--
        }

        val leaf = nodesList[currentId] as SvdagNode.LeafNode
        return leaf.voxelType
    }

    /**
     * Set voxel at 3D integer coordinates (x, y, z) to voxelType.
     * Bottom-up functional rebuild with canonical node deduplication.
     */
    fun setVoxel(x: Int, y: Int, z: Int, voxelType: VoxelType) {
        if (x !in 0 until gridSize || y !in 0 until gridSize || z !in 0 until gridSize) return
        val targetLeafId = getLeafId(voxelType)
        rootId = setVoxelRecursive(rootId, maxDepth, x, y, z, targetLeafId)
    }

    private fun setVoxelRecursive(nodeId: Int, depth: Int, x: Int, y: Int, z: Int, targetLeafId: Int): Int {
        if (depth == 0) {
            return targetLeafId
        }

        val node = nodesList[nodeId]
        val children = if (node is SvdagNode.LeafNode) {
            IntArray(8) { nodeId }
        } else {
            (node as SvdagNode.InternalNode).children.clone()
        }

        val shift = depth - 1
        val bitX = (x shr shift) and 1
        val bitY = (y shr shift) and 1
        val bitZ = (z shr shift) and 1

        val childIndex = (bitZ shl 2) or (bitY shl 1) or bitX
        val updatedChild = setVoxelRecursive(children[childIndex], depth - 1, x, y, z, targetLeafId)
        children[childIndex] = updatedChild

        return getOrInsertInternal(children)
    }

    /**
     * Build SVDAG from a 3D grid array of VoxelType.
     */
    fun populateFrom3DArray(grid: Array<Array<Array<VoxelType>>>): Long {
        val startMs = System.currentTimeMillis()
        val sizeX = grid.size
        val sizeY = grid[0].size
        val sizeZ = grid[0][0].size

        rootId = buildSubtreeFromGrid(0, 0, 0, maxDepth, grid, sizeX, sizeY, sizeZ)
        return System.currentTimeMillis() - startMs
    }

    private fun buildSubtreeFromGrid(
        startX: Int, startY: Int, startZ: Int,
        depth: Int,
        grid: Array<Array<Array<VoxelType>>>,
        sizeX: Int, sizeY: Int, sizeZ: Int
    ): Int {
        if (depth == 0) {
            val vType = if (startX < sizeX && startY < sizeY && startZ < sizeZ) {
                grid[startX][startY][startZ]
            } else {
                VoxelType.EMPTY
            }
            return getLeafId(vType)
        }

        val halfSize = 1 shl (depth - 1)
        val children = IntArray(8)

        for (cz in 0..1) {
            for (cy in 0..1) {
                for (cx in 0..1) {
                    val childIdx = (cz shl 2) or (cy shl 1) or cx
                    val nx = startX + cx * halfSize
                    val ny = startY + cy * halfSize
                    val nz = startZ + cz * halfSize

                    // Early prune if out of grid bounds
                    if (nx >= sizeX || ny >= sizeY || nz >= sizeZ) {
                        children[childIdx] = buildEmptyRoot(depth - 1)
                    } else {
                        children[childIdx] = buildSubtreeFromGrid(nx, ny, nz, depth - 1, grid, sizeX, sizeY, sizeZ)
                    }
                }
            }
        }

        return getOrInsertInternal(children)
    }

    /**
     * Fast 3D DDA Voxel Raycast traversing SVDAG.
     * Skips empty octree branches in O(log N) steps.
     */
    fun raycast(
        originX: Double, originY: Double, originZ: Double,
        dirX: Double, dirY: Double, dirZ: Double,
        maxDistance: Double = 64.0
    ): SvdagRaycastResult {
        var currX = originX
        var currY = originY
        var currZ = originZ

        val stepX = if (dirX > 0) 1 else -1
        val stepY = if (dirY > 0) 1 else -1
        val stepZ = if (dirZ > 0) 1 else -1

        val invDirX = if (abs(dirX) > 0.00001) 1.0 / dirX else 1e9
        val invDirY = if (abs(dirY) > 0.00001) 1.0 / dirY else 1e9
        val invDirZ = if (abs(dirZ) > 0.00001) 1.0 / dirZ else 1e9

        var traversedDist = 0.0
        var steps = 0
        val maxSteps = gridSize * 3

        while (traversedDist < maxDistance && steps < maxSteps) {
            steps++
            val vx = currX.toInt()
            val vy = currY.toInt()
            val vz = currZ.toInt()

            if (vx in 0 until gridSize && vy in 0 until gridSize && vz in 0 until gridSize) {
                val voxel = getVoxel(vx, vy, vz)
                if (voxel.isSolid) {
                    return SvdagRaycastResult(
                        hit = true,
                        hitX = currX, hitY = currY, hitZ = currZ,
                        voxelX = vx, voxelY = vy, voxelZ = vz,
                        voxelType = voxel,
                        stepsTaken = steps,
                        distance = traversedDist
                    )
                }
            } else if (traversedDist > 0 && (vx < -2 || vy < -2 || vz < -2 || vx > gridSize + 2 || vy > gridSize + 2 || vz > gridSize + 2)) {
                break
            }

            // Ray march step along smallest distance to next boundary
            val nextX = if (stepX > 0) (vx + 1.0 - currX) * invDirX else (currX - vx) * abs(invDirX)
            val nextY = if (stepY > 0) (vy + 1.0 - currY) * invDirY else (currY - vy) * abs(invDirY)
            val nextZ = if (stepZ > 0) (vz + 1.0 - currZ) * invDirZ else (currZ - vz) * abs(invDirZ)

            val minDelta = minOf(nextX.coerceAtLeast(0.01), nextY.coerceAtLeast(0.01), nextZ.coerceAtLeast(0.01))
            currX += dirX * minDelta
            currY += dirY * minDelta
            currZ += dirZ * minDelta
            traversedDist += minDelta
        }

        return SvdagRaycastResult(
            hit = false,
            hitX = currX, hitY = currY, hitZ = currZ,
            voxelX = currX.toInt(), voxelY = currY.toInt(), voxelZ = currZ.toInt(),
            voxelType = VoxelType.EMPTY,
            stepsTaken = steps,
            distance = traversedDist
        )
    }

    /**
     * DDA Voxel Raycast traversing SVDAG with a requested Level of Detail (LOD).
     */
    fun raycastLOD(
        originX: Double, originY: Double, originZ: Double,
        dirX: Double, dirY: Double, dirZ: Double,
        maxDistance: Double = 64.0,
        lod: Int = 0
    ): SvdagRaycastResult {
        if (lod <= 0) return raycast(originX, originY, originZ, dirX, dirY, dirZ, maxDistance)

        val stepSize = (1 shl lod).toDouble()
        var currX = originX
        var currY = originY
        var currZ = originZ

        val invDirX = if (abs(dirX) > 0.00001) 1.0 / dirX else 1e9
        val invDirY = if (abs(dirY) > 0.00001) 1.0 / dirY else 1e9
        val invDirZ = if (abs(dirZ) > 0.00001) 1.0 / dirZ else 1e9

        var traversedDist = 0.0
        var steps = 0
        val maxSteps = (gridSize / stepSize.toInt()).coerceAtLeast(1) * 3

        while (traversedDist < maxDistance && steps < maxSteps) {
            steps++
            val vx = currX.toInt()
            val vy = currY.toInt()
            val vz = currZ.toInt()

            if (vx in 0 until gridSize && vy in 0 until gridSize && vz in 0 until gridSize) {
                val voxel = getVoxelAtLod(vx, vy, vz, lod)
                if (voxel.isSolid) {
                    return SvdagRaycastResult(
                        hit = true,
                        hitX = currX, hitY = currY, hitZ = currZ,
                        voxelX = vx, voxelY = vy, voxelZ = vz,
                        voxelType = voxel,
                        stepsTaken = steps,
                        distance = traversedDist
                    )
                }
            } else if (traversedDist > 0 && (vx < -2 || vy < -2 || vz < -2 || vx > gridSize + 2 || vy > gridSize + 2 || vz > gridSize + 2)) {
                break
            }

            val stepMargin = stepSize
            val nextX = if (dirX > 0) ((vx / stepSize.toInt() + 1) * stepSize - currX) * invDirX else (currX - (vx / stepSize.toInt()) * stepSize) * abs(invDirX)
            val nextY = if (dirY > 0) ((vy / stepSize.toInt() + 1) * stepSize - currY) * invDirY else (currY - (vy / stepSize.toInt()) * stepSize) * abs(invDirY)
            val nextZ = if (dirZ > 0) ((vz / stepSize.toInt() + 1) * stepSize - currZ) * invDirZ else (currZ - (vz / stepSize.toInt()) * stepSize) * abs(invDirZ)

            val minDelta = minOf(nextX.coerceAtLeast(0.01), nextY.coerceAtLeast(0.01), nextZ.coerceAtLeast(0.01))
            currX += dirX * minDelta
            currY += dirY * minDelta
            currZ += dirZ * minDelta
            traversedDist += minDelta
        }

        return SvdagRaycastResult(
            hit = false,
            hitX = currX, hitY = currY, hitZ = currZ,
            voxelX = currX.toInt(), voxelY = currY.toInt(), voxelZ = currZ.toInt(),
            voxelType = VoxelType.EMPTY,
            stepsTaken = steps,
            distance = traversedDist
        )
    }

    /**
     * Compute current DAG node pool statistics & compression metrics.
     */
    fun getStats(buildTimeMs: Long = 0, raycastMicros: Double = 0.0, lodLevel: Int = 0): SvdagStats {
        var leaves = 0
        var internals = 0
        for (node in nodesList) {
            if (node is SvdagNode.LeafNode) leaves++
            else if (node is SvdagNode.InternalNode) internals++
        }

        val rawBytes = totalVoxelVolume * 1L // 1 byte per voxel in dense representation
        // Internal node has IntArray(8) (32 bytes) + object header (~24 bytes) = ~56 bytes
        val dagBytes = (leaves * 16L) + (internals * 56L)
        val compression = if (rawBytes > 0) (1.0f - (dagBytes.toFloat() / rawBytes.toFloat())) * 100.0f else 0f

        val cellSize = 1 shl lodLevel

        return SvdagStats(
            maxDepth = maxDepth,
            gridSize = gridSize,
            totalVoxels = totalVoxelVolume,
            totalNodes = nodesList.size,
            leafCount = leaves,
            internalCount = internals,
            rawMemoryBytes = rawBytes,
            dagMemoryBytes = dagBytes,
            compressionRatio = compression.coerceIn(0f, 99.99f),
            buildTimeMs = buildTimeMs,
            averageRaycastMicros = raycastMicros,
            currentLodLevel = lodLevel,
            lodCellSize = cellSize
        )
    }

    fun getNodeCount(): Int = nodesList.size
}
