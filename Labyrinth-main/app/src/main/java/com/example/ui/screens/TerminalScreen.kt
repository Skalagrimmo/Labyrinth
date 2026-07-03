package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
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
            .size(32.dp)
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

    var runnerNameInput by remember { mutableStateOf("") }
    var selectedClass by remember { mutableStateOf(NetrunnerClass.CODE_SLASHER) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(uiState.screen) {
        if (uiState.screen == GameViewModel.ActiveScreen.EXPLORATION) {
            focusRequester.requestFocus()
        }
    }

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
                    var x = 0f
                    while (x < size.width) {
                        drawLine(
                            color = Color(0x0800F3FF),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = strokePx
                        )
                        x += spacingPx
                    }
                    var y = 0f
                    while (y < size.height) {
                        drawLine(
                            color = Color(0x0800F3FF),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = strokePx
                        )
                        y += spacingPx
                    }
                    var yScan = 0f
                    while (yScan < size.height) {
                        drawLine(
                            color = Color(0x0600F3FF),
                            start = Offset(0f, yScan),
                            end = Offset(size.width, yScan),
                            strokeWidth = strokePx
                        )
                        yScan += scanlineHeightPx
                    }
                }
            }
            .padding(4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            TerminalHeader(uiState, onLeaderboardClick = { viewModel.viewLeaderboard() })

            Spacer(modifier = Modifier.height(2.dp))

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

            Spacer(modifier = Modifier.height(2.dp))

            if (uiState.screen != GameViewModel.ActiveScreen.CHARACTER_CREATION) {
                TerminalLogConsole(uiState)
                Spacer(modifier = Modifier.height(2.dp))
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
            .height(6.dp)
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
                .padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                    fontSize = 9.sp
                )
                Text(
                    text = "|",
                    color = CyberBorder,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
                Text(
                    text = "CORE: ${uiState.integrity}%",
                    color = CyberCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "|",
                    color = CyberBorder,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
                Text(
                    text = "RAM: ${uiState.ram}MB",
                    color = CyberPink,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onLeaderboardClick,
                    modifier = Modifier
                        .size(22.dp)
                        .testTag("leaderboard_tab_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "Mainframe Logs",
                        tint = CyberCyan,
                        modifier = Modifier.size(14.dp)
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
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = """
     _  _  ____ ____  ___  ____   __   _  _  __    ____ ____ 
    ( \( )(  __)(_  _)/ __)(  _ \ / _\ ( \/ )(  )  (  __)(  _ \
     )  (  ) _)   )( ( (__  )   /    \/ \/ \/ (_/\ ) _)  )   /
    (_)\_)(____) (__) \___)(_)\_)\_/\_/\_/\_/\____/(____)(_)\_)
            """.trimIndent(),
            color = CyberGreen,
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            lineHeight = 9.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "--- INITIALIZE NETRUNNER CYBERNET INTERRUPT ---",
            color = CyberGreen,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

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
                .padding(horizontal = 12.dp)
                .testTag("runner_name_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "CHOOSE CLASS ARCHETYPE:",
            color = CyberCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start).padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("class_card_${classType.name}")
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
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
                                fontSize = 12.sp
                            )
                            Row {
                                Text(
                                    text = "${classType.baseIntegrity}HP / ${classType.baseRam}RAM",
                                    color = CyberCyan,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                               )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = classType.description,
                            color = CyberBrightGreen,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            lineHeight = 11.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "PASSIVE: ${classType.passiveDesc}",
                            color = CyberPink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onStartGame,
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 12.dp)
                .testTag("compile_profile_button")
        ) {
            Text(
                text = "COMPILE PROFILE AND INJECT DATA",
                color = Color.Black,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.4f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
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
                                isCombat = (uiState.gameState != GameState.EXPLORATION)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

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
                                RepeatingNavigationButton(
                                    onClick = { viewModel.moveForward() },
                                    icon = { Icon(Icons.Default.KeyboardArrowUp, "Forward", tint = CyberCyan, modifier = Modifier.size(18.dp)) },
                                    modifier = Modifier.testTag("btn_move_forward")
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RepeatingNavigationButton(
                                        onClick = { viewModel.turnLeft() },
                                        icon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Turn Left", tint = CyberCyan, modifier = Modifier.size(18.dp)) },
                                        modifier = Modifier.testTag("btn_turn_left")
                                    )

                                    RepeatingNavigationButton(
                                        onClick = { viewModel.moveBackward() },
                                        icon = { Icon(Icons.Default.KeyboardArrowDown, "Backward", tint = CyberCyan, modifier = Modifier.size(18.dp)) },
                                        modifier = Modifier.testTag("btn_move_back")
                                    )

                                    RepeatingNavigationButton(
                                        onClick = { viewModel.turnRight() },
                                        icon = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Turn Right", tint = CyberCyan, modifier = Modifier.size(18.dp)) },
                                        modifier = Modifier.testTag("btn_turn_right")
                                    )
                                }
                            }
                        } else {
                            val enemy = uiState.activeEnemy
                            Column(
                                modifier = Modifier
                                    .padding(6.dp)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "⚠️ ${enemy?.name ?: "UNKNOWN"}",
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

                                var showItemMenu by remember { mutableStateOf(false) }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.combatAttack() },
                                        enabled = uiState.isCombatInputEnabled && uiState.attackCooldown <= 0,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CyberPink.copy(alpha = 0.5f),
                                            disabledContainerColor = CyberPink.copy(alpha = 0.25f)
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .height(24.dp)
                                            .testTag("btn_combat_attack")
                                    ) {
                                        Text(
                                            text = if (uiState.attackCooldown > 0) "ATTACK (${String.format("%.1f", uiState.attackCooldown / 10f)}s)" else "ATTACK",
                                            color = Color.White,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Button(
                                        onClick = { viewModel.combatDefend() },
                                        enabled = uiState.isCombatInputEnabled && uiState.defendCooldown <= 0 && uiState.activeFirewallTimeLeft <= 0,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CyberDark.copy(alpha = 0.5f),
                                            disabledContainerColor = CyberDark.copy(alpha = 0.25f)
                                        ),
                                        border = BorderStroke(1.dp, if (uiState.activeFirewallTimeLeft > 0) Color(0xFF10B981) else CyberBrightGreen.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .height(24.dp)
                                            .testTag("btn_combat_defend")
                                    ) {
                                        Text(
                                            text = if (uiState.activeFirewallTimeLeft > 0) "ACTIVE (${String.format("%.1f", uiState.activeFirewallTimeLeft / 10f)}s)" else if (uiState.defendCooldown > 0) "SHIELD (${String.format("%.1f", uiState.defendCooldown / 10f)}s)" else "DEFEND",
                                            color = if (uiState.activeFirewallTimeLeft > 0) Color(0xFF10B981) else CyberBrightGreen,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Button(
                                        onClick = { showItemMenu = !showItemMenu },
                                        enabled = uiState.isCombatInputEnabled && uiState.inventory.isNotEmpty(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CyberDark.copy(alpha = 0.5f),
                                            disabledContainerColor = CyberDark.copy(alpha = 0.25f)
                                        ),
                                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(24.dp)
                                            .testTag("btn_combat_item")
                                    ) {
                                        Text("ITEM", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { viewModel.fleeCombat() },
                                        enabled = uiState.isCombatInputEnabled,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = CyberDark.copy(alpha = 0.5f),
                                            disabledContainerColor = CyberDark.copy(alpha = 0.25f)
                                        ),
                                        border = BorderStroke(1.dp, CyberAmber.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(24.dp)
                                            .testTag("btn_combat_flee")
                                    ) {
                                        Text("FLEE", color = CyberAmber, fontFamily = FontFamily.Monospace, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

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
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
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
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
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

            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight()
            ) {
                val isCombatActiveForRadar = uiState.gameState != GameState.EXPLORATION
                val minimapAlpha by animateFloatAsState(
                    targetValue = if (isCombatActiveForRadar) 0f else 1f,
                    animationSpec = tween(durationMillis = 500, easing = LinearEasing),
                    label = "MinimapAlpha"
                )
                val minimapScale by animateFloatAsState(
                    targetValue = if (isCombatActiveForRadar) 0.8f else 1.0f,
                    animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                    label = "MinimapScale"
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                    border = BorderStroke(1.dp, CyberBorder.copy(alpha = minimapAlpha)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
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
                        Text(
                            text = "SECTOR LOGIC RADAR",
                            color = CyberCyan.copy(alpha = minimapAlpha),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )

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
                                .fillMaxWidth()
                                .height(24.dp)
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
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("item_$item")
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
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

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
                    .height(32.dp)
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
                    .height(32.dp)
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
                    wallPath.reset()
                    val p1 = getPixel(tl_c[d], adjustedTl_r[d])
                    val p2 = getPixel(tl_c[d+1], adjustedTl_r[d+1])
                    val p3 = getPixel(bl_c[d+1], adjustedBl_r[d+1])
                    val p4 = getPixel(bl_c[d], adjustedBl_r[d])
                    wallPath.moveTo(p1.x, p1.y)
                    wallPath.lineTo(p2.x, p2.y)
                    wallPath.lineTo(p3.x, p3.y)
                    wallPath.lineTo(p4.x, p4.y)
                    wallPath.close()
                    drawPath(path = wallPath, color = primaryColor.copy(alpha = alpha))

                    drawLine(color = primaryColor.copy(alpha = alpha * 2f), start = getPixel(tl_c[d], adjustedTl_r[d]), end = getPixel(tl_c[d+1], adjustedTl_r[d+1]), strokeWidth = 3f)
                    drawLine(color = primaryColor.copy(alpha = alpha * 2f), start = getPixel(bl_c[d], adjustedBl_r[d]), end = getPixel(bl_c[d+1], adjustedBl_r[d+1]), strokeWidth = 3f)
                    drawLine(color = primaryColor.copy(alpha = alpha * 2f), start = getPixel(tl_c[d+1], adjustedTl_r[d+1]), end = getPixel(bl_c[d+1], adjustedBl_r[d+1]), strokeWidth = 3f)

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
                    drawLine(color = primaryColor.copy(alpha = 0.2f), start = getPixel(tl_c[d], adjustedTl_r[d+1]), end = getPixel(tl_c[d+1], adjustedTl_r[d+1]), strokeWidth = 2f)
                    drawLine(color = primaryColor.copy(alpha = 0.2f), start = getPixel(bl_c[d], adjustedBl_r[d+1]), end = getPixel(bl_c[d+1], adjustedBl_r[d+1]), strokeWidth = 2f)
                    drawLine(color = primaryColor.copy(alpha = 0.3f), start = getPixel(tl_c[d+1], adjustedTl_r[d+1]), end = getPixel(bl_c[d+1], adjustedBl_r[d+1]), strokeWidth = 3f)
                }

                if (rightWallAt[d]) {
                    wallPath.reset()
                    val p1 = getPixel(tr_c[d], adjustedTr_r[d])
                    val p2 = getPixel(tr_c[d+1], adjustedTr_r[d+1])
                    val p3 = getPixel(br_c[d+1], adjustedBr_r[d+1])
                    val p4 = getPixel(br_c[d], adjustedBr_r[d])
                    wallPath.moveTo(p1.x, p1.y)
                    wallPath.lineTo(p2.x, p2.y)
                    wallPath.lineTo(p3.x, p3.y)
                    wallPath.lineTo(p4.x, p4.y)
                    wallPath.close()
                    drawPath(path = wallPath, color = primaryColor.copy(alpha = alpha))

                    drawLine(color = primaryColor.copy(alpha = alpha * 2f), start = getPixel(tr_c[d], adjustedTr_r[d]), end = getPixel(tr_c[d+1], adjustedTr_r[d+1]), strokeWidth = 3f)
                    drawLine(color = primaryColor.copy(alpha = alpha * 2f), start = getPixel(br_c[d], adjustedBr_r[d]), end = getPixel(br_c[d+1], adjustedBr_r[d+1]), strokeWidth = 3f)
                    drawLine(color = primaryColor.copy(alpha = alpha * 2f), start = getPixel(tr_c[d+1], adjustedTr_r[d+1]), end = getPixel(br_c[d+1], adjustedBr_r[d+1]), strokeWidth = 3f)

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
                    color = Color(0xFFC084FC).copy(alpha = 0.25f),
                    radius = sizeRadius * 1.6f,
                    center = center
                )

                drawCircle(color = Color(0xFFC084FC), radius = sizeRadius, center = center, style = Stroke(width = 6f))
                drawCircle(color = Color(0xFFC084FC).copy(alpha = 0.6f), radius = sizeRadius * 0.65f, center = center, style = Stroke(width = 4f))
                drawCircle(color = Color(0xFFC084FC), radius = sizeRadius * 0.3f, center = center)
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

            drawContext.canvas.restore()
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
                    .padding(top = 6.dp)
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
                    .padding(horizontal = 6.dp, vertical = 2.dp)
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
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = banner,
                        color = bannerColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
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

            val cellSize = minOf(w / viewGridCount, h / viewGridCount)
            val gridW = cellSize * viewGridCount
            val gridH = cellSize * viewGridCount
            val startX = (w - gridW) / 2f
            val startY = (h - gridH) / 2f

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

            for (vy in 0 until viewGridCount) {
                for (vx in 0 until viewGridCount) {
                    val mx = px - viewRadius + vx
                    val my = py - viewRadius + vy

                    val cellLeft = startX + vx * cellSize
                    val cellTop = startY + vy * cellSize
                    val isPlayer = (mx == px && my == py)

                    if (isPlayer) {
                        val playerCenter = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)

                        drawCircle(
                            color = Color(0xFF00E5FF).copy(alpha = pulseAlpha),
                            radius = cellSize * pulseScale * 1.5f,
                            center = playerCenter
                        )
                        drawCircle(
                            color = Color(0xFF00E5FF).copy(alpha = 0.4f),
                            radius = cellSize * 0.7f,
                            center = playerCenter
                        )
                        
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

                        if (!isExplored && !inActiveRange) {
                            drawCircle(
                                color = Color(0xFF0F172A).copy(alpha = 0.3f),
                                radius = 1.5f,
                                center = Offset(cellLeft + cellSize / 2f, cellTop + cellSize / 2f)
                            )
                            continue
                        }

                        val alpha = if (inActiveRange) 1.0f else 0.4f
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
                    }
                }
            }
        }
    }
}

// ==========================================
// Sub-Composable: Combat Mode Screen (Оптимізовано для бою: великий ворог, компактна статистика)
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
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.6f),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Left Panel: Enemy (ВЕЛИКИЙ)
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberDark),
                border = BorderStroke(1.dp, CyberBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1.8f)
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

                    Box(
                        modifier = Modifier
                            .weight(1.8f)
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

                    // Enemy stats (КОМПАКТНІ)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "CORE: ${enemy.integrity}/${enemy.maxIntegrity}",
                                color = CyberPink,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp
                            )
                            Text(
                                text = "SHIELD: ${enemy.shield}/${enemy.maxShield}",
                                color = CyberCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp
                            )
                        }
                        ProgressBarRetro(
                            current = enemy.integrity,
                            max = enemy.maxIntegrity,
                            color = CyberPink,
                            modifier = Modifier.height(4.dp)
                        )
                        ProgressBarRetro(
                            current = enemy.shield,
                            max = enemy.maxShield,
                            color = CyberCyan,
                            modifier = Modifier.height(4.dp)
                        )
                    }
                }
            }

            // Right Panel: Statistics (КОМПАКТНА)
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                border = BorderStroke(1.dp, CyberBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "TACTICAL CODES:",
                        color = CyberCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = "SYS: ${uiState.integrity}/${uiState.maxIntegrity}",
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.sp
                        )
                        ProgressBarRetro(
                            current = uiState.integrity,
                            max = uiState.maxIntegrity,
                            color = CyberCyan,
                            modifier = Modifier.height(3.dp)
                        )
                        Text(
                            text = "FW: ${uiState.playerShield}/${uiState.playerMaxShield}",
                            color = CyberBrightGreen,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.sp
                        )
                        ProgressBarRetro(
                            current = uiState.playerShield,
                            max = uiState.playerMaxShield,
                            color = CyberBrightGreen,
                            modifier = Modifier.height(3.dp)
                        )
                        Text(
                            text = "RAM: ${uiState.ram}/${uiState.maxRam}MB",
                            color = CyberPink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.sp
                        )
                        ProgressBarRetro(
                            current = uiState.ram,
                            max = uiState.maxRam,
                            color = CyberPink,
                            modifier = Modifier.height(3.dp)
                        )
                    }

                    HorizontalDivider(color = CyberBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 2.dp))

                    // 3 найважливіші програми
                    uiState.installedPrograms.take(3).forEach { prog ->
                        Card(
                            onClick = { onExecuteProgram(prog) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (uiState.ram >= prog.ramCost) CyberMutedGreen else CyberDark
                            ),
                            border = BorderStroke(1.dp, if (uiState.ram >= prog.ramCost) CyberCyan else CyberBorder),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = prog.name,
                                    color = if (uiState.ram >= prog.ramCost) CyberCyan else CyberBrightGreen.copy(alpha = 0.5f),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp
                                )
                                Text(
                                    text = "${prog.ramCost}MB",
                                    color = CyberPink,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 7.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = onFlee,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberDark.copy(alpha = 0.5f),
                            disabledContainerColor = CyberDark.copy(alpha = 0.25f)
                        ),
                        border = BorderStroke(1.dp, CyberPink.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .testTag("btn_flee_combat")
                    ) {
                        Text(
                            text = "FLEE",
                            color = CyberPink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

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
                    fontSize = 8.sp,
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
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
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
                        .padding(6.dp),
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
                                        .size(34.dp)
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
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

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
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "TARGET SEQUENCE:",
                        color = CyberCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

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

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "INTRUSION BUFFER: (${puzzle.currentBuffer.size}/${puzzle.bufferLimit})",
                        color = CyberAmber,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

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

                    Spacer(modifier = Modifier.height(4.dp))

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
                            .height(36.dp)
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
                .padding(bottom = 4.dp),
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

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
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
                            .padding(8.dp),
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
                                .height(30.dp)
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

            Spacer(modifier = Modifier.height(6.dp))

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
                            .padding(8.dp),
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
                                .height(30.dp)
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

        Spacer(modifier = Modifier.height(6.dp))

        Button(
            onClick = onExit,
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
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
                .padding(bottom = 4.dp),
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
                    .height(26.dp)
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CyberDark)
                .border(1.dp, CyberBorder, RoundedCornerShape(12.dp))
                .padding(vertical = 4.dp, horizontal = 6.dp)
        ) {
            Text("RUNNER HANDLE", color = CyberCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.weight(1.2f))
            Text("ARCHETYPE", color = CyberCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.weight(1f))
            Text("SECTOR", color = CyberCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center)
            Text("HACKS", color = CyberCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.weight(0.7f), textAlign = TextAlign.Center)
            Text("STATUS", color = CyberCyan, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 9.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        }

        Spacer(modifier = Modifier.height(4.dp))

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
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
                            .padding(horizontal = 6.dp, vertical = 6.dp)
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

        Spacer(modifier = Modifier.height(6.dp))

        Button(
            onClick = onExit,
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
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
            .padding(12.dp),
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
            modifier = Modifier.padding(bottom = 10.dp)
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
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = CyberCardBg),
            border = BorderStroke(1.dp, CyberBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onRestart,
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 12.dp)
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
        Color(0xFFEF4444)
    } else if (clampedPercent < 0.5f) {
        Color(0xFFFBBF24)
    } else {
        color
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CyberDark)
            .border(1.dp, finalColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
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
fun TerminalLogConsole(uiState: GameViewModel.GameUiState) {
    val logs = uiState.logFeed
    var activeConsoleTab by remember { mutableStateOf(0) }

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
        modifier = Modifier
            .fillMaxWidth()
            .height(115.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
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
                            .padding(horizontal = 4.dp, vertical = 2.dp)
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
                            .padding(horizontal = 4.dp, vertical = 2.dp)
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

            HorizontalDivider(color = CyberBorder.copy(alpha = 0.3f), thickness = 1.dp, modifier = Modifier.padding(bottom = 2.dp))

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
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
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

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            val isCombat = uiState.gameState != GameState.EXPLORATION

            var cpuLoad by remember { mutableStateOf(34f) }
            LaunchedEffect(isCombat) {
                while (true) {
                    val base = if (isCombat) 74f else 32f
                    val flutter = (Math.random() * 8 - 4).toFloat()
                    cpuLoad = (base + flutter).coerceIn(10f, 99f)
                    delay(500)
                }
            }

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

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black, RoundedCornerShape(4.dp))
                    .border(1.dp, CyberBorder.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(3.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = "MATRIX DATASTREAM //",
                    color = CyberMutedText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 6.sp,
                    fontWeight = FontWeight.Bold
                )
                hexStateList.forEach { line ->
                    Text(
                        text = line,
                        color = CyberBrightGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 7.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                .padding(vertical = 2.dp, horizontal = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("nav_tab_exploration")
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Nav",
                        tint = if (currentScreen == GameViewModel.ActiveScreen.EXPLORATION) CyberCyan else CyberBrightGreen.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "NAV",
                        color = if (currentScreen == GameViewModel.ActiveScreen.EXPLORATION) CyberCyan else CyberBrightGreen.copy(alpha = 0.4f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            if (currentScreen == GameViewModel.ActiveScreen.EXPLORATION) {
                                viewModel.interact()
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("nodes_tab_combat")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Nodes",
                        tint = if (currentScreen == GameViewModel.ActiveScreen.COMBAT || 
                                currentScreen == GameViewModel.ActiveScreen.HACKING_MINIGAME) CyberCyan else CyberBrightGreen.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "NODES",
                        color = if (currentScreen == GameViewModel.ActiveScreen.COMBAT || 
                                currentScreen == GameViewModel.ActiveScreen.HACKING_MINIGAME) CyberCyan else CyberBrightGreen.copy(alpha = 0.4f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

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
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("loadout_tab_store")
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Loadout",
                        tint = if (currentScreen == GameViewModel.ActiveScreen.UPGRADE_STORE) CyberCyan else CyberBrightGreen.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "LOADOUT",
                        color = if (currentScreen == GameViewModel.ActiveScreen.UPGRADE_STORE) CyberCyan else CyberBrightGreen.copy(alpha = 0.4f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

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
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .testTag("system_tab_leaderboard")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "System",
                        tint = if (currentScreen == GameViewModel.ActiveScreen.LEADERBOARD) CyberCyan else CyberBrightGreen.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "SYSTEM",
                        color = if (currentScreen == GameViewModel.ActiveScreen.LEADERBOARD) CyberCyan else CyberBrightGreen.copy(alpha = 0.4f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp)
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

@Composable
fun GlitchOverlay(progress: Float, modifier: Modifier = Modifier) {
    if (progress <= 0f || progress >= 1f) return
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val random = java.util.Random((progress * 100).toLong())
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