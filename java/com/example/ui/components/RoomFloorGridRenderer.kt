package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CellType
import com.example.data.FloorMapEntity
import com.example.data.FloorMapRepository
import com.example.data.FloorMapWithObstacles
import com.example.data.FloorObstacleEntity
import com.example.data.GameDatabase
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberBrightGreen
import com.example.ui.theme.CyberCardBg
import com.example.ui.theme.CyberCrimson
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberDark
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberPink
import com.example.ui.theme.CyberPurple
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.CyberTextPrimary

/**
 * Filter mode for highlighting specific elements on the rendered Room grid.
 */
enum class GridFilterMode(val label: String) {
    ALL("All Sectors"),
    TRAVERSABLE_ONLY("Traversable Channels"),
    OBSTACLES_ONLY("Obstacles & Firewalls"),
    INTERACTIVE_ONLY("Terminals & Gates")
}

/**
 * Visual metadata describing whether a tile is traversable floor or obstacle.
 */
data class GridTileVisualInfo(
    val x: Int,
    val y: Int,
    val cellType: CellType,
    val isTraversable: Boolean,
    val isObstacle: Boolean,
    val isPlayerHere: Boolean,
    val obstacleEntity: FloorObstacleEntity? = null
)

/**
 * Composable function that retrieves and renders the 2D floor grid stored in the Room database,
 * clearly distinguishing between traversable floor tiles and obstacles with high-contrast
 * visual styling, interactability, and inspection capabilities.
 */
@Composable
fun RoomFloorGridRenderer(
    mapId: String,
    modifier: Modifier = Modifier,
    repository: FloorMapRepository? = null,
    onTileClicked: ((x: Int, y: Int, cellType: CellType, obstacle: FloorObstacleEntity?) -> Unit)? = null,
    onObstacleAction: ((obstacle: FloorObstacleEntity) -> Unit)? = null
) {
    val context = LocalContext.current
    val effectiveRepo = remember(repository, context) {
        repository ?: GameDatabase.getFloorMapRepository(context)
    }

    val floorMapWithObstacles by effectiveRepo.getFloorMapWithObstacles(mapId)
        .collectAsState(initial = null)

    RoomFloorGridContent(
        floorMapWithObstacles = floorMapWithObstacles,
        modifier = modifier,
        onTileClicked = onTileClicked,
        onObstacleAction = onObstacleAction
    )
}

/**
 * Composable displaying a direct [FloorMapWithObstacles] data structure retrieved from Room,
 * rendering the grid layout and categorizing each cell as either traversable or obstacle.
 */
@Composable
fun RoomFloorGridContent(
    floorMapWithObstacles: FloorMapWithObstacles?,
    modifier: Modifier = Modifier,
    onTileClicked: ((x: Int, y: Int, cellType: CellType, obstacle: FloorObstacleEntity?) -> Unit)? = null,
    onObstacleAction: ((obstacle: FloorObstacleEntity) -> Unit)? = null
) {
    val view = LocalView.current
    var selectedFilter by remember { mutableStateOf(GridFilterMode.ALL) }
    var selectedTileInfo by remember { mutableStateOf<GridTileVisualInfo?>(null) }
    var isLegendExpanded by remember { mutableStateOf(false) }

    val floorMap = floorMapWithObstacles?.floorMap
    val obstacles = floorMapWithObstacles?.obstacles ?: emptyList()

    if (floorMap == null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(CyberDark, RoundedCornerShape(12.dp))
                .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .testTag("room_grid_loading_placeholder"),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Loading Database Grid",
                    tint = CyberCyan,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "SYNCHRONIZING ROOM GRID DATABASE...",
                    color = CyberCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        return
    }

    val width = floorMap.width.coerceAtLeast(1)
    val height = floorMap.height.coerceAtLeast(1)
    val gridMatrix = remember(floorMap.gridData, width, height) {
        FloorMapRepository.deserializeGrid(floorMap.gridData, width, height)
    }

    // Map obstacle entities by grid coordinate key "x_y" for O(1) overlay lookups
    val obstacleMap = remember(obstacles) {
        obstacles.associateBy { "${it.gridX}_${it.gridY}" }
    }

    var traversableCount = 0
    var obstacleCount = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            val cell = gridMatrix.getOrNull(y)?.getOrNull(x) ?: CellType.WALL
            val obs = obstacleMap["${x}_${y}"]
            val isObstacle = obs != null || isCellObstacle(cell)
            if (isObstacle) obstacleCount++ else traversableCount++
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("room_floor_grid_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        border = BorderStroke(1.5.dp, CyberCyan.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header: Map Identity & Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(CyberBrightGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = floorMap.floorName.uppercase(),
                            color = CyberTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "Sector: ${floorMap.sectorName} • Level ${floorMap.levelNumber} [F${floorMap.floorIndex}]",
                        color = CyberCyan,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${width}x${height} GRID",
                        color = CyberGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .background(CyberGreen.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .border(1.dp, CyberGreen.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Traversability vs Obstacle Summary HUD
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberSurfaceVariant.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .border(1.dp, CyberCyan.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Traversable Stats
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("stat_traversable_tiles")
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(CyberBrightGreen, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "TRAVERSABLE",
                            color = CyberBrightGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "$traversableCount Tiles",
                            color = CyberTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .width(1.dp)
                        .background(CyberCyan.copy(alpha = 0.3f))
                )

                // Obstacle Stats
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("stat_obstacle_tiles")
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(CyberCrimson, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "OBSTACLES",
                            color = CyberCrimson,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "$obstacleCount Nodes",
                            color = CyberTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .width(1.dp)
                        .background(CyberCyan.copy(alpha = 0.3f))
                )

                // Active Obstacles (Entity records)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("stat_active_obstacles")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Active Barriers",
                        tint = CyberAmber,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "BARRIERS",
                            color = CyberAmber,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${obstacles.size} Recorded",
                            color = CyberTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                GridFilterMode.entries.forEach { mode ->
                    FilterChip(
                        selected = selectedFilter == mode,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            selectedFilter = mode
                        },
                        label = {
                            Text(
                                text = mode.label,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (selectedFilter == mode) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = CyberDark,
                            selectedContainerColor = when (mode) {
                                GridFilterMode.ALL -> CyberCyan.copy(alpha = 0.2f)
                                GridFilterMode.TRAVERSABLE_ONLY -> CyberBrightGreen.copy(alpha = 0.2f)
                                GridFilterMode.OBSTACLES_ONLY -> CyberCrimson.copy(alpha = 0.2f)
                                GridFilterMode.INTERACTIVE_ONLY -> CyberAmber.copy(alpha = 0.2f)
                            },
                            labelColor = CyberCyan.copy(alpha = 0.7f),
                            selectedLabelColor = when (mode) {
                                GridFilterMode.ALL -> CyberCyan
                                GridFilterMode.TRAVERSABLE_ONLY -> CyberBrightGreen
                                GridFilterMode.OBSTACLES_ONLY -> CyberCrimson
                                GridFilterMode.INTERACTIVE_ONLY -> CyberAmber
                            }
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selectedFilter == mode,
                            borderColor = CyberCyan.copy(alpha = 0.3f),
                            selectedBorderColor = when (mode) {
                                GridFilterMode.ALL -> CyberCyan
                                GridFilterMode.TRAVERSABLE_ONLY -> CyberBrightGreen
                                GridFilterMode.OBSTACLES_ONLY -> CyberCrimson
                                GridFilterMode.INTERACTIVE_ONLY -> CyberAmber
                            }
                        ),
                        modifier = Modifier.testTag("filter_chip_${mode.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val cellSizeDp = 28.dp
            val cellSpacingDp = 2.dp
            val totalCellStep = cellSizeDp + cellSpacingDp

            val animatedPlayerX by animateDpAsState(
                targetValue = totalCellStep * floorMap.playerX.coerceIn(0, width - 1),
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "player_x_anim"
            )
            val animatedPlayerY by animateDpAsState(
                targetValue = totalCellStep * floorMap.playerY.coerceIn(0, height - 1),
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "player_y_anim"
            )
            val targetRotation = when (floorMap.playerDirection) {
                "NORTH" -> 270f
                "SOUTH" -> 90f
                "EAST" -> 0f
                "WEST" -> 180f
                else -> 0f
            }
            val animatedPlayerRotation by animateFloatAsState(
                targetValue = targetRotation,
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                label = "player_rot_anim"
            )

            // 2D Floor Map Grid Rendering Canvas / Matrix
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberDark, RoundedCornerShape(8.dp))
                    .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
                    .horizontalScroll(rememberScrollState())
                    .verticalScroll(rememberScrollState())
                    .testTag("room_grid_viewport")
            ) {
                Box {
                    // Base Grid Layout
                    Column(
                        verticalArrangement = Arrangement.spacedBy(cellSpacingDp)
                    ) {
                        for (y in 0 until height) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(cellSpacingDp)
                            ) {
                                for (x in 0 until width) {
                                    val cell = gridMatrix.getOrNull(y)?.getOrNull(x) ?: CellType.WALL
                                    val obstacleEntity = obstacleMap["${x}_${y}"]
                                    val isObstacle = obstacleEntity != null || isCellObstacle(cell)
                                    val isTraversable = !isObstacle || (obstacleEntity?.isPassable == true)
                                    val isPlayerHere = (x == floorMap.playerX && y == floorMap.playerY)
                                    val isSelected = selectedTileInfo?.x == x && selectedTileInfo?.y == y

                                    val isDimmed = when (selectedFilter) {
                                        GridFilterMode.ALL -> false
                                        GridFilterMode.TRAVERSABLE_ONLY -> !isTraversable
                                        GridFilterMode.OBSTACLES_ONLY -> !isObstacle
                                        GridFilterMode.INTERACTIVE_ONLY -> !(isInteractiveCell(cell) || obstacleEntity != null)
                                    }

                                    RoomGridTileCell(
                                        x = x,
                                        y = y,
                                        cellType = cell,
                                        obstacleEntity = obstacleEntity,
                                        isTraversable = isTraversable,
                                        isObstacle = isObstacle,
                                        isPlayerHere = isPlayerHere,
                                        isSelected = isSelected,
                                        isDimmed = isDimmed,
                                        playerFacing = floorMap.playerDirection,
                                        cellSize = cellSizeDp,
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            val tileInfo = GridTileVisualInfo(
                                                x = x,
                                                y = y,
                                                cellType = cell,
                                                isTraversable = isTraversable,
                                                isObstacle = isObstacle,
                                                isPlayerHere = isPlayerHere,
                                                obstacleEntity = obstacleEntity
                                            )
                                            selectedTileInfo = tileInfo
                                            onTileClicked?.invoke(x, y, cell, obstacleEntity)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Smooth Floating Player Token with Spring Motion & Pulse
                    Box(
                        modifier = Modifier
                            .offset(x = animatedPlayerX, y = animatedPlayerY)
                            .size(cellSizeDp)
                            .testTag("animated_player_token"),
                        contentAlignment = Alignment.Center
                    ) {
                        // Pulsing outer aura ring
                        Box(
                            modifier = Modifier
                                .size(cellSizeDp * 0.95f)
                                .background(CyberCyan.copy(alpha = 0.25f), CircleShape)
                                .border(1.5.dp, CyberCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Player Facing ${floorMap.playerDirection}",
                                tint = CyberBrightGreen,
                                modifier = Modifier
                                    .size(14.dp)
                                    .rotate(animatedPlayerRotation)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Selected Tile Inspector Card
            AnimatedVisibility(
                visible = selectedTileInfo != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                selectedTileInfo?.let { tile ->
                    TileInspectionDetailPanel(
                        tileInfo = tile,
                        onDismiss = { selectedTileInfo = null },
                        onObstacleAction = onObstacleAction
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Collapsible Legend
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isLegendExpanded = !isLegendExpanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SECTOR LEGEND & DISTINCTIONS",
                        color = CyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Icon(
                    imageVector = if (isLegendExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isLegendExpanded) "Collapse" else "Expand",
                    tint = CyberCyan,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = isLegendExpanded) {
                GridLegendPanel()
            }
        }
    }
}

/**
 * Individual 2D Grid Cell displaying distinct visual treatments for:
 * 1. Traversable Channels (Cyan/Green neon, open corridors, interactive loot/cache icons)
 * 2. Obstacles (Fortified metallic wall borders, laser grids, blast gates, virus nodes with hazard colors)
 * 3. Live Player token with facing arrow
 */
@Composable
fun RoomGridTileCell(
    x: Int,
    y: Int,
    cellType: CellType,
    obstacleEntity: FloorObstacleEntity?,
    isTraversable: Boolean,
    isObstacle: Boolean,
    isPlayerHere: Boolean,
    isSelected: Boolean,
    isDimmed: Boolean,
    playerFacing: String,
    cellSize: Dp = 28.dp,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // Base background colors based on traversability vs obstacle distinction
    val baseBgColor = when {
        isObstacle -> {
            when (obstacleEntity?.obstacleType) {
                "FIREWALL" -> CyberCrimson.copy(alpha = 0.35f)
                "LASER_GRID" -> CyberPink.copy(alpha = 0.35f)
                "SECURITY_GATE" -> CyberPurple.copy(alpha = 0.35f)
                "LOCKED_TERMINAL" -> CyberAmber.copy(alpha = 0.35f)
                "VIRUS_NODE" -> CyberCrimson.copy(alpha = 0.45f)
                else -> Color(0xFF0F1A15) // Solid Wall Barrier
            }
        }
        else -> {
            when (cellType) {
                CellType.SAFE_ZONE, CellType.PATH -> CyberBrightGreen.copy(alpha = 0.08f)
                CellType.DATA_STORE, CellType.SECRET_CACHE, CellType.SCAN_CACHE -> CyberAmber.copy(alpha = 0.18f)
                CellType.ENCRYPTED_PORTAL, CellType.ELEVATOR -> CyberCyan.copy(alpha = 0.22f)
                CellType.STAIRS_UP, CellType.STAIRS_DOWN -> CyberPurple.copy(alpha = 0.18f)
                else -> CyberBrightGreen.copy(alpha = 0.06f)
            }
        }
    }

    // Border color highlighting traversable vs obstacle state
    val borderColor = when {
        isSelected -> CyberPink
        isObstacle -> {
            if (obstacleEntity?.isPassable == true) CyberBrightGreen else CyberCrimson.copy(alpha = 0.8f)
        }
        cellType == CellType.ENCRYPTED_PORTAL || cellType == CellType.ELEVATOR -> CyberCyan
        cellType == CellType.DATA_STORE || cellType == CellType.SECRET_CACHE -> CyberAmber
        else -> CyberBrightGreen.copy(alpha = 0.35f)
    }

    val alphaMultiplier = if (isDimmed) 0.25f else 1f

    Box(
        modifier = Modifier
            .size(cellSize)
            .clip(RoundedCornerShape(3.dp))
            .background(baseBgColor.copy(alpha = baseBgColor.alpha * alphaMultiplier))
            .border(
                width = if (isSelected) 2.dp else if (isObstacle) 1.2.dp else 0.8.dp,
                color = borderColor.copy(alpha = borderColor.alpha * alphaMultiplier),
                shape = RoundedCornerShape(3.dp)
            )
            .clickable(onClick = onClick)
            .testTag("grid_cell_${x}_${y}"),
        contentAlignment = Alignment.Center
    ) {
        // Render Cell Icon / Symbol / Entity
        if (isPlayerHere) {
            // Player Token Marker with Facing Orientation
            Box(
                modifier = Modifier
                    .size(cellSize * 0.75f)
                    .background(CyberCyan.copy(alpha = 0.3f), CircleShape)
                    .border(1.5.dp, CyberCyan, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val rotationDegrees = when (playerFacing) {
                    "NORTH" -> 270f
                    "SOUTH" -> 90f
                    "EAST" -> 0f
                    "WEST" -> 180f
                    else -> 0f
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Player Facing $playerFacing",
                    tint = CyberBrightGreen,
                    modifier = Modifier
                        .size(14.dp)
                        .rotate(rotationDegrees)
                )
            }
        } else if (isObstacle) {
            // Obstacle Marker Icon
            val obstacleIcon = when (obstacleEntity?.obstacleType) {
                "SECURITY_GATE" -> if (obstacleEntity.isPassable) Icons.Default.CheckCircle else Icons.Default.Lock
                "LOCKED_TERMINAL" -> Icons.Default.Build
                "FIREWALL", "LASER_GRID" -> Icons.Default.Warning
                "VIRUS_NODE" -> Icons.Default.Warning
                else -> null
            }

            if (obstacleIcon != null) {
                Icon(
                    imageVector = obstacleIcon,
                    contentDescription = obstacleEntity?.name ?: "Obstacle",
                    tint = if (obstacleEntity?.isPassable == true) CyberBrightGreen else CyberCrimson,
                    modifier = Modifier.size(12.dp)
                )
            } else {
                // Default Wall / Solid Barrier glyph
                Text(
                    text = "#",
                    color = CyberCrimson.copy(alpha = 0.6f * alphaMultiplier),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            // Traversable Tile Symbol
            val traversableSymbol = when (cellType) {
                CellType.PATH -> "•"
                CellType.SAFE_ZONE -> "S"
                CellType.ENCRYPTED_PORTAL -> "P"
                CellType.DATA_STORE -> "D"
                CellType.SECRET_CACHE -> "C"
                CellType.ELEVATOR -> "X"
                CellType.STAIRS_UP -> "▲"
                CellType.STAIRS_DOWN -> "▼"
                CellType.SCAN_CACHE -> "M"
                CellType.VENT_TUNNEL -> "T"
                else -> "•"
            }

            val symbolColor = when (cellType) {
                CellType.ENCRYPTED_PORTAL, CellType.ELEVATOR -> CyberCyan
                CellType.DATA_STORE, CellType.SECRET_CACHE, CellType.SCAN_CACHE -> CyberAmber
                CellType.SAFE_ZONE -> CyberBrightGreen
                CellType.STAIRS_UP, CellType.STAIRS_DOWN -> CyberPurple
                else -> CyberBrightGreen.copy(alpha = 0.5f)
            }

            Text(
                text = traversableSymbol,
                color = symbolColor.copy(alpha = symbolColor.alpha * alphaMultiplier),
                fontSize = if (traversableSymbol == "•") 14.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

/**
 * Inspection detail panel displayed when a user taps any tile in the 2D grid.
 */
@Composable
fun TileInspectionDetailPanel(
    tileInfo: GridTileVisualInfo,
    onDismiss: () -> Unit,
    onObstacleAction: ((obstacle: FloorObstacleEntity) -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("tile_inspection_panel"),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CyberDark),
        border = BorderStroke(
            1.dp,
            if (tileInfo.isObstacle) CyberCrimson.copy(alpha = 0.8f) else CyberBrightGreen.copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (tileInfo.isObstacle) CyberCrimson else CyberBrightGreen,
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (tileInfo.isObstacle) "OBSTACLE BARRIER" else "TRAVERSABLE CHANNEL",
                        color = if (tileInfo.isObstacle) CyberCrimson else CyberBrightGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "COORDINATES [${tileInfo.x}, ${tileInfo.y}]",
                        color = CyberCyan,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Inspector",
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                color = CyberCyan.copy(alpha = 0.2f),
                modifier = Modifier.padding(vertical = 6.dp)
            )

            // Cell Type & Description
            Text(
                text = tileInfo.obstacleEntity?.name ?: tileInfo.cellType.displayName,
                color = CyberTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            if (tileInfo.obstacleEntity != null) {
                val obs = tileInfo.obstacleEntity
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = obs.description.ifBlank { "Hostile security partition blocking traversal across this node." },
                    color = CyberCyan.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Durability Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BARRIER INTEGRITY",
                        color = CyberAmber,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${obs.durability}/${obs.maxDurability} HP",
                        color = CyberAmber,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                LinearProgressIndicator(
                    progress = { (obs.durability.toFloat() / obs.maxDurability.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (obs.durability > 0) CyberCrimson else CyberBrightGreen,
                    trackColor = CyberDark
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Status Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusPill(
                        label = if (obs.isPassable) "PASSED / CLEARED" else "BLOCKED / IMPASSABLE",
                        color = if (obs.isPassable) CyberBrightGreen else CyberCrimson
                    )
                    if (obs.isHacked) {
                        StatusPill(label = "HACKED / DECRYPTED", color = CyberCyan)
                    }
                    if (obs.hackDifficulty > 0) {
                        StatusPill(label = "ICE LVL ${obs.hackDifficulty}", color = CyberPurple)
                    }
                }

                if (onObstacleAction != null && !obs.isPassable) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onObstacleAction(obs) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberCyan,
                            contentColor = CyberDark
                        ),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .testTag("action_interact_obstacle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = obs.interactionPrompt.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                val description = when (tileInfo.cellType) {
                    CellType.PATH -> "Open cyber-space data channel with zero resistance to movement."
                    CellType.SAFE_ZONE -> "Secure access portal for netrunner deployment and checkpoint storage."
                    CellType.ENCRYPTED_PORTAL -> "Sub-sector extraction gate leading to the next floor."
                    CellType.DATA_STORE -> "Encrypted memory fragment containing classified network intelligence."
                    CellType.SECRET_CACHE -> "Hidden hardware vault holding credits and utility components."
                    CellType.ELEVATOR -> "Express transit column connecting multi-floor levels."
                    CellType.STAIRS_UP, CellType.STAIRS_DOWN -> "Vertical staircase conduit between sub-floors."
                    CellType.WALL -> "Solid fortified firewall structure. Impassable."
                    else -> "Operational sector grid node."
                }
                Text(
                    text = description,
                    color = CyberCyan.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(6.dp))
                StatusPill(
                    label = if (tileInfo.isTraversable) "STATUS: TRAVERSABLE" else "STATUS: IMPASSABLE WALL",
                    color = if (tileInfo.isTraversable) CyberBrightGreen else CyberCrimson
                )
            }
        }
    }
}

/**
 * Status Pill Chip Component
 */
@Composable
fun StatusPill(label: String, color: Color) {
    Text(
        text = label,
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
            .border(0.8.dp, color.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/**
 * Visual Legend explaining Traversable vs Obstacle visual representations.
 */
@Composable
fun GridLegendPanel() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CyberDark, RoundedCornerShape(8.dp))
            .border(1.dp, CyberCyan.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "TRAVERSABLE FLOOR TILES (ACCESSIBLE)",
            color = CyberBrightGreen,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LegendItem(symbol = "•", label = "Path Channel", color = CyberBrightGreen)
            LegendItem(symbol = "S", label = "Access Point", color = CyberBrightGreen)
            LegendItem(symbol = "P", label = "Exit Portal", color = CyberCyan)
            LegendItem(symbol = "D", label = "Data Cache", color = CyberAmber)
        }

        HorizontalDivider(color = CyberCyan.copy(alpha = 0.15f))

        Text(
            text = "OBSTACLES & BARRIERS (RESTRICTED / IMPASSABLE)",
            color = CyberCrimson,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LegendItem(symbol = "#", label = "Solid Wall", color = CyberCrimson)
            LegendItem(symbol = "G", label = "Blast Gate", color = CyberPurple)
            LegendItem(symbol = "K", label = "Terminal", color = CyberAmber)
            LegendItem(symbol = "V", label = "Virus Node", color = CyberCrimson)
        }
    }
}

@Composable
private fun LegendItem(symbol: String, label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                .border(0.8.dp, color, RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = symbol,
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        Text(
            text = label,
            color = CyberTextPrimary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * Helper to determine if a raw CellType is fundamentally an obstacle.
 */
private fun isCellObstacle(cell: CellType): Boolean {
    return when (cell) {
        CellType.WALL,
        CellType.SECRET_WALL,
        CellType.VIRUS_NODE,
        CellType.TERMINAL_DOOR,
        CellType.HACKABLE_TERMINAL -> true
        else -> false
    }
}

/**
 * Helper to determine if a cell is an interactive node.
 */
private fun isInteractiveCell(cell: CellType): Boolean {
    return when (cell) {
        CellType.HACKABLE_TERMINAL,
        CellType.TERMINAL_DOOR,
        CellType.DATA_STORE,
        CellType.SECRET_CACHE,
        CellType.SCAN_CACHE,
        CellType.ENCRYPTED_PORTAL,
        CellType.ELEVATOR,
        CellType.STAIRS_UP,
        CellType.STAIRS_DOWN -> true
        else -> false
    }
}
