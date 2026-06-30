package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.ui.input.key.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val highScores by viewModel.runRecords.collectAsStateWithLifecycle()
    val view = LocalView.current

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

    // Base background with modern high-density cyan grid layout and subtle scanlines (highly optimized)
    val gridSpacing = 32.dp
    val scanlineHeight = 12.dp
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CyberDark)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && uiState.screen == GameViewModel.ActiveScreen.EXPLORATION) {
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
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Retro Cyber Terminal Header
            TerminalHeader(uiState, onLeaderboardClick = { viewModel.viewLeaderboard() })

            Spacer(modifier = Modifier.height(4.dp))

            // Body depending on active screen
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (uiState.screen) {
                    GameViewModel.ActiveScreen.CHARACTER_CREATION -> {
                        CharacterCreationView(
                            runnerName = runnerNameInput,
                            onNameChange = { runnerNameInput = it },
                            selectedClass = selectedClass,
                            onClassSelected = { selectedClass = it },
                            onStartGame = { viewModel.createCharacter(runnerNameInput, selectedClass) }
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
                            onFlee = { viewModel.fleeCombat() }
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
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Always Visible Terminal Log Output (Footer console log)
            if (uiState.screen != GameViewModel.ActiveScreen.CHARACTER_CREATION) {
                TerminalLogConsole(uiState.logFeed)
                Spacer(modifier = Modifier.height(4.dp))
                HighDensityBottomNavigation(
                    currentScreen = uiState.screen,
                    viewModel = viewModel
                )
            }
        }
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
fun TerminalHeader(uiState: GameViewModel.GameUiState, onLeaderboardClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        border = BorderStroke(1.dp, CyberBorder),
        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Level & stats summary in a single clean horizontal line
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "LYR ${uiState.level}",
                    color = CyberAmber,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
                Text(
                    text = "|",
                    color = CyberBorder,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
                Text(
                    text = "CORE: ${uiState.integrity}%",
                    color = CyberCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "|",
                    color = CyberBorder,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
                Text(
                    text = "RAM: ${uiState.ram}MB",
                    color = CyberPink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Right: ONLINE status indicator & logs action button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onLeaderboardClick,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("leaderboard_tab_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
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
                    .weight(1.1f)
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
                                isCombat = false
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Tactical Navigation buttons under 3D wireframe
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                    border = BorderStroke(1.dp, CyberBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(4.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // UP arrow
                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                viewModel.moveForward()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberMutedGreen),
                            border = BorderStroke(1.dp, CyberBorderLight),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_move_forward")
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, "Forward", tint = CyberCyan, modifier = Modifier.size(24.dp))
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    viewModel.turnLeft()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberMutedGreen),
                                border = BorderStroke(1.dp, CyberBorderLight),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("btn_turn_left")
                            ) {
                                Icon(Icons.Default.KeyboardArrowLeft, "Turn Left", tint = CyberCyan, modifier = Modifier.size(24.dp))
                            }

                            Button(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    viewModel.moveBackward()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberMutedGreen),
                                border = BorderStroke(1.dp, CyberBorderLight),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("btn_move_back")
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, "Backward", tint = CyberCyan, modifier = Modifier.size(24.dp))
                            }

                            Button(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    viewModel.turnRight()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberMutedGreen),
                                border = BorderStroke(1.dp, CyberBorderLight),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("btn_turn_right")
                            ) {
                                Icon(Icons.Default.KeyboardArrowRight, "Turn Right", tint = CyberCyan, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }

            // Right HUD Panel (2D Top down map and stats)
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight()
            ) {
                // Top-Down Mini-map
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                    border = BorderStroke(1.dp, CyberBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "SECTOR LOGIC RADAR",
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )

                        // Render top down map
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            RenderMiniMap(uiState)
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
                        .weight(1.1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "STATUS // ${uiState.runnerName.uppercase()}",
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
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

                        Divider(color = CyberBorder, thickness = 1.dp)

                        // Click to interact/hack
                        Button(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                viewModel.interact()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberPink),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .testTag("btn_interact_hack")
                        ) {
                            Text(
                                text = "EXECUTE NODE INTERACTION",
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
    isCombat: Boolean = false
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
        val types = Array(4) { CellType.WALL }
        for (d in 0..3) {
            val cx = px + d * dir.dx
            val cy = py + d * dir.dy
            if (cx in 0 until width && cy in 0 until height) {
                types[d] = maze[cy][cx]
            } else {
                types[d] = CellType.WALL
            }
        }
        types
    }

    val cellCoords = remember(px, py, dir) {
        val coords = Array(4) { Pair(-1, -1) }
        for (d in 0..3) {
            coords[d] = Pair(px + d * dir.dx, py + d * dir.dy)
        }
        coords
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

    val primaryColor = if (isCombat) Color(0xFFFB7185) else Color(0xFF00E5FF)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
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
        val shakeOffset = if (isCombat || uiState.integrity < 30) {
            val scaleVal = if (uiState.integrity < 30) 4f else 3f
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
                val path = Path().apply {
                    val p1 = getPixel(tl_c[d], adjustedTl_r[d])
                    val p2 = getPixel(tl_c[d+1], adjustedTl_r[d+1])
                    val p3 = getPixel(bl_c[d+1], adjustedBl_r[d+1])
                    val p4 = getPixel(bl_c[d], adjustedBl_r[d])
                    moveTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    lineTo(p3.x, p3.y)
                    lineTo(p4.x, p4.y)
                    close()
                }
                drawPath(path = path, color = primaryColor.copy(alpha = alpha))

                // Frame outlines
                drawLine(color = primaryColor.copy(alpha = alpha * 2f), start = getPixel(tl_c[d], adjustedTl_r[d]), end = getPixel(tl_c[d+1], adjustedTl_r[d+1]), strokeWidth = 3f)
                drawLine(color = primaryColor.copy(alpha = alpha * 2f), start = getPixel(bl_c[d], adjustedBl_r[d]), end = getPixel(bl_c[d+1], adjustedBl_r[d+1]), strokeWidth = 3f)
                drawLine(color = primaryColor.copy(alpha = alpha * 2f), start = getPixel(tl_c[d+1], adjustedTl_r[d+1]), end = getPixel(bl_c[d+1], adjustedBl_r[d+1]), strokeWidth = 3f)

                // High-fidelity vertical and horizontal wiring textures on side walls (2.5D details)
                val midLeft1 = Offset(
                    (getPixel(tl_c[d], adjustedTl_r[d]).x + getPixel(bl_c[d], adjustedBl_r[d]).x) / 2f,
                    (getPixel(tl_c[d], adjustedTl_r[d]).y + getPixel(bl_c[d], adjustedBl_r[d]).y) / 2f
                )
                val midLeft2 = Offset(
                    (getPixel(tl_c[d+1], adjustedTl_r[d+1]).x + getPixel(bl_c[d+1], adjustedBl_r[d+1]).x) / 2f,
                    (getPixel(tl_c[d+1], adjustedTl_r[d+1]).y + getPixel(bl_c[d+1], adjustedBl_r[d+1]).y) / 2f
                )
                drawLine(color = primaryColor.copy(alpha = alpha * 1.6f), start = midLeft1, end = midLeft2, strokeWidth = 1.5f)
            } else {
                // Open corridor left side boundary
                drawLine(color = primaryColor.copy(alpha = 0.2f), start = getPixel(tl_c[d], adjustedTl_r[d+1]), end = getPixel(tl_c[d+1], adjustedTl_r[d+1]), strokeWidth = 2f)
                drawLine(color = primaryColor.copy(alpha = 0.2f), start = getPixel(bl_c[d], adjustedBl_r[d+1]), end = getPixel(bl_c[d+1], adjustedBl_r[d+1]), strokeWidth = 2f)
                drawLine(color = primaryColor.copy(alpha = 0.3f), start = getPixel(tl_c[d+1], adjustedTl_r[d+1]), end = getPixel(bl_c[d+1], adjustedBl_r[d+1]), strokeWidth = 3f)
            }

            // --- Right wall side segment ---
            if (rightWallAt[d]) {
                val path = Path().apply {
                    val p1 = getPixel(tr_c[d], adjustedTr_r[d])
                    val p2 = getPixel(tr_c[d+1], adjustedTr_r[d+1])
                    val p3 = getPixel(br_c[d+1], adjustedBr_r[d+1])
                    val p4 = getPixel(br_c[d], adjustedBr_r[d])
                    moveTo(p1.x, p1.y)
                    lineTo(p2.x, p2.y)
                    lineTo(p3.x, p3.y)
                    lineTo(p4.x, p4.y)
                    close()
                }
                drawPath(path = path, color = primaryColor.copy(alpha = alpha))

                // Frame outlines
                drawLine(color = primaryColor.copy(alpha = alpha * 2f), start = getPixel(tr_c[d], adjustedTr_r[d]), end = getPixel(tr_c[d+1], adjustedTr_r[d+1]), strokeWidth = 3f)
                drawLine(color = primaryColor.copy(alpha = alpha * 2f), start = getPixel(br_c[d], adjustedBr_r[d]), end = getPixel(br_c[d+1], adjustedBr_r[d+1]), strokeWidth = 3f)
                drawLine(color = primaryColor.copy(alpha = alpha * 2f), start = getPixel(tr_c[d+1], adjustedTr_r[d+1]), end = getPixel(br_c[d+1], adjustedBr_r[d+1]), strokeWidth = 3f)

                // High-fidelity midline depth stripe on right side
                val midRight1 = Offset(
                    (getPixel(tr_c[d], adjustedTr_r[d]).x + getPixel(br_c[d], adjustedBr_r[d]).x) / 2f,
                    (getPixel(tr_c[d], adjustedTr_r[d]).y + getPixel(br_c[d], adjustedBr_r[d]).y) / 2f
                )
                val midRight2 = Offset(
                    (getPixel(tr_c[d+1], adjustedTr_r[d+1]).x + getPixel(br_c[d+1], adjustedBr_r[d+1]).x) / 2f,
                    (getPixel(tr_c[d+1], adjustedTr_r[d+1]).y + getPixel(br_c[d+1], adjustedBr_r[d+1]).y) / 2f
                )
                drawLine(color = primaryColor.copy(alpha = alpha * 1.6f), start = midRight1, end = midRight2, strokeWidth = 1.5f)
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
            val path = Path().apply {
                moveTo(center.x, center.y - sizeRadius)
                lineTo(center.x + sizeRadius, center.y)
                lineTo(center.x, center.y + sizeRadius)
                lineTo(center.x - sizeRadius, center.y)
                close()
            }
            drawPath(path = path, color = Color(0xFFF43F5E), style = Stroke(width = 5f))

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
                color = Color(0xFFC084FC).copy(alpha = 0.25f),
                radius = sizeRadius * 1.6f,
                center = center
            )

            // Nested quantum circles
            drawCircle(color = Color(0xFFC084FC), radius = sizeRadius, center = center, style = Stroke(width = 6f))
            drawCircle(color = Color(0xFFC084FC).copy(alpha = 0.6f), radius = sizeRadius * 0.65f, center = center, style = Stroke(width = 4f))
            drawCircle(color = Color(0xFFC084FC), radius = sizeRadius * 0.3f, center = center)
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

        // Restore shake transform translation
        drawContext.canvas.restore()

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

            // Calculate grid bounds to center it perfectly
            val cellSize = minOf(w / colCount, h / rowCount)
            val gridW = cellSize * colCount
            val gridH = cellSize * rowCount
            val startX = (w - gridW) / 2f
            val startY = (h - gridH) / 2f

            // Draw grid background subtle lines
            for (col in 0..colCount) {
                val x = startX + col * cellSize
                drawLine(
                    color = Color(0xFF1E293B).copy(alpha = 0.5f),
                    start = Offset(x, startY),
                    end = Offset(x, startY + gridH),
                    strokeWidth = 1f
                )
            }
            for (row in 0..rowCount) {
                val y = startY + row * cellSize
                drawLine(
                    color = Color(0xFF1E293B).copy(alpha = 0.5f),
                    start = Offset(startX, y),
                    end = Offset(startX + gridW, y),
                    strokeWidth = 1f
                )
            }

            // Draw cells
            for (y in 0 until rowCount) {
                for (x in 0 until colCount) {
                    val cell = maze[y][x]
                    val cellLeft = startX + x * cellSize
                    val cellTop = startY + y * cellSize
                    val isPlayer = (x == px && y == py)

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
                            val playerPath = Path().apply {
                                moveTo(playerCenter.x, playerCenter.y - playerSize * 0.5f)
                                lineTo(playerCenter.x + playerSize * 0.35f, playerCenter.y + playerSize * 0.45f)
                                lineTo(playerCenter.x - playerSize * 0.35f, playerCenter.y + playerSize * 0.45f)
                                close()
                            }
                            drawPath(
                                path = playerPath,
                                color = Color(0xFF00E5FF)
                            )
                        }
                    } else {
                        when (cell) {
                            CellType.WALL -> {
                                drawRoundRect(
                                    color = Color(0xFF334155),
                                    topLeft = Offset(cellLeft + cellSize * 0.1f, cellTop + cellSize * 0.1f),
                                    size = Size(cellSize * 0.8f, cellSize * 0.8f),
                                    cornerRadius = CornerRadius(4f, 4f)
                                )
                                drawRoundRect(
                                    color = Color(0xFF475569),
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
                                    color = Color(0xFFFBBF24).copy(alpha = 0.25f),
                                    radius = cellSize * 0.45f,
                                    center = center
                                )
                                drawCircle(
                                    color = Color(0xFFFBBF24),
                                    radius = cellSize * 0.22f,
                                    center = center
                                )
                                drawCircle(
                                    color = Color(0xFFFBBF24),
                                    radius = cellSize * 0.38f,
                                    center = center,
                                    style = Stroke(width = 2f)
                                )
                            }
                            CellType.ENCRYPTED_PORTAL -> {
                                // Purple vortex portal
                                val center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                drawCircle(
                                    color = Color(0xFFC084FC).copy(alpha = 0.25f),
                                    radius = cellSize * 0.45f,
                                    center = center
                                )
                                drawCircle(
                                    color = Color(0xFFC084FC),
                                    radius = cellSize * 0.35f,
                                    center = center,
                                    style = Stroke(width = 3f)
                                )
                                drawCircle(
                                    color = Color(0xFFC084FC),
                                    radius = cellSize * 0.15f,
                                    center = center
                                )
                            }
                            CellType.VIRUS_NODE -> {
                                // Hostile Crimson Rose hazard diamond
                                val center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                val rad = cellSize * 0.35f
                                
                                drawCircle(
                                    color = Color(0xFFF43F5E).copy(alpha = 0.25f),
                                    radius = cellSize * 0.45f,
                                    center = center
                                )

                                val path = Path().apply {
                                    moveTo(center.x, center.y - rad)
                                    lineTo(center.x + rad, center.y)
                                    lineTo(center.x, center.y + rad)
                                    lineTo(center.x - rad, center.y)
                                    close()
                                }
                                drawPath(path = path, color = Color(0xFFF43F5E))
                            }
                            CellType.SAFE_ZONE -> {
                                // Secure Emerald Green ring
                                val center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                drawCircle(
                                    color = Color(0xFF10B981).copy(alpha = 0.25f),
                                    radius = cellSize * 0.45f,
                                    center = center
                                )
                                drawCircle(
                                    color = Color(0xFF10B981),
                                    radius = cellSize * 0.3f,
                                    center = center,
                                    style = Stroke(width = 3f)
                                )
                                // Cross shape inside
                                drawLine(
                                    color = Color(0xFF10B981),
                                    start = Offset(center.x - cellSize * 0.15f, center.y),
                                    end = Offset(center.x + cellSize * 0.15f, center.y),
                                    strokeWidth = 3f
                                )
                                drawLine(
                                    color = Color(0xFF10B981),
                                    start = Offset(center.x, center.y - cellSize * 0.15f),
                                    end = Offset(center.x, center.y + cellSize * 0.15f),
                                    strokeWidth = 3f
                                )
                            }
                            CellType.SECRET_CACHE -> {
                                // Classified Crypt-Cache: Pulsing neon sky-blue square with centered core
                                val center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                val rad = cellSize * 0.35f
                                drawCircle(
                                    color = Color(0xFF38BDF8).copy(alpha = 0.25f),
                                    radius = cellSize * 0.45f,
                                    center = center
                                )
                                drawRect(
                                    color = Color(0xFF38BDF8),
                                    topLeft = Offset(center.x - rad * 0.7f, center.y - rad * 0.7f),
                                    size = Size(rad * 1.4f, rad * 1.4f),
                                    style = Stroke(width = 3f)
                                )
                                drawCircle(
                                    color = Color(0xFF38BDF8),
                                    radius = cellSize * 0.12f,
                                    center = center
                                )
                            }
                            CellType.GRAND_HALL -> {
                                // Indigo Grand Hall
                                val center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                drawRect(
                                    color = Color(0xFF6366F1).copy(alpha = 0.15f),
                                    topLeft = Offset(cellLeft + cellSize * 0.15f, cellTop + cellSize * 0.15f),
                                    size = Size(cellSize * 0.7f, cellSize * 0.7f)
                                )
                                drawRect(
                                    color = Color(0xFF6366F1),
                                    topLeft = Offset(cellLeft + cellSize * 0.15f, cellTop + cellSize * 0.15f),
                                    size = Size(cellSize * 0.7f, cellSize * 0.7f),
                                    style = Stroke(width = 2f)
                                )
                                // Draw 4 structural pillar dots
                                drawCircle(Color(0xFF818CF8), 1.5f, Offset(cellLeft + cellSize * 0.3f, cellTop + cellSize * 0.3f))
                                drawCircle(Color(0xFF818CF8), 1.5f, Offset(cellLeft + cellSize * 0.7f, cellTop + cellSize * 0.3f))
                                drawCircle(Color(0xFF818CF8), 1.5f, Offset(cellLeft + cellSize * 0.3f, cellTop + cellSize * 0.7f))
                                drawCircle(Color(0xFF818CF8), 1.5f, Offset(cellLeft + cellSize * 0.7f, cellTop + cellSize * 0.7f))
                            }
                            CellType.DOME_CHAMBER -> {
                                // Teal Dome central chamber
                                val center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                drawCircle(
                                    color = Color(0xFF14B8A6).copy(alpha = 0.2f),
                                    radius = cellSize * 0.4f,
                                    center = center
                                )
                                drawCircle(
                                    color = Color(0xFF14B8A6),
                                    radius = cellSize * 0.4f,
                                    center = center,
                                    style = Stroke(width = 2f)
                                )
                                drawCircle(
                                    color = Color(0xFF2DD4BF),
                                    radius = cellSize * 0.2f,
                                    center = center,
                                    style = Stroke(width = 1f)
                                )
                            }
                            CellType.VENT_TUNNEL -> {
                                // Amber service vent
                                drawRect(
                                    color = Color(0xFFF59E0B).copy(alpha = 0.25f),
                                    topLeft = Offset(cellLeft + cellSize * 0.3f, cellTop + cellSize * 0.3f),
                                    size = Size(cellSize * 0.4f, cellSize * 0.4f)
                                )
                                drawRect(
                                    color = Color(0xFFF59E0B),
                                    topLeft = Offset(cellLeft + cellSize * 0.3f, cellTop + cellSize * 0.3f),
                                    size = Size(cellSize * 0.4f, cellSize * 0.4f),
                                    style = Stroke(width = 2f)
                                )
                            }
                            CellType.ELEVATED_BALCONY -> {
                                // Pink raised balcony
                                val center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                drawRect(
                                    color = Color(0xFFEC4899).copy(alpha = 0.15f),
                                    topLeft = Offset(cellLeft + cellSize * 0.1f, cellTop + cellSize * 0.1f),
                                    size = Size(cellSize * 0.8f, cellSize * 0.8f)
                                )
                                drawLine(
                                    color = Color(0xFFEC4899),
                                    start = Offset(cellLeft + cellSize * 0.1f, cellTop + cellSize * 0.8f),
                                    end = Offset(cellLeft + cellSize * 0.9f, cellTop + cellSize * 0.8f),
                                    strokeWidth = 3f
                                )
                            }
                            CellType.STAIRS_UP -> {
                                // Cyber green upward stairs
                                val center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                drawLine(
                                    color = Color(0xFF10B981),
                                    start = Offset(cellLeft + cellSize * 0.2f, cellTop + cellSize * 0.8f),
                                    end = Offset(cellLeft + cellSize * 0.8f, cellTop + cellSize * 0.2f),
                                    strokeWidth = 2f
                                )
                                // Upwards arrow head
                                drawLine(
                                    color = Color(0xFF10B981),
                                    start = Offset(cellLeft + cellSize * 0.5f, cellTop + cellSize * 0.2f),
                                    end = Offset(cellLeft + cellSize * 0.8f, cellTop + cellSize * 0.2f),
                                    strokeWidth = 2f
                                )
                                drawLine(
                                    color = Color(0xFF10B981),
                                    start = Offset(cellLeft + cellSize * 0.8f, cellTop + cellSize * 0.2f),
                                    end = Offset(cellLeft + cellSize * 0.8f, cellTop + cellSize * 0.5f),
                                    strokeWidth = 2f
                                )
                            }
                            CellType.STAIRS_DOWN -> {
                                // Cyber green downward stairs
                                val center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                drawLine(
                                    color = Color(0xFF10B981),
                                    start = Offset(cellLeft + cellSize * 0.2f, cellTop + cellSize * 0.2f),
                                    end = Offset(cellLeft + cellSize * 0.8f, cellTop + cellSize * 0.8f),
                                    strokeWidth = 2f
                                )
                                // Downwards arrow head
                                drawLine(
                                    color = Color(0xFF10B981),
                                    start = Offset(cellLeft + cellSize * 0.5f, cellTop + cellSize * 0.8f),
                                    end = Offset(cellLeft + cellSize * 0.8f, cellTop + cellSize * 0.8f),
                                    strokeWidth = 2f
                                )
                                drawLine(
                                    color = Color(0xFF10B981),
                                    start = Offset(cellLeft + cellSize * 0.8f, cellTop + cellSize * 0.5f),
                                    end = Offset(cellLeft + cellSize * 0.8f, cellTop + cellSize * 0.8f),
                                    strokeWidth = 2f
                                )
                            }
                            CellType.GRAVITY_SLOPE -> {
                                // Slanted gold ramp lines
                                val center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                drawLine(
                                    color = Color(0xFFEAB308),
                                    start = Offset(cellLeft + cellSize * 0.1f, cellTop + cellSize * 0.9f),
                                    end = Offset(cellLeft + cellSize * 0.9f, cellTop + cellSize * 0.1f),
                                    strokeWidth = 2.5f
                                )
                                drawLine(
                                    color = Color(0xFFEAB308).copy(alpha = 0.5f),
                                    start = Offset(cellLeft + cellSize * 0.3f, cellTop + cellSize * 0.9f),
                                    end = Offset(cellLeft + cellSize * 0.9f, cellTop + cellSize * 0.3f),
                                    strokeWidth = 1.5f
                                )
                            }
                            else -> {
                                // Subtle empty space guide dot
                                drawCircle(
                                    color = Color(0xFF1E293B),
                                    radius = 2f,
                                    center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// Sub-Composable: Combat Mode Screen
// ==========================================
@Composable
fun CombatView(
    uiState: GameViewModel.GameUiState,
    onExecuteProgram: (Program) -> Unit,
    onFlee: () -> Unit
) {
    val enemy = uiState.activeEnemy ?: return

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "--- HARDWARE CONFLICT IMMINENT ---",
            color = CyberPink,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
        )

        // Split: Enemy visual representation + Player Tactical Programs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.4f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Left Panel: Enemy details & ASCII Art
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberDark),
                border = BorderStroke(1.dp, CyberBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = enemy.name.uppercase(),
                        color = CyberPink,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )

                    // First-Person 3D Tactical Viewport (Integrating 1st person perspective directly into combat)
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxWidth()
                            .background(Color.Black)
                            .border(1.dp, Color(0xFFFB7185).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        FirstPersonPerspectiveCanvas(
                            uiState = uiState,
                            modifier = Modifier.fillMaxSize().testTag("first_person_viewport"),
                            isCombat = true
                        )
                        
                        Text(
                            text = "TARGET LCK",
                            color = CyberPink.copy(alpha = 0.6f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(2.dp)
                        )
                    }

                    // Enemy stats
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "HOSTILE CORE: ${enemy.integrity}/${enemy.maxIntegrity}",
                            color = CyberPink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                        ProgressBarRetro(
                            current = enemy.integrity,
                            max = enemy.maxIntegrity,
                            color = CyberPink
                        )

                        Text(
                            text = "HOSTILE SHIELD: ${enemy.shield}/${enemy.maxShield}",
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                        ProgressBarRetro(
                            current = enemy.shield,
                            max = enemy.maxShield,
                            color = CyberCyan
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "ATTACK: ${enemy.damage}",
                                color = CyberAmber,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp
                            )
                            Text(
                                text = "SHIELDING: ${enemy.armor}",
                                color = CyberBrightGreen,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp
                            )
                        }
                    }
                }
            }

            // Right Panel: Program compilations available
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                border = BorderStroke(1.dp, CyberBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "TACTICAL CODES:",
                        color = CyberCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )

                    // Display runner stats with retro style progress bars
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "SYSTEM CORE: ${uiState.integrity}/${uiState.maxIntegrity}",
                                color = CyberCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp
                            )
                        }
                        ProgressBarRetro(
                            current = uiState.integrity,
                            max = uiState.maxIntegrity,
                            color = CyberCyan
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "FIREWALL: ${uiState.playerShield}/${uiState.playerMaxShield}",
                                color = CyberBrightGreen,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp
                            )
                        }
                        ProgressBarRetro(
                            current = uiState.playerShield,
                            max = uiState.playerMaxShield,
                            color = CyberBrightGreen
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "ALLOCATED RAM: ${uiState.ram}/${uiState.maxRam}MB",
                                color = CyberPink,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp
                            )
                        }
                        ProgressBarRetro(
                            current = uiState.ram,
                            max = uiState.maxRam,
                            color = CyberPink
                        )
                    }

                    Divider(color = CyberBorder, thickness = 1.dp)

                    // Render installed programs
                    uiState.installedPrograms.forEach { prog ->
                        Card(
                            onClick = { onExecuteProgram(prog) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (uiState.ram >= prog.ramCost) CyberMutedGreen else CyberDark
                            ),
                            border = BorderStroke(1.dp, if (uiState.ram >= prog.ramCost) CyberCyan else CyberBorder),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("program_btn_${prog.id}")
                        ) {
                            Column(modifier = Modifier.padding(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = prog.name,
                                        color = if (uiState.ram >= prog.ramCost) CyberCyan else CyberBrightGreen.copy(alpha = 0.5f),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = "${prog.ramCost}MB",
                                        color = CyberPink,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = prog.description,
                                    color = CyberBrightGreen,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    lineHeight = 10.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Flee combat
                    Button(
                        onClick = onFlee,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberDark),
                        border = BorderStroke(1.dp, CyberPink),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .testTag("btn_flee_combat")
                    ) {
                        Text(
                            text = "EMERGENCY RETREAT ROUTE",
                            color = CyberPink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Output banner showing current actions
        if (uiState.enemyCombatAction.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberDark),
                border = BorderStroke(1.dp, CyberBorder),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = uiState.enemyCombatAction,
                    color = CyberPink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
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

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "--- BREACH PROTOCOL ACCESS INTERRUPT ---",
            color = CyberAmber,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        )

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
                Pair("FirewallBuffer.pkg", 75)
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

                Divider(color = CyberBorder)

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
    val filledPercent = if (max > 0) current.toFloat() / max else 0f
    val clampedPercent = filledPercent.coerceIn(0f, 1f)

    // Draw custom vintage block progress bars with modern curved borders
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CyberDark)
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(clampedPercent)
                .background(color)
        )
    }
}

// ==========================================
// Sub-Composable: Terminal Scrolling Logs
// ==========================================
@Composable
fun TerminalLogConsole(logs: List<LogMessage>) {
    val infiniteTransition = rememberInfiniteTransition(label = "crt_pulse")
    val glowIntensity by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_float"
    )

    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.scrollToItem(logs.size - 1)
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CyberDark),
        border = BorderStroke(1.dp, CyberBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
            Text(
                text = "SYSTEM LOG MONITOR // LIVE DIAGNOSTICS",
                color = CyberCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(bottom = 2.dp)
                    .graphicsLayer { alpha = glowIntensity }
            )

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
            }
        }
    }
}

