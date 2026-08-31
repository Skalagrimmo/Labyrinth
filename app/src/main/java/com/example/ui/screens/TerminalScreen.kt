package com.example.ui.screens

import android.view.HapticFeedbackConstants
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.example.audio.CyberVibrationManager
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
import com.example.ui.components.FlickeringCrtScanlineTerminalOverlay
import com.example.ui.components.CyberVitalStatusHud
import com.example.ui.components.AnimatedCyberHudConsole
import com.example.gl.CyberCharacterGLView
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
import com.example.ui.ActiveScreen
import com.example.ui.GameViewModel
import com.example.ui.TutorialOverlay
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
    content: @Composable () -> Unit,
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
        content()
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
    val context = LocalContext.current
    val vibrationManager = remember(context) { CyberVibrationManager(context) }

    // Haptic vibration feedback for proximity to active ICE nodes & threat hosts
    LaunchedEffect(uiState.gridX, uiState.gridY, uiState.maze) {
        val px = uiState.gridX
        val py = uiState.gridY
        val maze = uiState.maze
        if (maze.isNotEmpty() && py in maze.indices && px in maze[0].indices) {
            var minDistance = 99.0
            val searchRadius = 3
            for (dy in -searchRadius..searchRadius) {
                for (dx in -searchRadius..searchRadius) {
                    val nx = px + dx
                    val ny = py + dy
                    if (ny in maze.indices && nx in maze[0].indices) {
                        val cell = maze[ny][nx]
                        if (cell == com.example.data.CellType.VIRUS_NODE ||
                            cell == com.example.data.CellType.ENCRYPTED_PORTAL ||
                            cell == com.example.data.CellType.HACKABLE_TERMINAL) {
                            val dist = Math.hypot(dx.toDouble(), dy.toDouble())
                            if (dist < minDistance) {
                                minDistance = dist
                            }
                        }
                    }
                }
            }
            if (minDistance <= 3.0) {
                vibrationManager.triggerIceProximityVibration(minDistance)
            }
        }
    }

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
    var selectedClass by remember { mutableStateOf(NetrunnerClass.NETRUNNER) }

    // Focus management for hardware keys
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(uiState.screen) {
        if (uiState.screen == ActiveScreen.EXPLORATION) {
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
                if (keyEvent.type == KeyEventType.KeyDown && uiState.screen == ActiveScreen.EXPLORATION) {
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
                        ActiveScreen.START_MENU -> {
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
                        ActiveScreen.CHARACTER_CREATION -> {
                            CharacterCreationView(
                                runnerName = runnerNameInput,
                                onNameChange = { runnerNameInput = it },
                                selectedClass = selectedClass,
                                onClassSelected = { selectedClass = it },
                                selectedImplant = uiState.selectedStartingImplant,
                                onImplantSelected = { viewModel.selectStartingImplant(it) },
                                onStartGameCustomized = { hpPts, ramPts, reflexPts, armorPts, fundPts, kit ->
                                    viewModel.createCharacter(
                                        name = runnerNameInput,
                                        selectedClass = selectedClass,
                                        startingImplant = uiState.selectedStartingImplant,
                                        allocatedHpPoints = hpPts,
                                        allocatedRamPoints = ramPts,
                                        allocatedReflexPoints = reflexPts,
                                        allocatedArmorPoints = armorPts,
                                        allocatedFundPoints = fundPts,
                                        starterKit = kit
                                    )
                                }
                            )
                        }
                        ActiveScreen.CYBERWARE_CLINIC -> {
                            CyberneticsClinicView(
                                uiState = uiState,
                                viewModel = viewModel,
                                onCloseClinic = { viewModel.closeCyberwareClinic() }
                            )
                        }
                        ActiveScreen.EXPLORATION,
                        ActiveScreen.COMBAT -> {
                            ExplorationView(
                                uiState = uiState,
                                viewModel = viewModel,
                                onShopClick = { viewModel.enterShop() },
                                onSafeDisconnect = { viewModel.disconnectRunSuccessfully() }
                            )
                        }
                        ActiveScreen.HACKING_MINIGAME -> {
                            HackingMinigableView(
                                uiState = uiState,
                                onCellSelected = { r, c -> viewModel.hackCell(r, c) },
                                onCancel = { viewModel.exitHackingMinigame() }
                            )
                        }
                        ActiveScreen.UPGRADE_STORE -> {
                            UpgradeStoreView(
                                uiState = uiState,
                                onBuyCyberware = { viewModel.purchaseCyberware(it) },
                                onBuyConsumable = { name, cost -> viewModel.purchaseConsumable(name, cost) },
                                onExit = { viewModel.exitShop() }
                            )
                        }
                        ActiveScreen.LEADERBOARD -> {
                            LeaderboardView(
                                scores = highScores,
                                onClearScores = { viewModel.clearHighScores() },
                                onExit = { viewModel.exitLeaderboard() }
                            )
                        }
                        ActiveScreen.GAME_OVER -> {
                            GameOverView(
                                uiState = uiState,
                                onRestart = {
                                    runnerNameInput = ""
                                    viewModel.restartGame()
                                }
                            )
                        }
                        ActiveScreen.SVDAG_WORLD_BUILDER -> {
                            val dag = uiState.svdagWorld
                            val stats = uiState.svdagStats
                            if (dag != null && stats != null) {
                                SvdagWorldInspectorScreen(
                                    currentDag = dag,
                                    currentStats = stats,
                                    worldState = uiState.svdagWorldState,
                                    scanSummary = uiState.svdagScanSummary,
                                    scanRippleState = uiState.svdagRippleState,
                                    iceEntities = uiState.svdagIceEntities,
                                    playerPos = uiState.svdagPlayerPos,
                                    playerHideStatus = uiState.svdagPlayerHideStatus,
                                    multiFloorLevel = uiState.currentMultiFloorLevel,
                                    activeFloorIndex = uiState.activeFloorIndex,
                                    onSelectFloor = { viewModel.setActiveFloorIndex(it) },
                                    onUseConnector = { viewModel.navigateVerticalConnector(it) },
                                    onRegenerateMultiFloorLevel = { viewModel.generateProceduralMultiFloorLevel(it) },
                                    onTriggerScan = { ox, oy, oz, radius -> viewModel.triggerSvdagScan(ox, oy, oz, radius) },
                                    onTickIceAI = { viewModel.tickSvdagIceAI() },
                                    onMovePlayer = { dx, dy, dz -> viewModel.moveSvdagPlayer(dx, dy, dz) },
                                    onRegenerateDag = { depth, seed -> viewModel.initOrRegenerateSvdag(depth, seed) },
                                    onModifyVoxel = { x, y, z, type -> viewModel.modifySvdagVoxel(x, y, z, type) },
                                    onBackToGame = { viewModel.exitSvdagWorldInspector() }
                                )
                            }
                        }
                        ActiveScreen.DATA_FRAGMENTS_VAULT -> {
                            DataVaultScreen(
                                uiState = uiState,
                                onUnlockTheme = { viewModel.unlockCosmeticTheme(it) },
                                onEquipTheme = { viewModel.setActiveTheme(it) },
                                onUnlockPrompt = { viewModel.unlockPromptStyle(it) },
                                onEquipPrompt = { viewModel.setActivePromptStyle(it) },
                                onUnlockBuff = { viewModel.unlockPerformanceBuff(it) },
                                onToggleBuff = { viewModel.togglePerformanceBuff(it) },
                                onExitVault = { viewModel.exitDataVaultScreen() }
                            )
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
                        onMenuClick = { viewModel.returnToStartMenu() },
                        onCyberwareClick = { viewModel.toggleCyberwareInventoryOverlay(true) }
                    )

                    if (uiState.screen != ActiveScreen.START_MENU &&
                        uiState.screen != ActiveScreen.CHARACTER_CREATION) {
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
                    onMenuClick = { viewModel.returnToStartMenu() },
                    onCyberwareClick = { viewModel.toggleCyberwareInventoryOverlay(true) }
                )

                if (uiState.screen != ActiveScreen.START_MENU &&
                    uiState.screen != ActiveScreen.CHARACTER_CREATION) {
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
                        ActiveScreen.START_MENU -> {
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
                        ActiveScreen.CHARACTER_CREATION -> {
                            CharacterCreationView(
                                runnerName = runnerNameInput,
                                onNameChange = { runnerNameInput = it },
                                selectedClass = selectedClass,
                                onClassSelected = { selectedClass = it },
                                selectedImplant = uiState.selectedStartingImplant,
                                onImplantSelected = { viewModel.selectStartingImplant(it) },
                                onStartGameCustomized = { hpPts, ramPts, reflexPts, armorPts, fundPts, kit ->
                                    viewModel.createCharacter(
                                        name = runnerNameInput,
                                        selectedClass = selectedClass,
                                        startingImplant = uiState.selectedStartingImplant,
                                        allocatedHpPoints = hpPts,
                                        allocatedRamPoints = ramPts,
                                        allocatedReflexPoints = reflexPts,
                                        allocatedArmorPoints = armorPts,
                                        allocatedFundPoints = fundPts,
                                        starterKit = kit
                                    )
                                }
                            )
                        }
                        ActiveScreen.CYBERWARE_CLINIC -> {
                            CyberneticsClinicView(
                                uiState = uiState,
                                viewModel = viewModel,
                                onCloseClinic = { viewModel.closeCyberwareClinic() }
                            )
                        }
                        ActiveScreen.EXPLORATION,
                        ActiveScreen.COMBAT -> {
                            ExplorationView(
                                uiState = uiState,
                                viewModel = viewModel,
                                onShopClick = { viewModel.enterShop() },
                                onSafeDisconnect = { viewModel.disconnectRunSuccessfully() }
                            )
                        }
                        ActiveScreen.HACKING_MINIGAME -> {
                            HackingMinigableView(
                                uiState = uiState,
                                onCellSelected = { r, c -> viewModel.hackCell(r, c) },
                                onCancel = { viewModel.exitHackingMinigame() }
                            )
                        }
                        ActiveScreen.UPGRADE_STORE -> {
                            UpgradeStoreView(
                                uiState = uiState,
                                onBuyCyberware = { viewModel.purchaseCyberware(it) },
                                onBuyConsumable = { name, cost -> viewModel.purchaseConsumable(name, cost) },
                                onExit = { viewModel.exitShop() }
                            )
                        }
                        ActiveScreen.LEADERBOARD -> {
                            LeaderboardView(
                                scores = highScores,
                                onClearScores = { viewModel.clearHighScores() },
                                onExit = { viewModel.exitLeaderboard() }
                            )
                        }
                        ActiveScreen.GAME_OVER -> {
                            GameOverView(
                                uiState = uiState,
                                onRestart = {
                                    runnerNameInput = ""
                                    viewModel.restartGame()
                                }
                            )
                        }
                        ActiveScreen.SVDAG_WORLD_BUILDER -> {
                            val dag = uiState.svdagWorld
                            val stats = uiState.svdagStats
                            if (dag != null && stats != null) {
                                SvdagWorldInspectorScreen(
                                    currentDag = dag,
                                    currentStats = stats,
                                    worldState = uiState.svdagWorldState,
                                    scanSummary = uiState.svdagScanSummary,
                                    scanRippleState = uiState.svdagRippleState,
                                    iceEntities = uiState.svdagIceEntities,
                                    playerPos = uiState.svdagPlayerPos,
                                    playerHideStatus = uiState.svdagPlayerHideStatus,
                                    multiFloorLevel = uiState.currentMultiFloorLevel,
                                    activeFloorIndex = uiState.activeFloorIndex,
                                    onSelectFloor = { viewModel.setActiveFloorIndex(it) },
                                    onUseConnector = { viewModel.navigateVerticalConnector(it) },
                                    onRegenerateMultiFloorLevel = { viewModel.generateProceduralMultiFloorLevel(it) },
                                    onTriggerScan = { ox, oy, oz, radius -> viewModel.triggerSvdagScan(ox, oy, oz, radius) },
                                    onTickIceAI = { viewModel.tickSvdagIceAI() },
                                    onMovePlayer = { dx, dy, dz -> viewModel.moveSvdagPlayer(dx, dy, dz) },
                                    onRegenerateDag = { depth, seed -> viewModel.initOrRegenerateSvdag(depth, seed) },
                                    onModifyVoxel = { x, y, z, type -> viewModel.modifySvdagVoxel(x, y, z, type) },
                                    onBackToGame = { viewModel.exitSvdagWorldInspector() }
                                )
                            }
                        }
                        ActiveScreen.DATA_FRAGMENTS_VAULT -> {
                            DataVaultScreen(
                                uiState = uiState,
                                onUnlockTheme = { viewModel.unlockCosmeticTheme(it) },
                                onEquipTheme = { viewModel.setActiveTheme(it) },
                                onUnlockPrompt = { viewModel.unlockPromptStyle(it) },
                                onEquipPrompt = { viewModel.setActivePromptStyle(it) },
                                onUnlockBuff = { viewModel.unlockPerformanceBuff(it) },
                                onToggleBuff = { viewModel.togglePerformanceBuff(it) },
                                onExitVault = { viewModel.exitDataVaultScreen() }
                            )
                        }
                    }
                }

                TutorialOverlay(
                    uiState = uiState,
                    onNext = { viewModel.tutorialAdvance() },
                    onSkip = { viewModel.tutorialSkip() },
                    onDismiss = { viewModel.tutorialSkip() }
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Animated Cyber HUD Console Ticker & Navigation Controls
                if (uiState.screen != ActiveScreen.START_MENU &&
                    uiState.screen != ActiveScreen.CHARACTER_CREATION) {
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

        // CyberToastHost overlay
        CyberToastHost(
            hostState = toastHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .testTag("toast_overlay")
        )

        // Cyberware inventory overlay
        if (uiState.showCyberwareInventoryOverlay) {
            CyberwareInventoryOverlay(
                uiState = uiState,
                viewModel = viewModel,
                onDismiss = { viewModel.toggleCyberwareInventoryOverlay(false) },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Fade overlay for transitions
        if (uiState.fadeAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = uiState.fadeAlpha))
                    .testTag("fade_overlay")
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
    onMenuClick: () -> Unit,
    onCyberwareClick: () -> Unit = {}
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
                        "рџЏў Floor ${uiState.buildingFloor}: $floorTheme"
                    }
                    com.example.data.Zone.COLLECTORS -> "рџЊЂ TUNNELS L${uiState.collectorsLevel}"
                    com.example.data.Zone.CITY -> {
                        val district = when (uiState.cityDistrictIndex) {
                            0 -> "Neon District"
                            1 -> "Tech Plaza"
                            else -> "Corp Core"
                        }
                        "рџЏ™пёЏ CITY [$district]"
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
                if (uiState.screen != ActiveScreen.START_MENU) {
                    IconButton(
                        onClick = onCyberwareClick,
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("cyberware_inventory_button")
                    ) {
                        Text(
                            text = "рџ”Њ",
                            fontSize = 12.sp
                        )
                    }

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
    onStartGameCustomized: (
        allocatedHpPoints: Int,
        allocatedRamPoints: Int,
        allocatedReflexPoints: Int,
        allocatedArmorPoints: Int,
        allocatedFundPoints: Int,
        starterKit: String
    ) -> Unit
) {
    // Customization state
    var allocatedHpPoints by remember { mutableIntStateOf(2) }
    var allocatedRamPoints by remember { mutableIntStateOf(2) }
    var allocatedReflexPoints by remember { mutableIntStateOf(2) }
    var allocatedArmorPoints by remember { mutableIntStateOf(2) }
    var allocatedFundPoints by remember { mutableIntStateOf(2) }
    var selectedStarterKit by remember { mutableStateOf("HACKER") }
    var showAllArchetypes by remember { mutableStateOf(false) }

    val totalPointsAllocated = allocatedHpPoints + allocatedRamPoints + allocatedReflexPoints + allocatedArmorPoints + allocatedFundPoints
    val pointsRemaining = (10 - totalPointsAllocated).coerceAtLeast(0)

    // Calculate dynamic live preview stats
    val calcMaxHp = selectedClass.baseIntegrity + selectedImplant.integrityBonus + (allocatedHpPoints * 10)
    val calcMaxRam = selectedClass.baseRam + selectedImplant.ramBonus + (allocatedRamPoints * 2)
    val calcRamRecovery = (if (selectedClass == NetrunnerClass.NETRUNNER) 3 else 2) + selectedImplant.recoveryBonus
    val calcDamageBonus = selectedImplant.damageBonus + allocatedReflexPoints
    val calcDefense = (if (selectedClass == NetrunnerClass.TECHIE) 5 else 0) + selectedImplant.defenseBonus + allocatedArmorPoints
    var calcCredits = when (selectedClass) {
        NetrunnerClass.TECHIE, NetrunnerClass.SCRIPT_KIDDIE -> 300
        NetrunnerClass.NETRUNNER -> 150
        NetrunnerClass.STREET_SAMURAI -> 100
        else -> 100
    }
    calcCredits += (allocatedFundPoints * 50) + (if (selectedStarterKit == "SCAVENGER") 150 else 0)

    // GL Preview Color
    val (hueR, hueG, hueB) = when (selectedClass) {
        NetrunnerClass.NETRUNNER -> Triple(0.0f, 1.0f, 0.85f)
        NetrunnerClass.STREET_SAMURAI -> Triple(1.0f, 0.15f, 0.4f)
        NetrunnerClass.TECHIE -> Triple(1.0f, 0.7f, 0.0f)
        NetrunnerClass.CODE_SLASHER -> Triple(0.2f, 1.0f, 0.3f)
        NetrunnerClass.CYBER_SHIELD -> Triple(0.1f, 0.6f, 1.0f)
        NetrunnerClass.BUFFER_OVERFLOW -> Triple(0.9f, 0.2f, 1.0f)
        NetrunnerClass.SCRIPT_KIDDIE -> Triple(1.0f, 0.9f, 0.2f)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Top ASCII Header & Status
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
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Text(
            text = "--- CHARACTER CUSTOMIZATION TERMINAL ---",
            color = CyberCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // 3D Hologram Preview Container
        Card(
            colors = CardDefaults.cardColors(containerColor = CyberCardBg),
            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "вљЎ 3D NEURAL HOLOGRAM PREVIEW",
                        color = CyberCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ARCHETYPE: ${selectedClass.title.uppercase()}",
                        color = CyberPink,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, CyberBorderLight, RoundedCornerShape(8.dp))
                        .background(Color.Black)
                ) {
                    CyberCharacterGLView(
                        modifier = Modifier.fillMaxSize(),
                        hueR = hueR,
                        hueG = hueG,
                        hueB = hueB
                    )

                    Text(
                        text = "LIVE RENDER // 60 FPS",
                        color = CyberBrightGreen.copy(alpha = 0.8f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // 1. Runner Handle Name Input
        OutlinedTextField(
            value = runnerName,
            onValueChange = onNameChange,
            label = { Text("Runner Handle / ID Tag", color = CyberCyan, fontFamily = FontFamily.Monospace) },
            textStyle = LocalTextStyle.current.copy(color = CyberCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = CyberCardBg,
                unfocusedContainerColor = CyberCardBg,
                focusedIndicatorColor = CyberCyan,
                unfocusedIndicatorColor = CyberBorder,
                focusedLabelColor = CyberCyan,
                unfocusedLabelColor = CyberMutedText
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("runner_name_input")
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Cyberpunk Archetype Selection Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SELECT CYBERPUNK ARCHETYPE:",
                color = CyberCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            TextButton(
                onClick = { showAllArchetypes = !showAllArchetypes },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = if (showAllArchetypes) "Show Primary [3]" else "All Classes [7]",
                    color = CyberPink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
        }

        val displayedClasses = if (showAllArchetypes) NetrunnerClass.VALUES.toList() else NetrunnerClass.PRIMARY_ARCHETYPES

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            displayedClasses.forEach { classType ->
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
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("class_card_${classType.name}")
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
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
                            Surface(
                                color = if (isSelected) CyberCyan.copy(alpha = 0.2f) else Color.Transparent,
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, if (isSelected) CyberCyan else CyberBorder)
                            ) {
                                Text(
                                    text = "${classType.baseIntegrity} HP | ${classType.baseRam} RAM",
                                    color = CyberCyan,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = classType.description,
                            color = CyberBrightGreen,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "вљЎ ${classType.passiveDesc}",
                                color = CyberPink,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Attribute Point Allocation
        Card(
            colors = CardDefaults.cardColors(containerColor = CyberCardBg),
            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ALLOCATE ATTRIBUTE POINTS",
                        color = CyberCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "POINTS REMAINING: $pointsRemaining",
                        color = if (pointsRemaining > 0) CyberAmber else CyberGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                AttributeAllocationRow(
                    label = "Integrity Core (HP)",
                    subText = "+10 Max HP per point",
                    points = allocatedHpPoints,
                    canDecrease = allocatedHpPoints > 0,
                    canIncrease = pointsRemaining > 0,
                    onDecrease = { allocatedHpPoints-- },
                    onIncrease = { allocatedHpPoints++ },
                    bonusText = "+${allocatedHpPoints * 10} HP"
                )

                AttributeAllocationRow(
                    label = "RAM Capacity",
                    subText = "+2 Max RAM per point",
                    points = allocatedRamPoints,
                    canDecrease = allocatedRamPoints > 0,
                    canIncrease = pointsRemaining > 0,
                    onDecrease = { allocatedRamPoints-- },
                    onIncrease = { allocatedRamPoints++ },
                    bonusText = "+${allocatedRamPoints * 2} RAM"
                )

                AttributeAllocationRow(
                    label = "Cyber-Reflexes",
                    subText = "+1 Weapon Dmg / Crit per point",
                    points = allocatedReflexPoints,
                    canDecrease = allocatedReflexPoints > 0,
                    canIncrease = pointsRemaining > 0,
                    onDecrease = { allocatedReflexPoints-- },
                    onIncrease = { allocatedReflexPoints++ },
                    bonusText = "+${allocatedReflexPoints} Dmg"
                )

                AttributeAllocationRow(
                    label = "Subdermal Armor",
                    subText = "+1 Armor & Shield per point",
                    points = allocatedArmorPoints,
                    canDecrease = allocatedArmorPoints > 0,
                    canIncrease = pointsRemaining > 0,
                    onDecrease = { allocatedArmorPoints-- },
                    onIncrease = { allocatedArmorPoints++ },
                    bonusText = "+${allocatedArmorPoints} Armor"
                )

                AttributeAllocationRow(
                    label = "Scavenger Capital",
                    subText = "+50 Starting Credits per point",
                    points = allocatedFundPoints,
                    canDecrease = allocatedFundPoints > 0,
                    canIncrease = pointsRemaining > 0,
                    onDecrease = { allocatedFundPoints-- },
                    onIncrease = { allocatedFundPoints++ },
                    bonusText = "+${allocatedFundPoints * 50} в‚Ў"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. Starter Cybernetic Implant Selection
        Text(
            text = "SELECT STARTER CYBERNETIC IMPLANT:",
            color = CyberCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start).padding(bottom = 6.dp)
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
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
                    shape = RoundedCornerShape(10.dp),
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
                                text = "вљЎ PASSIVE: ${implant.passiveAbility.title} - ${implant.passiveAbility.description}",
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

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Starter Utility Loadout Selection
        Text(
            text = "CHOOSE STARTER UTILITY KIT:",
            color = CyberCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start).padding(bottom = 6.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val kits = listOf(
                Triple("HACKER", "Hacker Deck", "RAM Boost + Decryptor + AntiShield"),
                Triple("COMBAT", "Combat Merc", "NanoMeds x2 + Armor Plating"),
                Triple("SCAVENGER", "Scavenger", "+150 Credits + EMP Grenade")
            )

            kits.forEach { (kitKey, kitName, kitDesc) ->
                val isSelected = selectedStarterKit == kitKey
                Card(
                    onClick = { selectedStarterKit = kitKey },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) CyberMutedGreen else CyberCardBg
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) CyberCyan else CyberBorder
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = kitName,
                            color = if (isSelected) CyberCyan else CyberBrightGreen,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = kitDesc,
                            color = CyberMutedText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            lineHeight = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 6. Live Stat Sheet Summary Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            border = BorderStroke(1.dp, CyberCyan),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "--- FINALIZED RUNNER SPECIFICATIONS ---",
                    color = CyberCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("вЂў Integrity (HP): $calcMaxHp", color = CyberGreen, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        Text("вЂў Max RAM: $calcMaxRam", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        Text("вЂў RAM Recover: ${calcRamRecovery}/turn", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("вЂў Weapon Dmg: +$calcDamageBonus", color = CyberPink, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        Text("вЂў Armor/Defense: +$calcDefense", color = CyberAmber, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        Text("вЂў Credits: $calcCredits в‚Ў", color = CyberGreen, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Compile & Inject Button
        Button(
            onClick = {
                onStartGameCustomized(
                    allocatedHpPoints,
                    allocatedRamPoints,
                    allocatedReflexPoints,
                    allocatedArmorPoints,
                    allocatedFundPoints,
                    selectedStarterKit
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("compile_profile_button")
        ) {
            Text(
                text = "COMPILE PROFILE & INJECT DATA",
                color = Color.Black,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun AttributeAllocationRow(
    label: String,
    subText: String,
    points: Int,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    bonusText: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = CyberBrightGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subText,
                color = CyberMutedText,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = bonusText,
                color = CyberPink,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(60.dp),
                textAlign = TextAlign.End
            )

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (canDecrease) CyberMutedGreen else Color.DarkGray)
                    .clickable(enabled = canDecrease, onClick = onDecrease),
                contentAlignment = Alignment.Center
            ) {
                Text("-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Text(
                text = "$points",
                color = CyberCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(18.dp),
                textAlign = TextAlign.Center
            )

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (canIncrease) CyberCyan else Color.DarkGray)
                    .clickable(enabled = canIncrease, onClick = onIncrease),
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
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
                    Text("рџ’– HP +$totalHp", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("вљЎ RAM +$totalRam", color = CyberPink, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("рџ”‹ REC +$totalRec/t", color = CyberAmber, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("рџ—ЎпёЏ DMG +$totalDmg", color = CyberBrightGreen, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("рџ›ЎпёЏ DEF +$totalDef%", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
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
        ImplantBodySlot.entries.forEach { slot ->
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
                                text = "вљЎ PASSIVE: ${installed.passiveAbility.title} - ${installed.passiveAbility.description}",
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
                                text = "вљЎ ${implant.passiveAbility.title}",
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
                        text = "вљ”пёЏ TACTICAL COMBAT MATRIX",
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
                    combatRound = uiState.combatRound,
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
                            Text("рџ›ЎпёЏ FIREWALL ACTIVE (-75%)", color = CyberBrightGreen, fontFamily = FontFamily.Monospace, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
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
                            Text("вњЁ BALCONY (+25% ATK)", color = CyberAmber, fontFamily = FontFamily.Monospace, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (standCell == com.example.data.CellType.GRAVITY_SLOPE) {
                        Box(
                            modifier = Modifier
                                .background(CyberCyan.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .border(1.dp, CyberCyan, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("вњЁ GRAVITY (+30% EVA)", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
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
                // Single Unified Stance Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "COMBAT STANCE: UNIFIED STRIKE PROTOCOL",
                        color = CyberAmber,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                }

                // Sub-Deck Toggle Bar (COMMANDS vs DAEMONS vs ITEMS)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "COMMANDS" to "вљ”пёЏ TACTICS",
                        "DAEMONS" to "рџ’» DAEMONS (${uiState.installedPrograms.size})",
                        "ITEMS" to "рџ’Љ ITEMS (${uiState.inventory.size})"
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
                                        text = "вљ”пёЏ STRIKE ATTACK",
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
                                        text = "вљЎ QUICK HACK (3MB)",
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
                                        text = "рџ›ЎпёЏ FORTIFY FIREWALL",
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
                                        text = "рџ”Ќ SCAN TELEMETRY",
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
                                        text = "вЏ­пёЏ END TURN",
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
                                        text = "рџЏѓ DISCONNECT",
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
                                text = "в–€",
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

