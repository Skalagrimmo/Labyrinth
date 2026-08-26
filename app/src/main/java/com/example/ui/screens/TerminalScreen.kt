package com.example.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.ActiveScreen
import com.example.ui.GameViewModel
import com.example.ui.components.CyberToastHost
import com.example.ui.components.rememberCyberToastHostState
import com.example.ui.theme.*
import kotlinx.coroutines.delay

// ----------------------------------------------------
// Modifier Extension: Repeating Clickable (long-press repeat)
// ----------------------------------------------------
fun Modifier.repeatingClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = this.pointerInput(enabled) {
    if (!enabled) return@pointerInput
    detectTapGestures(
        onPress = {
            onClick()
            while (true) {
                delay(150)
                if (!tryAwaitRelease()) break
                onClick()
            }
        },
        onTap = { onClick() }
    )
}

// ----------------------------------------------------
// Composable: RepeatingNavigationButton
// ----------------------------------------------------
@Composable
fun RepeatingNavigationButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    Box(
        modifier = modifier
            .repeatingClickable(enabled = enabled) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

// ----------------------------------------------------
// Root Composable: TerminalScreen
// ----------------------------------------------------
@Composable
fun TerminalScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val savedProgress by viewModel.savedGameProgress.collectAsState()
    val runRecords by viewModel.runRecords.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val view = LocalView.current
    val toastHostState = rememberCyberToastHostState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CyberDark)
            .drawWithContent {
                // Cyberpunk grid background
                drawRect(Color(0xFF020604))
                val gridSpacing = 40.dp.toPx()
                val gridColor = Color(0xFF00FF66).copy(alpha = 0.04f)
                var x = 0f
                while (x < size.width) {
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.5.dp.toPx())
                    x += gridSpacing
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5.dp.toPx())
                    y += gridSpacing
                }
                drawContent()
            }
            .testTag("terminal_screen_root")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Screen content based on active screen
            when (uiState.screen) {
                ActiveScreen.START_MENU -> {
                    StartMenuView(
                        viewModel = viewModel,
                        hasSavedGame = savedProgress != null,
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
                    CharacterCreationView(viewModel = viewModel)
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
                        onBuyCyberware = { cyberware -> viewModel.purchaseCyberware(cyberware) },
                        onBuyConsumable = { name, cost -> viewModel.purchaseConsumable(name, cost) },
                        onExit = { viewModel.exitShop() }
                    )
                }

                ActiveScreen.LEADERBOARD -> {
                    LeaderboardView(
                        scores = runRecords,
                        onClearScores = { viewModel.clearHighScores() },
                        onExit = { viewModel.exitLeaderboard() }
                    )
                }

                ActiveScreen.GAME_OVER -> {
                    GameOverView(
                        uiState = uiState,
                        onRestart = { viewModel.restartGame() }
                    )
                }

                ActiveScreen.SVDAG_WORLD_BUILDER -> {
                    val dag = uiState.svdagWorld
                    val stats = uiState.svdagStats
                    if (dag != null && stats != null) {
                        SvdagWorldInspectorScreen(
                            currentDag = dag,
                            currentStats = stats,
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
