package com.example.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.svdag.*
import com.example.ui.theme.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SvdagWorldInspectorScreen(
    currentDag: SparseVoxelDag,
    currentStats: SvdagStats,
    worldState: SvdagWorldState? = null,
    useFpeRender: Boolean = true,
    scanSummary: SvdagScanSummary? = null,
    scanRippleState: SvdagRippleState? = null,
    iceEntities: List<IceEntity> = emptyList(),
    playerPos: Triple<Int, Int, Int> = Triple(2, 2, 3),
    playerHideStatus: PlayerHideStatus? = null,
    multiFloorLevel: com.example.data.MultiFloorGridLevel? = null,
    activeFloorIndex: Int = 0,
    onSelectFloor: ((Int) -> Unit)? = null,
    onUseConnector: ((com.example.data.VerticalConnector) -> Unit)? = null,
    onRegenerateMultiFloorLevel: ((Int) -> Unit)? = null,
    onTriggerScan: ((ox: Int, oy: Int, oz: Int, radius: Int) -> Unit)? = null,
    onTickIceAI: (() -> Unit)? = null,
    onMovePlayer: ((dx: Int, dy: Int, dz: Int) -> Unit)? = null,
    onRegenerateDag: (targetDepth: Int, seed: Long) -> Unit,
    onModifyVoxel: (x: Int, y: Int, z: Int, type: VoxelType) -> Unit,
    onBackToGame: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var showMultiFloorInspector by remember { mutableStateOf(false) }
    var selectedDepth by remember(currentDag) { mutableStateOf(currentDag.maxDepth) }
    var selectedLodLevel by remember { mutableIntStateOf(currentStats.currentLodLevel) }
    var sliceAxis by remember { mutableStateOf("XY") } // "XY", "XZ", "YZ"
    var selectedZ by remember(currentDag) { mutableStateOf(currentDag.gridSize / 2) }
    var selectedVoxelType by remember { mutableStateOf(VoxelType.SOLID_WALL) }
    var hoveredVoxelInfo by remember { mutableStateOf<String?>(null) }
    var fpeRenderEnabled by remember { mutableStateOf(useFpeRender) }

    // FPE §6 probabilistic collapse: a read-only field over the same world state, plus a
    // slowly-animated observer phase so decayed voxels shimmer/materialise over time.
    val probField = remember(worldState) {
        worldState?.let { FpeProbabilityField(it, seed = 0xC0FFEE5EEDL) }
    }
    var collapsePhase by remember { mutableLongStateOf(0L) }
    LaunchedEffect(probField, fpeRenderEnabled) {
        while (fpeRenderEnabled && probField != null) {
            collapsePhase++
            kotlinx.coroutines.delay(140L)
        }
    }

    // Scanner Ripple Animation Loop
    var rippleAnimProgress by remember { mutableFloatStateOf(0f) }
    var isRipplingActive by remember { mutableStateOf(false) }

    LaunchedEffect(scanRippleState?.scanTimestamp) {
        if (scanRippleState != null) {
            val startTime = System.currentTimeMillis()
            val duration = 2500f
            isRipplingActive = true
            while (isRipplingActive) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= duration) {
                    rippleAnimProgress = 1f
                    isRipplingActive = false
                    break
                }
                rippleAnimProgress = elapsed / duration
                kotlinx.coroutines.delay(16)
            }
        }
    }

    // Raycast Simulator State
    var raycastOriginX by remember(currentDag) { mutableStateOf((currentDag.gridSize / 2).toDouble()) }
    var raycastOriginY by remember(currentDag) { mutableStateOf((currentDag.gridSize / 2).toDouble()) }
    var raycastOriginZ by remember(currentDag) { mutableStateOf(1.0) }
    var raycastDirX by remember { mutableStateOf(1.0) }
    var raycastDirY by remember { mutableStateOf(0.5) }
    var raycastDirZ by remember { mutableStateOf(0.2) }
    var raycastResult by remember { mutableStateOf<SvdagRaycastResult?>(null) }

    val scrollState = rememberScrollState()

    // Color definitions
    val cyberBg = Color(0xFF030712)
    val cyberCardBg = Color(0xFF0F172A)
    val cyberCyan = Color(0xFF00FFCC)
    val cyberBlue = Color(0xFF3B82F6)
    val cyberPurple = Color(0xFFA855F7)
    val cyberGold = Color(0xFFF59E0B)
    val cyberGreen = Color(0xFF10B981)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(cyberBg)
            .padding(12.dp)
            .verticalScroll(scrollState)
    ) {
        // --- 1. Header Bar ---
        Card(
            colors = CardDefaults.cardColors(containerColor = cyberCardBg),
            border = BorderStroke(1.dp, cyberCyan.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onBackToGame()
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("svdag_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = cyberCyan
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "SPARSE VOXEL DAG ENGINE",
                            color = cyberCyan,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "High-Scale Cyberspace World Builder • O(log N) Deduplication",
                            color = Color.LightGray.copy(alpha = 0.8f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Surface(
                    color = cyberCyan.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, cyberCyan)
                ) {
                    Text(
                        text = "${currentDag.gridSize}³ VOXELS",
                        color = cyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // --- 1B. MULTI-FLOOR REACHABLE LEVEL GENERATOR CARD ---
        Card(
            colors = CardDefaults.cardColors(containerColor = cyberCardBg),
            border = BorderStroke(1.dp, cyberCyan.copy(alpha = 0.8f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🌐 PROCEDURAL MULTI-FLOOR LEVEL SYSTEM",
                            color = cyberCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (multiFloorLevel != null)
                                "${multiFloorLevel.sectorName} • ${multiFloorLevel.floors.size} Floors • 100% Guaranteed Reachability"
                            else "Grid-Based Multi-Floor Cyberpunk World Generator",
                            color = Color.LightGray,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            if (multiFloorLevel == null) {
                                onRegenerateMultiFloorLevel?.invoke(4)
                            }
                            showMultiFloorInspector = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = cyberCyan),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("btn_open_multifloor_inspector")
                    ) {
                        Text(
                            text = if (multiFloorLevel != null) "INSPECT MAP" else "GENERATE",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // --- 2. Scale & Regeneration Control Bar ---
        Card(
            colors = CardDefaults.cardColors(containerColor = cyberCardBg),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "SELECT WORLD SCALE PRESET (SVDAG OCTREE DEPTH):",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        Pair(5, "32³ (32.7K)"),
                        Pair(6, "64³ (262K)"),
                        Pair(7, "128³ (2.09M)"),
                        Pair(8, "256³ (16.7M)")
                    ).forEach { (depth, label) ->
                        val isSelected = selectedDepth == depth
                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                selectedDepth = depth
                                onRegenerateDag(depth, System.currentTimeMillis())
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) cyberCyan else Color(0xFF1E293B)
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("svdag_scale_$depth")
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // --- 2B. LEVEL OF DETAIL (LOD) SYSTEM CONTROL BAR ---
        Card(
            colors = CardDefaults.cardColors(containerColor = cyberCardBg),
            border = BorderStroke(1.dp, cyberGold.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LEVEL OF DETAIL (LOD) HIERARCHY FILTER",
                        color = cyberGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Surface(
                        color = cyberGold.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, cyberGold)
                    ) {
                        Text(
                            text = "LOD $selectedLodLevel (${1 shl selectedLodLevel}³ VOXEL BLOCK)",
                            color = cyberGold,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Pair(0, "LOD 0\n[Full 1x1]"),
                        Pair(1, "LOD 1\n[Coarse 2x2]"),
                        Pair(2, "LOD 2\n[Block 4x4]"),
                        Pair(3, "LOD 3\n[Zone 8x8]")
                    ).forEach { (lod, label) ->
                        val isSelected = selectedLodLevel == lod
                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                selectedLodLevel = lod
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) cyberGold else Color(0xFF1E293B)
                            ),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("svdag_lod_$lod")
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // --- 3. Telemetry & Compression Metrics ---
        Card(
            colors = CardDefaults.cardColors(containerColor = cyberCardBg),
            border = BorderStroke(1.dp, cyberPurple.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DAG COMPRESSION & PERFORMANCE TELEMETRY",
                        color = cyberPurple,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = String.format(Locale.US, "COMPRESSION: %.1f%%", currentStats.compressionRatio),
                        color = cyberGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Compression Gauge Bar
                LinearProgressIndicator(
                    progress = { currentStats.compressionRatio / 100f },
                    color = cyberGreen,
                    trackColor = Color(0xFF334155),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Stats Grid (2x2)
                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricBox(
                        title = "TOTAL VOXEL VOLUME",
                        value = String.format(Locale.US, "%,d", currentStats.totalVoxels),
                        subtext = "Virtual Voxel Cells",
                        color = cyberCyan,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    MetricBox(
                        title = "UNIQUE DAG NODES",
                        value = String.format(Locale.US, "%,d", currentStats.totalNodes),
                        subtext = "${currentStats.internalCount} internal, ${currentStats.leafCount} leaves",
                        color = cyberPurple,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    MetricBox(
                        title = "MEMORY SAVINGS",
                        value = formatBytes(currentStats.dagMemoryBytes),
                        subtext = "vs Dense Raw: ${formatBytes(currentStats.rawMemoryBytes)}",
                        color = cyberGold,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    MetricBox(
                        title = "DDA RAYCAST LATENCY",
                        value = String.format(Locale.US, "%.3f μs", currentStats.averageRaycastMicros),
                        subtext = "O(log N) Octree Skip Speed",
                        color = cyberBlue,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // --- 4. 3D Voxel Slice Inspector & Real-Time Block Placement ---
        Card(
            colors = CardDefaults.cardColors(containerColor = cyberCardBg),
            border = BorderStroke(1.dp, cyberCyan.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "3D SLICE INSPECTOR & VOXEL EDITOR",
                        color = cyberCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    // Axis Toggle
                    Row {
                        listOf("XY", "XZ", "YZ").forEach { axis ->
                            val isSel = sliceAxis == axis
                            Text(
                                text = axis,
                                color = if (isSel) Color.Black else Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSel) cyberCyan else Color(0xFF1E293B))
                                    .clickable { sliceAxis = axis }
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (fpeRenderEnabled) "FPE RENDER: ON" else "FPE RENDER: OFF",
                            color = if (fpeRenderEnabled) Color.Black else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (fpeRenderEnabled) Color(0xFF34D399) else Color(0xFF1E293B))
                                .clickable { fpeRenderEnabled = !fpeRenderEnabled }
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Slice Position Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$sliceAxis SLICE LAYER: $selectedZ / ${currentDag.gridSize - 1}",
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(140.dp)
                    )
                    Slider(
                        value = selectedZ.toFloat(),
                        onValueChange = { selectedZ = it.toInt() },
                        valueRange = 0f..(currentDag.gridSize - 1).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = cyberCyan,
                            activeTrackColor = cyberCyan
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Voxel Tool Selector
                Text(
                    text = "SELECT VOXEL PALETTE TO PLACE/ERASE:",
                    color = Color.Gray,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(VoxelType.entries) { vType ->
                        val isSel = selectedVoxelType == vType
                        Surface(
                            color = if (isSel) Color(vType.colorHex).copy(alpha = 0.4f) else Color(0xFF1E293B),
                            border = BorderStroke(1.dp, if (isSel) Color(vType.colorHex) else Color.Transparent),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable { selectedVoxelType = vType }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color(vType.colorHex), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = vType.displayName,
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2D Canvas Slice Viewer
                val gridDisplaySize = minOf(32, currentDag.gridSize)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF020617))
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(currentDag, selectedZ, sliceAxis, selectedVoxelType, selectedLodLevel) {
                                detectTapGestures { offset ->
                                    val cellWidth = size.width / gridDisplaySize
                                    val cellHeight = size.height / gridDisplaySize
                                    val gx = (offset.x / cellWidth).toInt().coerceIn(0, gridDisplaySize - 1)
                                    val gy = (offset.y / cellHeight).toInt().coerceIn(0, gridDisplaySize - 1)

                                    val (vx, vy, vz) = when (sliceAxis) {
                                        "XY" -> Triple(gx, gy, selectedZ)
                                        "XZ" -> Triple(gx, selectedZ, gy)
                                        else -> Triple(selectedZ, gx, gy)
                                    }

                                    onModifyVoxel(vx, vy, vz, selectedVoxelType)
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                }
                            }
                    ) {
                        val cellW = size.width / gridDisplaySize
                        val cellH = size.height / gridDisplaySize

                        for (y in 0 until gridDisplaySize) {
                            for (x in 0 until gridDisplaySize) {
                                val (vx, vy, vz) = when (sliceAxis) {
                                    "XY" -> Triple(x, y, selectedZ)
                                    "XZ" -> Triple(x, selectedZ, y)
                                    else -> Triple(selectedZ, x, y)
                                }

                                val vType = currentDag.getVoxelAtLod(vx, vy, vz, selectedLodLevel)

                                // FPE §15 language selection + §6 probabilistic collapse.
                                val fpeActive = fpeRenderEnabled && worldState != null
                                val fpeState = if (fpeActive) worldState.stateAt(vx, vy, vz) else null
                                val fpeMode = fpeState?.let { FpeRenderStylist.modeFor(it) }
                                // Solid/robust geometry is always materialised; decayed modes
                                // flicker in and out as the collapse field animates (§6).
                                val materialised = !fpeActive ||
                                    (fpeMode == FpeRenderMode.SOLID) ||
                                    (probField?.isMaterialised(vx, vy, vz, collapsePhase) ?: true)
                                val baseAlpha = fpeState?.let { FpeRenderStylist.opacityFor(it, materialised) } ?: 1f

                                fun gone() = drawRect(
                                    color = Color(0xFF0F172A).copy(alpha = 0.5f),
                                    topLeft = Offset(x * cellW, y * cellH),
                                    size = Size(cellW - 1f, cellH - 1f)
                                )

                                if (materialised) {
                                    when {
                                        // FPE render: VOID collapsed states are treated as gone.
                                        fpeMode == FpeRenderMode.VOID -> gone()

                                        fpeMode == FpeRenderMode.ASCII_DECAY -> drawRect(
                                            color = Color(0xFF34D399).copy(alpha = 0.7f * baseAlpha),
                                            topLeft = Offset(x * cellW, y * cellH),
                                            size = Size(cellW - 1f, cellH - 1f)
                                        )

                                        fpeMode == FpeRenderMode.POINT_CLOUD -> drawRect(
                                            color = Color(vType.colorHex).copy(alpha = 0.45f * baseAlpha),
                                            topLeft = Offset(x * cellW + cellW * 0.15f, y * cellH + cellH * 0.15f),
                                            size = Size(cellW * 0.7f, cellH * 0.7f)
                                        )

                                        vType != VoxelType.EMPTY -> drawRect(
                                            color = Color(vType.colorHex),
                                            topLeft = Offset(x * cellW, y * cellH),
                                            size = Size(cellW - 1f, cellH - 1f)
                                        )

                                        else -> gone()
                                    }
                                } else {
                                    gone()
                                }
                            }
                        }

                        // Draw grid lines
                        for (i in 0..gridDisplaySize) {
                            drawLine(
                                color = Color(0xFF1E293B).copy(alpha = 0.5f),
                                start = Offset(i * cellW, 0f),
                                end = Offset(i * cellW, size.height),
                                strokeWidth = 1f
                            )
                            drawLine(
                                color = Color(0xFF1E293B).copy(alpha = 0.5f),
                                start = Offset(0f, i * cellH),
                                end = Offset(size.width, i * cellH),
                                strokeWidth = 1f
                            )
                        }

                        // --- DRAW SVDAG SCANNER SERVICE HIGHLIGHTS & VISUAL RIPPLE EFFECT ---
                        val items = scanSummary?.items ?: scanRippleState?.detectedItems ?: emptyList()
                        for (item in items) {
                            val inSlice = when (sliceAxis) {
                                "XY" -> item.z == selectedZ
                                "XZ" -> item.y == selectedZ
                                else -> item.x == selectedZ
                            }
                            if (inSlice) {
                                val (gx, gy) = when (sliceAxis) {
                                    "XY" -> Pair(item.x, item.y)
                                    "XZ" -> Pair(item.x, item.z)
                                    else -> Pair(item.y, item.z)
                                }
                                if (gx in 0 until gridDisplaySize && gy in 0 until gridDisplaySize) {
                                    val itemColor = Color(item.colorHex)
                                    // Glowing highlight fill
                                    drawRect(
                                        color = itemColor.copy(alpha = 0.35f),
                                        topLeft = Offset(gx * cellW, gy * cellH),
                                        size = Size(cellW, cellH)
                                    )
                                    // Glowing highlight border
                                    drawRect(
                                        color = itemColor,
                                        topLeft = Offset(gx * cellW, gy * cellH),
                                        size = Size(cellW, cellH),
                                        style = Stroke(width = 2.5f)
                                    )
                                    // Target pulse ring
                                    drawCircle(
                                        color = itemColor.copy(alpha = 0.9f),
                                        radius = cellW * 0.45f,
                                        center = Offset(gx * cellW + cellW / 2f, gy * cellH + cellH / 2f),
                                        style = Stroke(width = 1.5f)
                                    )
                                }
                            }
                        }

                        // Draw expanding sonar wave front if ripple animation is active
                        if (isRipplingActive && scanRippleState != null) {
                            val (oxCanvas, oyCanvas) = when (sliceAxis) {
                                "XY" -> Pair(scanRippleState.scanOriginX, scanRippleState.scanOriginY)
                                "XZ" -> Pair(scanRippleState.scanOriginX, scanRippleState.scanOriginZ)
                                else -> Pair(scanRippleState.scanOriginY, scanRippleState.scanOriginZ)
                            }
                            val originOffset = Offset(
                                (oxCanvas.coerceIn(0f, gridDisplaySize.toFloat())) * cellW,
                                (oyCanvas.coerceIn(0f, gridDisplaySize.toFloat())) * cellH
                            )
                            val currentR = rippleAnimProgress * scanRippleState.maxRadius * cellW
                            val fadeAlpha = (1f - rippleAnimProgress).coerceIn(0f, 1f)

                            drawCircle(
                                color = Color(0xFF00E5FF).copy(alpha = fadeAlpha * 0.8f),
                                radius = currentR,
                                center = originOffset,
                                style = Stroke(width = 4f)
                            )
                            drawCircle(
                                color = Color(0xFF38BDF8).copy(alpha = fadeAlpha * 0.4f),
                                radius = (currentR * 0.7f).coerceAtLeast(0f),
                                center = originOffset,
                                style = Stroke(width = 2f)
                            )
                        }

                        // --- DRAW ACTIVE ICE ENTITIES ON CANVAS ---
                        for (ice in iceEntities) {
                            val inSlice = when (sliceAxis) {
                                "XY" -> ice.z == selectedZ
                                "XZ" -> ice.y == selectedZ
                                else -> ice.x == selectedZ
                            }
                            if (inSlice) {
                                val (ix, iy) = when (sliceAxis) {
                                    "XY" -> Pair(ice.x, ice.y)
                                    "XZ" -> Pair(ice.x, ice.z)
                                    else -> Pair(ice.y, ice.z)
                                }
                                if (ix in 0 until gridDisplaySize && iy in 0 until gridDisplaySize) {
                                    val center = Offset(ix * cellW + cellW / 2f, iy * cellH + cellH / 2f)
                                    val alertColor = Color(ice.alertLevel.colorHex)

                                    // Faint detection radius ring
                                    drawCircle(
                                        color = alertColor.copy(alpha = 0.25f),
                                        radius = ice.type.detectionRadius * cellW,
                                        center = center,
                                        style = Stroke(width = 1.5f)
                                    )
                                    // Pulsing threat node
                                    drawCircle(
                                        color = alertColor.copy(alpha = 0.4f),
                                        radius = cellW * 0.6f,
                                        center = center
                                    )
                                    drawCircle(
                                        color = alertColor,
                                        radius = cellW * 0.35f,
                                        center = center,
                                        style = Stroke(width = 2.5f)
                                    )
                                    // Directional indicator ray
                                    val dirX = when (sliceAxis) {
                                        "XY" -> ice.facingDx
                                        "XZ" -> ice.facingDx
                                        else -> ice.facingDy
                                    }
                                    val dirY = when (sliceAxis) {
                                        "XY" -> ice.facingDy
                                        "XZ" -> ice.facingDz
                                        else -> ice.facingDz
                                    }
                                    val endRay = Offset(center.x + dirX * cellW * 0.8f, center.y + dirY * cellH * 0.8f)
                                    drawLine(
                                        color = alertColor,
                                        start = center,
                                        end = endRay,
                                        strokeWidth = 3f
                                    )
                                }
                            }
                        }

                        // --- DRAW PLAYER POSITION ON CANVAS ---
                        val pInSlice = when (sliceAxis) {
                            "XY" -> playerPos.third == selectedZ
                            "XZ" -> playerPos.second == selectedZ
                            else -> playerPos.first == selectedZ
                        }
                        if (pInSlice) {
                            val (px, py) = when (sliceAxis) {
                                "XY" -> Pair(playerPos.first, playerPos.second)
                                "XZ" -> Pair(playerPos.first, playerPos.third)
                                else -> Pair(playerPos.second, playerPos.third)
                            }
                            if (px in 0 until gridDisplaySize && py in 0 until gridDisplaySize) {
                                val pCenter = Offset(px * cellW + cellW / 2f, py * cellH + cellH / 2f)
                                val isHidden = playerHideStatus?.isHidden == true
                                val pColor = if (isHidden) Color(0xFF10B981) else Color(0xFF00E5FF)

                                drawCircle(
                                    color = pColor.copy(alpha = 0.5f),
                                    radius = cellW * 0.7f,
                                    center = pCenter
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = cellW * 0.4f,
                                    center = pCenter,
                                    style = Stroke(width = 3f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "TAP CANVAS GRID TO INSTANTLY MODIFY VOXEL (SVDAG COPY-ON-WRITE RE-DEDUPLICATION)",
                    color = cyberCyan.copy(alpha = 0.8f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // --- 5. ICE SECURITY PATROL & STEALTH HIDING SYSTEM ---
        Card(
            colors = CardDefaults.cardColors(containerColor = cyberCardBg),
            border = BorderStroke(1.dp, Color(0xFFFF0055).copy(alpha = 0.6f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🛡️ ICE SECURITY PATROL & STEALTH AI",
                        color = Color(0xFFFF0055),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    val isHidden = playerHideStatus?.isHidden == true
                    Surface(
                        color = if (isHidden) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFFF0055).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, if (isHidden) Color(0xFF10B981) else Color(0xFFFF0055))
                    ) {
                        Text(
                            text = if (isHidden) "🟢 STEALTH CONCEALED" else "🚨 EXPOSED IN HALLWAY",
                            color = if (isHidden) Color(0xFF10B981) else Color(0xFFFF0055),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Player Hide Reason Subtext
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = playerHideStatus?.hideReason ?: "🚨 Player exposed in hallway corridor. ICE patrols will initiate A* pathfinding pursuit if in line of sight!",
                        color = if (playerHideStatus?.isHidden == true) Color(0xFF10B981) else Color(0xFFF87171),
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Player Stepping Controls
                Text(
                    text = "RUNNER MOVEMENT CONTROLS (POS: (${playerPos.first}, ${playerPos.second}, ${playerPos.third}))",
                    color = cyberCyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onMovePlayer?.invoke(-1, 0, 0)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f).testTag("svdag_move_x_neg")
                    ) {
                        Text("◄ X-", fontSize = 9.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                    }
                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onMovePlayer?.invoke(1, 0, 0)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f).testTag("svdag_move_x_pos")
                    ) {
                        Text("► X+", fontSize = 9.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                    }
                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onMovePlayer?.invoke(0, -1, 0)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f).testTag("svdag_move_y_neg")
                    ) {
                        Text("▲ Y-", fontSize = 9.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                    }
                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onMovePlayer?.invoke(0, 1, 0)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f).testTag("svdag_move_y_pos")
                    ) {
                        Text("▼ Y+", fontSize = 9.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                    }
                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onMovePlayer?.invoke(0, 0, -1)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f).testTag("svdag_move_z_neg")
                    ) {
                        Text("▲ Z-", fontSize = 9.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                    }
                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onMovePlayer?.invoke(0, 0, 1)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f).testTag("svdag_move_z_pos")
                    ) {
                        Text("▼ Z+", fontSize = 9.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ICE AI Tick Trigger
                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onTickIceAI?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0055)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("svdag_tick_ice_ai")
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Tick ICE", tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "EXECUTE ICE PATHFINDING AI TICK (A* HALLWAY SWEEP)",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // List of Active ICE Entities
                Text(
                    text = "ACTIVE ICE PATROL UNITS (${iceEntities.size})",
                    color = cyberGold,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    iceEntities.forEach { ice ->
                        Surface(
                            color = Color(0xFF0F172A),
                            border = BorderStroke(1.dp, Color(ice.alertLevel.colorHex)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${ice.name} [${ice.id}]",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Surface(
                                        color = Color(ice.alertLevel.colorHex).copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = ice.alertLevel.label,
                                            color = Color(ice.alertLevel.colorHex),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Position: (${ice.x}, ${ice.y}, ${ice.z}) | Facing: (${ice.facingDx}, ${ice.facingDy}, ${ice.facingDz}) | Speed: ${ice.type.speed} | Radius: ${ice.type.detectionRadius}",
                                    color = Color.LightGray,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 6. SVDAG Scanner Service & Visual Ripple Highlights ---
        Card(
            colors = CardDefaults.cardColors(containerColor = cyberCardBg),
            border = BorderStroke(1.dp, cyberCyan.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📡 SVDAG SCANNER SERVICE",
                        color = cyberCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Surface(
                        color = cyberCyan.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, cyberCyan)
                    ) {
                        Text(
                            text = "RIPPLE SONAR ACTIVE",
                            color = cyberCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onTriggerScan?.invoke(currentDag.gridSize / 2, currentDag.gridSize / 2, selectedZ, 16)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = cyberCyan),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("svdag_execute_scanner_service")
                ) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Scan", tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "EXECUTE SVDAG SPATIAL SCANNER (RIPPLE EFFECT)",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                scanSummary?.let { sum ->
                    Spacer(modifier = Modifier.height(10.dp))

                    // 3 Category Badge Counters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("INTERACTIVE", color = Color(0xFF00E5FF), fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Text("${sum.interactiveCount}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }

                        Surface(
                            color = Color(0xFFFFD700).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFFFFD700)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("SECRETS", color = Color(0xFFFFD700), fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Text("${sum.secretCount}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }

                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFF10B981)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("BYPASS PATHS", color = Color(0xFF10B981), fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Text("${sum.alternativePathCount}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }

                        Surface(
                            color = Color(0xFFFF0055).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFFFF0055)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("ICE THREATS", color = Color(0xFFFF0055), fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                Text("${sum.iceCount}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "DETECTED OBJECTS & SECRETS (${sum.totalDetected}):",
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (sum.items.isEmpty()) {
                            Text(
                                text = "No interactive objects or secrets within scan radius.",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            sum.items.take(12).forEach { item ->
                                val catColor = Color(item.colorHex)
                                Surface(
                                    color = Color(0xFF020617),
                                    border = BorderStroke(1.dp, catColor.copy(alpha = 0.6f)),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = item.displayName,
                                                    color = catColor,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "[${item.category.displayName}]",
                                                    color = Color.Gray,
                                                    fontSize = 8.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            Text(
                                                text = item.description,
                                                color = Color.LightGray,
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        Text(
                                            text = "(${item.x}, ${item.y}, ${item.z})",
                                            color = catColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 6. Interactive DDA Raycast Simulator ---
        Card(
            colors = CardDefaults.cardColors(containerColor = cyberCardBg),
            border = BorderStroke(1.dp, cyberBlue.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "O(log N) OCTREE DDA RAYCAST TESTER",
                    color = cyberBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        raycastResult = currentDag.raycastLOD(
                            raycastOriginX, raycastOriginY, raycastOriginZ,
                            raycastDirX, raycastDirY, raycastDirZ,
                            maxDistance = currentDag.gridSize.toDouble(),
                            lod = selectedLodLevel
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = cyberBlue),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("svdag_fire_raycast")
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Fire Ray")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "EXECUTE SVDAG RAYCAST TEST (LOD $selectedLodLevel)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                raycastResult?.let { res ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFF020617),
                        border = BorderStroke(1.dp, if (res.hit) cyberGreen else cyberGold),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = if (res.hit) "RAYCAST HIT SOLID VOXEL!" else "RAY EXITED SVDAG VOLUME",
                                color = if (res.hit) cyberGreen else cyberGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Hit Type: ${res.voxelType.displayName} | Coords: (${res.voxelX}, ${res.voxelY}, ${res.voxelZ})",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = String.format(Locale.US, "Steps Taken: %d | Ray Distance: %.2f voxels", res.stepsTaken, res.distance),
                                color = Color.LightGray,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMultiFloorInspector) {
        MultiFloorLevelInspectorOverlay(
            multiFloorLevel = multiFloorLevel,
            activeFloorIndex = activeFloorIndex,
            playerX = playerPos.first,
            playerY = playerPos.second,
            onSelectFloor = { onSelectFloor?.invoke(it) },
            onUseConnector = { onUseConnector?.invoke(it) },
            onRegenerateLevel = { onRegenerateMultiFloorLevel?.invoke(it) },
            onDismiss = { showMultiFloorInspector = false }
        )
    }
}

@Composable
private fun MetricBox(
    title: String,
    value: String,
    subtext: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF020617),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = title,
                color = color.copy(alpha = 0.8f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = value,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 2.dp)
            )
            Text(
                text = subtext,
                color = Color.Gray,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    }
}
