package com.example.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CellType
import com.example.data.GridFloor
import com.example.data.MultiFloorGridLevel
import com.example.data.MultiFloorNodePosition
import com.example.data.VerticalConnector
import com.example.data.VerticalConnectorType
import com.example.ui.theme.CyberCardBg
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberDark

@Composable
fun MultiFloorLevelInspectorOverlay(
    multiFloorLevel: MultiFloorGridLevel?,
    activeFloorIndex: Int,
    playerX: Int,
    playerY: Int,
    onSelectFloor: (Int) -> Unit,
    onUseConnector: (VerticalConnector) -> Unit,
    onRegenerateLevel: (numFloors: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var showTelemetryDetails by remember { mutableStateOf(false) }
    var selectedFloorTab by remember(activeFloorIndex) { mutableStateOf(activeFloorIndex) }

    val cyberBg = Color(0xFF030712)
    val cyberCardBg = Color(0xFF0F172A)
    val cyberCyan = Color(0xFF00FFCC)
    val cyberGreen = Color(0xFF10B981)
    val cyberAmber = Color(0xFFF59E0B)
    val cyberPink = Color(0xFFEC4899)
    val cyberPurple = Color(0xFFA855F7)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable { /* backdrop click intercept */ },
        contentAlignment = Alignment.Center
    ) {
        val isNarrow = maxWidth < 400.dp
        val isShortHeight = maxHeight < 520.dp

        Card(
            colors = CardDefaults.cardColors(containerColor = cyberCardBg),
            border = BorderStroke(1.5.dp, cyberCyan),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth(if (maxWidth > 720.dp) 0.88f else 0.96f)
                .widthIn(max = 760.dp)
                .fillMaxHeight(if (isShortHeight) 0.98f else 0.92f)
                .heightIn(max = 860.dp)
                .padding(if (isNarrow) 4.dp else 10.dp)
                .testTag("multi_floor_level_overlay")
        ) {
            if (multiFloorLevel == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "🌐 NO MULTI-FLOOR LEVEL ACTIVE",
                        color = cyberCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onRegenerateLevel(4)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = cyberCyan)
                    ) {
                        Text("⚡ GENERATE MULTI-FLOOR SECTOR", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                return@Card
            }

            val currentFloor = multiFloorLevel.getFloor(selectedFloorTab) ?: multiFloorLevel.floors.first()
            val report = multiFloorLevel.reachabilityReport

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isNarrow) 8.dp else 14.dp)
            ) {
                // --- 1. Header Bar ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🏗️ ${multiFloorLevel.sectorName.uppercase()}",
                            color = cyberCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = if (isNarrow) 13.sp else 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${multiFloorLevel.districtTheme.title} • ${multiFloorLevel.floors.size} FLOORS",
                            color = Color.LightGray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = if (isNarrow) 8.sp else 9.5.sp
                        )
                    }

                    // 100% Reachable Status Badge
                    Surface(
                        color = if (report.isFullyReachable) cyberGreen.copy(alpha = 0.2f) else cyberAmber.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, if (report.isFullyReachable) cyberGreen else cyberAmber),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (report.isFullyReachable) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (report.isFullyReachable) cyberGreen else cyberAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (report.isFullyReachable) "100% REACHABLE" else "PARTIAL ACCESS",
                                color = if (report.isFullyReachable) cyberGreen else cyberAmber,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onDismiss()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close",
                            tint = cyberCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xFF334155), thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))

                // --- 2. Floor Selector Tabs ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    multiFloorLevel.floors.forEach { floor ->
                        val isSelected = floor.floorIndex == selectedFloorTab
                        val isCurrentPlayerFloor = floor.floorIndex == activeFloorIndex

                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                selectedFloorTab = floor.floorIndex
                                onSelectFloor(floor.floorIndex)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) cyberCyan else Color(0xFF1E293B)
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isCurrentPlayerFloor) cyberGreen else if (isSelected) cyberCyan else Color(0xFF334155)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = "F${floor.floorIndex}${if (isCurrentPlayerFloor) " 📍" else ""}",
                                color = if (isSelected) Color.Black else Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Regenerate Level Button
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onRegenerateLevel(multiFloorLevel.floors.size)
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("regenerate_multi_floor_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Regenerate Level",
                            tint = cyberCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // --- 3. Active Floor Info Bar ---
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📍 ${currentFloor.floorName}",
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "SECURITY INTENSITY: ${currentFloor.securityLevel}/5",
                            color = cyberAmber,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // --- 4. Main Floor Grid Visualizer & Connectors Drawer ---
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Grid Render Box
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Black),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "FLOOR ${currentFloor.floorIndex} GRID ARCHITECTURE (${currentFloor.width}x${currentFloor.height})",
                                color = cyberCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )

                            // Render 2D Grid Representation
                            FloorGridCanvas(
                                floor = currentFloor,
                                playerX = if (selectedFloorTab == activeFloorIndex) playerX else -1,
                                playerY = if (selectedFloorTab == activeFloorIndex) playerY else -1,
                                spawnPos = multiFloorLevel.spawnPoint,
                                exitPos = multiFloorLevel.exitPoint
                            )
                        }
                    }

                    // --- 5. Vertical Transit Connectors on Floor ---
                    val floorConnectors = multiFloorLevel.verticalConnectors.filter {
                        it.fromFloor == selectedFloorTab || it.toFloor == selectedFloorTab
                    }

                    if (floorConnectors.isNotEmpty()) {
                        Text(
                            text = "🛗 VERTICAL TRANSIT SHAFTS & CONNECTORS ON THIS FLOOR:",
                            color = cyberCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        floorConnectors.forEach { connector ->
                            val isFromCurrent = connector.fromFloor == selectedFloorTab
                            val targetFloor = if (isFromCurrent) connector.toFloor else connector.fromFloor
                            val pos = if (isFromCurrent) connector.fromPos else connector.toPos

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                border = BorderStroke(1.dp, cyberCyan.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = connector.name,
                                            color = Color.White,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Position (${pos.first}, ${pos.second}) • Connects Floor $selectedFloorTab ↔ Floor $targetFloor",
                                            color = Color.LightGray,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 8.5.sp
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                            onUseConnector(connector)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = cyberCyan),
                                        shape = RoundedCornerShape(4.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text(
                                            text = "TRANSIT TO F$targetFloor",
                                            color = Color.Black,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // --- 6. Nodes & Loot Caches on Floor ---
                    val floorNodes = multiFloorLevel.nodes.filter { it.floorIndex == selectedFloorTab }
                    if (floorNodes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "🖥️ CYBER TERMINALS & VAULTS ON FLOOR $selectedFloorTab:",
                            color = cyberPurple,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        floorNodes.forEach { node ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = node.name,
                                            color = cyberCyan,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${node.description} [Pos: (${node.x}, ${node.y})]",
                                            color = Color.Gray,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 8.5.sp
                                        )
                                    }

                                    Surface(
                                        color = if (node.isReachableFromSpawn) cyberGreen.copy(alpha = 0.2f) else cyberPink.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = if (node.isReachableFromSpawn) "REACHABLE" else "ISOLATED",
                                            color = if (node.isReachableFromSpawn) cyberGreen else cyberPink,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // --- 7. Reachability Telemetry Drawer Toggle ---
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTelemetryDetails = !showTelemetryDetails }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = cyberCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "REACHABILITY ALGORITHM TELEMETRY",
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                imageVector = if (showTelemetryDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = cyberCyan
                            )
                        }
                    }

                    AnimatedVisibility(visible = showTelemetryDetails) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Black),
                            border = BorderStroke(1.dp, cyberCyan.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("• Walkable Cells: ${report.reachableCellsCount} / ${report.totalWalkableCells} (${report.reachableRatioPercent.toInt()}%)", color = Color.LightGray, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                                Text("• Reachable Nodes: ${report.reachableNodesCount} / ${report.totalNodes}", color = Color.LightGray, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                                Text("• Auto-Repaired Bridge Paths: ${report.repairedPathsCount}", color = cyberGreen, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                                Text("• Multi-Floor BFS Validation Latency: ${report.validationTimeMs}ms", color = cyberCyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renders active floor grid layout.
 */
@Composable
private fun FloorGridCanvas(
    floor: GridFloor,
    playerX: Int,
    playerY: Int,
    spawnPos: Triple<Int, Int, Int>,
    exitPos: Triple<Int, Int, Int>
) {
    val cyberCyan = Color(0xFF00FFCC)
    val cyberGreen = Color(0xFF10B981)
    val cyberGold = Color(0xFFF59E0B)
    val cyberPink = Color(0xFFEC4899)

    Column(
        verticalArrangement = Arrangement.spacedBy(1.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (y in 0 until floor.height) {
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                for (x in 0 until floor.width) {
                    val cellType = floor.grid[y][x]
                    val isPlayerHere = x == playerX && y == playerY
                    val isSpawnHere = spawnPos.first == floor.floorIndex && spawnPos.second == x && spawnPos.third == y
                    val isExitHere = exitPos.first == floor.floorIndex && exitPos.second == x && exitPos.third == y

                    val cellColor = when {
                        isPlayerHere -> cyberGreen
                        isSpawnHere -> Color(0xFF00E676)
                        isExitHere -> cyberGold
                        cellType == CellType.WALL -> Color(0xFF1E293B)
                        cellType == CellType.ELEVATOR || cellType == CellType.STAIRS_UP || cellType == CellType.STAIRS_DOWN -> cyberCyan
                        cellType == CellType.DATA_STORE || cellType == CellType.SECRET_CACHE -> Color(0xFFA855F7)
                        cellType == CellType.VIRUS_NODE -> cyberPink
                        cellType == CellType.SAFE_ZONE -> Color(0xFF059669)
                        else -> Color(0xFF0F172A)
                    }

                    Box(
                        modifier = Modifier
                            .size(if (floor.width > 16) 14.dp else 18.dp)
                            .background(cellColor, shape = RoundedCornerShape(2.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPlayerHere) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color.White, CircleShape)
                            )
                        } else if (cellType == CellType.ELEVATOR) {
                            Text("E", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
