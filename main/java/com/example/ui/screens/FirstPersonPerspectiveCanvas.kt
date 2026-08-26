package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.GameViewModel
import com.example.ui.components.FlickeringCrtScanlineTerminalOverlay
import com.example.ui.theme.*

private data class CyberParticle(
    var xRatio: Float,
    var yRatio: Float,
    var zRatio: Float,
    val speedX: Float,
    val speedY: Float,
    val speedZ: Float,
    val size: Float,
    val color: Color
)

private data class PerspectiveData(
    val adjustedTl_r: FloatArray,
    val adjustedTr_r: FloatArray,
    val adjustedBl_r: FloatArray,
    val adjustedBr_r: FloatArray
)

// 3D Wireframe Voxel Wall Segment Drawer (Advanced Voxel graphics)
private fun androidx.compose.ui.graphics.drawscope.DrawScope.draw3DVoxelWallSegment(
    w1: Offset, w2: Offset, w3: Offset, w4: Offset,
    primaryColor: Color,
    alpha: Float,
    isLeft: Boolean,
    w: Float,
    adjustedTl_r: FloatArray,
    adjustedBl_r: FloatArray,
    adjustedTr_r: FloatArray,
    adjustedBr_r: FloatArray,
    d: Int,
    h: Float
) {
    fun getPixelLocal(col: Float, row: Float): Offset {
        return Offset((col / 30f) * w, (row / 10f) * h)
    }

    val shiftScale = 0.15f

    val p1: Offset
    val p2: Offset
    val p3: Offset
    val p4: Offset

    if (isLeft) {
        p1 = Offset(w1.x + (getPixelLocal(15f, adjustedTl_r[d]).x - w1.x) * shiftScale, w1.y)
        p2 = Offset(w2.x + (getPixelLocal(15f, adjustedTl_r[d+1]).x - w2.x) * shiftScale, w2.y)
        p3 = Offset(w3.x + (getPixelLocal(15f, adjustedBl_r[d+1]).x - w3.x) * shiftScale, w3.y)
        p4 = Offset(w4.x + (getPixelLocal(15f, adjustedBl_r[d]).x - w4.x) * shiftScale, w4.y)
    } else {
        p1 = Offset(w1.x + (getPixelLocal(15f, adjustedTr_r[d]).x - w1.x) * shiftScale, w1.y)
        p2 = Offset(w2.x + (getPixelLocal(15f, adjustedTr_r[d+1]).x - w2.x) * shiftScale, w2.y)
        p3 = Offset(w3.x + (getPixelLocal(15f, adjustedBr_r[d+1]).x - w3.x) * shiftScale, w3.y)
        p4 = Offset(w4.x + (getPixelLocal(15f, adjustedBr_r[d]).x - w4.x) * shiftScale, w4.y)
    }

    val wallPath = Path()

    wallPath.reset()
    wallPath.moveTo(w1.x, w1.y)
    wallPath.lineTo(w2.x, w2.y)
    wallPath.lineTo(w3.x, w3.y)
    wallPath.lineTo(w4.x, w4.y)
    wallPath.close()
    drawPath(path = wallPath, color = primaryColor.copy(alpha = alpha * 0.3f))

    drawLine(color = primaryColor.copy(alpha = alpha * 0.7f), start = w1, end = w2, strokeWidth = 1.5f)
    drawLine(color = primaryColor.copy(alpha = alpha * 0.7f), start = w2, end = w3, strokeWidth = 1.5f)
    drawLine(color = primaryColor.copy(alpha = alpha * 0.7f), start = w3, end = w4, strokeWidth = 1.5f)
    drawLine(color = primaryColor.copy(alpha = alpha * 0.7f), start = w4, end = w1, strokeWidth = 1.5f)

    val frontPath = Path()
    frontPath.moveTo(p1.x, p1.y)
    frontPath.lineTo(p2.x, p2.y)
    frontPath.lineTo(p3.x, p3.y)
    frontPath.lineTo(p4.x, p4.y)
    frontPath.close()
    drawPath(path = frontPath, color = Color.Black)
    drawPath(path = frontPath, color = primaryColor.copy(alpha = alpha * 0.45f))

    drawLine(color = primaryColor.copy(alpha = alpha * 2f), start = p1, end = p2, strokeWidth = 3f)
    drawLine(color = primaryColor.copy(alpha = alpha * 2f), start = p2, end = p3, strokeWidth = 3f)
    drawLine(color = primaryColor.copy(alpha = alpha * 2f), start = p3, end = p4, strokeWidth = 3f)
    drawLine(color = primaryColor.copy(alpha = alpha * 2f), start = p4, end = p1, strokeWidth = 3f)

    drawLine(color = primaryColor.copy(alpha = alpha * 1.3f), start = w1, end = p1, strokeWidth = 2f)
    drawLine(color = primaryColor.copy(alpha = alpha * 1.3f), start = w2, end = p2, strokeWidth = 2f)
    drawLine(color = primaryColor.copy(alpha = alpha * 1.3f), start = w3, end = p3, strokeWidth = 2f)
    drawLine(color = primaryColor.copy(alpha = alpha * 1.3f), start = w4, end = p4, strokeWidth = 2f)

    val wMidY_near = (w1.y + w4.y) / 2f
    val wMidY_far = (w2.y + w3.y) / 2f
    val pMidY_near = (p1.y + p4.y) / 2f
    val pMidY_far = (p2.y + p3.y) / 2f

    val wMidL = Offset((w1.x + w4.x) / 2f, wMidY_near)
    val wMidR = Offset((w2.x + w3.x) / 2f, wMidY_far)
    val pMidL = Offset((p1.x + p4.x) / 2f, pMidY_near)
    val pMidR = Offset((p2.x + p3.x) / 2f, pMidY_far)

    drawLine(color = primaryColor.copy(alpha = alpha * 1.1f), start = wMidL, end = wMidR, strokeWidth = 1f)
    drawLine(color = primaryColor.copy(alpha = alpha * 1.5f), start = pMidL, end = pMidR, strokeWidth = 1.5f)
    drawLine(color = primaryColor.copy(alpha = alpha * 1.1f), start = wMidL, end = pMidL, strokeWidth = 1f)
    drawLine(color = primaryColor.copy(alpha = alpha * 1.1f), start = wMidR, end = pMidR, strokeWidth = 1f)
}

@Composable
fun FirstPersonPerspectiveCanvas(
    uiState: GameViewModel.GameUiState,
    modifier: Modifier = Modifier,
    isCombat: Boolean = false,
    onInteract: () -> Unit = {}
) {
    val maze = uiState.maze
    if (maze.isEmpty()) {
        Box(modifier = modifier.background(Color.Black))
        return
    }

    val px = uiState.gridX
    val py = uiState.gridY
    val dir = uiState.direction

    val width = maze[0].size
    val height = maze.size

    val cellTypes = remember(maze, px, py, dir) {
        val types = MutableList(4) { CellType.WALL }
        for (d in 0..3) {
            val cx = px + d * dir.dx
            val cy = py + d * dir.dy
            if (cx in 0 until width && cy in 0 until height) {
                types[d] = maze[cy][cx]
            } else {
                types[d] = CellType.WALL
            }
        }
        types.toList()
    }

    val cellCoords = remember(px, py, dir) {
        val coords = MutableList(4) { Pair(-1, -1) }
        for (d in 0..3) {
            coords[d] = Pair(px + d * dir.dx, py + d * dir.dy)
        }
        coords.toList()
    }

    val leftWallAt = remember(maze, cellCoords) {
        val leftWall = BooleanArray(3) { true }
        val leftDir = dir.turnLeft()
        for (d in 0..2) {
            val cc = cellCoords[d]
            if (cc.first != -1) {
                val lx = cc.first + leftDir.dx
                val ly = cc.second + leftDir.dy
                if (lx in 0 until width && ly in 0 until height) {
                    leftWall[d] = maze[ly][lx] == CellType.WALL
                }
            }
        }
        leftWall
    }

    val rightWallAt = remember(maze, cellCoords) {
        val rightWall = BooleanArray(3) { true }
        val rightDir = dir.turnRight()
        for (d in 0..2) {
            val cc = cellCoords[d]
            if (cc.first != -1) {
                val rx = cc.first + rightDir.dx
                val ry = cc.second + rightDir.dy
                if (rx in 0 until width && ry in 0 until height) {
                    rightWall[d] = maze[ry][rx] == CellType.WALL
                }
            }
        }
        rightWall
    }

    val maxVisibleDepth = remember(cellTypes) {
        var maxD = 3
        for (d in 1..3) {
            if (cellTypes[d] == CellType.WALL) {
                maxD = d
                break
            }
        }
        maxD
    }

    val perspectiveData = remember(cellTypes) {
        val tl_r = floatArrayOf(0f, 2f, 3f, 4f)
        val bl_r = floatArrayOf(10f, 8f, 7f, 6f)
        val tr_r = floatArrayOf(0f, 2f, 3f, 4f)
        val br_r = floatArrayOf(10f, 8f, 7f, 6f)

        val ceilingShifts = FloatArray(4) { 0f }
        val floorShifts = FloatArray(4) { 0f }

        for (d in 0..3) {
            val type = cellTypes[d]
            when (type) {
                CellType.GRAND_HALL -> {
                    ceilingShifts[d] = -1.6f
                    floorShifts[d] = 0.4f
                }
                CellType.VENT_TUNNEL -> {
                    ceilingShifts[d] = 1.3f
                    floorShifts[d] = -0.3f
                }
                CellType.ELEVATED_BALCONY -> {
                    ceilingShifts[d] = -0.6f
                    floorShifts[d] = 1.6f
                }
                CellType.STAIRS_UP -> {
                    ceilingShifts[d] = -0.6f * d
                    floorShifts[d] = -0.8f * d
                }
                CellType.STAIRS_DOWN -> {
                    ceilingShifts[d] = 0.4f * d
                    floorShifts[d] = 1.0f * d
                }
                CellType.GRAVITY_SLOPE -> {
                    ceilingShifts[d] = -0.5f * d
                    floorShifts[d] = -0.5f * d
                }
                else -> {
                    ceilingShifts[d] = 0f
                    floorShifts[d] = 0f
                }
            }
        }

        PerspectiveData(
            adjustedTl_r = FloatArray(4) { d -> (tl_r[d] + ceilingShifts[d]).coerceIn(-1.5f, 11.5f) },
            adjustedTr_r = FloatArray(4) { d -> (tr_r[d] + ceilingShifts[d]).coerceIn(-1.5f, 11.5f) },
            adjustedBl_r = FloatArray(4) { d -> (bl_r[d] + floorShifts[d]).coerceIn(-1.5f, 11.5f) },
            adjustedBr_r = FloatArray(4) { d -> (br_r[d] + floorShifts[d]).coerceIn(-1.5f, 11.5f) }
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "CyberEngine")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ParticlesProgress"
    )

    val alertAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AlertAlpha"
    )

    val frameTime by produceState(initialValue = 0L) {
        while (true) {
            withFrameMillis {
                value = it
            }
        }
    }

    val particles = remember {
        val random = java.util.Random(1337)
        List(20) {
            val isBlue = random.nextBoolean()
            CyberParticle(
                xRatio = random.nextFloat(),
                yRatio = random.nextFloat(),
                zRatio = 0.1f + random.nextFloat() * 0.9f,
                speedX = (random.nextFloat() - 0.5f) * 0.006f,
                speedY = -0.003f - random.nextFloat() * 0.008f,
                speedZ = -0.004f - random.nextFloat() * 0.006f,
                size = 1.5f + random.nextFloat() * 3.5f,
                color = if (isBlue) Color(0xFF00E5FF) else Color(0xFFC084FC)
            )
        }
    }

    val zoomScale by animateFloatAsState(
        targetValue = if (isCombat) 1.15f else 1.0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "CameraZoom"
    )
    val dimAlpha by animateFloatAsState(
        targetValue = if (isCombat) 0.35f else 0.0f,
        animationSpec = tween(durationMillis = 500, easing = LinearEasing),
        label = "BackgroundDim"
    )

    var previousTurnState by remember { mutableStateOf(uiState.combatTurn) }
    val turnPulseAnim = remember { Animatable(0f) }
    LaunchedEffect(uiState.combatTurn) {
        if (uiState.combatTurn != previousTurnState) {
            previousTurnState = uiState.combatTurn
            turnPulseAnim.snapTo(1f)
            turnPulseAnim.animateTo(0f, animationSpec = tween(500, easing = LinearOutSlowInEasing))
        }
    }

    val basePrimaryColor = if (isCombat) {
        Color(0xFFFB7185)
    } else {
        when (uiState.currentZone) {
            com.example.data.Zone.BUILDING -> {
                when (uiState.buildingFloor) {
                    1 -> Color(0xFF00E5FF)
                    2 -> Color(0xFF60A5FA)
                    3 -> Color(0xFFF97316)
                    4 -> Color(0xFFC084FC)
                    else -> Color(0xFF00E5FF)
                }
            }
            com.example.data.Zone.COLLECTORS -> {
                if (uiState.collectorsLevel == 1) Color(0xFF10B981)
                else Color(0xFF8B5CF6)
            }
            com.example.data.Zone.CITY -> {
                when (uiState.cityDistrictIndex) {
                    0 -> Color(0xFFEC4899)
                    1 -> Color(0xFFFBBF24)
                    else -> Color(0xFF00E5FF)
                }
            }
        }
    }
    val primaryColor = if (uiState.combatFlashEnemy) Color.White else basePrimaryColor
    val wallPath = remember { Path() }
    val threatPath = remember { Path() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onInteract() }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = zoomScale,
                    scaleY = zoomScale
                )
        ) {
            val w = size.width
            val h = size.height

            val tl_c = floatArrayOf(0f, 6f, 11f, 13f)
            val tl_r = floatArrayOf(0f, 2f, 3f, 4f)
            val bl_c = floatArrayOf(0f, 6f, 11f, 13f)
            val bl_r = floatArrayOf(10f, 8f, 7f, 6f)

            val tr_c = floatArrayOf(30f, 24f, 19f, 17f)
            val tr_r = floatArrayOf(0f, 2f, 3f, 4f)
            val br_c = floatArrayOf(30f, 24f, 19f, 17f)
            val br_r = floatArrayOf(10f, 8f, 7f, 6f)

            val adjustedTl_r = perspectiveData.adjustedTl_r
            val adjustedTr_r = perspectiveData.adjustedTr_r
            val adjustedBl_r = perspectiveData.adjustedBl_r
            val adjustedBr_r = perspectiveData.adjustedBr_r

            fun getPixel(col: Float, row: Float): Offset {
                return Offset((col / 30f) * w, (row / 10f) * h)
            }

            val isShaking = uiState.combatScreenShake || uiState.integrity < 30
            val shakeOffset = if (isShaking) {
                val scaleVal = if (uiState.combatScreenShake) 14f else 4f
                val shakeX = ((animProgress * 73f) % scaleVal) - (scaleVal / 2f)
                val shakeY = ((animProgress * 113f) % scaleVal) - (scaleVal / 2f)
                Offset(shakeX, shakeY)
            } else {
                Offset.Zero
            }

            drawContext.canvas.save()
            drawContext.canvas.translate(shakeOffset.x, shakeOffset.y)

            for (d in 0..3) {
                drawLine(
                    color = primaryColor.copy(alpha = 0.15f),
                    start = getPixel(tl_c[d], adjustedTl_r[d]),
                    end = getPixel(tr_c[d], adjustedTr_r[d]),
                    strokeWidth = 2f
                )
                drawLine(
                    color = primaryColor.copy(alpha = 0.15f),
                    start = getPixel(bl_c[d], adjustedBl_r[d]),
                    end = getPixel(br_c[d], adjustedBr_r[d]),
                    strokeWidth = 2f
                )
            }

            for (d in 0..2) {
                drawLine(
                    color = primaryColor.copy(alpha = 0.15f),
                    start = getPixel(tl_c[d], adjustedTl_r[d]),
                    end = getPixel(tl_c[d+1], adjustedTl_r[d+1]),
                    strokeWidth = 2f
                )
                drawLine(
                    color = primaryColor.copy(alpha = 0.15f),
                    start = getPixel(tr_c[d], adjustedTr_r[d]),
                    end = getPixel(tr_c[d+1], adjustedTr_r[d+1]),
                    strokeWidth = 2f
                )
                drawLine(
                    color = primaryColor.copy(alpha = 0.15f),
                    start = getPixel(bl_c[d], adjustedBl_r[d]),
                    end = getPixel(bl_c[d+1], adjustedBl_r[d+1]),
                    strokeWidth = 2f
                )
                drawLine(
                    color = primaryColor.copy(alpha = 0.15f),
                    start = getPixel(br_c[d], adjustedBr_r[d]),
                    end = getPixel(br_c[d+1], adjustedBr_r[d+1]),
                    strokeWidth = 2f
                )
            }

            for (d in (maxVisibleDepth - 1) downTo 0) {
                val alpha = when (d) {
                    0 -> 0.4f
                    1 -> 0.25f
                    2 -> 0.15f
                    else -> 0.1f
                }

                if (leftWallAt[d]) {
                    val w1 = getPixel(tl_c[d], adjustedTl_r[d])
                    val w2 = getPixel(tl_c[d+1], adjustedTl_r[d+1])
                    val w3 = getPixel(bl_c[d+1], adjustedBl_r[d+1])
                    val w4 = getPixel(bl_c[d], adjustedBl_r[d])
                    draw3DVoxelWallSegment(w1, w2, w3, w4, primaryColor, alpha, isLeft = true, w, adjustedTl_r, adjustedBl_r, adjustedTr_r, adjustedBr_r, d, h)
                } else {
                    drawLine(color = primaryColor.copy(alpha = 0.2f), start = getPixel(tl_c[d], adjustedTl_r[d+1]), end = getPixel(tl_c[d+1], adjustedTl_r[d+1]), strokeWidth = 2f)
                    drawLine(color = primaryColor.copy(alpha = 0.2f), start = getPixel(bl_c[d], adjustedBl_r[d+1]), end = getPixel(bl_c[d+1], adjustedBl_r[d+1]), strokeWidth = 2f)
                    drawLine(color = primaryColor.copy(alpha = 0.3f), start = getPixel(tl_c[d+1], adjustedTl_r[d+1]), end = getPixel(bl_c[d+1], adjustedBl_r[d+1]), strokeWidth = 3f)
                }

                if (rightWallAt[d]) {
                    val w1 = getPixel(tr_c[d], adjustedTr_r[d])
                    val w2 = getPixel(tr_c[d+1], adjustedTr_r[d+1])
                    val w3 = getPixel(br_c[d+1], adjustedBr_r[d+1])
                    val w4 = getPixel(br_c[d], adjustedBr_r[d])
                    draw3DVoxelWallSegment(w1, w2, w3, w4, primaryColor, alpha, isLeft = false, w, adjustedTl_r, adjustedBl_r, adjustedTr_r, adjustedBr_r, d, h)
                } else {
                    drawLine(color = primaryColor.copy(alpha = 0.2f), start = getPixel(tr_c[d+1], adjustedTr_r[d+1]), end = getPixel(tr_c[d], adjustedTr_r[d+1]), strokeWidth = 2f)
                    drawLine(color = primaryColor.copy(alpha = 0.2f), start = getPixel(br_c[d+1], adjustedBr_r[d+1]), end = getPixel(br_c[d], adjustedBr_r[d+1]), strokeWidth = 2f)
                    drawLine(color = primaryColor.copy(alpha = 0.3f), start = getPixel(tr_c[d+1], adjustedTr_r[d+1]), end = getPixel(br_c[d+1], adjustedBr_r[d+1]), strokeWidth = 3f)
                }
            }

            val d = maxVisibleDepth
            val pTL = getPixel(tl_c[d.coerceAtMost(3)], adjustedTl_r[d.coerceAtMost(3)])
            val pBR = getPixel(tr_c[d.coerceAtMost(3)], adjustedBr_r[d.coerceAtMost(3)])

            if (d <= 3) {
                val wallAlpha = when (d) {
                    1 -> 0.75f
                    2 -> 0.5f
                    3 -> 0.3f
                    else -> 0.2f
                }

                drawRect(
                    color = primaryColor.copy(alpha = wallAlpha),
                    topLeft = pTL,
                    size = Size(pBR.x - pTL.x, pBR.y - pTL.y)
                )
                drawRect(
                    color = primaryColor,
                    topLeft = pTL,
                    size = Size(pBR.x - pTL.x, pBR.y - pTL.y),
                    style = Stroke(width = 4f)
                )
            } else {
                drawRect(
                    color = primaryColor.copy(alpha = 0.25f),
                    topLeft = pTL,
                    size = Size(pBR.x - pTL.x, pBR.y - pTL.y),
                    style = Stroke(width = 2f)
                )
            }

            if (dimAlpha > 0f) {
                drawRect(
                    color = Color.Black.copy(alpha = dimAlpha),
                    topLeft = Offset(0f, 0f),
                    size = size
                )
            }

            val primaryNode = if (isCombat) CellType.VIRUS_NODE else cellTypes[1]
            if (primaryNode == CellType.VIRUS_NODE) {
                val center = getPixel(15f, 5f)
                val sizeRadius = w * 0.11f

                drawCircle(
                    color = Color(0xFFF43F5E).copy(alpha = 0.25f),
                    radius = sizeRadius * 1.5f,
                    center = center
                )

                threatPath.reset()
                threatPath.moveTo(center.x, center.y - sizeRadius)
                threatPath.lineTo(center.x + sizeRadius, center.y)
                threatPath.lineTo(center.x, center.y + sizeRadius)
                threatPath.lineTo(center.x - sizeRadius, center.y)
                threatPath.close()
                drawPath(path = threatPath, color = Color(0xFFF43F5E), style = Stroke(width = 5f))

                drawCircle(color = Color(0xFFF43F5E), radius = sizeRadius * 0.4f, center = center)

                drawLine(Color(0xFFF43F5E), center, getPixel(11f, 3.5f), strokeWidth = 3f)
                drawLine(Color(0xFFF43F5E), center, getPixel(19f, 3.5f), strokeWidth = 3f)
                drawLine(Color(0xFFF43F5E), center, getPixel(11f, 6.5f), strokeWidth = 3f)
                drawLine(Color(0xFFF43F5E), center, getPixel(19f, 6.5f), strokeWidth = 3f)

            } else if (primaryNode == CellType.DATA_STORE) {
                val center = getPixel(15f, 5f)
                val sizeRadius = w * 0.1f

                drawCircle(
                    color = Color(0xFFFBBF24).copy(alpha = 0.22f),
                    radius = sizeRadius * 1.5f,
                    center = center
                )

                val boxW = sizeRadius * 1.6f
                val boxH = sizeRadius * 0.35f

                drawRoundRect(
                    color = Color(0xFFFBBF24),
                    topLeft = Offset(center.x - boxW / 2, center.y - sizeRadius * 0.75f),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawRoundRect(
                    color = Color(0xFFFBBF24),
                    topLeft = Offset(center.x - boxW / 2, center.y - boxH / 2),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawRoundRect(
                    color = Color(0xFFFBBF24),
                    topLeft = Offset(center.x - boxW / 2, center.y + sizeRadius * 0.4f),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(6f, 6f)
                )

                drawCircle(Color.Black, radius = 5f, center = Offset(center.x - boxW * 0.35f, center.y - sizeRadius * 0.55f))
                drawCircle(Color.Black, radius = 5f, center = Offset(center.x - boxW * 0.35f, center.y))
                drawCircle(Color.Black, radius = 5f, center = Offset(center.x - boxW * 0.35f, center.y + sizeRadius * 0.55f))

            } else if (primaryNode == CellType.ENCRYPTED_PORTAL) {
                val center = getPixel(15f, 5f)
                val sizeRadius = w * 0.12f

                drawCircle(
                    color = Color(0xFFC084FC).copy(alpha = 0.15f),
                    radius = sizeRadius * 1.8f,
                    center = center
                )

                fun drawFractalStarLocal(cx: Float, cy: Float, radius: Float, depth: Int) {
                    if (depth <= 0 || radius < 2f) return
                    val numPoints = 5
                    val path = Path()
                    val rotationPhase = animProgress * 2f * Math.PI.toFloat()
                    for (i in 0..numPoints * 2) {
                        val angle = (i * Math.PI / numPoints).toFloat() - (Math.PI / 2).toFloat() + (if (depth % 2 == 0) rotationPhase else -rotationPhase)
                        val r = if (i % 2 == 0) radius else radius * 0.4f
                        val x = cx + kotlin.math.cos(angle) * r
                        val y = cy + kotlin.math.sin(angle) * r
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawPath(path = path, color = Color(0xFFC084FC).copy(alpha = 0.12f * depth))
                    drawPath(path = path, color = Color(0xFFC084FC).copy(alpha = 0.4f + 0.12f * depth), style = Stroke(width = 1.5f + 0.5f * depth))

                    if (depth > 1) {
                        for (i in 0 until numPoints) {
                            val angle = (i * 2 * Math.PI / numPoints).toFloat() + rotationPhase
                            val tipX = cx + kotlin.math.cos(angle) * radius
                            val tipY = cy + kotlin.math.sin(angle) * radius
                            drawFractalStarLocal(tipX, tipY, radius * 0.35f, depth - 1)
                        }
                    }
                }
                drawFractalStarLocal(center.x, center.y, sizeRadius * 1.2f, depth = 3)
            } else if (primaryNode == CellType.SECRET_CACHE) {
                val center = getPixel(15f, 5f)
                val sizeRadius = w * 0.09f
                drawCircle(
                    color = Color(0xFF38BDF8).copy(alpha = 0.25f),
                    radius = sizeRadius * 1.5f,
                    center = center
                )
                drawRoundRect(
                    color = Color(0xFF38BDF8),
                    topLeft = Offset(center.x - sizeRadius, center.y - sizeRadius),
                    size = Size(sizeRadius * 2f, sizeRadius * 2f),
                    cornerRadius = CornerRadius(8f, 8f),
                    style = Stroke(width = 3f)
                )
                drawCircle(
                    color = Color(0xFF38BDF8),
                    radius = sizeRadius * 0.3f,
                    center = center
                )
            } else if (primaryNode == CellType.STAIRS_UP || primaryNode == CellType.STAIRS_DOWN) {
                val numSteps = 5
                for (i in 0..numSteps) {
                    val ratio = i.toFloat() / numSteps
                    val stepY = pTL.y + (pBR.y - pTL.y) * ratio
                    val stepL = pTL.x + (pBR.x - pTL.x) * 0.18f * ratio
                    val stepR = pBR.x - (pBR.x - pTL.x) * 0.18f * ratio
                    drawLine(
                        color = Color(0xFF10B981).copy(alpha = 0.75f),
                        start = Offset(stepL, stepY),
                        end = Offset(stepR, stepY),
                        strokeWidth = 3.5f
                    )
                }
            } else if (primaryNode == CellType.GRAVITY_SLOPE) {
                val numChevrons = 4
                for (i in 0 until numChevrons) {
                    val flowOffset = (animProgress * 1.5f) % 1.0f
                    val ratio = ((i.toFloat() / numChevrons) + flowOffset) % 1.0f
                    val yCoord = pTL.y + (pBR.y - pTL.y) * ratio
                    val spread = (pBR.x - pTL.x) * 0.35f * ratio
                    val midX = pTL.x + (pBR.x - pTL.x) * 0.5f

                    drawLine(
                        color = Color(0xFFEAB308).copy(alpha = 0.7f * (1f - ratio)),
                        start = Offset(midX - spread, yCoord + 15f),
                        end = Offset(midX, yCoord),
                        strokeWidth = 3f
                    )
                    drawLine(
                        color = Color(0xFFEAB308).copy(alpha = 0.7f * (1f - ratio)),
                        start = Offset(midX, yCoord),
                        end = Offset(midX + spread, yCoord + 15f),
                        strokeWidth = 3f
                    )
                }
            } else if (primaryNode == CellType.ELEVATOR) {
                val cx = pTL.x + (pBR.x - pTL.x) * 0.5f
                val cy = pTL.y + (pBR.y - pTL.y) * 0.5f
                val hSize = pBR.x - pTL.x
                val vSize = pBR.y - pTL.y

                drawRect(
                    color = Color(0xFF00E5FF).copy(alpha = 0.35f),
                    topLeft = Offset(pTL.x + hSize * 0.15f, pTL.y),
                    size = Size(hSize * 0.7f, vSize),
                    style = Stroke(width = 3.5f)
                )

                drawLine(
                    color = Color(0xFF00E5FF).copy(alpha = 0.7f),
                    start = Offset(pTL.x + hSize * 0.3f, pTL.y),
                    end = Offset(pTL.x + hSize * 0.3f, pBR.y),
                    strokeWidth = 2.5f
                )
                drawLine(
                    color = Color(0xFF00E5FF).copy(alpha = 0.7f),
                    start = Offset(pTL.x + hSize * 0.7f, pTL.y),
                    end = Offset(pTL.x + hSize * 0.7f, pBR.y),
                    strokeWidth = 2.5f
                )

                val liftOffsetY = vSize * 0.35f * kotlin.math.sin(animProgress * 2f * kotlin.math.PI.toFloat())
                val capY = cy - vSize * 0.2f + liftOffsetY
                val capW = hSize * 0.36f
                val capH = vSize * 0.4f

                drawRoundRect(
                    color = Color(0xFF00E5FF),
                    topLeft = Offset(cx - capW / 2, capY),
                    size = Size(capW, capH),
                    cornerRadius = CornerRadius(10f, 10f),
                    style = Stroke(width = 4f)
                )

                drawRoundRect(
                    color = Color(0xFF00E5FF).copy(alpha = 0.18f),
                    topLeft = Offset(cx - capW / 2, capY),
                    size = Size(capW, capH),
                    cornerRadius = CornerRadius(10f, 10f)
                )

                val stripeCount = 3
                for (s in 0 until stripeCount) {
                    val sY = capY + capH * 0.25f + (capH * 0.5f) * (s.toFloat() / (stripeCount - 1))
                    drawLine(
                        color = Color(0xFFEAB308).copy(alpha = 0.8f),
                        start = Offset(cx - capW * 0.35f, sY),
                        end = Offset(cx + capW * 0.35f, sY),
                        strokeWidth = 2f
                    )
                }

                val arrowDir = if (animProgress < 0.5f) 1 else -1
                if (arrowDir > 0) {
                    drawLine(Color(0xFF00E5FF), Offset(cx, capY + capH * 0.15f), Offset(cx - 10f, capY + capH * 0.28f), strokeWidth = 3f)
                    drawLine(Color(0xFF00E5FF), Offset(cx, capY + capH * 0.15f), Offset(cx + 10f, capY + capH * 0.28f), strokeWidth = 3f)
                } else {
                    drawLine(Color(0xFF00E5FF), Offset(cx, capY + capH * 0.85f), Offset(cx - 10f, capY + capH * 0.72f), strokeWidth = 3f)
                    drawLine(Color(0xFF00E5FF), Offset(cx, capY + capH * 0.85f), Offset(cx + 10f, capY + capH * 0.72f), strokeWidth = 3f)
                }

            } else if (primaryNode == CellType.ELEVATED_BALCONY) {
                val cx = pTL.x + (pBR.x - pTL.x) * 0.5f
                val hSize = pBR.x - pTL.x
                val vSize = pBR.y - pTL.y
                val railingTopY = pTL.y + vSize * 0.5f

                drawRect(
                    color = Color(0xFF020617).copy(alpha = 0.8f),
                    topLeft = Offset(pTL.x, railingTopY),
                    size = Size(hSize, pBR.y - railingTopY)
                )

                val numGridLines = 6
                for (g in 0..numGridLines) {
                    val ratio = g.toFloat() / numGridLines
                    val lineX = pTL.x + hSize * ratio
                    drawLine(
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        start = Offset(lineX, railingTopY),
                        end = Offset(cx + (lineX - cx) * 1.5f, pBR.y),
                        strokeWidth = 1.5f
                    )
                }

                val railCount = 4
                for (r in 0 until railCount) {
                    val rY = railingTopY + (pBR.y - railingTopY) * (r.toFloat() / railCount)
                    drawLine(
                        color = Color(0xFF10B981).copy(alpha = 0.85f - (r * 0.15f)),
                        start = Offset(pTL.x, rY),
                        end = Offset(pBR.x, rY),
                        strokeWidth = 4f - (r * 0.5f)
                    )
                }

                val balusterCount = 10
                for (b in 0..balusterCount) {
                    val bX = pTL.x + hSize * (b.toFloat() / balusterCount)
                    drawLine(
                        color = Color(0xFF10B981).copy(alpha = 0.4f),
                        start = Offset(bX, railingTopY),
                        end = Offset(bX, pBR.y),
                        strokeWidth = 2f
                    )
                }

                val holoY = railingTopY - 15f - ((animProgress * 12f) % 8f)
                drawRoundRect(
                    color = Color(0xFF10B981),
                    topLeft = Offset(cx - 75f, holoY - 18f),
                    size = Size(150f, 24f),
                    cornerRadius = CornerRadius(5f, 5f),
                    style = Stroke(width = 2f)
                )
                drawRoundRect(
                    color = Color(0xFF10B981).copy(alpha = 0.1f),
                    topLeft = Offset(cx - 75f, holoY - 18f),
                    size = Size(150f, 24f),
                    cornerRadius = CornerRadius(5f, 5f)
                )

                drawLine(
                    color = Color(0xFFEAB308),
                    start = Offset(cx - 65f, holoY - 6f),
                    end = Offset(cx - 55f, holoY - 6f),
                    strokeWidth = 3f
                )
                drawLine(
                    color = Color(0xFFEAB308),
                    start = Offset(cx + 55f, holoY - 6f),
                    end = Offset(cx + 65f, holoY - 6f),
                    strokeWidth = 3f
                )
            }

            val timeSec = frameTime / 1000f
            particles.forEach { p ->
                var curX = p.xRatio + p.speedX * timeSec * 30f
                var curY = p.yRatio + p.speedY * timeSec * 30f
                var curZ = p.zRatio + p.speedZ * timeSec * 30f

                while (curX < 0f) curX += 1f
                while (curX > 1f) curX -= 1f
                while (curY < 0f) curY += 1f
                while (curY > 1f) curY -= 1f
                while (curZ < 0.1f) curZ += 0.9f
                while (curZ > 1f) curZ -= 0.9f

                val centerOffsetX = (curX - 0.5f) * w / curZ
                val centerOffsetY = (curY - 0.5f) * h / curZ
                val pxX = (w / 2f) + centerOffsetX
                val pxY = (h / 2f) + centerOffsetY

                if (pxX in 0f..w && pxY in 0f..h) {
                    val baseColor = if (isCombat) Color(0xFFF43F5E) else p.color
                    val drawColor = baseColor.copy(alpha = (1f - curZ).coerceIn(0f, 1f))
                    val drawSize = (p.size / curZ).coerceIn(1f, 12f)
                    drawCircle(
                        color = drawColor,
                        radius = drawSize,
                        center = Offset(pxX, pxY)
                    )
                }
            }

            if (uiState.showShieldEffect) {
                val pulseRadius = w * 0.35f + ((animProgress * 30f) % 20f)
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = 0.35f),
                    radius = pulseRadius,
                    center = Offset(w / 2f, h / 2f),
                    style = Stroke(width = 6f)
                )
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = 0.12f),
                    radius = pulseRadius * 0.82f,
                    center = Offset(w / 2f, h / 2f)
                )
            }

            drawLine(
                color = primaryColor.copy(alpha = 0.35f),
                start = Offset(w * 0.15f, h * 0.5f),
                end = Offset(w * 0.22f, h * 0.5f),
                strokeWidth = 3f
            )
            drawLine(
                color = primaryColor.copy(alpha = 0.35f),
                start = Offset(w * 0.78f, h * 0.5f),
                end = Offset(w * 0.85f, h * 0.5f),
                strokeWidth = 3f
            )
            drawCircle(
                color = primaryColor.copy(alpha = 0.45f),
                radius = 3f,
                center = Offset(w * 0.5f, h * 0.5f)
            )

            val scanlineCount = 18
            for (i in 0 until scanlineCount) {
                val lineY = (h / scanlineCount) * i + (animProgress * (h / scanlineCount))
                val finalY = lineY % h
                drawLine(
                    color = primaryColor.copy(alpha = 0.05f),
                    start = Offset(0f, finalY),
                    end = Offset(w, finalY),
                    strokeWidth = 2.5f
                )
            }

            if (isCombat || uiState.integrity < 40) {
                val alertColor = if (uiState.integrity < 40) Color(0xFFEF4444) else Color(0xFFF43F5E)
                drawRect(
                    color = alertColor.copy(alpha = alertAlpha * 0.8f),
                    topLeft = Offset(0f, 0f),
                    size = size,
                    style = Stroke(width = 10f)
                )

                val gradient = Brush.radialGradient(
                    colors = listOf(Color.Transparent, alertColor.copy(alpha = alertAlpha * 0.35f)),
                    center = Offset(w/2f, h/2f),
                    radius = w * 0.72f
                )
                drawRect(
                    brush = gradient,
                    topLeft = Offset(0f, 0f),
                    size = size
                )
            }

            if (uiState.combatFlashEnemy) {
                val beamProgress = (animProgress * 5f) % 1.0f
                drawLine(
                    color = Color(0xFF00E5FF),
                    start = Offset(w / 2f, h),
                    end = Offset(w / 2f, h * 0.45f),
                    strokeWidth = 12f
                )
                drawLine(
                    color = Color.White,
                    start = Offset(w / 2f, h),
                    end = Offset(w / 2f, h * 0.45f),
                    strokeWidth = 4f
                )
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = 0.8f),
                    radius = 45f * beamProgress,
                    center = Offset(w / 2f, h * 0.45f),
                    style = Stroke(width = 4f)
                )
            }

            if (uiState.combatFlashPlayer) {
                val waveProgress = (animProgress * 3f) % 1.0f
                drawCircle(
                    color = Color(0xFFEF4444).copy(alpha = (1f - waveProgress) * 0.75f),
                    radius = w * 0.85f * waveProgress,
                    center = Offset(w / 2f, h * 0.45f),
                    style = Stroke(width = 10f)
                )
                drawCircle(
                    color = Color(0xFFF43F5E).copy(alpha = (1f - waveProgress) * 0.45f),
                    radius = w * 0.45f * waveProgress,
                    center = Offset(w / 2f, h * 0.45f),
                    style = Stroke(width = 6f)
                )
            }

            if (uiState.showShieldEffect || uiState.activeFirewallTimeLeft > 0) {
                val shieldPath = Path()
                shieldPath.moveTo(0f, h)
                shieldPath.cubicTo(w * 0.25f, h * 0.72f, w * 0.75f, h * 0.72f, w, h)
                shieldPath.close()

                val shimmerAlpha = 0.25f + 0.15f * kotlin.math.sin(frameTime.toFloat() / 150f)
                drawPath(
                    path = shieldPath,
                    color = Color(0xFF00E5FF).copy(alpha = shimmerAlpha)
                )
                drawPath(
                    path = shieldPath,
                    color = Color(0xFF00E5FF),
                    style = Stroke(width = 4f)
                )

                for (i in 1..9) {
                    val ratio = i.toFloat() / 10f
                    drawLine(
                        color = Color.White.copy(alpha = 0.3f),
                        start = Offset(w * ratio, h),
                        end = Offset(w * ratio, h * 0.82f),
                        strokeWidth = 1.5f
                    )
                }
            }

            if (turnPulseAnim.value > 0f) {
                val pulseColor = if (uiState.combatTurn == com.example.ui.CombatTurn.PLAYER) CyberCyan else CyberPink
                drawRect(
                    color = pulseColor.copy(alpha = turnPulseAnim.value * 0.18f),
                    topLeft = Offset(0f, 0f),
                    size = size
                )
                drawRect(
                    color = pulseColor.copy(alpha = turnPulseAnim.value * 0.45f),
                    topLeft = Offset(0f, 0f),
                    size = size,
                    style = Stroke(width = 15f * turnPulseAnim.value)
                )
            }

            val crosshairX = w / 2f
            val crosshairY = h / 2f
            val isTargetInteractive = if (isCombat) {
                true
            } else {
                val nextCell = cellTypes[1]
                nextCell == CellType.DATA_STORE || nextCell == CellType.ENCRYPTED_PORTAL ||
                nextCell == CellType.VIRUS_NODE || nextCell == CellType.SECRET_CACHE ||
                nextCell == CellType.STAIRS_UP || nextCell == CellType.STAIRS_DOWN ||
                nextCell == CellType.ELEVATOR
            }

            val crosshairColor = if (isTargetInteractive) {
                if (isCombat) Color(0xFFFB7185) else Color(0xFF00E5FF)
            } else {
                Color.White.copy(alpha = 0.4f)
            }

            drawCircle(
                color = crosshairColor,
                radius = 3f,
                center = Offset(crosshairX, crosshairY)
            )

            val reticleOffset = 10f
            val reticleLength = 8f
            drawLine(crosshairColor, Offset(crosshairX, crosshairY - reticleOffset), Offset(crosshairX, crosshairY - reticleOffset - reticleLength), strokeWidth = 2f)
            drawLine(crosshairColor, Offset(crosshairX, crosshairY + reticleOffset), Offset(crosshairX, crosshairY + reticleOffset + reticleLength), strokeWidth = 2f)
            drawLine(crosshairColor, Offset(crosshairX - reticleOffset, crosshairY), Offset(crosshairX - reticleOffset - reticleLength, crosshairY), strokeWidth = 2f)
            drawLine(crosshairColor, Offset(crosshairX + reticleOffset, crosshairY), Offset(crosshairX + reticleOffset + reticleLength, crosshairY), strokeWidth = 2f)

            if (isTargetInteractive) {
                val bSize = 25f
                val bThick = 2f
                drawLine(crosshairColor, Offset(crosshairX - bSize, crosshairY - bSize), Offset(crosshairX - bSize + 8f, crosshairY - bSize), strokeWidth = bThick)
                drawLine(crosshairColor, Offset(crosshairX - bSize, crosshairY - bSize), Offset(crosshairX - bSize, crosshairY - bSize + 8f), strokeWidth = bThick)
                drawLine(crosshairColor, Offset(crosshairX + bSize, crosshairY - bSize), Offset(crosshairX + bSize - 8f, crosshairY - bSize), strokeWidth = bThick)
                drawLine(crosshairColor, Offset(crosshairX + bSize, crosshairY - bSize), Offset(crosshairX + bSize, crosshairY - bSize + 8f), strokeWidth = bThick)
                drawLine(crosshairColor, Offset(crosshairX - bSize, crosshairY + bSize), Offset(crosshairX - bSize + 8f, crosshairY + bSize), strokeWidth = bThick)
                drawLine(crosshairColor, Offset(crosshairX - bSize, crosshairY + bSize), Offset(crosshairX - bSize, crosshairY + bSize - 8f), strokeWidth = bThick)
                drawLine(crosshairColor, Offset(crosshairX + bSize, crosshairY + bSize), Offset(crosshairX + bSize - 8f, crosshairY + bSize), strokeWidth = bThick)
                drawLine(crosshairColor, Offset(crosshairX + bSize, crosshairY + bSize), Offset(crosshairX + bSize, crosshairY + bSize - 8f), strokeWidth = bThick)
            }

            val swingProgress = uiState.weaponSwingProgress
            val swingType = uiState.weaponSwingType

            val restingX = w * 0.75f
            val restingY = h * 0.85f

            val dynamicOffset = when (swingType) {
                "Slash" -> {
                    Offset(-w * 0.3f * swingProgress, -h * 0.1f * kotlin.math.sin(swingProgress * kotlin.math.PI.toFloat()))
                }
                "Chop" -> {
                    Offset(-w * 0.1f * swingProgress, h * 0.15f * swingProgress - h * 0.1f * kotlin.math.sin(swingProgress * kotlin.math.PI.toFloat()))
                }
                "Thrust" -> {
                    Offset(-w * 0.25f * swingProgress, -h * 0.25f * swingProgress)
                }
                else -> Offset.Zero
            }

            val weaponOrigin = Offset(restingX + dynamicOffset.x, restingY + dynamicOffset.y)

            val weaponColor = when (uiState.equippedWeaponName) {
                "Daedric Cyber-Katana" -> Color(0xFFF43F5E)
                "Aegis Shock-Mace" -> Color(0xFFFBBF24)
                "Glass Cyber-Dagger" -> Color(0xFF10B981)
                "Ebony Plasma-Staff" -> Color(0xFF8B5CF6)
                else -> Color(0xFF00E5FF)
            }

            when (uiState.equippedWeaponName) {
                "Daedric Cyber-Katana" -> {
                    val tip = Offset(weaponOrigin.x - w * 0.3f, weaponOrigin.y - h * 0.45f)
                    val guard = Offset(weaponOrigin.x - w * 0.05f, weaponOrigin.y - h * 0.08f)

                    drawLine(
                        color = weaponColor,
                        start = guard,
                        end = tip,
                        strokeWidth = 6f
                    )
                    drawLine(
                        color = Color.White,
                        start = guard,
                        end = tip,
                        strokeWidth = 2f
                    )
                    drawLine(
                        color = Color.DarkGray,
                        start = Offset(guard.x - 15f, guard.y + 10f),
                        end = Offset(guard.x + 15f, guard.y - 10f),
                        strokeWidth = 5f
                    )
                    drawLine(
                        color = Color.Black,
                        start = guard,
                        end = weaponOrigin,
                        strokeWidth = 8f
                    )
                }
                "Glass Cyber-Dagger" -> {
                    val tip = Offset(weaponOrigin.x - w * 0.15f, weaponOrigin.y - h * 0.25f)
                    val guard = Offset(weaponOrigin.x - w * 0.03f, weaponOrigin.y - h * 0.05f)

                    val bladePath = Path().apply {
                        moveTo(guard.x - 10f, guard.y)
                        lineTo(tip.x, tip.y)
                        lineTo(guard.x + 10f, guard.y)
                        close()
                    }
                    drawPath(
                        path = bladePath,
                        color = weaponColor
                    )
                    drawPath(
                        path = bladePath,
                        color = Color.White,
                        style = Stroke(width = 2f)
                    )
                    drawLine(
                        color = Color.DarkGray,
                        start = Offset(guard.x - 20f, guard.y),
                        end = Offset(guard.x + 20f, guard.y),
                        strokeWidth = 4f
                    )
                    drawLine(
                        color = Color.Black,
                        start = guard,
                        end = weaponOrigin,
                        strokeWidth = 6f
                    )
                }
                "Aegis Shock-Mace" -> {
                    val tip = Offset(weaponOrigin.x - w * 0.2f, weaponOrigin.y - h * 0.3f)

                    drawLine(
                        color = Color.DarkGray,
                        start = weaponOrigin,
                        end = tip,
                        strokeWidth = 10f
                    )
                    drawCircle(
                        color = weaponColor,
                        radius = 28f,
                        center = tip
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 12f,
                        center = tip
                    )
                    for (i in 0 until 8) {
                        val angle = (i * kotlin.math.PI / 4).toFloat()
                        val spikeEnd = Offset(
                            tip.x + 45f * kotlin.math.cos(angle),
                            tip.y + 45f * kotlin.math.sin(angle)
                        )
                        drawLine(
                            color = weaponColor,
                            start = tip,
                            end = spikeEnd,
                            strokeWidth = 4f
                        )
                    }
                }
                "Ebony Plasma-Staff" -> {
                    val tip = Offset(weaponOrigin.x - w * 0.22f, weaponOrigin.y - h * 0.38f)

                    drawLine(
                        color = Color(0xFF1E293B),
                        start = weaponOrigin,
                        end = tip,
                        strokeWidth = 8f
                    )
                    val hornLeft = Offset(tip.x - 25f, tip.y - 15f)
                    val hornRight = Offset(tip.x + 15f, tip.y + 25f)
                    drawLine(
                        color = Color.DarkGray,
                        start = hornLeft,
                        end = tip,
                        strokeWidth = 5f
                    )
                    drawLine(
                        color = Color.DarkGray,
                        start = hornRight,
                        end = tip,
                        strokeWidth = 5f
                    )

                    val orbCenter = Offset(tip.x - 10f, tip.y - 10f)
                    val glowPulse = 18f + 5f * kotlin.math.sin(frameTime.toFloat() / 100f)
                    drawCircle(
                        color = weaponColor.copy(alpha = 0.35f),
                        radius = glowPulse * 1.5f,
                        center = orbCenter
                    )
                    drawCircle(
                        color = weaponColor,
                        radius = glowPulse,
                        center = orbCenter
                    )
                    drawCircle(
                        color = Color.White,
                        radius = glowPulse * 0.4f,
                        center = orbCenter
                    )
                }
                else -> {
                    val tip = Offset(weaponOrigin.x - w * 0.15f, weaponOrigin.y - h * 0.25f)
                    drawLine(weaponColor, weaponOrigin, tip, strokeWidth = 4f)
                }
            }

            drawContext.canvas.restore()
        }

        val targetCell = if (isCombat) CellType.VIRUS_NODE else cellTypes[1]
        val hoverText = when (targetCell) {
            CellType.DATA_STORE -> "Data Store\n[CLICK VIEWPORT TO ACTIVATE]"
            CellType.SECRET_CACHE -> "Crypt-Cache\n[CLICK VIEWPORT TO DECRYPT]"
            CellType.VIRUS_NODE -> if (isCombat) null else "Active Threat Host\n[CLICK TO INITIATE CONFLICT]"
            CellType.ELEVATOR -> "Express Elevator Terminal\n[CLICK TO INITIATE FLOORS LIST]"
            CellType.STAIRS_UP -> "Stairwell: Ascent Link\n[CLICK TO CLIMB FLOORS]"
            CellType.STAIRS_DOWN -> "Stairwell: Descent Link\n[CLICK TO DESCEND FLOORS]"
            CellType.ENCRYPTED_PORTAL -> "Sector Decryption Portal\n[CLICK TO UNLOCK SECTOR]"
            else -> null
        }

        if (hoverText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(BorderStroke(1.dp, if (isCombat) CyberPink else CyberCyan), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = hoverText,
                    color = if (isCombat) CyberPink else CyberCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 10.sp
                )
            }
        }

        if (isCombat) {
            val badgePulse by rememberInfiniteTransition().animateFloat(
                initialValue = 0.85f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "BadgePulse"
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .graphicsLayer(
                        scaleX = badgePulse,
                        scaleY = badgePulse
                    )
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(
                        width = 1.dp,
                        color = (if (uiState.activeFirewallTimeLeft > 0) Color(0xFF10B981) else CyberPink).copy(alpha = badgePulse),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (uiState.activeFirewallTimeLeft > 0) "\uD83D\uDEE1\uFE0F FIREWALL ACTIVE [${String.format("%.1f", uiState.activeFirewallTimeLeft / 10f)}s]" else "\u26A1 REAL-TIME SYSTEM OVERLOAD ACTIVE",
                    color = if (uiState.activeFirewallTimeLeft > 0) Color(0xFF10B981) else CyberPink,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp
                )
            }
        }

        FloatingDamagePopup(
            text = uiState.enemyDamagePopup,
            color = CyberPink,
            isPlayer = false,
            modifier = Modifier.align(Alignment.Center)
        )

        FloatingDamagePopup(
            text = uiState.playerDamagePopup,
            color = Color.Red,
            isPlayer = true,
            modifier = Modifier.align(Alignment.Center)
        )

        uiState.showCombatBanner?.let { banner ->
            var animateTrigger by remember { mutableStateOf(false) }
            LaunchedEffect(banner) {
                animateTrigger = true
            }
            val bannerScale by animateFloatAsState(
                targetValue = if (animateTrigger) 1f else 0.8f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "BannerScale"
            )
            val bannerAlpha by animateFloatAsState(
                targetValue = if (animateTrigger) 1f else 0f,
                animationSpec = tween(durationMillis = 300),
                label = "BannerAlpha"
            )

            val bannerColor = if (banner.contains("VICTORY")) CyberBrightGreen else if (banner.contains("DEFEAT")) CyberPink else CyberAmber

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f * bannerAlpha))
                    .graphicsLayer(alpha = bannerAlpha),
                contentAlignment = Alignment.Center
            ) {
                DigitalSparks(
                    color = bannerColor,
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .graphicsLayer(
                            scaleX = bannerScale,
                            scaleY = bannerScale
                        )
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyberDark.copy(alpha = 0.95f))
                        .border(
                            width = 2.dp,
                            color = bannerColor,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 28.dp, vertical = 20.dp)
                ) {
                    Text(
                        text = banner,
                        color = bannerColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (banner.contains("VICTORY")) "SECURITIES SHUT DOWN // BOUNTY EXTRAPOLATED"
                               else if (banner.contains("DEFEAT")) "CORE MEMORY DUMPED // REBOOTING..."
                               else "LOCKING TARGET SUITE // SYSTEM HARDENING",
                        color = Color.White.copy(alpha = 0.7f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        FirstPersonGlitchShaderOverlay(
            integrity = uiState.integrity,
            maxIntegrity = uiState.maxIntegrity,
            isCombat = isCombat,
            isPlayerHit = uiState.combatFlashPlayer,
            frameTime = frameTime,
            modifier = Modifier.fillMaxSize()
        )
    }
}
