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
import com.example.gl.CharacterVariant
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
import com.example.ui.GameUiState
import com.example.core.model.NetrunnerClass
import com.example.core.model.Cyberware
import com.example.core.model.ActiveStatusEffect
import com.example.core.model.Program
import com.example.core.model.CellType
import com.example.core.model.Zone
import com.example.core.model.CyberWeather
import com.example.core.model.Enemy
import com.example.core.model.LogType

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
                        if (cell == com.example.core.model.CellType.VIRUS_NODE ||
                            cell == com.example.core.model.CellType.ENCRYPTED_PORTAL ||
                            cell == com.example.core.model.CellType.HACKABLE_TERMINAL) {
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
                if (uiState.activeWeather == com.example.core.model.CyberWeather.COLD_SPOT) {
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
                        ActiveScreen.SETTINGS -> {
                            SettingsView(
                                viewModel = viewModel,
                                onExit = { viewModel.exitSettings() }
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
                        ActiveScreen.SETTINGS -> {
                            SettingsView(
                                viewModel = viewModel,
                                onExit = { viewModel.exitSettings() }
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
    uiState: GameUiState,
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
                    com.example.core.model.Zone.BUILDING -> {
                        val floorTheme = when (uiState.buildingFloor) {
                            1 -> "Residential"
                            2 -> "Office"
                            3 -> "Technical"
                            4 -> "Storage"
                            else -> "Unknown"
                        }
                        "рџЏў Floor ${uiState.buildingFloor}: $floorTheme"
                    }
                    com.example.core.model.Zone.COLLECTORS -> "рџЊЂ TUNNELS L${uiState.collectorsLevel}"
                    com.example.core.model.Zone.CITY -> {
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

    val glVariant = when (selectedClass) {
        NetrunnerClass.NETRUNNER -> CharacterVariant.NETRUNNER
        NetrunnerClass.STREET_SAMURAI -> CharacterVariant.SAMURAI
        NetrunnerClass.TECHIE -> CharacterVariant.TECHIE
        else -> CharacterVariant.GENERIC
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
                        hueB = hueB,
                        variant = glVariant
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
                                text = "вљЎ PASSIVE: ${implant.passiveAbility!!.title} - ${implant.passiveAbility!!.description}",
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
    uiState: GameUiState,
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
                                text = "вљЎ PASSIVE: ${installed.passiveAbility!!.title} - ${installed.passiveAbility!!.description}",
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
                                text = "вљЎ ${implant.passiveAbility!!.title}",
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