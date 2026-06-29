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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    // Interactive name text state for creation screen
    var runnerNameInput by remember { mutableStateOf("") }
    var selectedClass by remember { mutableStateOf(NetrunnerClass.CODE_SLASHER) }

    // CRT Phosphor glow animation pulse
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

    // Base background with modern high-density cyan grid layout and subtle scanlines (highly optimized)
    val gridSpacing = 32.dp
    val scanlineHeight = 12.dp
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CyberDark)
            .drawBehind {
                val spacingPx = gridSpacing.toPx()
                val strokePx = 1.dp.toPx()
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
                val scanlineHeightPx = scanlineHeight.toPx()
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
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Retro Cyber Terminal Header
            TerminalHeader(uiState, onLeaderboardClick = { viewModel.viewLeaderboard() })

            Spacer(modifier = Modifier.height(10.dp))

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

            Spacer(modifier = Modifier.height(10.dp))

            // Always Visible Terminal Log Output (Footer console log)
            if (uiState.screen != GameViewModel.ActiveScreen.CHARACTER_CREATION) {
                TerminalLogConsole(uiState.logFeed, glowIntensity)
                Spacer(modifier = Modifier.height(10.dp))
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
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Top Row: Sector identification + Status lights/actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SECTOR: 0x4A-CRONOS // LAYER ${uiState.level}",
                    color = CyberCyan,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Syncing",
                        tint = CyberCyan,
                        modifier = Modifier.size(14.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Encryption Mode",
                        tint = CyberAmber,
                        modifier = Modifier.size(14.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

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
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CyberCyan)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ONLINE",
                            color = CyberDark,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dual Grid Columns of high-density statistics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Integrity Health column
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "INTEGRITY",
                            color = CyberMutedText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val percent = if (uiState.maxIntegrity > 0) (uiState.integrity * 100 / uiState.maxIntegrity).coerceIn(0, 100) else 0
                        Text(
                            text = "$percent%",
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    HighDensityProgressBar(
                        current = uiState.integrity,
                        max = uiState.maxIntegrity,
                        isGradient = true,
                        color = CyberCyan
                    )
                }

                // RAM Buffer column
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "RAM BUFFER",
                            color = CyberMutedText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${uiState.ram}/${uiState.maxRam}",
                            color = CyberPink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    HighDensityProgressBar(
                        current = uiState.ram,
                        max = uiState.maxRam,
                        isGradient = false,
                        color = CyberPink
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
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Main split: Left Viewport (3D ASCII), Right Panel (Map + Stats)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.2f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Renders 3D ascii output
                        Text(
                            text = uiState.perspectiveText,
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("first_person_viewport")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tactical Navigation buttons under 3D wireframe
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                    border = BorderStroke(1.dp, CyberBorder),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // UP arrow
                        Button(
                            onClick = { viewModel.moveForward() },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberMutedGreen),
                            border = BorderStroke(1.dp, CyberBorderLight),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("btn_move_forward")
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, "Forward", tint = CyberCyan)
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { viewModel.turnLeft() },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberMutedGreen),
                                border = BorderStroke(1.dp, CyberBorderLight),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("btn_turn_left")
                            ) {
                                Icon(Icons.Default.KeyboardArrowLeft, "Turn Left", tint = CyberCyan)
                            }

                            Button(
                                onClick = { viewModel.moveBackward() },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberMutedGreen),
                                border = BorderStroke(1.dp, CyberBorderLight),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("btn_move_back")
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, "Backward", tint = CyberCyan)
                            }

                            Button(
                                onClick = { viewModel.turnRight() },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberMutedGreen),
                                border = BorderStroke(1.dp, CyberBorderLight),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("btn_turn_right")
                            ) {
                                Icon(Icons.Default.KeyboardArrowRight, "Turn Right", tint = CyberCyan)
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
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "SECTOR LOGIC RADAR",
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
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

                Spacer(modifier = Modifier.height(8.dp))

                // Primary Stats Overview
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                    border = BorderStroke(1.dp, CyberBorder),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "STATUS // ${uiState.runnerName.uppercase()}",
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )

                        // HP Bar
                        Text(
                            text = "INTEGRITY: ${uiState.integrity}/${uiState.maxIntegrity}",
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
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
                            fontSize = 10.sp
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
                                fontSize = 10.sp
                            )
                            Text(
                                text = "DMG: +${uiState.damageBonus}",
                                color = CyberPink,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }

                        Divider(color = CyberBorder, thickness = 1.dp)

                        // Click to interact/hack
                        Button(
                            onClick = { viewModel.interact() },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberPink),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("btn_interact_hack")
                        ) {
                            Text(
                                text = "EXECUTE NODE INTERACTION",
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Inventory & Consumable selection pane
        Card(
            colors = CardDefaults.cardColors(containerColor = CyberCardBg),
            border = BorderStroke(1.dp, CyberBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.5f)
        ) {
            Column(modifier = Modifier.padding(6.dp)) {
                Text(
                    text = "VIRTUAL STORAGE // CONSUMABLES (TAP TO LOAD):",
                    color = CyberCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
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
                            fontSize = 9.sp
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, itemColor, RoundedCornerShape(8.dp))
                                    .background(CyberDark)
                                    .clickable { viewModel.useInventoryItem(item) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("item_$item")
                            ) {
                                Text(
                                    text = item,
                                    color = itemColor,
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

        Spacer(modifier = Modifier.height(8.dp))

        // Base navigation: Store, Leaders, Dissolve Connection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = onShopClick,
                colors = ButtonDefaults.buttonColors(containerColor = CyberMutedGreen),
                border = BorderStroke(1.dp, CyberBorderLight),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .testTag("btn_shop_console")
            ) {
                Text("SHOP SOURCE", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onSafeDisconnect,
                colors = ButtonDefaults.buttonColors(containerColor = CyberDark),
                border = BorderStroke(1.dp, CyberPink),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1.2f)
                    .height(40.dp)
                    .testTag("btn_safe_disconnect")
            ) {
                Text("DISCONNECT RUN", color = CyberPink, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
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

    val annotatedMap = remember(maze, px, py, dir) {
        androidx.compose.ui.text.buildAnnotatedString {
            for (y in maze.indices) {
                for (x in maze[y].indices) {
                    val cell = maze[y][x]
                    val isPlayer = (x == px && y == py)

                    val char = when {
                        isPlayer -> {
                            when (dir) {
                                Direction.NORTH -> "▲"
                                Direction.EAST -> "▶"
                                Direction.SOUTH -> "▼"
                                Direction.WEST -> "◀"
                            }
                        }
                        cell == CellType.WALL -> "█"
                        cell == CellType.DATA_STORE -> "D"
                        cell == CellType.ENCRYPTED_PORTAL -> "P"
                        cell == CellType.VIRUS_NODE -> "V"
                        cell == CellType.SAFE_ZONE -> "S"
                        else -> "·"
                    }

                    val color = when {
                        isPlayer -> CyberCyan
                        cell == CellType.WALL -> CyberBorderLight
                        cell == CellType.DATA_STORE -> CyberAmber
                        cell == CellType.ENCRYPTED_PORTAL -> CyberPink
                        cell == CellType.VIRUS_NODE -> CyberPink
                        cell == CellType.SAFE_ZONE -> CyberGreen
                        else -> Color.DarkGray
                    }

                    withStyle(style = androidx.compose.ui.text.SpanStyle(color = color, fontWeight = if (isPlayer) FontWeight.Bold else FontWeight.Normal)) {
                        append(char)
                    }
                    if (x < maze[y].size - 1) {
                        append(" ")
                    }
                }
                if (y < maze.size - 1) {
                    append("\n")
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = annotatedMap,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center
        )
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
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )

        // Split: Enemy visual representation + Player Tactical Programs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.2f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Left Panel: Enemy details & ASCII Art
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberDark),
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
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = enemy.name.uppercase(),
                        color = CyberPink,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    // Enemy ASCII icon
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = enemy.iconAscii,
                            color = CyberPink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("enemy_ascii")
                        )
                    }

                    // Enemy stats
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "INTEGRITY: ${enemy.integrity}/${enemy.maxIntegrity}",
                            color = CyberPink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                        ProgressBarRetro(
                            current = enemy.integrity,
                            max = enemy.maxIntegrity,
                            color = CyberPink
                        )

                        Text(
                            text = "SHIELD BARRIER: ${enemy.shield}/${enemy.maxShield}",
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
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
                                fontSize = 9.sp
                            )
                            Text(
                                text = "SHIELDING: ${enemy.armor}",
                                color = CyberBrightGreen,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }

            // Right Panel: Program compilations available
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                border = BorderStroke(1.dp, CyberBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "TACTICAL CODES:",
                        color = CyberCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )

                    // Display runner stats
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "INTEG: ${uiState.integrity}%",
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "RAM: ${uiState.ram}MB",
                            color = CyberPink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
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
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("program_btn_${prog.id}")
                        ) {
                            Column(modifier = Modifier.padding(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = prog.name,
                                        color = if (uiState.ram >= prog.ramCost) CyberCyan else CyberBrightGreen.copy(alpha = 0.5f),
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "${prog.ramCost}MB",
                                        color = CyberPink,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = prog.description,
                                    color = CyberBrightGreen,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    lineHeight = 11.sp
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
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("btn_flee_combat")
                    ) {
                        Text(
                            text = "EMERGENCY RETREAT ROUTE",
                            color = CyberPink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Output banner showing current actions
        if (uiState.enemyCombatAction.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberDark),
                border = BorderStroke(1.dp, CyberBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = uiState.enemyCombatAction,
                    color = CyberPink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(8.dp)
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
                                        .clickable(enabled = !isSelected) { onCellSelected(r, c) }
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
fun TerminalLogConsole(logs: List<LogMessage>, glow: Float) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CyberDark),
        border = BorderStroke(1.dp, CyberBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Text(
                text = "SYSTEM LOG MONITOR // LIVE DIAGNOSTICS",
                color = CyberCyan.copy(alpha = glow),
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 2.dp)
            )

            LazyColumn(
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
    Card(
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        border = BorderStroke(1.dp, CyberBorder),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp)
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

