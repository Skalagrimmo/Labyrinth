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
    onRegenerateDag: (targetDepth: Int, seed: Long) -> Unit,
    onModifyVoxel: (x: Int, y: Int, z: Int, type: VoxelType) -> Unit,
    onBackToGame: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var selectedDepth by remember(currentDag) { mutableStateOf(currentDag.maxDepth) }
    var sliceAxis by remember { mutableStateOf("XY") } // "XY", "XZ", "YZ"
    var selectedZ by remember(currentDag) { mutableStateOf(currentDag.gridSize / 2) }
    var selectedVoxelType by remember { mutableStateOf(VoxelType.SOLID_WALL) }
    var hoveredVoxelInfo by remember { mutableStateOf<String?>(null) }

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
                    items(VoxelType.values()) { vType ->
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
                            .pointerInput(currentDag, selectedZ, sliceAxis, selectedVoxelType) {
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

                                val vType = currentDag.getVoxel(vx, vy, vz)

                                if (vType != VoxelType.EMPTY) {
                                    drawRect(
                                        color = Color(vType.colorHex),
                                        topLeft = Offset(x * cellW, y * cellH),
                                        size = Size(cellW - 1f, cellH - 1f)
                                    )
                                } else {
                                    drawRect(
                                        color = Color(0xFF0F172A).copy(alpha = 0.5f),
                                        topLeft = Offset(x * cellW, y * cellH),
                                        size = Size(cellW - 1f, cellH - 1f)
                                    )
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

        // --- 5. Interactive DDA Raycast Simulator ---
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
                        raycastResult = currentDag.raycast(
                            raycastOriginX, raycastOriginY, raycastOriginZ,
                            raycastDirX, raycastDirY, raycastDirZ,
                            maxDistance = currentDag.gridSize.toDouble()
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
                        text = "EXECUTE SVDAG RAYCAST TEST",
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
