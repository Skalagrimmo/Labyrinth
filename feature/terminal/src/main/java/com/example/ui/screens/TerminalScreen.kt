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
import androidx.compose.ui.platform.LocalConfiguration
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
import com.example.ui.components.CyberLogToastEffect
import com.example.ui.components.IceProximityHapticEffect
import com.example.ui.components.terminalHardwareKeyHandler
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
    // Cross-cutting router effects (haptics, log->toast, hardware keys) live in :feature:widgets (slice C)
    IceProximityHapticEffect(uiState = uiState)

    val toastHostState = rememberCyberToastHostState()

    // Observe latest log message to trigger instant cyber toasts for pickups, access denial, health/RAM regen
    val latestLog = uiState.logFeed.firstOrNull()
    CyberLogToastEffect(latestLog = latestLog, hostState = toastHostState)

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
            .terminalHardwareKeyHandler(
                screen = uiState.screen,
                inCombat = uiState.gameState != GameState.EXPLORATION,
                onMoveForward = { viewModel.moveForward() },
                onMoveBackward = { viewModel.moveBackward() },
                onTurnLeft = { viewModel.turnLeft() },
                onTurnRight = { viewModel.turnRight() },
                onInteract = { viewModel.interact() }
            )
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
                                viewModel = viewModel
                            )
                        }
                        ActiveScreen.CYBERWARE_CLINIC -> {
                            CyberneticsClinicView(
                                uiState = uiState,
                                viewModel = viewModel
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
                                viewModel = viewModel
                            )
                        }
                        ActiveScreen.CYBERWARE_CLINIC -> {
                            CyberneticsClinicView(
                                uiState = uiState,
                                viewModel = viewModel
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