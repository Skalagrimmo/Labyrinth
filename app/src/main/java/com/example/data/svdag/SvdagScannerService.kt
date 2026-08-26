package com.example.data.svdag

import kotlin.math.sqrt

/**
 * Scan Categories for SVDAG Scanner Service.
 */
enum class SvdagScanCategory(
    val displayName: String,
    val colorHex: Long,
    val description: String
) {
    INTERACTIVE(
        displayName = "Interactive Object",
        colorHex = 0xFF00E5FF, // Cyber Cyan
        description = "Security terminals, data cores, access hubs, portals, and stealth caches."
    ),
    SECRET(
        displayName = "Classified Secret",
        colorHex = 0xFFFFD700, // Gold / Amber
        description = "Phase-matrix illusory walls, hidden chambers, and encrypted loot vaults."
    ),
    ALTERNATIVE_PATH(
        displayName = "Bypass / Sub-Conduit",
        colorHex = 0xFF10B981, // Cyber Emerald / Teal
        description = "Ventilation service ducts, alternative conduits, elevators, and staircases."
    ),
    HAZARD_ICE(
        displayName = "ICE Security Threat",
        colorHex = 0xFFFF0055, // Cyber Crimson / Red
        description = "Active Intrusion Countermeasures Electronics patrolling SVDAG hallways."
    )
}

/**
 * Represents an individual interactive item, secret, or alternative path detected within the SVDAG environment.
 */
data class SvdagDetectedItem(
    val x: Int,
    val y: Int,
    val z: Int,
    val voxelType: VoxelType,
    val category: SvdagScanCategory,
    val distanceFromOrigin: Double,
    val displayName: String,
    val description: String,
    val colorHex: Long
)

/**
 * Animated visual ripple wave state triggering propagating pulses across detected items.
 */
data class SvdagRippleState(
    val isRippling: Boolean,
    val scanOriginX: Float,
    val scanOriginY: Float,
    val scanOriginZ: Float,
    val maxRadius: Float,
    val currentRadius: Float,
    val rippleProgress: Float, // 0.0f .. 1.0f
    val pulseAlpha: Float,
    val detectedItems: List<SvdagDetectedItem>,
    val scanTimestamp: Long
)

/**
 * Summary outcome of a completed SVDAG volume scan.
 */
data class SvdagScanSummary(
    val totalDetected: Int,
    val interactiveCount: Int,
    val secretCount: Int,
    val alternativePathCount: Int,
    val iceCount: Int = 0,
    val maxRadius: Float,
    val scanDurationMs: Long,
    val items: List<SvdagDetectedItem>
)

/**
 * High-performance Scanner Service for Sparse Voxel DAG environments.
 * Highlights interactive game objects, secrets, and alternative paths,
 * triggering visual ripple wave effects on detected items.
 */
object SvdagScannerService {

    fun categorizeVoxel(type: VoxelType): SvdagScanCategory? {
        return when (type) {
            VoxelType.HACKABLE_TERMINAL,
            VoxelType.DATA_CORE,
            VoxelType.ENCRYPTED_PORTAL,
            VoxelType.SAFE_ZONE,
            VoxelType.SCAN_CACHE -> SvdagScanCategory.INTERACTIVE

            VoxelType.SECRET_WALL,
            VoxelType.LOOT_CACHE -> SvdagScanCategory.SECRET

            VoxelType.ALTERNATIVE_VENT,
            VoxelType.VENT_TUNNEL,
            VoxelType.STAIRS,
            VoxelType.ELEVATOR,
            VoxelType.GRAVITY_SLOPE,
            VoxelType.ELEVATED_BALCONY -> SvdagScanCategory.ALTERNATIVE_PATH

            VoxelType.BLACK_ICE,
            VoxelType.ICE_PATROL -> SvdagScanCategory.HAZARD_ICE

            else -> null
        }
    }

    fun getVoxelDescription(type: VoxelType): String = when (type) {
        VoxelType.HACKABLE_TERMINAL -> "Override-capable security terminal controlling sector barriers."
        VoxelType.DATA_CORE -> "Encrypted data stream node holding sector intelligence & credits."
        VoxelType.ENCRYPTED_PORTAL -> "Sub-sector gate portal requiring high-level security decryption."
        VoxelType.SAFE_ZONE -> "Terminal access point providing temporary ICE immunity."
        VoxelType.SCAN_CACHE -> "Quantum stealth cache packed with rare software packages."
        VoxelType.SECRET_WALL -> "Phase-matrix illusory wall hiding classified alcoves."
        VoxelType.LOOT_CACHE -> "Encrypted vault containing high-tier software & data currency."
        VoxelType.ALTERNATIVE_VENT -> "Sub-conduit ventilation duct for bypassing security gates."
        VoxelType.VENT_TUNNEL -> "Service conduit channel circumventing primary corridors."
        VoxelType.STAIRS -> "Voxel staircase linking vertical sector planes."
        VoxelType.ELEVATOR -> "Elevator column connecting building floor levels."
        VoxelType.GRAVITY_SLOPE -> "Gravity-assisted slope transition route."
        VoxelType.ELEVATED_BALCONY -> "High-altitude balcony overlook with tactical vantage."
        VoxelType.BLACK_ICE -> "Lethal Black-ICE barrier process guarding core data routes."
        VoxelType.ICE_PATROL -> "Active Intrusion Countermeasures Electronics patrolling SVDAG hallway."
        else -> type.displayName
    }

    /**
     * Performs a 3D spatial scan query over the SparseVoxelDag structure
     * identifying interactive game objects, secrets, alternative paths, and active ICE threats.
     */
    fun performSvdagScan(
        dag: SparseVoxelDag,
        originX: Int,
        originY: Int,
        originZ: Int,
        radius: Int = 12,
        activeIceEntities: List<IceEntity> = emptyList()
    ): SvdagScanSummary {
        val startTime = System.currentTimeMillis()
        val detected = mutableListOf<SvdagDetectedItem>()
        val gridSize = dag.gridSize
        val radiusSq = (radius * radius).toDouble()

        val minX = (originX - radius).coerceIn(0, gridSize - 1)
        val maxX = (originX + radius).coerceIn(0, gridSize - 1)
        val minY = (originY - radius).coerceIn(0, gridSize - 1)
        val maxY = (originY + radius).coerceIn(0, gridSize - 1)
        val minZ = (originZ - radius).coerceIn(0, gridSize - 1)
        val maxZ = (originZ + radius).coerceIn(0, gridSize - 1)

        for (z in minZ..maxZ) {
            for (y in minY..maxY) {
                for (x in minX..maxX) {
                    val dx = x - originX
                    val dy = y - originY
                    val dz = z - originZ
                    val distSq = (dx * dx + dy * dy + dz * dz).toDouble()
                    if (distSq <= radiusSq) {
                        val voxel = dag.getVoxel(x, y, z)
                        val cat = categorizeVoxel(voxel)
                        if (cat != null) {
                            val dist = sqrt(distSq)
                            detected.add(
                                SvdagDetectedItem(
                                    x = x,
                                    y = y,
                                    z = z,
                                    voxelType = voxel,
                                    category = cat,
                                    distanceFromOrigin = dist,
                                    displayName = voxel.displayName,
                                    description = getVoxelDescription(voxel),
                                    colorHex = cat.colorHex
                                )
                            )
                        }
                    }
                }
            }
        }

        // Also detect active ICE entities passed from state
        for (ice in activeIceEntities) {
            val dx = ice.x - originX
            val dy = ice.y - originY
            val dz = ice.z - originZ
            val distSq = (dx * dx + dy * dy + dz * dz).toDouble()
            if (distSq <= radiusSq) {
                val dist = sqrt(distSq)
                val statusText = when (ice.alertLevel) {
                    IceAlertLevel.PATROL -> "Patrolling hallway corridor"
                    IceAlertLevel.SUSPICIOUS -> "Searching last known signal"
                    IceAlertLevel.HUNTING -> "🚨 HUNTING PLAYER!"
                }
                detected.add(
                    SvdagDetectedItem(
                        x = ice.x,
                        y = ice.y,
                        z = ice.z,
                        voxelType = VoxelType.ICE_PATROL,
                        category = SvdagScanCategory.HAZARD_ICE,
                        distanceFromOrigin = dist,
                        displayName = "${ice.name} [${ice.id}]",
                        description = "Type: ${ice.type.typeName} | Status: ${ice.alertLevel.label} | $statusText",
                        colorHex = SvdagScanCategory.HAZARD_ICE.colorHex
                    )
                )
            }
        }

        val sortedItems = detected.sortedBy { it.distanceFromOrigin }
        val durationMs = System.currentTimeMillis() - startTime

        return SvdagScanSummary(
            totalDetected = sortedItems.size,
            interactiveCount = sortedItems.count { it.category == SvdagScanCategory.INTERACTIVE },
            secretCount = sortedItems.count { it.category == SvdagScanCategory.SECRET },
            alternativePathCount = sortedItems.count { it.category == SvdagScanCategory.ALTERNATIVE_PATH },
            iceCount = sortedItems.count { it.category == SvdagScanCategory.HAZARD_ICE },
            maxRadius = radius.toFloat(),
            scanDurationMs = durationMs,
            items = sortedItems
        )
    }

    /**
     * Calculates the animated visual ripple wave propagation state at current time.
     */
    fun computeRippleState(
        scanTimestamp: Long,
        currentTimeMs: Long,
        originX: Float,
        originY: Float,
        originZ: Float,
        maxRadius: Float = 12f,
        detectedItems: List<SvdagDetectedItem>,
        rippleDurationMs: Long = 2500L
    ): SvdagRippleState {
        val elapsed = currentTimeMs - scanTimestamp
        if (elapsed < 0 || elapsed > rippleDurationMs) {
            return SvdagRippleState(
                isRippling = false,
                scanOriginX = originX,
                scanOriginY = originY,
                scanOriginZ = originZ,
                maxRadius = maxRadius,
                currentRadius = maxRadius,
                rippleProgress = 1f,
                pulseAlpha = 0f,
                detectedItems = detectedItems,
                scanTimestamp = scanTimestamp
            )
        }

        val progress = elapsed.toFloat() / rippleDurationMs.toFloat()
        val currentRadius = progress * maxRadius
        val pulseAlpha = (1f - progress).coerceIn(0f, 1f)

        return SvdagRippleState(
            isRippling = true,
            scanOriginX = originX,
            scanOriginY = originY,
            scanOriginZ = originZ,
            maxRadius = maxRadius,
            currentRadius = currentRadius,
            rippleProgress = progress,
            pulseAlpha = pulseAlpha,
            detectedItems = detectedItems,
            scanTimestamp = scanTimestamp
        )
    }
}
