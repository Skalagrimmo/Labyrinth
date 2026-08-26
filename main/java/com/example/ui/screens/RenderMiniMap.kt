package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.ui.GameViewModel

@Composable
fun RenderMiniMap(uiState: GameViewModel.GameUiState) {
    val maze = uiState.maze
    if (maze.isEmpty()) return

    val px = uiState.gridX
    val py = uiState.gridY
    val dir = uiState.direction

    val rowCount = maze.size
    val colCount = maze[0].size

    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseAlpha"
    )

    val playerPath = remember { Path() }
    val virusPath = remember { Path() }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .fillMaxSize()
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val viewRadius = 7
            val viewGridCount = 15

            // Calculate grid bounds to center it perfectly for 15x15 viewport
            val cellSize = minOf(w / viewGridCount, h / viewGridCount)
            val gridW = cellSize * viewGridCount
            val gridH = cellSize * viewGridCount
            val startX = (w - gridW) / 2f
            val startY = (h - gridH) / 2f

            // Draw grid background subtle lines
            for (col in 0..viewGridCount) {
                val x = startX + col * cellSize
                drawLine(
                    color = Color(0xFF1E293B).copy(alpha = 0.5f),
                    start = Offset(x, startY),
                    end = Offset(x, startY + gridH),
                    strokeWidth = 1f
                )
            }
            for (row in 0..viewGridCount) {
                val y = startY + row * cellSize
                drawLine(
                    color = Color(0xFF1E293B).copy(alpha = 0.5f),
                    start = Offset(startX, y),
                    end = Offset(startX + gridW, y),
                    strokeWidth = 1f
                )
            }

            // Draw cells in the scrolling viewport centered on the player (px, py)
            for (vy in 0 until viewGridCount) {
                for (vx in 0 until viewGridCount) {
                    val mx = px - viewRadius + vx
                    val my = py - viewRadius + vy

                    val cellLeft = startX + vx * cellSize
                    val cellTop = startY + vy * cellSize
                    val isPlayer = (mx == px && my == py)

                    if (isPlayer) {
                        val playerCenter = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)

                        // Draw animated pulsing radar wave
                        drawCircle(
                            color = Color(0xFF00E5FF).copy(alpha = pulseAlpha),
                            radius = cellSize * pulseScale * 1.5f,
                            center = playerCenter
                        )
                        // Draw core glowing radar circle under player
                        drawCircle(
                            color = Color(0xFF00E5FF).copy(alpha = 0.4f),
                            radius = cellSize * 0.7f,
                            center = playerCenter
                        )

                        // Draw player pointer
                        val playerSize = cellSize * 0.7f
                        val dirAngle = when (dir) {
                            Direction.NORTH -> 0f
                            Direction.EAST -> 90f
                            Direction.SOUTH -> 180f
                            Direction.WEST -> 270f
                        }

                        rotate(degrees = dirAngle, pivot = playerCenter) {
                            playerPath.reset()
                            playerPath.moveTo(playerCenter.x, playerCenter.y - playerSize * 0.5f)
                            playerPath.lineTo(playerCenter.x + playerSize * 0.35f, playerCenter.y + playerSize * 0.45f)
                            playerPath.lineTo(playerCenter.x - playerSize * 0.35f, playerCenter.y + playerSize * 0.45f)
                            playerPath.close()
                            drawPath(
                                path = playerPath,
                                color = Color(0xFF00E5FF)
                            )
                        }
                    } else {
                        val insideBounds = mx in 0 until colCount && my in 0 until rowCount
                        val isExplored = insideBounds && uiState.exploredCells.contains(Pair(mx, my))
                        val distSq = (mx - px) * (mx - px) + (my - py) * (my - py)
                        val inActiveRange = insideBounds && (distSq <= 3 * 3 + 1)
                        val isScannedEnemy = insideBounds && uiState.scannedEnemies.contains(Pair(mx, my))
                        val isScannedLoot = insideBounds && uiState.scannedLoot.contains(Pair(mx, my))
                        val isScanActive = uiState.isScanActive || uiState.scanTurnsLeft > 0
                        val isScannedCell = isScanActive && (isScannedEnemy || isScannedLoot)

                        // Outer boundary or unexplored cells are hidden in Fog of War unless revealed by Scan
                        if (!isExplored && !inActiveRange && !isScannedCell) {
                            // Faint mesh dot for unexplored cells
                            drawCircle(
                                color = Color(0xFF0F172A).copy(alpha = 0.3f),
                                radius = 1.5f,
                                center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                            )
                            continue
                        }

                        val alpha = if (inActiveRange || isScannedCell) 1.0f else 0.4f
                        val cell = if (insideBounds) maze[my][mx] else CellType.WALL

                        when (cell) {
                            CellType.WALL -> {
                                drawRoundRect(
                                    color = Color(0xFF334155).copy(alpha = alpha * 0.8f),
                                    topLeft = Offset(cellLeft + cellSize * 0.1f, cellTop + cellSize * 0.1f),
                                    size = Size(cellSize * 0.8f, cellSize * 0.8f),
                                    cornerRadius = CornerRadius(4f, 4f)
                                )
                                drawRoundRect(
                                    color = Color(0xFF475569).copy(alpha = alpha),
                                    topLeft = Offset(cellLeft + cellSize * 0.1f, cellTop + cellSize * 0.1f),
                                    size = Size(cellSize * 0.8f, cellSize * 0.8f),
                                    cornerRadius = CornerRadius(4f, 4f),
                                    style = Stroke(width = 2f)
                                )
                            }
                            CellType.DATA_STORE -> {
                                val center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                drawCircle(
                                    color = Color(0xFFFBBF24).copy(alpha = alpha * 0.25f),
                                    radius = cellSize * 0.45f,
                                    center = center
                                )
                                drawCircle(
                                    color = Color(0xFFFBBF24).copy(alpha = alpha),
                                    radius = cellSize * 0.22f,
                                    center = center
                                )
                                drawCircle(
                                    color = Color(0xFFFBBF24).copy(alpha = alpha),
                                    radius = cellSize * 0.38f,
                                    center = center,
                                    style = Stroke(width = 2f)
                                )
                            }
                            CellType.ENCRYPTED_PORTAL -> {
                                val center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                drawCircle(
                                    color = Color(0xFFC084FC).copy(alpha = alpha * 0.25f),
                                    radius = cellSize * 0.45f,
                                    center = center
                                )
                                drawCircle(
                                    color = Color(0xFFC084FC).copy(alpha = alpha),
                                    radius = cellSize * 0.35f,
                                    center = center,
                                    style = Stroke(width = 3f)
                                )
                                drawCircle(
                                    color = Color(0xFFC084FC).copy(alpha = alpha),
                                    radius = cellSize * 0.15f,
                                    center = center
                                )
                            }
                             CellType.VIRUS_NODE -> {
                                val center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                val rad = cellSize * 0.35f

                                drawCircle(
                                    color = Color(0xFFF43F5E).copy(alpha = alpha * 0.25f),
                                    radius = cellSize * 0.45f,
                                    center = center
                                )

                                virusPath.reset()
                                virusPath.moveTo(center.x, center.y - rad)
                                virusPath.lineTo(center.x + rad, center.y)
                                virusPath.lineTo(center.x, center.y + rad)
                                virusPath.lineTo(center.x - rad, center.y)
                                virusPath.close()
                                drawPath(path = virusPath, color = Color(0xFFF43F5E).copy(alpha = alpha))
                            }
                            CellType.SAFE_ZONE -> {
                                val center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                drawCircle(
                                    color = Color(0xFF10B981).copy(alpha = alpha * 0.25f),
                                    radius = cellSize * 0.45f,
                                    center = center
                                )
                                drawCircle(
                                    color = Color(0xFF10B981).copy(alpha = alpha),
                                    radius = cellSize * 0.3f,
                                    center = center,
                                    style = Stroke(width = 3f)
                                )
                                drawLine(
                                    color = Color(0xFF10B981).copy(alpha = alpha),
                                    start = Offset(center.x - cellSize * 0.15f, center.y),
                                    end = Offset(center.x + cellSize * 0.15f, center.y),
                                    strokeWidth = 3f
                                )
                                drawLine(
                                    color = Color(0xFF10B981).copy(alpha = alpha),
                                    start = Offset(center.x, center.y - cellSize * 0.15f),
                                    end = Offset(center.x, center.y + cellSize * 0.15f),
                                    strokeWidth = 3f
                                )
                            }
                            CellType.SECRET_WALL -> {
                                if (isScanActive || isScannedCell) {
                                    val center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                    drawRoundRect(
                                        color = Color(0xFF06B6D4).copy(alpha = alpha * 0.3f),
                                        topLeft = Offset(cellLeft + cellSize * 0.1f, cellTop + cellSize * 0.1f),
                                        size = Size(cellSize * 0.8f, cellSize * 0.8f),
                                        cornerRadius = CornerRadius(4f, 4f)
                                    )
                                    drawRoundRect(
                                        color = Color(0xFF22D3EE).copy(alpha = alpha),
                                        topLeft = Offset(cellLeft + cellSize * 0.1f, cellTop + cellSize * 0.1f),
                                        size = Size(cellSize * 0.8f, cellSize * 0.8f),
                                        cornerRadius = CornerRadius(4f, 4f),
                                        style = Stroke(width = 2f)
                                    )
                                } else {
                                    drawRoundRect(
                                        color = Color(0xFF334155).copy(alpha = alpha * 0.8f),
                                        topLeft = Offset(cellLeft + cellSize * 0.1f, cellTop + cellSize * 0.1f),
                                        size = Size(cellSize * 0.8f, cellSize * 0.8f),
                                        cornerRadius = CornerRadius(4f, 4f)
                                    )
                                }
                            }
                            CellType.HACKABLE_TERMINAL -> {
                                val center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                drawCircle(
                                    color = Color(0xFF00E5FF).copy(alpha = alpha * 0.25f),
                                    radius = cellSize * 0.45f,
                                    center = center
                                )
                                drawCircle(
                                    color = Color(0xFF00E5FF).copy(alpha = alpha),
                                    radius = cellSize * 0.35f,
                                    center = center,
                                    style = Stroke(width = 2.5f)
                                )
                            }
                            CellType.TERMINAL_DOOR -> {
                                drawRect(
                                    color = Color(0xFFEF4444).copy(alpha = alpha * 0.3f),
                                    topLeft = Offset(cellLeft + cellSize * 0.15f, cellTop + cellSize * 0.15f),
                                    size = Size(cellSize * 0.7f, cellSize * 0.7f)
                                )
                                drawRect(
                                    color = Color(0xFFF87171).copy(alpha = alpha),
                                    topLeft = Offset(cellLeft + cellSize * 0.15f, cellTop + cellSize * 0.15f),
                                    size = Size(cellSize * 0.7f, cellSize * 0.7f),
                                    style = Stroke(width = 3f)
                                )
                            }
                            CellType.SCAN_CACHE -> {
                                val center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                drawCircle(
                                    color = Color(0xFFF59E0B).copy(alpha = alpha * 0.35f),
                                    radius = cellSize * 0.45f,
                                    center = center
                                )
                                drawCircle(
                                    color = Color(0xFFFBBF24).copy(alpha = alpha),
                                    radius = cellSize * 0.28f,
                                    center = center,
                                    style = Stroke(width = 3f)
                                )
                            }
                            CellType.ALTERNATIVE_VENT -> {
                                drawRect(
                                    color = Color(0xFF14B8A6).copy(alpha = alpha * 0.2f),
                                    topLeft = Offset(cellLeft + cellSize * 0.2f, cellTop + cellSize * 0.2f),
                                    size = Size(cellSize * 0.6f, cellSize * 0.6f)
                                )
                                drawRect(
                                    color = Color(0xFF2DD4BF).copy(alpha = alpha),
                                    topLeft = Offset(cellLeft + cellSize * 0.2f, cellTop + cellSize * 0.2f),
                                    size = Size(cellSize * 0.6f, cellSize * 0.6f),
                                    style = Stroke(width = 2f)
                                )
                            }
                            CellType.SECRET_CACHE -> {
                                val center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                val rad = cellSize * 0.35f
                                drawCircle(
                                    color = Color(0xFF38BDF8).copy(alpha = alpha * 0.25f),
                                    radius = cellSize * 0.45f,
                                    center = center
                                )
                                drawRect(
                                    color = Color(0xFF38BDF8).copy(alpha = alpha),
                                    topLeft = Offset(center.x - rad * 0.7f, center.y - rad * 0.7f),
                                    size = Size(rad * 1.4f, rad * 1.4f),
                                    style = Stroke(width = 3f)
                                )
                                drawCircle(
                                    color = Color(0xFF38BDF8).copy(alpha = alpha),
                                    radius = cellSize * 0.12f,
                                    center = center
                                )
                            }
                            CellType.GRAND_HALL -> {
                                val center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                drawRect(
                                    color = Color(0xFF6366F1).copy(alpha = alpha * 0.15f),
                                    topLeft = Offset(cellLeft + cellSize * 0.15f, cellTop + cellSize * 0.15f),
                                    size = Size(cellSize * 0.7f, cellSize * 0.7f)
                                )
                                drawRect(
                                    color = Color(0xFF6366F1).copy(alpha = alpha),
                                    topLeft = Offset(cellLeft + cellSize * 0.15f, cellTop + cellSize * 0.15f),
                                    size = Size(cellSize * 0.7f, cellSize * 0.7f),
                                    style = Stroke(width = 2f)
                                )
                                drawCircle(Color(0xFF818CF8).copy(alpha = alpha), 1.5f, Offset(cellLeft + cellSize * 0.3f, cellTop + cellSize * 0.3f))
                                drawCircle(Color(0xFF818CF8).copy(alpha = alpha), 1.5f, Offset(cellLeft + cellSize * 0.7f, cellTop + cellSize * 0.3f))
                                drawCircle(Color(0xFF818CF8).copy(alpha = alpha), 1.5f, Offset(cellLeft + cellSize * 0.3f, cellTop + cellSize * 0.7f))
                                drawCircle(Color(0xFF818CF8).copy(alpha = alpha), 1.5f, Offset(cellLeft + cellSize * 0.7f, cellTop + cellSize * 0.7f))
                            }
                            CellType.DOME_CHAMBER -> {
                                val center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                drawCircle(
                                    color = Color(0xFF14B8A6).copy(alpha = alpha * 0.2f),
                                    radius = cellSize * 0.4f,
                                    center = center
                                )
                                drawCircle(
                                    color = Color(0xFF14B8A6).copy(alpha = alpha),
                                    radius = cellSize * 0.4f,
                                    center = center,
                                    style = Stroke(width = 2f)
                                )
                                drawCircle(
                                    color = Color(0xFF2DD4BF).copy(alpha = alpha),
                                    radius = cellSize * 0.2f,
                                    center = center,
                                    style = Stroke(width = 1f)
                                )
                            }
                            CellType.VENT_TUNNEL -> {
                                drawRect(
                                    color = Color(0xFFF59E0B).copy(alpha = alpha * 0.25f),
                                    topLeft = Offset(cellLeft + cellSize * 0.3f, cellTop + cellSize * 0.3f),
                                    size = Size(cellSize * 0.4f, cellSize * 0.4f)
                                )
                                drawRect(
                                    color = Color(0xFFF59E0B).copy(alpha = alpha),
                                    topLeft = Offset(cellLeft + cellSize * 0.3f, cellTop + cellSize * 0.3f),
                                    size = Size(cellSize * 0.4f, cellSize * 0.4f),
                                    style = Stroke(width = 2f)
                                )
                            }
                            CellType.ELEVATED_BALCONY -> {
                                val center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                drawRect(
                                    color = Color(0xFFEC4899).copy(alpha = alpha * 0.15f),
                                    topLeft = Offset(cellLeft + cellSize * 0.1f, cellTop + cellSize * 0.1f),
                                    size = Size(cellSize * 0.8f, cellSize * 0.8f)
                                )
                                drawLine(
                                    color = Color(0xFFEC4899).copy(alpha = alpha),
                                    start = Offset(cellLeft + cellSize * 0.1f, cellTop + cellSize * 0.8f),
                                    end = Offset(cellLeft + cellSize * 0.9f, cellTop + cellSize * 0.8f),
                                    strokeWidth = 3f
                                )
                            }
                            CellType.STAIRS_UP -> {
                                val center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                drawLine(
                                    color = Color(0xFF10B981).copy(alpha = alpha),
                                    start = Offset(cellLeft + cellSize * 0.2f, cellTop + cellSize * 0.8f),
                                    end = Offset(cellLeft + cellSize * 0.8f, cellTop + cellSize * 0.2f),
                                    strokeWidth = 2f
                                )
                                drawLine(
                                    color = Color(0xFF10B981).copy(alpha = alpha),
                                    start = Offset(cellLeft + cellSize * 0.5f, cellTop + cellSize * 0.2f),
                                    end = Offset(cellLeft + cellSize * 0.8f, cellTop + cellSize * 0.2f),
                                    strokeWidth = 2f
                                )
                                drawLine(
                                    color = Color(0xFF10B981).copy(alpha = alpha),
                                    start = Offset(cellLeft + cellSize * 0.8f, cellTop + cellSize * 0.2f),
                                    end = Offset(cellLeft + cellSize * 0.8f, cellTop + cellSize * 0.5f),
                                    strokeWidth = 2f
                                )
                            }
                            CellType.STAIRS_DOWN -> {
                                val center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                drawLine(
                                    color = Color(0xFF10B981).copy(alpha = alpha),
                                    start = Offset(cellLeft + cellSize * 0.2f, cellTop + cellSize * 0.2f),
                                    end = Offset(cellLeft + cellSize * 0.8f, cellTop + cellSize * 0.8f),
                                    strokeWidth = 2f
                                )
                                drawLine(
                                    color = Color(0xFF10B981).copy(alpha = alpha),
                                    start = Offset(cellLeft + cellSize * 0.5f, cellTop + cellSize * 0.8f),
                                    end = Offset(cellLeft + cellSize * 0.8f, cellTop + cellSize * 0.8f),
                                    strokeWidth = 2f
                                )
                                drawLine(
                                    color = Color(0xFF10B981).copy(alpha = alpha),
                                    start = Offset(cellLeft + cellSize * 0.8f, cellTop + cellSize * 0.5f),
                                    end = Offset(cellLeft + cellSize * 0.8f, cellTop + cellSize * 0.8f),
                                    strokeWidth = 2f
                                )
                            }
                            CellType.GRAVITY_SLOPE -> {
                                val center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                drawLine(
                                    color = Color(0xFFEAB308).copy(alpha = alpha),
                                    start = Offset(cellLeft + cellSize * 0.1f, cellTop + cellSize * 0.9f),
                                    end = Offset(cellLeft + cellSize * 0.9f, cellTop + cellSize * 0.1f),
                                    strokeWidth = 2.5f
                                )
                                drawLine(
                                    color = Color(0xFFEAB308).copy(alpha = alpha * 0.5f),
                                    start = Offset(cellLeft + cellSize * 0.3f, cellTop + cellSize * 0.9f),
                                    end = Offset(cellLeft + cellSize * 0.9f, cellTop + cellSize * 0.3f),
                                    strokeWidth = 1.5f
                                )
                            }
                            CellType.ECHO -> {
                                val center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                drawCircle(
                                    color = Color(0xFFC084FC).copy(alpha = alpha * 0.35f),
                                    radius = cellSize * 0.45f,
                                    center = center
                                )
                                drawCircle(
                                    color = Color(0xFFC084FC).copy(alpha = alpha),
                                    radius = cellSize * 0.25f,
                                    center = center,
                                    style = Stroke(width = 2f)
                                )
                                drawLine(
                                    color = Color(0xFFE9D5FF).copy(alpha = alpha),
                                    start = Offset(center.x - cellSize * 0.12f, center.y - cellSize * 0.12f),
                                    end = Offset(center.x + cellSize * 0.12f, center.y + cellSize * 0.12f),
                                    strokeWidth = 2f
                                )
                            }
                            else -> {
                                drawCircle(
                                    color = Color(0xFF1E293B).copy(alpha = alpha),
                                    radius = 2f,
                                    center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                )
                            }
                        }

                        // Draw Scanned Target / Threat Lock Overlay Indicators
                        if (uiState.isScanActive || uiState.scanTurnsLeft > 0) {
                            val cellCenter = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                            if (isScannedEnemy) {
                                drawCircle(
                                    color = Color(0xFFFF0055).copy(alpha = 0.35f),
                                    radius = cellSize * 0.65f,
                                    center = cellCenter
                                )
                                drawCircle(
                                    color = Color(0xFFFF0055),
                                    radius = cellSize * 0.45f,
                                    center = cellCenter,
                                    style = Stroke(width = 2.5f)
                                )
                                drawLine(
                                    color = Color(0xFFFF0055),
                                    start = Offset(cellCenter.x - cellSize * 0.5f, cellCenter.y),
                                    end = Offset(cellCenter.x + cellSize * 0.5f, cellCenter.y),
                                    strokeWidth = 1.5f
                                )
                                drawLine(
                                    color = Color(0xFFFF0055),
                                    start = Offset(cellCenter.x, cellCenter.y - cellSize * 0.5f),
                                    end = Offset(cellCenter.x, cellCenter.y + cellSize * 0.5f),
                                    strokeWidth = 1.5f
                                )
                            } else if (isScannedLoot) {
                                drawCircle(
                                    color = Color(0xFFFFB703).copy(alpha = 0.35f),
                                    radius = cellSize * 0.65f,
                                    center = cellCenter
                                )
                                drawCircle(
                                    color = Color(0xFFFFB703),
                                    radius = cellSize * 0.45f,
                                    center = cellCenter,
                                    style = Stroke(width = 2.5f)
                                )
                                drawCircle(
                                    color = Color(0xFF00E5FF),
                                    radius = cellSize * 0.25f,
                                    center = cellCenter,
                                    style = Stroke(width = 1.5f)
                                )
                            }
                        }
                    }
                }

                // Global Active Radar Sonar Pulse Wave Overlay
                if (uiState.isScanActive || uiState.scanTurnsLeft > 0) {
                    val playerCenter = Offset(startX + viewRadius * cellSize + cellSize / 2f, startY + viewRadius * cellSize + cellSize / 2f)
                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                        radius = cellSize * 7.5f,
                        center = playerCenter
                    )
                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = 0.5f),
                        radius = cellSize * 7.5f,
                        center = playerCenter,
                        style = Stroke(width = 2f)
                    )
                }
            }
        }
    }
}
