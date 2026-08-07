package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import com.example.ui.components.CyberToastHost
import com.example.ui.components.CyberToastType
import com.example.ui.components.rememberCyberToastHostState
import com.example.ui.components.VisualTurnIndicator
import com.example.ui.components.CombatHackingMinigameView
import com.example.ui.components.CyberVitalStatusHud
import com.example.ui.components.AnimatedCyberHudConsole
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.GameViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Modifier.repeatingClickable(
    enabled: Boolean = true,
    initialDelayMillis: Long = 200,
    repeatDelayMillis: Long = 120,
    onClick: () -> Unit
): Modifier = this.pointerInput(enabled, onClick) {
    if (!enabled) return@pointerInput
    coroutineScope {
        detectTapGestures(
            onPress = {
                val delayJob = launch {
                    onClick()
                    delay(initialDelayMillis)
                    while (isActive) {
                        onClick()
                        delay(repeatDelayMillis)
                    }
                }
                try {
                    awaitRelease()
                } finally {
                    delayJob.cancel()
                }
            }
        )
    }
}

@Composable
fun RepeatingNavigationButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, CyberBorderLight.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .background(CyberMutedGreen.copy(alpha = 0.5f))
            .repeatingClickable(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val highScores by viewModel.runRecords.collectAsStateWithLifecycle()
    val view = LocalView.current

    val toastHostState = rememberCyberToastHostState()

    // Observe latest log message to trigger instant cyber toasts for pickups, access denial, health/RAM regen
    val latestLog = uiState.logFeed.firstOrNull()
    LaunchedEffect(latestLog) {
        latestLog?.let { log ->
            val text = log.text
            when {
                text.contains("OBTAINED", ignoreCase = true) ||
                text.contains("ACQUIRED", ignoreCase = true) ||
                text.contains("TRANSFER COMPLETE", ignoreCase = true) ||
                text.contains("INSTALLED CYBERWARE", ignoreCase = true) ||
                text.contains("PURCHASED", ignoreCase = true) -> {
                    toastHostState.showItemPickup(text)
                }
                text.contains("ERROR", ignoreCase = true) ||
                text.contains("DENIED", ignoreCase = true) ||
                text.contains("LOCKOUT", ignoreCase = true) ||
                text.contains("INVALID", ignoreCase = true) -> {
                    toastHostState.showAccessDenied(text)
                }
                text.contains("HP", ignoreCase = true) ||
                text.contains("RESTORED", ignoreCase = true) ||
                text.contains("REPAIRED", ignoreCase = true) ||
                text.contains("INTEGRITY", ignoreCase = true) -> {
                    if (!text.contains("DAMAGE", ignoreCase = true) && !text.contains("DEALT", ignoreCase = true)) {
                        toastHostState.showHealthRegen(25)
                    }
                }
                text.contains("RAM RECOVERY", ignoreCase = true) ||
                text.contains("RAM RELEASED", ignoreCase = true) -> {
                    toastHostState.showRamRegen(4)
                }
            }
        }
    }

    // Interactive name text state for creation screen
    var runnerNameInput by remember { mutableStateOf("") }
    var selectedClass by remember { mutableStateOf(NetrunnerClass.CODE_SLASHER) }

    // Focus management for hardware keys
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(uiState.screen) {
        if (uiState.screen == GameViewModel.ActiveScreen.EXPLORATION) {
            focusRequester.requestFocus()
        }
    }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Base background with modern high-density cyan grid layout and subtle scanlines (highly optimized)
    val gridSpacing = 32.dp
    val scanlineHeight = 12.dp
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                drawContent()
                if (uiState.activeWeather == com.example.data.CyberWeather.COLD_SPOT) {
                    drawRect(
                        color = Color(0xFF4B617F).copy(alpha = 0.50f),
                        blendMode = androidx.compose.ui.graphics.BlendMode.Color
                    )
                }
            }
            .background(CyberDark)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && uiState.screen == GameViewModel.ActiveScreen.EXPLORATION) {
                    val isCombat = uiState.gameState != GameState.EXPLORATION
                    val isFirstPress = keyEvent.nativeKeyEvent.repeatCount == 0
                    if (isCombat && !isFirstPress) {
                        return@onKeyEvent true
                    }
                    when (keyEvent.key) {
                        Key.W, Key.DirectionUp -> {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.moveForward()
                            true
                        }
                        Key.S, Key.DirectionDown -> {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.moveBackward()
                            true
                        }
                        Key.A, Key.DirectionLeft -> {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.turnLeft()
                            true
                        }
                        Key.D, Key.DirectionRight -> {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.turnRight()
                            true
                        }
                        Key.E, Key.Spacebar, Key.Enter -> {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            viewModel.interact()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .drawWithCache {
                val spacingPx = gridSpacing.toPx()
                val strokePx = 1.dp.toPx()
                val scanlineHeightPx = scanlineHeight.toPx()
                onDrawBehind {
                    // 1. Draw highly optimized vertical/horizontal grid lines (30x faster than thousands of drawCircle calls)
                    var x = 0f
                    while (x < size.width) {
                        drawLine(
                            color = Color(0x0800F3FF), // Neon Cyan grid line at 3% opacity
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = strokePx
                        )
                        x += spacingPx
                    }
                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            color = Color(0x0800F3FF), // Neon Cyan grid line at 3% opacity
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = strokePx
                        )
                        y += spacingPx
                    }

                    // 2. Draw CRT subtle scanlines with lower density
                    var yScan = 0f
                    while (yScan < size.height) {
                        drawLine(
                            color = Color(0x0600F3FF), // Faint scanline
                            start = Offset(0f, yScan),
                            end = Offset(size.width, yScan),
                            strokeWidth = strokePx
                        )
                        yScan += scanlineHeightPx
                    }
                }
            }
            .padding(6.dp)
    ) {
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Left main section: Active Screen Viewport (interactive exploration/combat/character/menus)
                Box(
                    modifier = Modifier
                        .weight(1.75f)
                        .fillMaxHeight()
                ) {
                    when (uiState.screen) {
                        GameViewModel.ActiveScreen.START_MENU -> {
                            StartMenuView(
                                viewModel = viewModel,
                                hasSavedGame = viewModel.hasSavedGame(),
                                isActiveRun = uiState.runnerName.isNotEmpty() && uiState.integrity > 0,
                                onStartNewRun = {
                                    runnerNameInput = ""
                                    viewModel.startNewRun()
                                },
                                onLoadGame = { viewModel.loadGame() },
                                onSaveGame = { viewModel.saveGame() },
                                onResumeGame = { viewModel.resumeGame() },
                                onLeaderboardClick = { viewModel.viewLeaderboard() }
                            )
                        }
                        GameViewModel.ActiveScreen.CHARACTER_CREATION -> {
                            CharacterCreationView(
                                runnerName = runnerNameInput,
                                onNameChange = { runnerNameInput = it },
                                selectedClass = selectedClass,
                                onClassSelected = { selectedClass = it },
                                selectedImplant = uiState.selectedStartingImplant,
                                onImplantSelected = { viewModel.selectStartingImplant(it) },
                                onStartGame = { viewModel.createCharacter(runnerNameInput, selectedClass, uiState.selectedStartingImplant) }
                            )
                        }
                        GameViewModel.ActiveScreen.CYBERWARE_CLINIC -> {
                            CyberneticsClinicView(
                                uiState = uiState,
                                viewModel = viewModel,
                                onCloseClinic = { viewModel.closeCyberwareClinic() }
                            )
                        }
                        GameViewModel.ActiveScreen.EXPLORATION -> {
                            ExplorationView(
                                uiState = uiState,
                                viewModel = viewModel,
                                onShopClick = { viewModel.enterShop() },
                                onSafeDisconnect = { viewModel.disconnectRunSuccessfully() }
                            )
                        }
                        GameViewModel.ActiveScreen.COMBAT -> {
                            CombatView(
                                uiState = uiState,
                                onExecuteProgram = { viewModel.executeCombatProgram(it) },
                                onFlee = { viewModel.fleeCombat() },
                                onAttack = { viewModel.combatAttack() },
                                onSetCombatStyle = { viewModel.setCombatStyle(it) },
                                onDefend = { viewModel.combatDefend() },
                                onHack = { viewModel.combatHack() },
                                onScan = { viewModel.combatScan() },
                                onUseItem = { viewModel.useInventoryItem(it) },
                                onEndTurn = { viewModel.endTurn() },
                                onSelectSymbol = { viewModel.selectCombatHackSymbol(it) },
                                onClearHackBuffer = { viewModel.clearCombatHackBuffer() },
                                onAbortHack = { viewModel.abortCombatHack() }
                            )
                        }
                        GameViewModel.ActiveScreen.HACKING_MINIGAME -> {
                            HackingMinigableView(
                                uiState = uiState,
                                onCellSelected = { r, c -> viewModel.hackCell(r, c) },
                                onCancel = { viewModel.exitHackingMinigame() }
                            )
                        }
                        GameViewModel.ActiveScreen.UPGRADE_STORE -> {
                            UpgradeStoreView(
                                uiState = uiState,
                                onBuyCyberware = { viewModel.purchaseCyberware(it) },
                                onBuyConsumable = { name, cost -> viewModel.purchaseConsumable(name, cost) },
                                onExit = { viewModel.exitShop() }
                            )
                        }
                        GameViewModel.ActiveScreen.LEADERBOARD -> {
                            LeaderboardView(
                                scores = highScores,
                                onClearScores = { viewModel.clearHighScores() },
                                onExit = { viewModel.exitLeaderboard() }
                            )
                        }
                        GameViewModel.ActiveScreen.GAME_OVER -> {
                            GameOverView(
                                uiState = uiState,
                                onRestart = {
                                    runnerNameInput = ""
                                    viewModel.restartGame()
                                }
                            )
                        }
                        GameViewModel.ActiveScreen.SVDAG_WORLD_BUILDER -> {
                            val dag = uiState.svdagWorld
                            val stats = uiState.svdagStats
                            if (dag != null && stats != null) {
                                SvdagWorldInspectorScreen(
                                    currentDag = dag,
                                    currentStats = stats,
                                    scanSummary = uiState.svdagScanSummary,
                                    scanRippleState = uiState.svdagRippleState,
                                    onTriggerScan = { ox, oy, oz, radius -> viewModel.triggerSvdagScan(ox, oy, oz, radius) },
                                    onRegenerateDag = { depth, seed -> viewModel.initOrRegenerateSvdag(depth, seed) },
                                    onModifyVoxel = { x, y, z, type -> viewModel.modifySvdagVoxel(x, y, z, type) },
                                    onBackToGame = { viewModel.exitSvdagWorldInspector() }
                                )
                            }
                        }
                    }
                }

                // Right side column: Header + Vital Status HUD + Animated Console + High-density Controls
                Column(
                    modifier = Modifier
                        .weight(0.65f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TerminalHeader(
                        uiState = uiState,
                        onLeaderboardClick = { viewModel.viewLeaderboard() },
                        onMenuClick = { viewModel.returnToStartMenu() }
                    )

                    if (uiState.screen != GameViewModel.ActiveScreen.START_MENU &&
                        uiState.screen != GameViewModel.ActiveScreen.CHARACTER_CREATION) {
                        CyberVitalStatusHud(uiState = uiState)
                        AnimatedCyberHudConsole(
                            uiState = uiState,
                            onSendCommand = { viewModel.runTerminalCommand(it) },
                            modifier = Modifier.weight(0.45f)
                        )
                        HighDensityBottomNavigation(
                            currentScreen = uiState.screen,
                            viewModel = viewModel
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Retro Cyber Terminal Header
                TerminalHeader(
                    uiState = uiState,
                    onLeaderboardClick = { viewModel.viewLeaderboard() },
                    onMenuClick = { viewModel.returnToStartMenu() }
                )

                if (uiState.screen != GameViewModel.ActiveScreen.START_MENU &&
                    uiState.screen != GameViewModel.ActiveScreen.CHARACTER_CREATION) {
                    Spacer(modifier = Modifier.height(2.dp))
                    CyberVitalStatusHud(uiState = uiState)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Body depending on active screen
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (uiState.screen) {
                        GameViewModel.ActiveScreen.START_MENU -> {
                            StartMenuView(
                                viewModel = viewModel,
                                hasSavedGame = viewModel.hasSavedGame(),
                                isActiveRun = uiState.runnerName.isNotEmpty() && uiState.integrity > 0,
                                onStartNewRun = {
                                    runnerNameInput = ""
                                    viewModel.startNewRun()
                                },
                                onLoadGame = { viewModel.loadGame() },
                                onSaveGame = { viewModel.saveGame() },
                                onResumeGame = { viewModel.resumeGame() },
                                onLeaderboardClick = { viewModel.viewLeaderboard() }
                            )
                        }
                        GameViewModel.ActiveScreen.CHARACTER_CREATION -> {
                            CharacterCreationView(
                                runnerName = runnerNameInput,
                                onNameChange = { runnerNameInput = it },
                                selectedClass = selectedClass,
                                onClassSelected = { selectedClass = it },
                                selectedImplant = uiState.selectedStartingImplant,
                                onImplantSelected = { viewModel.selectStartingImplant(it) },
                                onStartGame = { viewModel.createCharacter(runnerNameInput, selectedClass, uiState.selectedStartingImplant) }
                            )
                        }
                        GameViewModel.ActiveScreen.CYBERWARE_CLINIC -> {
                            CyberneticsClinicView(
                                uiState = uiState,
                                viewModel = viewModel,
                                onCloseClinic = { viewModel.closeCyberwareClinic() }
                            )
                        }
                        GameViewModel.ActiveScreen.EXPLORATION -> {
                            ExplorationView(
                                uiState = uiState,
                                viewModel = viewModel,
                                onShopClick = { viewModel.enterShop() },
                                onSafeDisconnect = { viewModel.disconnectRunSuccessfully() }
                            )
                        }
                        GameViewModel.ActiveScreen.COMBAT -> {
                            CombatView(
                                uiState = uiState,
                                onExecuteProgram = { viewModel.executeCombatProgram(it) },
                                onFlee = { viewModel.fleeCombat() },
                                onAttack = { viewModel.combatAttack() },
                                onSetCombatStyle = { viewModel.setCombatStyle(it) },
                                onDefend = { viewModel.combatDefend() },
                                onHack = { viewModel.combatHack() },
                                onScan = { viewModel.combatScan() },
                                onUseItem = { viewModel.useInventoryItem(it) },
                                onEndTurn = { viewModel.endTurn() },
                                onSelectSymbol = { viewModel.selectCombatHackSymbol(it) },
                                onClearHackBuffer = { viewModel.clearCombatHackBuffer() },
                                onAbortHack = { viewModel.abortCombatHack() }
                            )
                        }
                        GameViewModel.ActiveScreen.HACKING_MINIGAME -> {
                            HackingMinigableView(
                                uiState = uiState,
                                onCellSelected = { r, c -> viewModel.hackCell(r, c) },
                                onCancel = { viewModel.exitHackingMinigame() }
                            )
                        }
                        GameViewModel.ActiveScreen.UPGRADE_STORE -> {
                            UpgradeStoreView(
                                uiState = uiState,
                                onBuyCyberware = { viewModel.purchaseCyberware(it) },
                                onBuyConsumable = { name, cost -> viewModel.purchaseConsumable(name, cost) },
                                onExit = { viewModel.exitShop() }
                            )
                        }
                        GameViewModel.ActiveScreen.LEADERBOARD -> {
                            LeaderboardView(
                                scores = highScores,
                                onClearScores = { viewModel.clearHighScores() },
                                onExit = { viewModel.exitLeaderboard() }
                            )
                        }
                        GameViewModel.ActiveScreen.GAME_OVER -> {
                            GameOverView(
                                uiState = uiState,
                                onRestart = {
                                    runnerNameInput = ""
                                    viewModel.restartGame()
                                }
                            )
                        }
                        GameViewModel.ActiveScreen.SVDAG_WORLD_BUILDER -> {
                            val dag = uiState.svdagWorld
                            val stats = uiState.svdagStats
                            if (dag != null && stats != null) {
                                SvdagWorldInspectorScreen(
                                    currentDag = dag,
                                    currentStats = stats,
                                    scanSummary = uiState.svdagScanSummary,
                                    scanRippleState = uiState.svdagRippleState,
                                    onTriggerScan = { ox, oy, oz, radius -> viewModel.triggerSvdagScan(ox, oy, oz, radius) },
                                    onRegenerateDag = { depth, seed -> viewModel.initOrRegenerateSvdag(depth, seed) },
                                    onModifyVoxel = { x, y, z, type -> viewModel.modifySvdagVoxel(x, y, z, type) },
                                    onBackToGame = { viewModel.exitSvdagWorldInspector() }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Animated Cyber HUD Console Ticker & Navigation Controls
                if (uiState.screen != GameViewModel.ActiveScreen.START_MENU &&
                    uiState.screen != GameViewModel.ActiveScreen.CHARACTER_CREATION) {
                    AnimatedCyberHudConsole(
                        uiState = uiState,
                        onSendCommand = { viewModel.runTerminalCommand(it) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    HighDensityBottomNavigation(
                        currentScreen = uiState.screen,
                        viewModel = viewModel
                    )
                }
            }
        }

        // Custom Cyberpunk Snackbar/Toast Overlay Host
        CyberToastHost(
            hostState = toastHostState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

// ==========================================
// Sub-Composable: High Density Progress Bar
// ==========================================
@Composable
fun HighDensityProgressBar(
    current: Int,
    max: Int,
    isGradient: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val fraction = if (max > 0) (current.toFloat() / max).coerceIn(0f, 1f) else 0f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(Color(0xFF1A1F26))
            .border(1.dp, CyberBorder, RoundedCornerShape(100.dp))
    ) {
        if (isGradient) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(CyberCyan, CyberBlueGradientEnd)
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(color)
            )
        }
    }
}

// ==========================================
// Sub-Composable: Terminal Header
// ==========================================
@Composable
fun TerminalHeader(
    uiState: GameViewModel.GameUiState,
    onLeaderboardClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        border = BorderStroke(1.dp, CyberBorder),
        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Level & stats summary in a single clean horizontal line
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val locationText = when (uiState.currentZone) {
                    com.example.data.Zone.BUILDING -> {
                        val floorTheme = when (uiState.buildingFloor) {
                            1 -> "Residential"
                            2 -> "Office"
                            3 -> "Technical"
                            4 -> "Storage"
                            else -> "Unknown"
                        }
                        "🏢 Floor ${uiState.buildingFloor}: $floorTheme"
                    }
                    com.example.data.Zone.COLLECTORS -> "🌀 TUNNELS L${uiState.collectorsLevel}"
                    com.example.data.Zone.CITY -> {
                        val district = when (uiState.cityDistrictIndex) {
                            0 -> "Neon District"
                            1 -> "Tech Plaza"
                            else -> "Corp Core"
                        }
                        "🏙️ CITY [$district]"
                    }
                }
                Text(
                    text = locationText,
                    color = CyberAmber,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.5.sp
                )
                Text(
                    text = "|",
                    color = CyberBorder,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.5.sp
                )
                Text(
                    text = "CORE: ${uiState.integrity}%",
                    color = CyberCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "|",
                    color = CyberBorder,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.5.sp
                )
                Text(
                    text = "RAM: ${uiState.ram}MB",
                    color = CyberPink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Right: ONLINE status indicator & logs action button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (uiState.screen != GameViewModel.ActiveScreen.START_MENU) {
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("menu_pause_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "System Menu",
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onLeaderboardClick,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("leaderboard_tab_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "Mainframe Logs",
                        tint = CyberCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CyberCyan)
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "ONLN",
                        color = CyberDark,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ==========================================
// Sub-Composable: Character Creation Screen
// ==========================================
@Composable
fun CharacterCreationView(
    runnerName: String,
    onNameChange: (String) -> Unit,
    selectedClass: NetrunnerClass,
    onClassSelected: (NetrunnerClass) -> Unit,
    selectedImplant: CyberwareImplant,
    onImplantSelected: (CyberwareImplant) -> Unit,
    onStartGame: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = """
     _  _  ____ ____  ___  ____   __   _  _  __    ____ ____ 
    ( \( )(  __)(_  _)/ __)(  _ \ / _\ ( \/ )(  )  (  __)(  _ \
     )  (  ) _)   )( ( (__  )   //    \/ \/ \/ (_/\ ) _)  )   /
    (_)\_)(____) (__) \___)(_)\_)\_/\_/\_/\_/\____/(____)(_)\_)
            """.trimIndent(),
            color = CyberGreen,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            lineHeight = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Text(
            text = "--- INITIALIZE NETRUNNER CYBERNET INTERRUPT ---",
            color = CyberGreen,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Name input field
        OutlinedTextField(
            value = runnerName,
            onValueChange = onNameChange,
            label = { Text("Runner Handle Name", color = CyberCyan, fontFamily = FontFamily.Monospace) },
            textStyle = LocalTextStyle.current.copy(color = CyberCyan, fontFamily = FontFamily.Monospace),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = CyberCyan,
                unfocusedIndicatorColor = CyberBorder,
                focusedLabelColor = CyberCyan,
                unfocusedLabelColor = CyberMutedText
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("runner_name_input")
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "CHOOSE CLASS ARCHETYPE:",
            color = CyberCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start).padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )

        // Grid of classes
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NetrunnerClass.VALUES.forEach { classType ->
                val isSelected = classType == selectedClass
                Card(
                    onClick = { onClassSelected(classType) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) CyberMutedGreen else CyberCardBg
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) CyberCyan else CyberBorder
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("class_card_${classType.name}")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = classType.title,
                                color = if (isSelected) CyberCyan else CyberBrightGreen,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            )
                            Row {
                                Text(
                                    text = "${classType.baseIntegrity}HP / ${classType.baseRam}RAM",
                                    color = CyberCyan,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                               )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = classType.description,
                            color = CyberBrightGreen,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "PASSIVE: ${classType.passiveDesc}",
                            color = CyberPink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "SELECT STARTER CYBERNETIC IMPLANT:",
            color = CyberCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start).padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CyberwareImplantRegistry.STARTER_IMPLANTS.forEach { implant ->
                val isSelected = implant.id == selectedImplant.id
                Card(
                    onClick = { onImplantSelected(implant) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) CyberMutedGreen else CyberCardBg
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) CyberCyan else CyberBorder
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("implant_card_${implant.id}")
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${implant.icon} ${implant.name}",
                                color = if (isSelected) CyberCyan else CyberBrightGreen,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "[${implant.slot.displayName.uppercase()}]",
                                color = CyberPink,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = implant.description,
                            color = CyberBrightGreen,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                        if (implant.passiveAbility != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "⚡ PASSIVE: ${implant.passiveAbility.title} - ${implant.passiveAbility.description}",
                                color = CyberAmber,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Compile Button
        Button(
            onClick = onStartGame,
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp)
                .testTag("compile_profile_button")
        ) {
            Text(
                text = "COMPILE PROFILE AND INJECT DATA",
                color = Color.Black,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

// ==========================================
// Sub-Composable: Cybernetics Clinic Screen
// ==========================================
@Composable
fun CyberneticsClinicView(
    uiState: GameViewModel.GameUiState,
    viewModel: GameViewModel,
    onCloseClinic: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberDark)
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title banner
        Text(
            text = "--- CYBERNETIC SURGERY & IMPLANT MATRIX ---",
            color = CyberCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "ANATOMICAL CHASSIS OVERCLOCK // NEURAL & BIOMETRIC MODIFICATIONS",
            color = CyberMutedText,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Overclock Stats Summary Card
        val totalHp = uiState.installedImplants.values.sumOf { it?.integrityBonus ?: 0 }
        val totalRam = uiState.installedImplants.values.sumOf { it?.ramBonus ?: 0 }
        val totalRec = uiState.installedImplants.values.sumOf { it?.recoveryBonus ?: 0 }
        val totalDmg = uiState.installedImplants.values.sumOf { it?.damageBonus ?: 0 }
        val totalDef = uiState.installedImplants.values.sumOf { it?.defenseBonus ?: 0 }

        Card(
            colors = CardDefaults.cardColors(containerColor = CyberCardBg),
            border = BorderStroke(1.dp, CyberCyan),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "ACTIVE IMPLANT OVERCLOCK BONUSES",
                    color = CyberGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("💖 HP +$totalHp", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("⚡ RAM +$totalRam", color = CyberPink, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("🔋 REC +$totalRec/t", color = CyberAmber, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("🗡️ DMG +$totalDmg", color = CyberBrightGreen, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("🛡️ DEF +$totalDef%", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Text(
            text = "ANATOMICAL BODY SLOTS:",
            color = CyberCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
        )

        // 5 Anatomical Slots Grid/List
        ImplantBodySlot.values().forEach { slot ->
            val installed = uiState.installedImplants[slot]
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                border = BorderStroke(1.dp, if (installed != null) CyberCyan else CyberBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .testTag("clinic_slot_${slot.name}")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${slot.icon} ${slot.displayName.uppercase()}",
                            color = CyberGreen,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        if (installed != null) {
                            Text(
                                text = installed.rarity.displayName.uppercase(),
                                color = when(installed.rarity) {
                                    ItemRarity.COMMON -> CyberBrightGreen
                                    ItemRarity.UNCOMMON -> CyberCyan
                                    ItemRarity.RARE -> CyberPink
                                    ItemRarity.EPIC -> CyberAmber
                                    ItemRarity.LEGENDARY -> CyberPink
                                },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text("VACANT SLOT", color = CyberMutedText, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (installed != null) {
                        Text(
                            text = installed.name,
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = installed.description,
                            color = CyberBrightGreen,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            lineHeight = 11.sp
                        )
                        if (installed.passiveAbility != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "⚡ PASSIVE: ${installed.passiveAbility.title} - ${installed.passiveAbility.description}",
                                color = CyberAmber,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.uninstallImplant(slot) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberPink),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.align(Alignment.End).height(32.dp).testTag("btn_uninstall_${slot.name}")
                        ) {
                            Text("UNINSTALL IMPLANT", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            text = "No cyberware unit currently fitted into this chassis socket.",
                            color = CyberMutedText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "CYBERNETICS CATALOG (AVAILABLE IMPLANTS):",
            color = CyberCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
        )

        CyberwareImplantRegistry.ALL_IMPLANTS.forEach { implant ->
            val isAlreadyInstalled = uiState.installedImplants[implant.slot]?.id == implant.id
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                border = BorderStroke(1.dp, CyberBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .testTag("catalog_implant_${implant.id}")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${implant.icon} ${implant.name} [${implant.slot.displayName.uppercase()}]",
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Text(
                            text = implant.description,
                            color = CyberBrightGreen,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                        if (implant.passiveAbility != null) {
                            Text(
                                text = "⚡ ${implant.passiveAbility.title}",
                                color = CyberAmber,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Button(
                        onClick = { viewModel.installImplant(implant) },
                        enabled = !isAlreadyInstalled,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp).padding(start = 8.dp).testTag("btn_install_${implant.id}")
                    ) {
                        Text(if (isAlreadyInstalled) "INSTALLED" else "INSTALL", color = Color.Black, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onCloseClinic,
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(44.dp).testTag("btn_exit_clinic")
        ) {
            Text("RETURN TO MAINFRAME", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

// ==========================================
// Sub-Composable: Exploration Screen HUD
// ==========================================
@Composable
fun ExplorationView(
    uiState: GameViewModel.GameUiState,
    viewModel: GameViewModel,
    onShopClick: () -> Unit,
    onSafeDisconnect: () -> Unit
) {
    val view = LocalView.current
    val fadeAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = uiState.fadeAlpha,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 350, easing = androidx.compose.animation.core.LinearEasing),
        label = "TransitionFade"
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
        // Main split: Left Viewport (3D ASCII), Right Panel (Map + Stats)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.4f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Left Viewport (ASCII wireframe)
            Column(
                modifier = Modifier
                    .weight(1.55f)
                    .fillMaxHeight()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    border = BorderStroke(1.dp, CyberBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                            .pointerInput(Unit) {
                                var totalDragX = 0f
                                var totalDragY = 0f
                                detectDragGestures(
                                    onDragStart = {
                                        totalDragX = 0f
                                        totalDragY = 0f
                                    },
                                    onDragEnd = {
                                        val threshold = 40f
                                        if (Math.abs(totalDragX) > Math.abs(totalDragY)) {
                                            if (totalDragX > threshold) {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                viewModel.turnRight()
                                            } else if (totalDragX < -threshold) {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                viewModel.turnLeft()
                                            }
                                        } else {
                                            if (totalDragY > threshold) {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                viewModel.moveBackward()
                                            } else if (totalDragY < -threshold) {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                viewModel.moveForward()
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        totalDragX = 0f
                                        totalDragY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        totalDragX += dragAmount.x
                                        totalDragY += dragAmount.y
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Crossfade(
                            targetState = uiState.perspectiveText,
                            animationSpec = tween(120),
                            label = "perspective_crossfade"
                        ) { _ ->
                            FirstPersonPerspectiveCanvas(
                                uiState = uiState,
                                modifier = Modifier.fillMaxSize().testTag("first_person_viewport"),
                                isCombat = (uiState.gameState != GameState.EXPLORATION),
                                onInteract = { viewModel.interact() }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Tactical Navigation or Combat Actions panel under 3D wireframe
                val cardBorderColor by animateColorAsState(
                    targetValue = if (uiState.gameState != GameState.EXPLORATION) CyberPink else CyberBorder,
                    animationSpec = tween(durationMillis = 500),
                    label = "CardBorderColor"
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                    border = BorderStroke(1.dp, cardBorderColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AnimatedContent(
                        targetState = uiState.gameState,
                        transitionSpec = {
                            (slideInVertically(animationSpec = tween(400)) { height -> height } + fadeIn(animationSpec = tween(400)))
                                .togetherWith(slideOutVertically(animationSpec = tween(400)) { height -> -height } + fadeOut(animationSpec = tween(400)))
                        },
                        label = "CombatPanelTransition"
                    ) { targetState ->
                        if (targetState == GameState.EXPLORATION) {
                            Column(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // UP arrow (Custom repeating navigation button)
                                RepeatingNavigationButton(
                                    onClick = { viewModel.moveForward() },
                                    icon = { Icon(Icons.Default.KeyboardArrowUp, "Forward", tint = CyberCyan, modifier = Modifier.size(20.dp)) },
                                    modifier = Modifier.testTag("btn_move_forward")
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RepeatingNavigationButton(
                                        onClick = { viewModel.turnLeft() },
                                        icon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Turn Left", tint = CyberCyan, modifier = Modifier.size(20.dp)) },
                                        modifier = Modifier.testTag("btn_turn_left")
                                    )

                                    RepeatingNavigationButton(
                                        onClick = { viewModel.moveBackward() },
                                        icon = { Icon(Icons.Default.KeyboardArrowDown, "Backward", tint = CyberCyan, modifier = Modifier.size(20.dp)) },
                                        modifier = Modifier.testTag("btn_move_back")
                                    )

                                    RepeatingNavigationButton(
                                        onClick = { viewModel.turnRight() },
                                        icon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Turn Right", tint = CyberCyan, modifier = Modifier.size(20.dp)) },
                                        modifier = Modifier.testTag("btn_turn_right")
                                    )
                                }
                            }
                        } else {
                            // GameState.COMBAT: Seamless tactical battle panel!
                            val enemy = uiState.activeEnemy
                            Column(
                                modifier = Modifier
                                    .padding(6.dp)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                VisualTurnIndicator(
                                    combatTurn = uiState.combatTurn,
                                    isCombatInputEnabled = uiState.isCombatInputEnabled,
                                    bannerMessage = uiState.showCombatBanner,
                                    compactMode = true
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "⚠️ HOSTILE INTRUDER DETECTED // ${enemy?.name ?: "UNKNOWN"}",
                                        color = CyberPink,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 8.sp
                                    )
                                    Text(
                                        text = "HP: ${enemy?.integrity ?: 0}/${enemy?.maxIntegrity ?: 0}",
                                        color = CyberPink,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                ProgressBarRetro(
                                    current = enemy?.integrity ?: 0,
                                    max = enemy?.maxIntegrity ?: 1,
                                    color = CyberPink,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                // Real-Time Enemy Decryption Compile Bar (System Shock cyber style)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "💥 HOSTILE DECRYPTION PACKET:",
                                        color = CyberAmber,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${(uiState.enemyAttackCharge * 100).toInt()}% COMPILING",
                                        color = CyberAmber,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                ProgressBarRetro(
                                    current = (uiState.enemyAttackCharge * 100).toInt(),
                                    max = 100,
                                    color = CyberAmber,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )

                                 // Active Hacking Pattern Minigame vs Standard Combat Actions
                                 if (uiState.activeCombatHack != null) {
                                     CombatHackingMinigameView(
                                         hackState = uiState.activeCombatHack,
                                         onSelectSymbol = { viewModel.selectCombatHackSymbol(it) },
                                         onClearBuffer = { viewModel.clearCombatHackBuffer() },
                                         onAbort = { viewModel.abortCombatHack() },
                                         modifier = Modifier.padding(vertical = 4.dp)
                                     )
                                 } else {
                                     // Row 1: Primary Combat Actions (Attack, Defend, Item, Flee)
                                     var showItemMenu by remember { mutableStateOf(false) }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // 1. Attack Button
                                    Button(
                                        onClick = { viewModel.combatAttack() },
                                        enabled = uiState.isCombatInputEnabled,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFE11D48),
                                            disabledContainerColor = Color(0xFFE11D48).copy(alpha = 0.35f)
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .heightIn(min = 44.dp)
                                            .testTag("btn_combat_attack")
                                    ) {
                                        Text(
                                            text = "ATTACK",
                                            color = Color.White,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // 2. Defend Button
                                    Button(
                                        onClick = { viewModel.combatDefend() },
                                        enabled = uiState.isCombatInputEnabled,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF0F172A),
                                            disabledContainerColor = Color(0xFF0F172A).copy(alpha = 0.5f)
                                        ),
                                        border = BorderStroke(1.5.dp, if (uiState.activeFirewallTimeLeft > 0) Color(0xFF10B981) else CyberBrightGreen),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .heightIn(min = 44.dp)
                                            .testTag("btn_combat_defend")
                                    ) {
                                        Text(
                                            text = if (uiState.activeFirewallTimeLeft > 0) "FIREWALL ACTIVE" else "DEFEND",
                                            color = if (uiState.activeFirewallTimeLeft > 0) Color(0xFF34D399) else CyberBrightGreen,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // 3. Item Button
                                    Button(
                                        onClick = { showItemMenu = !showItemMenu },
                                        enabled = uiState.isCombatInputEnabled && uiState.inventory.isNotEmpty(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF0F172A),
                                            disabledContainerColor = Color(0xFF0F172A).copy(alpha = 0.5f)
                                        ),
                                        border = BorderStroke(1.5.dp, CyberCyan),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 44.dp)
                                            .testTag("btn_combat_item")
                                    ) {
                                        Text("ITEM", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // 4. Flee Button
                                    Button(
                                        onClick = { viewModel.fleeCombat() },
                                        enabled = uiState.isCombatInputEnabled,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF0F172A),
                                            disabledContainerColor = Color(0xFF0F172A).copy(alpha = 0.5f)
                                        ),
                                        border = BorderStroke(1.5.dp, CyberAmber),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 44.dp)
                                            .testTag("btn_combat_flee")
                                    ) {
                                        Text("FLEE", color = CyberAmber, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Row 2: Secondary / Tactical Combat Actions (Quick Hack, Scan, End Turn)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // 5. Quick Hack Button
                                    Button(
                                        onClick = { viewModel.combatHack() },
                                        enabled = uiState.isCombatInputEnabled && uiState.ram >= 3,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (uiState.ram >= 3) Color(0xFF4C1D95) else Color(0xFF0F172A),
                                            disabledContainerColor = Color(0xFF0F172A).copy(alpha = 0.5f)
                                        ),
                                        border = BorderStroke(1.5.dp, if (uiState.ram >= 3) CyberPink else Color.Gray),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .height(36.dp)
                                            .testTag("btn_combat_hack")
                                    ) {
                                        Text("QUICK HACK", color = if (uiState.ram >= 3) Color.White else Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // 6. Scan Target Button
                                    Button(
                                        onClick = { viewModel.combatScan() },
                                        enabled = uiState.isCombatInputEnabled,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF0F172A),
                                            disabledContainerColor = Color(0xFF0F172A).copy(alpha = 0.5f)
                                        ),
                                        border = BorderStroke(1.5.dp, CyberCyan),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .height(36.dp)
                                            .testTag("btn_combat_scan")
                                    ) {
                                        Text("SCAN TARGET", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // 7. End Turn Button
                                    Button(
                                        onClick = { viewModel.endTurn() },
                                        enabled = uiState.isCombatInputEnabled,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF059669),
                                            disabledContainerColor = Color(0xFF059669).copy(alpha = 0.35f)
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .testTag("btn_combat_end_turn")
                                    ) {
                                        Text("END TURN", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Expandable Item Menu
                                if (showItemMenu && uiState.inventory.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        uiState.inventory.forEach { item ->
                                            val itemColor = when (item) {
                                                "NanoMed.sys" -> CyberCyan
                                                "RAMBoost.exe" -> CyberCyan
                                                "Decryptor.pkg" -> CyberAmber
                                                "ChipsetMod.pkg" -> CyberPink
                                                else -> Color.LightGray
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .border(1.dp, itemColor, RoundedCornerShape(4.dp))
                                                    .background(CyberDark)
                                                    .clickable {
                                                        viewModel.useInventoryItem(item)
                                                        showItemMenu = false
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                                    .testTag("combat_item_$item")
                                            ) {
                                                Text(
                                                    text = item,
                                                    color = itemColor,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                // Row 3: Installed Programs Quick Launcher
                                if (uiState.installedPrograms.isNotEmpty()) {
                                    HorizontalDivider(color = CyberBorder.copy(alpha = 0.4f), thickness = 1.dp, modifier = Modifier.padding(vertical = 2.dp))
                                    Text(
                                        text = "INSTALLED SOFTWARE PROTOCOLS //",
                                        color = CyberCyan,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.Start).padding(start = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        uiState.installedPrograms.forEach { prog ->
                                            val progCooldown = uiState.programCooldowns[prog.id] ?: 0
                                            val isReady = progCooldown <= 0
                                            val hasRam = uiState.ram >= prog.ramCost
                                            val isButtonEnabled = hasRam && isReady && uiState.isCombatInputEnabled
                                            Box(
                                                modifier = Modifier
                                                    .background(if (isButtonEnabled) CyberMutedGreen else CyberDark, RoundedCornerShape(4.dp))
                                                    .border(1.dp, if (isButtonEnabled) CyberCyan else CyberBorder.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                                    .clickable(enabled = isButtonEnabled) {
                                                        viewModel.executeCombatProgramInline(prog)
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                                    .testTag("inline_program_${prog.id}")
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = if (progCooldown > 0) "${prog.name} (${String.format("%.1f", progCooldown / 10f)}s)" else prog.name,
                                                        color = if (isButtonEnabled) CyberCyan else Color.Gray,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "(${prog.ramCost}MB)",
                                                        color = if (isButtonEnabled) CyberPink else Color.Gray,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 7.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                 }
                            }
                        }
                    }
                }
            }

            // Right HUD Panel (2D Top down map and stats)
            val minimapAlpha = 1f
            val minimapScale = 1f

            Column(
                modifier = Modifier
                    .weight(0.70f)
                    .fillMaxHeight()
            ) {
                // Top-Down Mini-map
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                    border = BorderStroke(1.dp, CyberBorder.copy(alpha = minimapAlpha)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxWidth()
                        .graphicsLayer(
                            alpha = minimapAlpha,
                            scaleX = minimapScale,
                            scaleY = minimapScale
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "SECTOR LOGIC RADAR",
                                color = CyberCyan.copy(alpha = minimapAlpha),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (uiState.isScanActive || uiState.scanTurnsLeft > 0) {
                                Text(
                                    text = "📡 ACTIVE (${uiState.scanTurnsLeft} CYCLES)",
                                    color = CyberPink,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Render top down map
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            RenderMiniMap(uiState)
                            GlitchOverlay(progress = 1f - minimapAlpha)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Primary Stats Overview
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                    border = BorderStroke(1.dp, CyberBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(0.65f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "STATUS // ${uiState.runnerName.uppercase()}",
                                color = CyberCyan,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "LVL ${uiState.characterLevel}",
                                color = CyberAmber,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }

                        // XP Progress Bar
                        Text(
                            text = "XP: ${uiState.characterXp}/${uiState.xpToNextLevel}",
                            color = CyberAmber,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                        ProgressBarRetro(
                            current = uiState.characterXp,
                            max = uiState.xpToNextLevel,
                            color = CyberAmber
                        )

                        // HP Bar
                        Text(
                            text = "INTEGRITY: ${uiState.integrity}/${uiState.maxIntegrity}",
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                        ProgressBarRetro(
                            current = uiState.integrity,
                            max = uiState.maxIntegrity,
                            color = CyberCyan
                        )

                        // RAM Bar
                        Text(
                            text = "RAM: ${uiState.ram}/${uiState.maxRam} MB",
                            color = CyberPink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                        ProgressBarRetro(
                            current = uiState.ram,
                            max = uiState.maxRam,
                            color = CyberPink
                        )

                        // Credits & Upgrades
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "CREDITS: ${uiState.credits} MB",
                                color = CyberAmber,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            )
                            Text(
                                text = "DMG: +${uiState.damageBonus}",
                                color = CyberPink,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            )
                        }

                        HorizontalDivider(color = CyberBorder, thickness = 1.dp)

                        // Cyber-Space Weather Environmental HUD
                        Spacer(modifier = Modifier.height(4.dp))
                        val weatherColor = Color(uiState.activeWeather.colorHex)
                        Text(
                            text = "GRID ATMOSPHERE:",
                            color = CyberMutedText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(weatherColor, androidx.compose.foundation.shape.CircleShape)
                            )
                            Text(
                                text = uiState.activeWeather.title.uppercase(),
                                color = weatherColor,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                        }
                        if (uiState.activeWeather != com.example.data.CyberWeather.CLEAR) {
                            Text(
                                text = "CYCLES REMAINING: ${uiState.weatherTurnsLeft}",
                                color = weatherColor.copy(alpha = 0.8f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp
                            )
                        }
                        
                        // Vertical Structural Cell HUD
                        val currentCell = if (uiState.maze.isNotEmpty() && uiState.gridY in uiState.maze.indices && uiState.gridX in uiState.maze[0].indices) {
                            uiState.maze[uiState.gridY][uiState.gridX]
                        } else null

                        currentCell?.let { cell ->
                            if (cell == com.example.data.CellType.ELEVATED_BALCONY ||
                                cell == com.example.data.CellType.GRAVITY_SLOPE ||
                                cell == com.example.data.CellType.ELEVATOR ||
                                cell == com.example.data.CellType.STAIRS_UP ||
                                cell == com.example.data.CellType.STAIRS_DOWN) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "VERTICAL STRUCTURE CELL:",
                                    color = CyberMutedText,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val (cellText, cellColor) = when (cell) {
                                    com.example.data.CellType.ELEVATED_BALCONY -> "BALCONY VANTAGE (+25% ATK)" to Color(0xFF10B981)
                                    com.example.data.CellType.GRAVITY_SLOPE -> "GRAVITY SLOPE (30% EVADE)" to Color(0xFFEAB308)
                                    com.example.data.CellType.ELEVATOR -> "EXPRESS ELEVATOR ACCESS" to Color(0xFF00E5FF)
                                    com.example.data.CellType.STAIRS_UP -> "STAIRWELL: ASCENT LINK" to Color(0xFF8B5CF6)
                                    com.example.data.CellType.STAIRS_DOWN -> "STAIRWELL: DESCENT LINK" to Color(0xFF3B82F6)
                                    else -> "" to Color.Gray
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(cellColor, androidx.compose.foundation.shape.CircleShape)
                                    )
                                    Text(
                                        text = cellText.uppercase(),
                                        color = cellColor,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(color = CyberBorder, thickness = 1.dp)

                        // Action Row: Click to interact / Execute Radar Scan
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    viewModel.interact()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyberPink.copy(alpha = 0.5f),
                                    disabledContainerColor = CyberPink.copy(alpha = 0.25f)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(26.dp)
                                    .testTag("btn_interact_hack")
                            ) {
                                Text(
                                    text = "INTERACT",
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    viewModel.triggerMapScan()
                                },
                                enabled = uiState.ram >= 2,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00E5FF).copy(alpha = 0.6f),
                                    disabledContainerColor = Color(0xFF00E5FF).copy(alpha = 0.2f)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(26.dp)
                                    .testTag("btn_radar_scan")
                            ) {
                                Text(
                                    text = if (uiState.isScanActive) "SCANNING (${uiState.scanTurnsLeft})" else "📡 SCAN (2 RAM)",
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Inventory & Consumable selection pane
        Card(
            colors = CardDefaults.cardColors(containerColor = CyberCardBg),
            border = BorderStroke(1.dp, CyberBorder),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.4f)
        ) {
            Column(modifier = Modifier.padding(4.dp)) {
                Text(
                    text = "VIRTUAL STORAGE // CONSUMABLES (TAP TO LOAD):",
                    color = CyberCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                if (uiState.inventory.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "[ STORAGE COLD CORE EMPTY ]",
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        uiState.inventory.forEach { item ->
                            val itemColor = when (item) {
                                "NanoMed.sys" -> CyberCyan
                                "RAMBoost.exe" -> CyberCyan
                                "Decryptor.pkg" -> CyberAmber
                                "ChipsetMod.pkg" -> CyberPink
                                else -> Color.LightGray
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, itemColor, RoundedCornerShape(6.dp))
                                    .background(CyberDark)
                                    .clickable { viewModel.useInventoryItem(item) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("item_$item")
                            ) {
                                Text(
                                    text = item,
                                    color = itemColor,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Base navigation: Store, Leaders, Dissolve Connection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = onShopClick,
                colors = ButtonDefaults.buttonColors(containerColor = CyberMutedGreen),
                border = BorderStroke(1.dp, CyberBorderLight),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .testTag("btn_shop_console")
            ) {
                Text("SHOP SOURCE", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { viewModel.openCyberwareClinic() },
                colors = ButtonDefaults.buttonColors(containerColor = CyberMutedGreen),
                border = BorderStroke(1.dp, CyberCyan),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .testTag("btn_cyberware_clinic")
            ) {
                Text("🔌 CYBERWARE", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onSafeDisconnect,
                colors = ButtonDefaults.buttonColors(containerColor = CyberDark),
                border = BorderStroke(1.dp, CyberPink),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1.2f)
                    .height(34.dp)
                    .testTag("btn_safe_disconnect")
            ) {
                Text("DISCONNECT RUN", color = CyberPink, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    }
    if (fadeAlpha > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = fadeAlpha))
        )
    }
}

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

// Draw first-person 3D vector wireframe of the cyberspace maze
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

    // --- Infinite Transitions & Animation loops ---
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

    // --- Continuous frame clock for ultra-smooth monotonic particle drifting ---
    val frameTime by produceState(initialValue = 0L) {
        while (true) {
            withFrameMillis {
                value = it
            }
        }
    }

    // --- Remember Cyber particles for Duke 3D floating sparks/dust ---
    val particles = remember {
        val random = java.util.Random(1337)
        List(20) {
            val isBlue = random.nextBoolean()
            CyberParticle(
                xRatio = random.nextFloat(),
                yRatio = random.nextFloat(),
                zRatio = 0.1f + random.nextFloat() * 0.9f,
                speedX = (random.nextFloat() - 0.5f) * 0.006f,
                speedY = -0.003f - random.nextFloat() * 0.008f, // drifting upwards
                speedZ = -0.004f - random.nextFloat() * 0.006f, // fly towards player
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
                    1 -> Color(0xFF00E5FF) // Neon Cyan
                    2 -> Color(0xFF60A5FA) // Electric Blue
                    3 -> Color(0xFFF97316) // Warning Orange
                    4 -> Color(0xFFC084FC) // Executive Purple
                    else -> Color(0xFF00E5FF)
                }
            }
            com.example.data.Zone.COLLECTORS -> {
                if (uiState.collectorsLevel == 1) Color(0xFF10B981) // Poison Green
                else Color(0xFF8B5CF6) // Toxic Violet
            }
            com.example.data.Zone.CITY -> {
                when (uiState.cityDistrictIndex) {
                    0 -> Color(0xFFEC4899) // Hot Pink (Neon District)
                    1 -> Color(0xFFFBBF24) // Golden Yellow (Tech Plaza)
                    else -> Color(0xFF00E5FF) // Cyan (Corp Core)
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

            // Define rendering coordinates for depths 0, 1, 2, 3
            val tl_c = floatArrayOf(0f, 6f, 11f, 13f)
            val tl_r = floatArrayOf(0f, 2f, 3f, 4f)
            val bl_c = floatArrayOf(0f, 6f, 11f, 13f)
            val bl_r = floatArrayOf(10f, 8f, 7f, 6f)

            val tr_c = floatArrayOf(30f, 24f, 19f, 17f)
            val tr_r = floatArrayOf(0f, 2f, 3f, 4f)
            val br_c = floatArrayOf(30f, 24f, 19f, 17f)
            val br_r = floatArrayOf(10f, 8f, 7f, 6f)

            // --- Use pre-calculated 2.5D verticality shifts (highly optimized to avoid allocations on draw) ---
            val adjustedTl_r = perspectiveData.adjustedTl_r
            val adjustedTr_r = perspectiveData.adjustedTr_r
            val adjustedBl_r = perspectiveData.adjustedBl_r
            val adjustedBr_r = perspectiveData.adjustedBr_r

            fun getPixel(col: Float, row: Float): Offset {
                return Offset((col / 30f) * w, (row / 10f) * h)
            }

            // Screen Shake during combat or heavy low integrity alerts
            val isShaking = uiState.combatScreenShake || uiState.integrity < 30
            val shakeOffset = if (isShaking) {
                val scaleVal = if (uiState.combatScreenShake) 14f else 4f
                val shakeX = ((animProgress * 73f) % scaleVal) - (scaleVal / 2f)
                val shakeY = ((animProgress * 113f) % scaleVal) - (scaleVal / 2f)
                Offset(shakeX, shakeY)
            } else {
                Offset.Zero
            }

            // Apply screen shake translation
            drawContext.canvas.save()
            drawContext.canvas.translate(shakeOffset.x, shakeOffset.y)

            // --- 1. Draw Grid Ceilings & Floors (Cyber-Wire Perspective lines) ---
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

            // --- 2. Side Walls with Retro Midline Depth Stripes (Far to Near) ---
            for (d in (maxVisibleDepth - 1) downTo 0) {
                val alpha = when (d) {
                    0 -> 0.4f
                    1 -> 0.25f
                    2 -> 0.15f
                    else -> 0.1f
                }

                // --- Left wall side segment ---
                if (leftWallAt[d]) {
                    val w1 = getPixel(tl_c[d], adjustedTl_r[d])
                    val w2 = getPixel(tl_c[d+1], adjustedTl_r[d+1])
                    val w3 = getPixel(bl_c[d+1], adjustedBl_r[d+1])
                    val w4 = getPixel(bl_c[d], adjustedBl_r[d])
                    draw3DVoxelWallSegment(w1, w2, w3, w4, primaryColor, alpha, isLeft = true, w, adjustedTl_r, adjustedBl_r, adjustedTr_r, adjustedBr_r, d, h)
                } else {
                    // Open corridor left side boundary
                    drawLine(color = primaryColor.copy(alpha = 0.2f), start = getPixel(tl_c[d], adjustedTl_r[d+1]), end = getPixel(tl_c[d+1], adjustedTl_r[d+1]), strokeWidth = 2f)
                    drawLine(color = primaryColor.copy(alpha = 0.2f), start = getPixel(bl_c[d], adjustedBl_r[d+1]), end = getPixel(bl_c[d+1], adjustedBl_r[d+1]), strokeWidth = 2f)
                    drawLine(color = primaryColor.copy(alpha = 0.3f), start = getPixel(tl_c[d+1], adjustedTl_r[d+1]), end = getPixel(bl_c[d+1], adjustedBl_r[d+1]), strokeWidth = 3f)
                }

                // --- Right wall side segment ---
                if (rightWallAt[d]) {
                    val w1 = getPixel(tr_c[d], adjustedTr_r[d])
                    val w2 = getPixel(tr_c[d+1], adjustedTr_r[d+1])
                    val w3 = getPixel(br_c[d+1], adjustedBr_r[d+1])
                    val w4 = getPixel(br_c[d], adjustedBr_r[d])
                    draw3DVoxelWallSegment(w1, w2, w3, w4, primaryColor, alpha, isLeft = false, w, adjustedTl_r, adjustedBl_r, adjustedTr_r, adjustedBr_r, d, h)
                } else {
                    // Open corridor right side boundary
                    drawLine(color = primaryColor.copy(alpha = 0.2f), start = getPixel(tr_c[d+1], adjustedTr_r[d+1]), end = getPixel(tr_c[d], adjustedTr_r[d+1]), strokeWidth = 2f)
                    drawLine(color = primaryColor.copy(alpha = 0.2f), start = getPixel(br_c[d+1], adjustedBr_r[d+1]), end = getPixel(br_c[d], adjustedBr_r[d+1]), strokeWidth = 2f)
                    drawLine(color = primaryColor.copy(alpha = 0.3f), start = getPixel(tr_c[d+1], adjustedTr_r[d+1]), end = getPixel(br_c[d+1], adjustedBr_r[d+1]), strokeWidth = 3f)
                }
            }

            // --- 3. Front Wall Bulkhead (if blocked) ---
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
                // Far vanishing point boundary
                drawRect(
                    color = primaryColor.copy(alpha = 0.25f),
                    topLeft = pTL,
                    size = Size(pBR.x - pTL.x, pBR.y - pTL.y),
                    style = Stroke(width = 2f)
                )
            }

            // --- Background Dimming overlay in combat ---
            if (dimAlpha > 0f) {
                drawRect(
                    color = Color.Black.copy(alpha = dimAlpha),
                    topLeft = Offset(0f, 0f),
                    size = size
                )
            }

            // --- 4. Central Vector Cyber Objects ---
            val primaryNode = if (isCombat) CellType.VIRUS_NODE else cellTypes[1]
            if (primaryNode == CellType.VIRUS_NODE) {
                val center = getPixel(15f, 5f)
                val sizeRadius = w * 0.11f

                // Pulse threat aura
                drawCircle(
                    color = Color(0xFFF43F5E).copy(alpha = 0.25f),
                    radius = sizeRadius * 1.5f,
                    center = center
                )

                // Outer danger shield
                threatPath.reset()
                threatPath.moveTo(center.x, center.y - sizeRadius)
                threatPath.lineTo(center.x + sizeRadius, center.y)
                threatPath.lineTo(center.x, center.y + sizeRadius)
                threatPath.lineTo(center.x - sizeRadius, center.y)
                threatPath.close()
                drawPath(path = threatPath, color = Color(0xFFF43F5E), style = Stroke(width = 5f))

                // Core center
                drawCircle(color = Color(0xFFF43F5E), radius = sizeRadius * 0.4f, center = center)

                // Threat spikes
                drawLine(Color(0xFFF43F5E), center, getPixel(11f, 3.5f), strokeWidth = 3f)
                drawLine(Color(0xFFF43F5E), center, getPixel(19f, 3.5f), strokeWidth = 3f)
                drawLine(Color(0xFFF43F5E), center, getPixel(11f, 6.5f), strokeWidth = 3f)
                drawLine(Color(0xFFF43F5E), center, getPixel(19f, 6.5f), strokeWidth = 3f)

            } else if (primaryNode == CellType.DATA_STORE) {
                val center = getPixel(15f, 5f)
                val sizeRadius = w * 0.1f

                // Data storage core glow
                drawCircle(
                    color = Color(0xFFFBBF24).copy(alpha = 0.22f),
                    radius = sizeRadius * 1.5f,
                    center = center
                )

                val boxW = sizeRadius * 1.6f
                val boxH = sizeRadius * 0.35f

                // Upper server bay
                drawRoundRect(
                    color = Color(0xFFFBBF24),
                    topLeft = Offset(center.x - boxW / 2, center.y - sizeRadius * 0.75f),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                // Middle server bay
                drawRoundRect(
                    color = Color(0xFFFBBF24),
                    topLeft = Offset(center.x - boxW / 2, center.y - boxH / 2),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                // Lower server bay
                drawRoundRect(
                    color = Color(0xFFFBBF24),
                    topLeft = Offset(center.x - boxW / 2, center.y + sizeRadius * 0.4f),
                    size = Size(boxW, boxH),
                    cornerRadius = CornerRadius(6f, 6f)
                )

                // Active disk indicator dots
                drawCircle(Color.Black, radius = 5f, center = Offset(center.x - boxW * 0.35f, center.y - sizeRadius * 0.55f))
                drawCircle(Color.Black, radius = 5f, center = Offset(center.x - boxW * 0.35f, center.y))
                drawCircle(Color.Black, radius = 5f, center = Offset(center.x - boxW * 0.35f, center.y + sizeRadius * 0.55f))

            } else if (primaryNode == CellType.ENCRYPTED_PORTAL) {
                val center = getPixel(15f, 5f)
                val sizeRadius = w * 0.12f

                // Portal swirling glow
                drawCircle(
                    color = Color(0xFFC084FC).copy(alpha = 0.15f),
                    radius = sizeRadius * 1.8f,
                    center = center
                )

                // Render dynamic recursive fractal star inside the Portal
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
                // Floating 2.5D neon hologram box
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
                // Awesome 2.5D stair steps wireframe mesh overlaid in viewport center
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
                // Slanted yellow chevrons moving on the gravity slope ramp
                val numChevrons = 4
                for (i in 0 until numChevrons) {
                    val flowOffset = (animProgress * 1.5f) % 1.0f
                    val ratio = ((i.toFloat() / numChevrons) + flowOffset) % 1.0f
                    val yCoord = pTL.y + (pBR.y - pTL.y) * ratio
                    val spread = (pBR.x - pTL.x) * 0.35f * ratio
                    val midX = pTL.x + (pBR.x - pTL.x) * 0.5f

                    // Chevron pointing upward: '^'
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
                // Futuristic 3D Elevator Shaft cage
                val cx = pTL.x + (pBR.x - pTL.x) * 0.5f
                val cy = pTL.y + (pBR.y - pTL.y) * 0.5f
                val hSize = pBR.x - pTL.x
                val vSize = pBR.y - pTL.y

                // Outer Glass Tube Shaft Frame
                drawRect(
                    color = Color(0xFF00E5FF).copy(alpha = 0.35f),
                    topLeft = Offset(pTL.x + hSize * 0.15f, pTL.y),
                    size = Size(hSize * 0.7f, vSize),
                    style = Stroke(width = 3.5f)
                )

                // Vertical Lift Columns
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

                // Elevator lift capsule moving vertically with animation progress
                val liftOffsetY = vSize * 0.35f * kotlin.math.sin(animProgress * 2f * kotlin.math.PI.toFloat())
                val capY = cy - vSize * 0.2f + liftOffsetY
                val capW = hSize * 0.36f
                val capH = vSize * 0.4f

                // Outer Lift Capsule Box
                drawRoundRect(
                    color = Color(0xFF00E5FF),
                    topLeft = Offset(cx - capW / 2, capY),
                    size = Size(capW, capH),
                    cornerRadius = CornerRadius(10f, 10f),
                    style = Stroke(width = 4f)
                )

                // Glass Capsule Interior Glow
                drawRoundRect(
                    color = Color(0xFF00E5FF).copy(alpha = 0.18f),
                    topLeft = Offset(cx - capW / 2, capY),
                    size = Size(capW, capH),
                    cornerRadius = CornerRadius(10f, 10f)
                )

                // Horizontal sliding caution stripes inside capsule door
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

                // Digital upward/downward flashing arrows indicator
                val arrowDir = if (animProgress < 0.5f) 1 else -1
                if (arrowDir > 0) {
                    // Up arrow inside capsule
                    drawLine(Color(0xFF00E5FF), Offset(cx, capY + capH * 0.15f), Offset(cx - 10f, capY + capH * 0.28f), strokeWidth = 3f)
                    drawLine(Color(0xFF00E5FF), Offset(cx, capY + capH * 0.15f), Offset(cx + 10f, capY + capH * 0.28f), strokeWidth = 3f)
                } else {
                    // Down arrow inside capsule
                    drawLine(Color(0xFF00E5FF), Offset(cx, capY + capH * 0.85f), Offset(cx - 10f, capY + capH * 0.72f), strokeWidth = 3f)
                    drawLine(Color(0xFF00E5FF), Offset(cx, capY + capH * 0.85f), Offset(cx + 10f, capY + capH * 0.72f), strokeWidth = 3f)
                }

            } else if (primaryNode == CellType.ELEVATED_BALCONY) {
                // High-fidelity Neon Overlook Balcony / Hanging Gallery railing
                val cx = pTL.x + (pBR.x - pTL.x) * 0.5f
                val hSize = pBR.x - pTL.x
                val vSize = pBR.y - pTL.y
                val railingTopY = pTL.y + vSize * 0.5f

                // Draw solid background grid for the floor below (deep blue vector mist)
                drawRect(
                    color = Color(0xFF020617).copy(alpha = 0.8f),
                    topLeft = Offset(pTL.x, railingTopY),
                    size = Size(hSize, pBR.y - railingTopY)
                )

                // Draw structural cross support grids under the floor
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

                // Balcony horizontal neon safety rails
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

                // Vertical steel baluster lines connecting rails
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

                // Floating 2.5D Holographic Danger warning display above the railing
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

                // Warning hazard bar symbol inside hologram
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

            // --- 5. Render Floating 3D Cyber Particle sparks (Duke Nukem 3D dust style!) ---
            val timeSec = frameTime / 1000f
            particles.forEach { p ->
                var curX = p.xRatio + p.speedX * timeSec * 30f
                var curY = p.yRatio + p.speedY * timeSec * 30f
                var curZ = p.zRatio + p.speedZ * timeSec * 30f

                // wrap particle bounds safely
                while (curX < 0f) curX += 1f
                while (curX > 1f) curX -= 1f
                while (curY < 0f) curY += 1f
                while (curY > 1f) curY -= 1f
                while (curZ < 0.1f) curZ += 0.9f
                while (curZ > 1f) curZ -= 0.9f

                // Project 3D coordinate to screen with perspective scaling
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

            // --- Pulsing Cyber Shield Visual Effect ---
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

            // --- 6. Tactical HUD Crosshairs & Brackets ---
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

            // --- 7. Scanline CRT CRT static filter overlay ---
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

            // --- 8. Screen Alert Vignette Overlay ---
            if (isCombat || uiState.integrity < 40) {
                val alertColor = if (uiState.integrity < 40) Color(0xFFEF4444) else Color(0xFFF43F5E)
                // Pulse outer vignette border
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

            // --- A1. Vector Player Attack (glowing cyan beam starting from bottom of screen to the center enemy) ---
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
                // Reticle burst at target center
                drawCircle(
                    color = Color(0xFF00E5FF).copy(alpha = 0.8f),
                    radius = 45f * beamProgress,
                    center = Offset(w / 2f, h * 0.45f),
                    style = Stroke(width = 4f)
                )
            }

            // --- A2. Vector Enemy Attack (crimson pulse shockwaves expanding from center out) ---
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

            // --- A3. Glowing Cyber Shield (shimmering curved defensive dome) ---
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

                // Shimmering grid lines inside the shield
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

            // --- A4. Turn Pulse Screen Overlay ---
            if (turnPulseAnim.value > 0f) {
                val pulseColor = if (uiState.combatTurn == com.example.ui.CombatTurn.PLAYER) CyberCyan else CyberPink
                drawRect(
                    color = pulseColor.copy(alpha = turnPulseAnim.value * 0.18f),
                    topLeft = Offset(0f, 0f),
                    size = size
                )
                // Draw a beautiful glowing border vignette that scales down
                drawRect(
                    color = pulseColor.copy(alpha = turnPulseAnim.value * 0.45f),
                    topLeft = Offset(0f, 0f),
                    size = size,
                    style = Stroke(width = 15f * turnPulseAnim.value)
                )
            }

            // --- TES Morrowind-Style Crosshair ---
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

            // Central dot
            drawCircle(
                color = crosshairColor,
                radius = 3f,
                center = Offset(crosshairX, crosshairY)
            )

            // Radial Reticle Lines
            val reticleOffset = 10f
            val reticleLength = 8f
            // Top
            drawLine(crosshairColor, Offset(crosshairX, crosshairY - reticleOffset), Offset(crosshairX, crosshairY - reticleOffset - reticleLength), strokeWidth = 2f)
            // Bottom
            drawLine(crosshairColor, Offset(crosshairX, crosshairY + reticleOffset), Offset(crosshairX, crosshairY + reticleOffset + reticleLength), strokeWidth = 2f)
            // Left
            drawLine(crosshairColor, Offset(crosshairX - reticleOffset, crosshairY), Offset(crosshairX - reticleOffset - reticleLength, crosshairY), strokeWidth = 2f)
            // Right
            drawLine(crosshairColor, Offset(crosshairX + reticleOffset, crosshairY), Offset(crosshairX + reticleOffset + reticleLength, crosshairY), strokeWidth = 2f)

            if (isTargetInteractive) {
                // Bracket bounds around center
                val bSize = 25f
                val bThick = 2f
                // Top-Left bracket
                drawLine(crosshairColor, Offset(crosshairX - bSize, crosshairY - bSize), Offset(crosshairX - bSize + 8f, crosshairY - bSize), strokeWidth = bThick)
                drawLine(crosshairColor, Offset(crosshairX - bSize, crosshairY - bSize), Offset(crosshairX - bSize, crosshairY - bSize + 8f), strokeWidth = bThick)
                // Top-Right bracket
                drawLine(crosshairColor, Offset(crosshairX + bSize, crosshairY - bSize), Offset(crosshairX + bSize - 8f, crosshairY - bSize), strokeWidth = bThick)
                drawLine(crosshairColor, Offset(crosshairX + bSize, crosshairY - bSize), Offset(crosshairX + bSize, crosshairY - bSize + 8f), strokeWidth = bThick)
                // Bottom-Left bracket
                drawLine(crosshairColor, Offset(crosshairX - bSize, crosshairY + bSize), Offset(crosshairX - bSize + 8f, crosshairY + bSize), strokeWidth = bThick)
                drawLine(crosshairColor, Offset(crosshairX - bSize, crosshairY + bSize), Offset(crosshairX - bSize, crosshairY + bSize - 8f), strokeWidth = bThick)
                // Bottom-Right bracket
                drawLine(crosshairColor, Offset(crosshairX + bSize, crosshairY + bSize), Offset(crosshairX + bSize - 8f, crosshairY + bSize), strokeWidth = bThick)
                drawLine(crosshairColor, Offset(crosshairX + bSize, crosshairY + bSize), Offset(crosshairX + bSize, crosshairY + bSize - 8f), strokeWidth = bThick)
            }

            // --- Morrowind Weapon Vector Drawing ---
            val swingProgress = uiState.weaponSwingProgress
            val swingType = uiState.weaponSwingType
            
            // Base offset of weapon resting in bottom right
            val restingX = w * 0.75f
            val restingY = h * 0.85f
            
            // Calculate dynamic offsets based on swing progress & type
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
            
            // Draw different weapon lines based on name
            val weaponColor = when (uiState.equippedWeaponName) {
                "Daedric Cyber-Katana" -> Color(0xFFF43F5E) // Red
                "Aegis Shock-Mace" -> Color(0xFFFBBF24) // Amber / Gold
                "Glass Cyber-Dagger" -> Color(0xFF10B981) // Poison Green
                "Ebony Plasma-Staff" -> Color(0xFF8B5CF6) // Purple
                else -> Color(0xFF00E5FF) // Cyber Cyan
            }
            
            when (uiState.equippedWeaponName) {
                "Daedric Cyber-Katana" -> {
                    val tip = Offset(weaponOrigin.x - w * 0.3f, weaponOrigin.y - h * 0.45f)
                    val guard = Offset(weaponOrigin.x - w * 0.05f, weaponOrigin.y - h * 0.08f)
                    
                    // Katana Blade Edge
                    drawLine(
                        color = weaponColor,
                        start = guard,
                        end = tip,
                        strokeWidth = 6f
                    )
                    // Inner glowing core line
                    drawLine(
                        color = Color.White,
                        start = guard,
                        end = tip,
                        strokeWidth = 2f
                    )
                    // Hilt/Guard
                    drawLine(
                        color = Color.DarkGray,
                        start = Offset(guard.x - 15f, guard.y + 10f),
                        end = Offset(guard.x + 15f, guard.y - 10f),
                        strokeWidth = 5f
                    )
                    // Grip
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
                    // Guard
                    drawLine(
                        color = Color.DarkGray,
                        start = Offset(guard.x - 20f, guard.y),
                        end = Offset(guard.x + 20f, guard.y),
                        strokeWidth = 4f
                    )
                    // Grip
                    drawLine(
                        color = Color.Black,
                        start = guard,
                        end = weaponOrigin,
                        strokeWidth = 6f
                    )
                }
                "Aegis Shock-Mace" -> {
                    val tip = Offset(weaponOrigin.x - w * 0.2f, weaponOrigin.y - h * 0.3f)
                    
                    // Shaft
                    drawLine(
                        color = Color.DarkGray,
                        start = weaponOrigin,
                        end = tip,
                        strokeWidth = 10f
                    )
                    // Spiked Mace Head Circle
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
                    // Spikes
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
                    
                    // Staff Shaft
                    drawLine(
                        color = Color(0xFF1E293B),
                        start = weaponOrigin,
                        end = tip,
                        strokeWidth = 8f
                    )
                    // Crescent horns guard
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
                    
                    // Floating plasma orb
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
                    // Default generic dagger lines
                    val tip = Offset(weaponOrigin.x - w * 0.15f, weaponOrigin.y - h * 0.25f)
                    drawLine(weaponColor, weaponOrigin, tip, strokeWidth = 4f)
                }
            }

            // Restore shake transform translation
            drawContext.canvas.restore()
        }

        // --- Morrowind Hover Info Box Overlay ---
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

        // --- Tactical HUD Turn Indicator Badge ---
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
                    text = if (uiState.activeFirewallTimeLeft > 0) "🛡️ FIREWALL ACTIVE [${String.format("%.1f", uiState.activeFirewallTimeLeft / 10f)}s]" else "⚡ REAL-TIME SYSTEM OVERLOAD ACTIVE",
                    color = if (uiState.activeFirewallTimeLeft > 0) Color(0xFF10B981) else CyberPink,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp
                )
            }
        }

        // --- Enemy Damage Floating Popup ---
        FloatingDamagePopup(
            text = uiState.enemyDamagePopup,
            color = CyberPink,
            isPlayer = false,
            modifier = Modifier.align(Alignment.Center)
        )

        // --- Player Damage Floating Popup ---
        FloatingDamagePopup(
            text = uiState.playerDamagePopup,
            color = Color.Red,
            isPlayer = true,
            modifier = Modifier.align(Alignment.Center)
        )

        // --- Grand Overlay Banner for Starting, Victory, and Defeat transitions ---
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
                // Subtle particles drifting in background
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

        // --- Post-Processing Glitch & Scanline Overlay Shader (intensity increases as health/stability drops) ---
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

// Renders the top-down 2D mini-map (highly optimized for low-end devices with 100x fewer Compose nodes)
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
                                // Golden amber glowing terminal data cell
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
                                // Purple vortex portal
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
                                // Hostile Crimson Rose hazard diamond
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
                                // Secure Emerald Green ring
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
                                // Cross shape inside
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
                                // Classified Crypt-Cache: Pulsing neon sky-blue square with centered core
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
                                // Indigo Grand Hall
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
                                // Draw 4 structural pillar dots
                                drawCircle(Color(0xFF818CF8).copy(alpha = alpha), 1.5f, Offset(cellLeft + cellSize * 0.3f, cellTop + cellSize * 0.3f))
                                drawCircle(Color(0xFF818CF8).copy(alpha = alpha), 1.5f, Offset(cellLeft + cellSize * 0.7f, cellTop + cellSize * 0.3f))
                                drawCircle(Color(0xFF818CF8).copy(alpha = alpha), 1.5f, Offset(cellLeft + cellSize * 0.3f, cellTop + cellSize * 0.7f))
                                drawCircle(Color(0xFF818CF8).copy(alpha = alpha), 1.5f, Offset(cellLeft + cellSize * 0.7f, cellTop + cellSize * 0.7f))
                            }
                            CellType.DOME_CHAMBER -> {
                                // Teal Dome central chamber
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
                                // Amber service vent
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
                                // Pink raised balcony
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
                                // Cyber green upward stairs
                                val center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                drawLine(
                                    color = Color(0xFF10B981).copy(alpha = alpha),
                                    start = Offset(cellLeft + cellSize * 0.2f, cellTop + cellSize * 0.8f),
                                    end = Offset(cellLeft + cellSize * 0.8f, cellTop + cellSize * 0.2f),
                                    strokeWidth = 2f
                                )
                                // Upwards arrow head
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
                                // Cyber green downward stairs
                                val center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                drawLine(
                                    color = Color(0xFF10B981).copy(alpha = alpha),
                                    start = Offset(cellLeft + cellSize * 0.2f, cellTop + cellSize * 0.2f),
                                    end = Offset(cellLeft + cellSize * 0.8f, cellTop + cellSize * 0.8f),
                                    strokeWidth = 2f
                                )
                                // Downwards arrow head
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
                                // Slanted gold ramp lines
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
                                // Phantom spectral telemetry echo: Glitchy glowing violet cross orb
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
                                // Subtle empty space guide dot
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
                                // Glowing Hostile Threat Ping Ring & Target Crosshairs
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
                                // Glowing Loot / Cache Beacon Ring
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

// ==========================================
// Sub-Composable: Status Effect Badge
// ==========================================
@Composable
fun StatusEffectBadge(effect: com.example.data.ActiveStatusEffect) {
    val badgeColor = Color(effect.type.colorHex)
    Box(
        modifier = Modifier
            .background(badgeColor.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
            .border(1.dp, badgeColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = "${effect.type.icon} ${effect.type.displayName.uppercase()} (${effect.turnsRemaining}t)",
            color = badgeColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ==========================================
// Sub-Composable: Combat Mode Screen (Wizardry 1st Person Turn-Based Combat)
// ==========================================
@Composable
fun CombatView(
    uiState: GameViewModel.GameUiState,
    onExecuteProgram: (Program) -> Unit,
    onFlee: () -> Unit,
    onAttack: () -> Unit = {},
    onSetCombatStyle: (String) -> Unit = {},
    onDefend: () -> Unit = {},
    onHack: () -> Unit = {},
    onScan: () -> Unit = {},
    onUseItem: (String) -> Unit = {},
    onEndTurn: () -> Unit = {},
    onSelectSymbol: (String) -> Unit = {},
    onClearHackBuffer: () -> Unit = {},
    onAbortHack: () -> Unit = {}
) {
    val enemy = uiState.activeEnemy ?: return
    var activeSubMenu by remember { mutableStateOf("COMMANDS") } // "COMMANDS", "DAEMONS", "ITEMS"
    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp
    val isSmallScreen = screenHeightDp < 700
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030712))
            .verticalScroll(rememberScrollState())
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // --- 1. TOP HEADER: ENCOUNTER & TURN PHASE BANNER ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = BorderStroke(1.dp, CyberPink),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚔️ TACTICAL COMBAT MATRIX",
                        color = CyberPink,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isSmallScreen) 9.sp else 10.sp
                    )
                    Text(
                        text = "ICE ${uiState.level} | LVL ${uiState.characterLevel} [${uiState.characterXp}/${uiState.xpToNextLevel} XP]",
                        color = CyberAmber,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.5.sp
                    )
                }

                VisualTurnIndicator(
                    combatTurn = uiState.combatTurn,
                    isCombatInputEnabled = uiState.isCombatInputEnabled,
                    bannerMessage = uiState.showCombatBanner,
                    compactMode = false
                )
            }
        }

        // --- 2. FIRST-PERSON VIEWPORT & HOSTILE OVERLAY (Wizardry 3D Viewport) ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            border = BorderStroke(1.5.dp, CyberPink.copy(alpha = 0.8f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 220.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Background 3D Cyber Vector Canvas Viewport
                FirstPersonPerspectiveCanvas(
                    uiState = uiState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("first_person_viewport"),
                    isCombat = true,
                    onInteract = onAttack
                )

                // Flash overlay on damage/hit
                if (uiState.combatFlashEnemy) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.35f))
                    )
                }
                if (uiState.combatFlashPlayer) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(CyberPink.copy(alpha = 0.4f))
                    )
                }

                // Top Target Lock HUD & Enemy Vitals
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                            )
                        )
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "[ TARGET: ${enemy.name.uppercase()} ]",
                            color = CyberPink,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "TARGET LOCK 100%",
                            color = CyberBrightGreen,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Hostile Integrity (HP) Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "CORE INTEGRITY: ${enemy.integrity}/${enemy.maxIntegrity}",
                            color = CyberPink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                        Text(
                            text = "${((enemy.integrity.toFloat() / enemy.maxIntegrity.coerceAtLeast(1)) * 100).toInt()}%",
                            color = CyberPink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    ProgressBarRetro(
                        current = enemy.integrity,
                        max = enemy.maxIntegrity,
                        color = CyberPink
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Hostile Firewall Shield Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "FIREWALL SHIELD: ${enemy.shield}/${enemy.maxShield}",
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.5.sp
                        )
                        Text(
                            text = "ARMOR: ${enemy.armor} | ATK: ${enemy.damage}",
                            color = CyberAmber,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.5.sp
                        )
                    }
                    ProgressBarRetro(
                        current = enemy.shield,
                        max = enemy.maxShield,
                        color = CyberCyan
                    )

                    // Hostile Active Status Effects Badges
                    if (uiState.enemyStatusEffects.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            uiState.enemyStatusEffects.forEach { effect ->
                                StatusEffectBadge(effect = effect)
                            }
                        }
                    }
                }

                // Center Damage Float Popup
                if (uiState.enemyDamagePopup != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                            .border(1.5.dp, CyberPink, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = uiState.enemyDamagePopup!!,
                            color = CyberPink,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }

                // Player Damage Popup
                if (uiState.playerDamagePopup != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp)
                            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                            .border(1.5.dp, Color.Red, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "DAMAGE TAKEN: ${uiState.playerDamagePopup!!}",
                            color = Color.Red,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                // Active Combat Badges & Player Status Effects at Viewport Bottom
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    uiState.playerStatusEffects.forEach { effect ->
                        StatusEffectBadge(effect = effect)
                    }

                    if (uiState.activeFirewallTimeLeft > 0) {
                        Box(
                            modifier = Modifier
                                .background(CyberBrightGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .border(1.dp, CyberBrightGreen, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("🛡️ FIREWALL ACTIVE (-75%)", color = CyberBrightGreen, fontFamily = FontFamily.Monospace, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    val standCell = uiState.maze.getOrNull(uiState.gridY)?.getOrNull(uiState.gridX)
                    if (standCell == com.example.data.CellType.ELEVATED_BALCONY) {
                        Box(
                            modifier = Modifier
                                .background(CyberAmber.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .border(1.dp, CyberAmber, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("✨ BALCONY (+25% ATK)", color = CyberAmber, fontFamily = FontFamily.Monospace, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (standCell == com.example.data.CellType.GRAVITY_SLOPE) {
                        Box(
                            modifier = Modifier
                                .background(CyberCyan.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .border(1.dp, CyberCyan, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("✨ GRAVITY (+30% EVA)", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- 3. WIZARDRY TACTICAL COMMAND MENU & DECK ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = BorderStroke(1.dp, CyberBorder),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Stance Selector Row (Slash / Chop / Thrust)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "STANCE:",
                        color = CyberAmber,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )

                    listOf("Slash", "Chop", "Thrust").forEach { style ->
                        val isSelected = uiState.selectedCombatStyle == style
                        val borderCol = if (isSelected) CyberCyan else CyberBorder
                        val bgCol = if (isSelected) CyberMutedGreen else CyberDark
                        val textCol = if (isSelected) CyberCyan else CyberBrightGreen.copy(alpha = 0.7f)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(bgCol)
                                .border(1.dp, borderCol, RoundedCornerShape(6.dp))
                                .clickable { onSetCombatStyle(style) }
                                .padding(vertical = 6.dp)
                                .testTag("btn_combat_stance_${style.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = style.uppercase(),
                                color = textCol,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                // Sub-Deck Toggle Bar (COMMANDS vs DAEMONS vs ITEMS)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "COMMANDS" to "⚔️ TACTICS",
                        "DAEMONS" to "💻 DAEMONS (${uiState.installedPrograms.size})",
                        "ITEMS" to "💊 ITEMS (${uiState.inventory.size})"
                    ).forEach { (key, label) ->
                        val isSelected = activeSubMenu == key
                        Button(
                            onClick = { activeSubMenu = key },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) CyberCyan.copy(alpha = 0.25f) else CyberDark,
                                contentColor = if (isSelected) CyberCyan else CyberBrightGreen.copy(alpha = 0.7f)
                            ),
                            border = BorderStroke(1.dp, if (isSelected) CyberCyan else CyberBorder),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp)
                                .testTag("btn_combat_deck_${key.lowercase()}")
                        ) {
                            Text(
                                text = label,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                HorizontalDivider(color = CyberBorder, thickness = 1.dp)

                // Sub-Menu Content Switcher
                when (activeSubMenu) {
                    "DAEMONS" -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (uiState.installedPrograms.isEmpty()) {
                                Text(
                                    text = "NO TACTICAL DAEMONS INSTALLED.",
                                    color = CyberBrightGreen.copy(alpha = 0.5f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.5.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                            uiState.installedPrograms.forEach { prog ->
                                val canAfford = uiState.ram >= prog.ramCost && uiState.isCombatInputEnabled
                                Card(
                                    onClick = { if (canAfford) onExecuteProgram(prog) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (canAfford) Color(0xFF0F172A) else CyberDark
                                    ),
                                    border = BorderStroke(1.dp, if (canAfford) CyberCyan else CyberBorder),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("btn_combat_program_${prog.id}")
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
                                                text = prog.name,
                                                color = if (canAfford) CyberCyan else CyberBrightGreen.copy(alpha = 0.4f),
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )
                                            Text(
                                                text = prog.description,
                                                color = CyberBrightGreen.copy(alpha = 0.7f),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 8.sp
                                            )
                                        }
                                        Button(
                                            onClick = { onExecuteProgram(prog) },
                                            enabled = canAfford,
                                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.heightIn(min = 36.dp)
                                        ) {
                                            Text(
                                                text = "${prog.ramCost} MB",
                                                color = Color.Black,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "ITEMS" -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (uiState.inventory.isEmpty()) {
                                Text(
                                    text = "NO COMBAT CONSUMABLES AVAILABLE.",
                                    color = CyberBrightGreen.copy(alpha = 0.5f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.5.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                            uiState.inventory.distinct().forEach { item ->
                                val count = uiState.inventory.count { it == item }
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                    border = BorderStroke(1.dp, CyberAmber),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("btn_combat_item_${item}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "$item x$count",
                                            color = CyberAmber,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                        Button(
                                            onClick = { onUseItem(item) },
                                            enabled = uiState.isCombatInputEnabled,
                                            colors = ButtonDefaults.buttonColors(containerColor = CyberAmber),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.heightIn(min = 36.dp)
                                        ) {
                                            Text(
                                                text = "COMPILE",
                                                color = Color.Black,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        if (uiState.activeCombatHack != null) {
                            CombatHackingMinigameView(
                                hackState = uiState.activeCombatHack,
                                onSelectSymbol = onSelectSymbol,
                                onClearBuffer = onClearHackBuffer,
                                onAbort = onAbortHack,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            // COMMANDS: Primary Tactician Action Grid
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                            // Row 1: Attack & Quick Hack
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = onAttack,
                                    enabled = uiState.isCombatInputEnabled,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF1E1B4B),
                                        disabledContainerColor = CyberDark
                                    ),
                                    border = BorderStroke(1.5.dp, CyberPink),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("btn_combat_attack")
                                ) {
                                    Text(
                                        text = "⚔️ STRIKE (${uiState.selectedCombatStyle.uppercase()})",
                                        color = CyberPink,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }

                                Button(
                                    onClick = onHack,
                                    enabled = uiState.isCombatInputEnabled && uiState.ram >= 3,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF064E3B),
                                        disabledContainerColor = CyberDark
                                    ),
                                    border = BorderStroke(1.5.dp, CyberCyan),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("btn_combat_hack")
                                ) {
                                    Text(
                                        text = "⚡ QUICK HACK (3MB)",
                                        color = CyberCyan,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            // Row 2: Defend & Scan Target
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = onDefend,
                                    enabled = uiState.isCombatInputEnabled,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF064E3B),
                                        disabledContainerColor = CyberDark
                                    ),
                                    border = BorderStroke(1.5.dp, CyberBrightGreen),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("btn_combat_defend")
                                ) {
                                    Text(
                                        text = "🛡️ FORTIFY FIREWALL",
                                        color = CyberBrightGreen,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }

                                Button(
                                    onClick = onScan,
                                    enabled = uiState.isCombatInputEnabled,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF451A03),
                                        disabledContainerColor = CyberDark
                                    ),
                                    border = BorderStroke(1.5.dp, CyberAmber),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("btn_combat_scan")
                                ) {
                                    Text(
                                        text = "🔍 SCAN TELEMETRY",
                                        color = CyberAmber,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            // Row 3: Pass Turn & Emergency Flee
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = onEndTurn,
                                    enabled = uiState.isCombatInputEnabled,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF0F172A),
                                        disabledContainerColor = CyberDark
                                    ),
                                    border = BorderStroke(1.5.dp, CyberBorder),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 44.dp)
                                        .testTag("btn_combat_end_turn")
                                ) {
                                    Text(
                                        text = "⏭️ END TURN",
                                        color = CyberBrightGreen.copy(alpha = 0.9f),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.5.sp
                                    )
                                }

                                Button(
                                    onClick = onFlee,
                                    enabled = uiState.isCombatInputEnabled,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF881337),
                                        disabledContainerColor = CyberDark
                                    ),
                                    border = BorderStroke(1.5.dp, CyberPink),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 44.dp)
                                        .testTag("btn_combat_flee")
                                ) {
                                    Text(
                                        text = "🏃 DISCONNECT",
                                        color = CyberPink,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.5.sp
                                    )
                                }
                            }
                        }
                    }
                    }
                }
            }
        }

        // --- 4. WIZARDRY PARTY HUD / RUNNER VITALS PANEL ---
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = BorderStroke(1.dp, CyberBorder),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Runner Info Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RUNNER: ${uiState.runnerName.ifEmpty { "OPERATIVE" }.uppercase()} [${uiState.runnerClass.name}]",
                        color = CyberCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                    Text(
                        text = "WEAPON: ${uiState.equippedWeaponName.uppercase()}",
                        color = CyberAmber,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.5.sp
                    )
                }

                // Vitals Progress Bars
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // System Core HP
                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("CORE (HP)", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                            Text("${uiState.integrity}/${uiState.maxIntegrity}", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 7.5.sp)
                        }
                        ProgressBarRetro(current = uiState.integrity, max = uiState.maxIntegrity, color = CyberCyan)
                    }

                    // Firewall Shield
                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("SHIELD", color = CyberBrightGreen, fontFamily = FontFamily.Monospace, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                            Text("${uiState.playerShield}/${uiState.playerMaxShield}", color = CyberBrightGreen, fontFamily = FontFamily.Monospace, fontSize = 7.5.sp)
                        }
                        ProgressBarRetro(current = uiState.playerShield, max = uiState.playerMaxShield, color = CyberBrightGreen)
                    }

                    // Memory RAM
                    Column(modifier = Modifier.weight(1f)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("RAM", color = CyberPink, fontFamily = FontFamily.Monospace, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                            Text("${uiState.ram}/${uiState.maxRam}MB", color = CyberPink, fontFamily = FontFamily.Monospace, fontSize = 7.5.sp)
                        }
                        ProgressBarRetro(current = uiState.ram, max = uiState.maxRam, color = CyberPink)
                    }
                }
            }
        }

        // --- 5. COMBAT TELEMETRY LOG BANNER ---
        if (uiState.enemyCombatAction.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberDark),
                border = BorderStroke(1.dp, CyberPink.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = uiState.enemyCombatAction,
                    color = CyberPink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.5.sp,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }
    }
}

// ==========================================
// Sub-Composable: Hacking Minigame
// ==========================================
@Composable
fun HackingMinigableView(
    uiState: GameViewModel.GameUiState,
    onCellSelected: (Int, Int) -> Unit,
    onCancel: () -> Unit
) {
    val puzzle = uiState.activePuzzle ?: return
    val view = LocalView.current

    var hackModeTab by remember { mutableStateOf(0) } // 0 = MATRIX COMMAND TERMINAL, 1 = BREACH PROTOCOL GRID

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(CutCornerShape(4.dp))
                        .background(if (hackModeTab == 0) CyberGreen else CyberDark)
                        .border(BorderStroke(1.dp, CyberGreen), CutCornerShape(4.dp))
                        .clickable { hackModeTab = 0 }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "COMMAND TERMINAL",
                        color = if (hackModeTab == 0) CyberDark else CyberGreen,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CutCornerShape(4.dp))
                        .background(if (hackModeTab == 1) CyberAmber else CyberDark)
                        .border(BorderStroke(1.dp, CyberAmber), CutCornerShape(4.dp))
                        .clickable { hackModeTab = 1 }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "BREACH GRID",
                        color = if (hackModeTab == 1) CyberDark else CyberAmber,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            Text(
                text = "--- BREACH PROTOCOL ACCESS ---",
                color = CyberAmber,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }

        if (hackModeTab == 0) {
            MatrixHackingTerminalScreen(
                onExit = onCancel,
                modifier = Modifier.fillMaxSize()
            )
        } else {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Left Panel: 5x5 Hex code matrix
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberDark),
                border = BorderStroke(1.dp, CyberBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (r in 0 until 5) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (c in 0 until 5) {
                                val value = puzzle.grid[r][c]
                                val isSelected = puzzle.selectedIndices.contains(Pair(r, c))
                                val isRowHighlighted = puzzle.highlightedRow == r
                                val isColHighlighted = puzzle.highlightedCol == c

                                // Determine style
                                val cellColor = when {
                                    isSelected -> Color.Gray
                                    isRowHighlighted || isColHighlighted -> CyberAmber
                                    else -> CyberCyan
                                }

                                val cellBg = when {
                                    isSelected -> CyberDark
                                    isRowHighlighted || isColHighlighted -> CyberMutedGreen
                                    else -> Color.Transparent
                                }

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(cellBg)
                                        .border(
                                            width = 1.dp,
                                            color = if (isRowHighlighted || isColHighlighted) CyberAmber else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable(enabled = !isSelected) {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            onCellSelected(r, c)
                                        }
                                        .testTag("hex_cell_${r}_${c}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isSelected) "--" else value,
                                        color = cellColor,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Right Panel: Targets and Buffer state
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                border = BorderStroke(1.dp, CyberBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "TARGET SEQUENCE:",
                        color = CyberCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Displays targets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        puzzle.targetSequence.forEach { target ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CyberMutedGreen)
                                    .border(1.dp, CyberCyan, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = target,
                                    color = CyberCyan,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "INTRUSION BUFFER: (${puzzle.currentBuffer.size}/${puzzle.bufferLimit})",
                        color = CyberAmber,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Displays selections so far
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (i in 0 until puzzle.bufferLimit) {
                            val value = puzzle.currentBuffer.getOrNull(i)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CyberDark)
                                    .border(1.dp, if (value != null) CyberAmber else Color.DarkGray, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = value ?: "",
                                    color = CyberAmber,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "INFO: Rule alternate Horizontal -> Vertical. Start on top horizontal row.",
                        color = CyberBrightGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        lineHeight = 11.sp
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberDark),
                        border = BorderStroke(1.dp, CyberPink),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("btn_cancel_hack")
                    ) {
                        Text(
                            text = "DISCONNECT TERMINAL",
                            color = CyberPink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

}

// ==========================================
// Sub-Composable: Shop Screen
// ==========================================
@Composable
fun UpgradeStoreView(
    uiState: GameViewModel.GameUiState,
    onBuyCyberware: (Cyberware) -> Unit,
    onBuyConsumable: (String, Int) -> Unit,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "--- BLACK-MARKET MAINFRAME RE-ROUTE ---",
                color = CyberCyan,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Text(
                text = "MEM CREDITS: ${uiState.credits} MB",
                color = CyberAmber,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }

        // Main store scroll list
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Hardware Cyberware Modules section
            Text(
                text = "HARDWARE CYBERWARE INTEGRATIONS:",
                color = CyberCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )

            val cyberwares = GameEngine.getStoreCyberware()
            cyberwares.forEach { cyber ->
                val alreadyInstalled = uiState.installedCyberware.any { it.id == cyber.id }
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                    border = BorderStroke(1.dp, if (alreadyInstalled) CyberBorder else CyberCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cyber.name,
                                color = if (alreadyInstalled) CyberMutedText else CyberCyan,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = cyber.description,
                                color = CyberBrightGreen,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                lineHeight = 11.sp
                            )
                        }

                        Button(
                            onClick = { onBuyCyberware(cyber) },
                            enabled = !alreadyInstalled && uiState.credits >= cyber.cost,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (alreadyInstalled) Color.Transparent else CyberMutedGreen,
                                disabledContainerColor = CyberDark
                            ),
                            border = BorderStroke(1.dp, if (alreadyInstalled) CyberBorder else CyberCyan),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("btn_buy_${cyber.id}")
                        ) {
                            Text(
                                text = if (alreadyInstalled) "MOUNTED" else "${cyber.cost} MB",
                                color = if (alreadyInstalled) CyberMutedText else CyberCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Consumable downloads section
            Text(
                text = "VIRTUAL UTILITY CONSOLE DOWNLOADS:",
                color = CyberCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )

            val utilities = listOf(
                Pair("NanoMed.sys", 50),
                Pair("RAMBoost.exe", 40),
                Pair("Decryptor.pkg", 80),
                Pair("AntiShield.bin", 60),
                Pair("FirewallBuffer.pkg", 75),
                Pair("GibsonForecast.sys", 40)
            )

            utilities.forEach { util ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                    border = BorderStroke(1.dp, CyberBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = util.first,
                                color = CyberCyan,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            val desc = when (util.first) {
                                "NanoMed.sys" -> "Hot patch sector. Restore 40 System Integrity."
                                "RAMBoost.exe" -> "Refresh allocation pipelines. Restore 6 MB RAM."
                                "Decryptor.pkg" -> "Analyze encrypted tokens. Unlocks credits upon crack."
                                "AntiShield.bin" -> "Integrate heavy packet algorithms (+2 basic attack bonus)."
                                "FirewallBuffer.pkg" -> "Increase active protective padding (+2 defense rating)."
                                "GibsonForecast.sys" -> "Atmospheric monitor. Predict upcoming sub-grid weather."
                                else -> "General software package."
                            }
                            Text(
                                text = desc,
                                color = CyberBrightGreen,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                lineHeight = 11.sp
                            )
                        }

                        Button(
                            onClick = { onBuyConsumable(util.first, util.second) },
                            enabled = uiState.credits >= util.second,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberMutedGreen),
                            border = BorderStroke(1.dp, CyberCyan),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("btn_buy_utility_${util.first}")
                        ) {
                            Text(
                                text = "${util.second} MB",
                                color = CyberCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onExit,
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .testTag("btn_exit_shop")
        ) {
            Text(
                text = "DISCONNECT BLACK-MARKET",
                color = Color.Black,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}

// ==========================================
// Sub-Composable: Leaderboard Screen (Room Database)
// ==========================================
@Composable
fun LeaderboardView(
    scores: List<RunRecord>,
    onClearScores: () -> Unit,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "--- MAINFRAME HISTORIC ARCHIVES ---",
                color = CyberCyan,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Button(
                onClick = onClearScores,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                border = BorderStroke(1.dp, CyberPink),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier
                    .height(28.dp)
                    .testTag("btn_purge_scores")
            ) {
                Text(
                    text = "PURGE LOGS",
                    color = CyberPink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CyberDark)
                .border(1.dp, CyberBorder, RoundedCornerShape(12.dp))
                .padding(vertical = 6.dp, horizontal = 8.dp)
        ) {
            Text("RUNNER HANDLE", color = CyberCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.weight(1.2f))
            Text("ARCHETYPE", color = CyberCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.weight(1f))
            Text("SECTOR", color = CyberCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center)
            Text("HACKS", color = CyberCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center)
            Text("STATUS", color = CyberCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        }

        Spacer(modifier = Modifier.height(6.dp))

        // List
        if (scores.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "[ NO TERMINAL ARCHIVE RECORDS DETECTED ]",
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("scores_list"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(scores) { record ->
                    val isDead = record.outcome == "DECEASED"
                    val statusColor = if (isDead) CyberPink else CyberCyan

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberCardBg)
                            .border(1.dp, CyberBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text(record.runnerName, color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.weight(1.2f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(record.runnerClass, color = CyberBrightGreen, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.weight(1f))
                        Text("Lvl ${record.levelReached}", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center)
                        Text("${record.nodesHacked}", color = CyberAmber, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center)
                        Text(record.outcome, color = statusColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = onExit,
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .testTag("btn_exit_leaderboard")
        ) {
            Text(
                text = "DISCONNECT ARCHIVE TERMINAL",
                color = Color.Black,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}

// ==========================================
// Sub-Composable: Game Over / Outcome Screen
// ==========================================
@Composable
fun GameOverView(
    uiState: GameViewModel.GameUiState,
    onRestart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val isDead = uiState.integrity <= 0

        Text(
            text = if (isDead) "!!! SYSTEM CORE SHUTDOWN !!!" else "--- SECTOR TRANSMISSION TERMINATED ---",
            color = if (isDead) CyberPink else CyberCyan,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Text(
            text = if (isDead) {
                """
      ___   _   __  __ ___    ___  __   _____ ___ 
     / __| /_\ |  \/  | __|  / _ \ \ \ / / __| _ \
    | (_ |/ _ \| |\/| | _|  | (_) | \ V /| _||   /
     \___/_/ \_\_|  |_|___|  \___/   \_/ |___|_|_\
                """.trimIndent()
            } else {
                """
     _  _  ____  ____  ____  __  ____  _  _  ____ 
    ( \/ )(_  _)/ ___)(_  _)(  )(_  _)( \/ )(  __)
    / \/ \ _)(_ \___ \  )(   )(   )(   \  /  ) _) 
    \_/\_/(____)(____/ (__) (__) (__)   \/  (____)
                """.trimIndent()
            },
            color = if (isDead) CyberPink else CyberCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            lineHeight = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = CyberCardBg),
            border = BorderStroke(1.dp, CyberBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "INTRUSION AUDIT SUMMARY // ${uiState.runnerName.uppercase()}",
                    color = CyberCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )

                HorizontalDivider(color = CyberBorder)

                Text(
                    text = "RUNNER SPECS: ${uiState.runnerClass.title}",
                    color = CyberBrightGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )

                Text(
                    text = "MAXIMUM DEPTH REACHED: Layer ${uiState.level}",
                    color = CyberCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "ENCRYPTED NODES BYPASSED: ${uiState.nodesHackedCount}",
                    color = CyberPink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )

                Text(
                    text = "TOTAL CREDITS ACQUIRED: ${uiState.totalCreditsEarned} MB",
                    color = CyberAmber,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )

                Text(
                    text = "CONNECTION LOG TERMINATION STATE: ${uiState.runOutcome}",
                    color = if (isDead) CyberPink else CyberCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRestart,
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 16.dp)
                .testTag("btn_reboot_system")
        ) {
            Text(
                text = "REBOOT MAIN OS COMPILER",
                color = Color.Black,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

// ==========================================
// Sub-Composable: Retro ProgressBar
// ==========================================
@Composable
fun ProgressBarRetro(
    current: Int,
    max: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animatedCurrent by androidx.compose.animation.core.animateFloatAsState(
        targetValue = current.toFloat(),
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "ProgressBarProgress"
    )
    val filledPercent = if (max > 0) animatedCurrent / max else 0f
    val clampedPercent = filledPercent.coerceIn(0f, 1f)

    val finalColor = if (clampedPercent < 0.25f) {
        Color(0xFFEF4444) // Red
    } else if (clampedPercent < 0.5f) {
        Color(0xFFFBBF24) // Yellow
    } else {
        color
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(CyberDark)
            .border(1.dp, finalColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(clampedPercent)
                .background(finalColor)
        )
    }
}

// ==========================================
// Sub-Composable: Terminal Scrolling Logs & Visual Telemetry Dashboard
// ==========================================
@Composable
fun TerminalLogConsole(
    uiState: GameViewModel.GameUiState,
    onSendCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val logs = uiState.logFeed
    var activeConsoleTab by remember { mutableStateOf(0) } // 0 = TELEMETRY DIAGNOSTICS, 1 = RAW SIGNAL FEED

    val infiniteTransition = rememberInfiniteTransition(label = "crt_pulse")
    val glowIntensity by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_float"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = CyberDark),
        border = BorderStroke(1.dp, CyberBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                if (uiState.gameState != GameState.EXPLORATION) CyberPink else CyberCyan,
                                CircleShape
                            )
                    )
                    Text(
                        text = if (activeConsoleTab == 0) "CYBER-TELEMETRY // VISUAL SYSTEM GRAPHICS" else "RAW COGNITIVE LOG // LOGICAL TRACE",
                        color = if (uiState.gameState != GameState.EXPLORATION) CyberPink else CyberCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.graphicsLayer { alpha = glowIntensity }
                    )
                }

                // Aesthetic tabs
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .border(
                                1.dp,
                                if (activeConsoleTab == 0) CyberCyan else CyberBorder,
                                RoundedCornerShape(4.dp)
                            )
                            .background(if (activeConsoleTab == 0) CyberCyan.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { activeConsoleTab = 0 }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "VISUAL TELEMETRY",
                            color = if (activeConsoleTab == 0) CyberCyan else Color.Gray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .border(
                                1.dp,
                                if (activeConsoleTab == 1) CyberCyan else CyberBorder,
                                RoundedCornerShape(4.dp)
                            )
                            .background(if (activeConsoleTab == 1) CyberCyan.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { activeConsoleTab = 1 }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "LOG FEED",
                            color = if (activeConsoleTab == 1) CyberCyan else Color.Gray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            HorizontalDivider(color = CyberBorder.copy(alpha = 0.3f), thickness = 1.dp, modifier = Modifier.padding(bottom = 4.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (activeConsoleTab == 0) {
                    TelemetryDashboardView(uiState)
                } else {
                    val listState = rememberLazyListState()
                    LaunchedEffect(logs.size) {
                        if (logs.isNotEmpty()) {
                            listState.scrollToItem(logs.size - 1)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("terminal_live_logs"),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(logs) { log ->
                            val color = when (log.type) {
                                LogType.INFO -> CyberCyan
                                LogType.ALERT -> CyberAmber
                                LogType.SUCCESS -> CyberBrightGreen
                                LogType.ERROR -> CyberPink
                            }
                            val prefix = when (log.type) {
                                LogType.INFO -> "> "
                                LogType.ALERT -> "[!] "
                                LogType.SUCCESS -> "[+] "
                                LogType.ERROR -> "[X] "
                            }

                            Text(
                                text = "$prefix${log.text}",
                                color = color,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                lineHeight = 11.sp
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = CyberBorder.copy(alpha = 0.3f), thickness = 1.dp, modifier = Modifier.padding(vertical = 2.dp))

            // Command input row with blinking retro cursor
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 1.dp, bottom = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "[runner@cybergrid]$ ",
                    color = CyberCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )

                var textValue by remember { mutableStateOf("") }
                val cursorTransition = rememberInfiniteTransition(label = "cursor_blink")
                val cursorAlpha by cursorTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 500, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "cursor_alpha"
                )

                BasicTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 8.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (textValue.isNotBlank()) {
                            onSendCommand(textValue)
                            textValue = ""
                        }
                    }),
                    cursorBrush = SolidColor(CyberCyan),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 2.dp)
                        .testTag("terminal_command_input"),
                    decorationBox = @Composable { innerTextField ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (textValue.isEmpty()) {
                                    Text(
                                        text = "type 'help' for protocols...",
                                        color = Color.Gray.copy(alpha = 0.5f),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.sp
                                    )
                                }
                                innerTextField()
                            }
                            Text(
                                text = "█",
                                color = CyberCyan.copy(alpha = cursorAlpha),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                modifier = Modifier.padding(start = 1.dp)
                            )
                        }
                    }
                )

                // Quick execute button
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(CyberCyan.copy(alpha = 0.15f))
                        .border(1.dp, CyberCyan.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .clickable {
                            if (textValue.isNotBlank()) {
                                onSendCommand(textValue)
                                textValue = ""
                            }
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "EXE",
                        color = CyberCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun TelemetryDashboardView(uiState: GameViewModel.GameUiState) {
    val phaseTransition = rememberInfiniteTransition(label = "OscPhase")
    val phaseProgress by phaseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // COLUMN 1: OSCILLOSCOPE CYBER-WAVE (40% width)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF030406)),
            border = BorderStroke(1.dp, CyberBorder.copy(alpha = 0.5f)),
            modifier = Modifier
                .weight(1.3f)
                .fillMaxHeight()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val midY = height / 2f

                    val isCombat = uiState.gameState != GameState.EXPLORATION
                    val isWeatherFlare = uiState.activeWeather == com.example.data.CyberWeather.DATA_STORM
                    val isWeatherCold = uiState.activeWeather == com.example.data.CyberWeather.COLD_SPOT
                    val integrityFraction = uiState.integrity.toFloat() / uiState.maxIntegrity.coerceAtLeast(1)

                    val baseColor = when {
                        isCombat -> CyberPink
                        isWeatherFlare -> CyberAmber
                        isWeatherCold -> Color(0xFF60A5FA)
                        else -> CyberCyan
                    }

                    val amplitude = when {
                        integrityFraction < 0.3f -> height * 0.40f
                        isCombat -> height * 0.32f
                        isWeatherFlare -> height * 0.35f
                        isWeatherCold -> height * 0.12f
                        else -> height * 0.22f
                    }

                    val frequency = when {
                        isCombat -> 5.5f
                        isWeatherFlare -> 8f
                        isWeatherCold -> 1.5f
                        else -> 3f
                    }

                    // Draw subtle grid line coordinates
                    drawLine(Color(0x1F00F3FF), Offset(0f, midY), Offset(width, midY), strokeWidth = 1f)
                    for (gridX in (width / 5).toInt() until width.toInt() step (width / 5).toInt()) {
                        drawLine(Color(0x1100F3FF), Offset(gridX.toFloat(), 0f), Offset(gridX.toFloat(), height), strokeWidth = 1f)
                    }

                    val path1 = Path()
                    val path2 = Path()

                    for (x in 0..width.toInt() step 2) {
                        val xFloat = x.toFloat()
                        val radians = (xFloat / width) * frequency * (2f * Math.PI.toFloat()) + phaseProgress

                        val glitchOffset = if (isWeatherFlare && x % 10 == 0) {
                            val rand = java.util.Random(x.toLong() + (phaseProgress * 10).toLong())
                            (rand.nextFloat() - 0.5f) * amplitude * 0.5f
                        } else 0f

                        val yFloat = midY + (Math.sin(radians.toDouble()).toFloat() * amplitude) + glitchOffset

                        if (x == 0) {
                            path1.moveTo(xFloat, yFloat)
                        } else {
                            path1.lineTo(xFloat, yFloat)
                        }

                        val yFloat2 = midY + (Math.cos((radians - 1.2f).toDouble()).toFloat() * amplitude * 0.6f)
                        if (x == 0) {
                            path2.moveTo(xFloat, yFloat2)
                        } else {
                            path2.lineTo(xFloat, yFloat2)
                        }
                    }

                    drawPath(path2, color = baseColor.copy(alpha = 0.25f), style = Stroke(width = 1f))
                    drawPath(path1, color = baseColor, style = Stroke(width = 1.5f))
                }

                // Wave label
                Text(
                    text = if (uiState.gameState != GameState.EXPLORATION) "COMBAT WAVE: SIG_ALRT" else "COHERENCE: SIG_OK",
                    color = (if (uiState.gameState != GameState.EXPLORATION) CyberPink else CyberCyan).copy(alpha = 0.8f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
            }
        }

        // COLUMN 2: SUB-SYSTEM ANALYTICS (30% width)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            val isCombat = uiState.gameState != GameState.EXPLORATION

            // Core load animated flutter
            var cpuLoad by remember { mutableStateOf(34f) }
            LaunchedEffect(isCombat) {
                while (true) {
                    val base = if (isCombat) 74f else 32f
                    val flutter = (Math.random() * 8 - 4).toFloat()
                    cpuLoad = (base + flutter).coerceIn(10f, 99f)
                    delay(500)
                }
            }

            // Sync index
            val syncIndex = (60 + uiState.level * 8).coerceIn(60, 100).toFloat() / 100f

            MiniSensorRow(
                label = "MEM [ RAM ]",
                valueText = "${uiState.ram}/${uiState.maxRam}MB",
                progress = uiState.ram.toFloat() / uiState.maxRam.coerceAtLeast(1),
                color = CyberPink
            )

            MiniSensorRow(
                label = "CORE [ CPU ]",
                valueText = "${cpuLoad.toInt()}% LOAD",
                progress = cpuLoad / 100f,
                color = CyberCyan
            )

            MiniSensorRow(
                label = "SYNC [ LNK ]",
                valueText = "${(syncIndex * 100).toInt()}% SYNC",
                progress = syncIndex,
                color = CyberAmber
            )
        }

        // COLUMN 3: DIGITAL BEACONS & DATA MATRIX (30% width)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // System LEDs indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // LED 1: SEC
                val secActive = uiState.gameState != GameState.EXPLORATION
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(CyberMutedGreen, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(if (secActive) CyberPink else Color(0xFF10B981), CircleShape)
                    )
                    Text(
                        text = if (secActive) "ALRT" else "SAFE",
                        color = if (secActive) CyberPink else Color(0xFF10B981),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // LED 2: WTR
                val isWeatherClear = uiState.activeWeather == com.example.data.CyberWeather.CLEAR
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(CyberMutedGreen, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(if (isWeatherClear) Color(0xFF10B981) else CyberAmber, CircleShape)
                    )
                    Text(
                        text = if (isWeatherClear) "NORM" else "HAZR",
                        color = if (isWeatherClear) Color(0xFF10B981) else CyberAmber,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Hex datastream memory dump
            val hexStateList = remember { mutableStateListOf("0x4A1E MEM_STABLE", "0x5F12 SYNC_WAIT", "0x9E2B LNK_ESTB") }
            LaunchedEffect(Unit) {
                val rand = java.util.Random()
                val ops = listOf("TX_READ", "RX_WRITE", "ALLOC_M", "PORT_PN", "SEC_PUL", "GATE_CY")
                while (true) {
                    delay(700)
                    val nextHex = "0x${Integer.toHexString(rand.nextInt(0xFFF)).uppercase()}"
                    val nextOp = ops[rand.nextInt(ops.size)]

                    if (hexStateList.size >= 3) {
                        hexStateList.removeAt(0)
                    }
                    hexStateList.add("$nextHex $nextOp")
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                border = BorderStroke(1.dp, CyberBorder.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.3f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    FractalGeometryCanvas(
                        modifier = Modifier.fillMaxSize(),
                        primaryColor = CyberCyan,
                        animProgress = phaseProgress / (2f * Math.PI.toFloat())
                    )
                    Text(
                        text = "FRACTAL LOGIC CORE // ACTV",
                        color = CyberCyan.copy(alpha = 0.8f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 6.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(3.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MiniSensorRow(label: String, valueText: String, progress: Float, color: Color) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.Gray,
                fontFamily = FontFamily.Monospace,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = valueText,
                color = color,
                fontFamily = FontFamily.Monospace,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(1.dp))

        Row(
            modifier = Modifier.fillMaxWidth().height(5.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            val activeSegments = (progress * 10).toInt().coerceIn(0, 10)
            for (i in 0 until 10) {
                val segmentColor = if (i < activeSegments) color else Color.Gray.copy(alpha = 0.15f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(segmentColor, RoundedCornerShape(1.dp))
                )
            }
        }
    }
}


// ==========================================
// Sub-Composable: High Density Bottom Navigation
// ==========================================
@Composable
fun HighDensityBottomNavigation(
    currentScreen: GameViewModel.ActiveScreen,
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    Card(
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        border = BorderStroke(1.dp, CyberBorder),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Tab 1: NAV
                val isNavActive = currentScreen == GameViewModel.ActiveScreen.EXPLORATION
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            if (currentScreen != GameViewModel.ActiveScreen.CHARACTER_CREATION) {
                                viewModel.exitShop()
                                viewModel.exitLeaderboard()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("nav_tab_exploration")
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Nav",
                        tint = if (isNavActive) CyberCyan else CyberBrightGreen.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "NAV",
                        color = if (isNavActive) CyberCyan else CyberBrightGreen.copy(alpha = 0.4f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Tab 2: NODES
                val isNodesActive = currentScreen == GameViewModel.ActiveScreen.COMBAT || 
                                    currentScreen == GameViewModel.ActiveScreen.HACKING_MINIGAME
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            if (currentScreen == GameViewModel.ActiveScreen.EXPLORATION) {
                                viewModel.interact()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("nodes_tab_combat")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Nodes",
                        tint = if (isNodesActive) CyberCyan else CyberBrightGreen.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "NODES",
                        color = if (isNodesActive) CyberCyan else CyberBrightGreen.copy(alpha = 0.4f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Tab 3: LOADOUT
                val isLoadoutActive = currentScreen == GameViewModel.ActiveScreen.UPGRADE_STORE
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            if (currentScreen != GameViewModel.ActiveScreen.CHARACTER_CREATION &&
                                currentScreen != GameViewModel.ActiveScreen.GAME_OVER) {
                                viewModel.enterShop()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("loadout_tab_store")
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Loadout",
                        tint = if (isLoadoutActive) CyberCyan else CyberBrightGreen.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "LOADOUT",
                        color = if (isLoadoutActive) CyberCyan else CyberBrightGreen.copy(alpha = 0.4f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Tab 4: SYSTEM
                val isSystemActive = currentScreen == GameViewModel.ActiveScreen.LEADERBOARD
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            if (currentScreen != GameViewModel.ActiveScreen.CHARACTER_CREATION &&
                                currentScreen != GameViewModel.ActiveScreen.GAME_OVER) {
                                viewModel.viewLeaderboard()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("system_tab_leaderboard")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "System",
                        tint = if (isSystemActive) CyberCyan else CyberBrightGreen.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "SYSTEM",
                        color = if (isSystemActive) CyberCyan else CyberBrightGreen.copy(alpha = 0.4f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Tab 5: SVDAG
                val isSvdagActive = currentScreen == GameViewModel.ActiveScreen.SVDAG_WORLD_BUILDER
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            if (currentScreen != GameViewModel.ActiveScreen.CHARACTER_CREATION &&
                                currentScreen != GameViewModel.ActiveScreen.GAME_OVER) {
                                viewModel.enterSvdagWorldInspector()
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("svdag_tab_world_builder")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "SVDAG",
                        tint = if (isSvdagActive) CyberCyan else CyberBrightGreen.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "SVDAG",
                        color = if (isSvdagActive) CyberCyan else CyberBrightGreen.copy(alpha = 0.4f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FloatingDamagePopup(
    text: String?,
    color: Color,
    isPlayer: Boolean,
    modifier: Modifier = Modifier
) {
    var activeText by remember { mutableStateOf<String?>(null) }
    val anim = remember { Animatable(0f) }

    LaunchedEffect(text) {
        if (text != null) {
            activeText = text
            anim.snapTo(0f)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 600, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
            )
            activeText = null
        }
    }

    activeText?.let { popup ->
        val yOffset = if (isPlayer) {
            (40 + anim.value * 60).dp
        } else {
            (-40 - anim.value * 60).dp
        }
        val scale = 1f + anim.value * 0.3f
        Box(
            modifier = modifier
                .offset(y = yOffset)
                .graphicsLayer(
                    alpha = 1f - anim.value,
                    scaleX = scale,
                    scaleY = scale
                )
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.8f))
                .border(1.dp, color, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = popup,
                color = color,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun DigitalSparks(color: Color, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "Sparks")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "SparksTime"
    )
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val random = java.util.Random(42)
        val count = 25
        for (i in 0 until count) {
            val xRatio = random.nextFloat()
            val yRatio = random.nextFloat()
            val dx = (random.nextFloat() - 0.5f) * 60f
            val dy = (random.nextFloat() - 0.5f) * 60f
            
            val pTime = (time + xRatio) % 1.0f
            val x = xRatio * w + dx * pTime
            val y = yRatio * h + dy * pTime
            val alpha = 1f - pTime
            val sizePx = random.nextFloat() * 6f + 2f
            
            drawRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(x, y),
                size = Size(sizePx, sizePx)
            )
        }
    }
}

// Post-processing Glitch & Scanline Overlay Shader for First-Person Cyberspace Viewport
@Composable
fun FirstPersonGlitchShaderOverlay(
    integrity: Int,
    maxIntegrity: Int,
    isCombat: Boolean,
    isPlayerHit: Boolean = false,
    frameTime: Long,
    modifier: Modifier = Modifier
) {
    val healthRatio = (integrity.toFloat() / maxIntegrity.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val missingRatio = (1f - healthRatio).coerceIn(0f, 1f)
    val hitBoost = if (isPlayerHit) 0.35f else 0f
    val targetIntensity = (missingRatio + hitBoost).coerceIn(0f, 1f)

    val animatedIntensity by animateFloatAsState(
        targetValue = targetIntensity,
        animationSpec = tween(durationMillis = 150, easing = LinearOutSlowInEasing),
        label = "GlitchIntensity"
    )

    if (animatedIntensity <= 0.02f && !isCombat) return

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val timeSec = frameTime / 1000f

            // 1. Dynamic CRT Scanlines (Increases density & opacity with instability)
            val effectiveIntensity = if (animatedIntensity < 0.05f && isCombat) 0.08f else animatedIntensity
            val numScanlines = (22 + (effectiveIntensity * 60).toInt()).coerceIn(20, 100)
            val scanlineOpacity = 0.04f + (effectiveIntensity * 0.22f)
            val scrollOffset = (timeSec * 75f) % (h / numScanlines)

            for (i in 0 until numScanlines) {
                val lineY = (h / numScanlines) * i + scrollOffset
                val finalY = lineY % h
                drawLine(
                    color = Color.Black.copy(alpha = scanlineOpacity),
                    start = Offset(0f, finalY),
                    end = Offset(w, finalY),
                    strokeWidth = 1.5f + (effectiveIntensity * 2.5f)
                )
            }

            if (effectiveIntensity > 0.05f) {
                val frameSeed = (frameTime / 45L) + (effectiveIntensity * 1000).toLong()
                val random = java.util.Random(frameSeed)

                // 2. Horizontal Screen Displacement / CRT Line Glitch Slices
                val numSlices = (effectiveIntensity * 16).toInt()
                for (i in 0 until numSlices) {
                    val sliceY = random.nextFloat() * h
                    val sliceH = 2f + random.nextFloat() * (14f * effectiveIntensity)
                    val shiftX = (random.nextFloat() - 0.5f) * (45f * effectiveIntensity)
                    val sliceAlpha = 0.12f + random.nextFloat() * (0.45f * effectiveIntensity)

                    val sliceColor = if (random.nextBoolean()) Color(0xFF00E5FF) else Color(0xFFFB7185)
                    drawRect(
                        color = sliceColor.copy(alpha = sliceAlpha),
                        topLeft = Offset(shiftX.coerceAtLeast(0f), sliceY),
                        size = Size(w, sliceH)
                    )
                }

                // 3. Digital Micro-Block Noise Corruption Glitches (Pixel Blocks)
                val blockCount = (effectiveIntensity * 20).toInt()
                for (b in 0 until blockCount) {
                    val blockW = 12f + random.nextFloat() * (70f * effectiveIntensity)
                    val blockH = 6f + random.nextFloat() * (35f * effectiveIntensity)
                    val blockX = random.nextFloat() * (w - blockW)
                    val blockY = random.nextFloat() * (h - blockH)

                    val blockType = random.nextInt(3)
                    val blockColor = when (blockType) {
                        0 -> Color(0xFF00E5FF) // Cyber Cyan static
                        1 -> Color(0xFFFB7185) // Cyber Pink static
                        else -> Color.White   // Static white noise
                    }

                    drawRect(
                        color = blockColor.copy(alpha = 0.15f + random.nextFloat() * 0.45f * effectiveIntensity),
                        topLeft = Offset(blockX, blockY),
                        size = Size(blockW, blockH)
                    )

                    if (random.nextBoolean()) {
                        drawLine(
                            color = Color.Black.copy(alpha = 0.7f),
                            start = Offset(blockX, blockY + blockH / 2f),
                            end = Offset(blockX + blockW, blockY + blockH / 2f),
                            strokeWidth = 1.5f
                        )
                    }
                }

                // 4. Chromatic Aberration Edge Fringe (RGB Split at extreme instability)
                if (effectiveIntensity > 0.35f) {
                    val fringeShift = (effectiveIntensity - 0.35f) * 18f
                    val fringeAlpha = ((effectiveIntensity - 0.35f) * 0.65f).coerceIn(0f, 0.45f)

                    drawRect(
                        color = Color(0xFFFB7185).copy(alpha = fringeAlpha),
                        topLeft = Offset(0f, 0f),
                        size = Size(fringeShift, h)
                    )
                    drawRect(
                        color = Color(0xFF00E5FF).copy(alpha = fringeAlpha),
                        topLeft = Offset(w - fringeShift, 0f),
                        size = Size(fringeShift, h)
                    )
                }

                // 5. Critical Stability Warning Vignette Pulse (When health < 35%)
                if (healthRatio < 0.35f) {
                    val critPulse = 0.3f + 0.30f * kotlin.math.sin(timeSec * 14f)
                    val critColor = Color(0xFFEF4444)

                    val vignetteGradient = Brush.radialGradient(
                        colors = listOf(Color.Transparent, critColor.copy(alpha = critPulse * (1f - healthRatio * 2f).coerceIn(0.2f, 0.85f))),
                        center = Offset(w / 2f, h / 2f),
                        radius = w * 0.65f
                    )
                    drawRect(
                        brush = vignetteGradient,
                        topLeft = Offset(0f, 0f),
                        size = size
                    )
                }
            }
        }

        // 6. HUD Critical Glitch Alert Overlay Text
        if (healthRatio < 0.30f) {
            val alertFlicker = kotlin.math.sin((frameTime / 100f).toDouble()) > 0.0
            if (alertFlicker) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 36.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Red.copy(alpha = 0.85f))
                        .border(BorderStroke(1.dp, Color.White), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "⚠️ CRITICAL STABILITY // INTEGRITY ${(healthRatio * 100).toInt()}%",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

@Composable
fun GlitchOverlay(progress: Float, modifier: Modifier = Modifier) {
    if (progress <= 0f || progress >= 1f) return
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val random = java.util.Random((progress * 100).toLong())
        // Draw some glitch bars
        val count = (random.nextFloat() * 5 + 3).toInt()
        for (i in 0 until count) {
            val barY = random.nextFloat() * h
            val barH = random.nextFloat() * 15f + 4f
            val barW = random.nextFloat() * w * 0.7f + w * 0.2f
            val barX = random.nextFloat() * (w - barW)
            val color = if (random.nextBoolean()) Color(0xFF00E5FF).copy(alpha = 0.7f) else Color(0xFFFB7185).copy(alpha = 0.7f)
            drawRect(
                color = color,
                topLeft = Offset(barX, barY),
                size = Size(barW, barH)
            )
        }
        // Draw static lines
        val lineCount = 8
        for (i in 0 until lineCount) {
            val lineY = random.nextFloat() * h
            drawLine(
                color = Color.White.copy(alpha = 0.4f),
                start = Offset(0f, lineY),
                end = Offset(w, lineY),
                strokeWidth = 2f
            )
        }
    }
}

// ----------------------------------------------------
// Sub-Composable: Start/Pause Menu Screen
// ----------------------------------------------------
@Composable
fun StartMenuView(
    viewModel: GameViewModel,
    hasSavedGame: Boolean,
    isActiveRun: Boolean,
    onStartNewRun: () -> Unit,
    onLoadGame: () -> Unit,
    onSaveGame: () -> Unit,
    onResumeGame: () -> Unit,
    onLeaderboardClick: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Aesthetic ASCII title card for Netcrawler
        Text(
            text = """
 _  _  ____ ____  ___  ____   __   _  _  __    ____ ____ 
( \( )(  __)(_  _)/ __)(  _ \ / _\ ( \/ )(  )  (  __)(  _ \
 )  (  ) _)   )( ( (__  )   //    \/ \/ \/ (_/\ ) _)  )   /
(_)\_)(____) (__) \___)(_)\_)\_/\_/\_/\_/\____/(____)(_)\_)
            """.trimIndent(),
            color = CyberCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "SYSTEM ARCHITECTURE TERMINAL V8.91 //",
            color = CyberGreen,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = CyberCardBg.copy(alpha = 0.85f)),
            border = BorderStroke(1.dp, CyberBorder),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isActiveRun) {
                    // Resume Game Button
                    Button(
                        onClick = onResumeGame,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, CyberCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_resume_uplink")
                    ) {
                        Text(
                            text = "⚡ RESUME ACTIVE UPLINK",
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    // Save Game Button
                    Button(
                        onClick = onSaveGame,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberGreen.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, CyberGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_save_game")
                    ) {
                        Text(
                            text = "💾 SAVE COGNITIVE STATE",
                            color = CyberGreen,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // New Run Button
                Button(
                    onClick = onStartNewRun,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberAmber.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, CyberAmber),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_new_run")
                ) {
                    Text(
                        text = "🛰️ START NEW UPLINK",
                        color = CyberAmber,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                // Load Game Button
                Button(
                    onClick = onLoadGame,
                    enabled = hasSavedGame,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasSavedGame) CyberCyan.copy(alpha = 0.15f) else Color.Transparent,
                        disabledContainerColor = Color.Transparent
                    ),
                    border = BorderStroke(1.dp, if (hasSavedGame) CyberCyan else CyberBorder.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_load_game")
                ) {
                    Text(
                        text = if (hasSavedGame) "📶 LOAD SECURE POINT" else "🔒 NO RESTORE POINT FOUND",
                        color = if (hasSavedGame) CyberCyan else CyberMutedText,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                // Leaderboard Button
                Button(
                    onClick = onLeaderboardClick,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberBorder.copy(alpha = 0.1f)),
                    border = BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_mainframe_leaderboard")
                ) {
                    Text(
                        text = "📊 MAINFRAME HISTORIC RECORDS",
                        color = CyberCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }

                // SVDAG World Builder Button
                Button(
                    onClick = { viewModel.enterSvdagWorldInspector() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7).copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, Color(0xFFA855F7)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_svdag_world_builder")
                ) {
                    Text(
                        text = "🧊 SVDAG WORLD BUILDER (128³ VOXELS)",
                        color = Color(0xFFA855F7),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                // System Exit Button
                Button(
                    onClick = {
                        context.findActivity()?.finish() ?: kotlin.system.exitProcess(0)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPink.copy(alpha = 0.1f)),
                    border = BorderStroke(1.dp, CyberPink.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_system_exit")
                ) {
                    Text(
                        text = "❌ TERMINATE SESSION & EXIT",
                        color = CyberPink,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Footer aesthetic notes
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "CRITICAL CHIP INTEGRITY CHANNELS SECURED //",
            color = CyberBorder,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            textAlign = TextAlign.Center
        )
    }
}

// Helper to extract Activity from Context in Compose
fun android.content.Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}

// 3D Wireframe Voxel Wall Segment Drawer (Advanced Voxel graphics)
private fun androidx.compose.ui.graphics.drawscope.DrawScope.draw3DVoxelWallSegment(
    w1: Offset, w2: Offset, w3: Offset, w4: Offset, // Wall corners (back face)
    primaryColor: Color,
    alpha: Float,
    isLeft: Boolean,
    w: Float, // Viewport width
    adjustedTl_r: FloatArray,
    adjustedBl_r: FloatArray,
    adjustedTr_r: FloatArray,
    adjustedBr_r: FloatArray,
    d: Int,
    h: Float // Viewport height
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

    // 1. Solid back-wall fill
    wallPath.reset()
    wallPath.moveTo(w1.x, w1.y)
    wallPath.lineTo(w2.x, w2.y)
    wallPath.lineTo(w3.x, w3.y)
    wallPath.lineTo(w4.x, w4.y)
    wallPath.close()
    drawPath(path = wallPath, color = primaryColor.copy(alpha = alpha * 0.3f))

    // Back face outline
    drawLine(color = primaryColor.copy(alpha = alpha * 0.7f), start = w1, end = w2, strokeWidth = 1.5f)
    drawLine(color = primaryColor.copy(alpha = alpha * 0.7f), start = w2, end = w3, strokeWidth = 1.5f)
    drawLine(color = primaryColor.copy(alpha = alpha * 0.7f), start = w3, end = w4, strokeWidth = 1.5f)
    drawLine(color = primaryColor.copy(alpha = alpha * 0.7f), start = w4, end = w1, strokeWidth = 1.5f)

    // 2. Solid front face overlay (hides behind-the-voxel lines)
    val frontPath = Path()
    frontPath.moveTo(p1.x, p1.y)
    frontPath.lineTo(p2.x, p2.y)
    frontPath.lineTo(p3.x, p3.y)
    frontPath.lineTo(p4.x, p4.y)
    frontPath.close()
    drawPath(path = frontPath, color = Color.Black)
    drawPath(path = frontPath, color = primaryColor.copy(alpha = alpha * 0.45f))

    // Front face outline (bright vector highlights)
    drawLine(color = primaryColor.copy(alpha = alpha * 2f), start = p1, end = p2, strokeWidth = 3f)
    drawLine(color = primaryColor.copy(alpha = alpha * 2f), start = p2, end = p3, strokeWidth = 3f)
    drawLine(color = primaryColor.copy(alpha = alpha * 2f), start = p3, end = p4, strokeWidth = 3f)
    drawLine(color = primaryColor.copy(alpha = alpha * 2f), start = p4, end = p1, strokeWidth = 3f)

    // 3. Connect corners (forming the 3D Voxel block)
    drawLine(color = primaryColor.copy(alpha = alpha * 1.3f), start = w1, end = p1, strokeWidth = 2f)
    drawLine(color = primaryColor.copy(alpha = alpha * 1.3f), start = w2, end = p2, strokeWidth = 2f)
    drawLine(color = primaryColor.copy(alpha = alpha * 1.3f), start = w3, end = p3, strokeWidth = 2f)
    drawLine(color = primaryColor.copy(alpha = alpha * 1.3f), start = w4, end = p4, strokeWidth = 2f)

    // 4. Voxel Grid detailing (split each block horizontally to form dual-deck voxels)
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

// Advanced recursive fractal geometry canvas
@Composable
fun FractalGeometryCanvas(
    modifier: Modifier = Modifier,
    primaryColor: Color = CyberCyan,
    animProgress: Float
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val startX = w / 2f
        val startY = h * 0.92f
        val initialLength = h * 0.28f
        val angle = -Math.PI.toFloat() / 2f

        fun drawBranch(x1: Float, y1: Float, length: Float, currentAngle: Float, depth: Int) {
            if (depth <= 0 || length < 2f) return

            val x2 = x1 + kotlin.math.cos(currentAngle) * length
            val y2 = y1 + kotlin.math.sin(currentAngle) * length

            val alpha = (depth.toFloat() / 6f).coerceIn(0.2f, 1.0f)
            val stroke = (depth.toFloat() * 0.5f).coerceIn(1f, 3f)
            drawLine(
                color = primaryColor.copy(alpha = alpha),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = stroke
            )

            val branchAngleShift = 0.35f + 0.15f * kotlin.math.sin(animProgress * 2f * Math.PI.toFloat()).toFloat()

            drawBranch(x2, y2, length * 0.72f, currentAngle - branchAngleShift, depth - 1)
            drawBranch(x2, y2, length * 0.72f, currentAngle + branchAngleShift, depth - 1)
        }

        drawRect(color = Color(0xFF030406))

        drawCircle(
            color = primaryColor.copy(alpha = 0.05f),
            radius = w * 0.4f,
            center = Offset(w / 2f, h / 2f),
            style = Stroke(width = 1f)
        )
        drawCircle(
            color = primaryColor.copy(alpha = 0.02f),
            radius = w * 0.2f,
            center = Offset(w / 2f, h / 2f),
            style = Stroke(width = 1f)
        )

        drawBranch(startX, startY, initialLength, angle, depth = 6)
    }
}


