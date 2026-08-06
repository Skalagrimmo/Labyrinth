package com.example.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.CyberSoundEffectsManager
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberCardBg
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberDark
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberPink
import com.example.ui.theme.TerminalFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Modes supported in the Cyber Hacking Suite.
 */
enum class HackingMinigameMode(val title: String, val subtitle: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HEX_BREACH("HEX MATRIX BREACH", "Row/Col Buffer Protocol", Icons.Default.Lock),
    SIGNAL_TUNER("FREQUENCY TUNER", "Oscilloscope Wave Form Lock", Icons.Default.Refresh),
    CIRCUIT_ROUTER("CIRCUIT RELAY", "Conduit Network Pathing", Icons.Default.Build)
}

/**
 * Data Daemon Payload Target for Hex Breach
 */
data class DaemonPayload(
    val name: String,
    val sequence: List<String>,
    val rewardCredits: Int,
    var isMatched: Boolean = false
)

/**
 * Circuit Relay Tile definition for Node Network Router
 */
enum class ConduitDirection { NORTH, EAST, SOUTH, WEST }

data class RelayConduitTile(
    val id: Int,
    val row: Int,
    val col: Int,
    val connections: Set<ConduitDirection>, // active direction connections relative to tile
    val isStart: Boolean = false,
    val isEnd: Boolean = false,
    val isConnectedToPower: Boolean = false
)

/**
 * Master Cyber Hacking Minigame Suite Composable.
 */
@Composable
fun CyberHackingMinigameSuite(
    nodeName: String = "ARASAKA_CORE_77X",
    securityLevel: Int = 3,
    initialMode: HackingMinigameMode = HackingMinigameMode.HEX_BREACH,
    onSuccess: (creditsReward: Int) -> Unit = {},
    onFailed: () -> Unit = {},
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var currentMode by remember { mutableStateOf(initialMode) }
    val context = LocalContext.current
    val soundManager = remember(context) { CyberSoundEffectsManager.getInstance(context) }
    val view = LocalView.current

    var totalCreditsEarned by remember { mutableIntStateOf(0) }
    var globalTraceLevel by remember { mutableFloatStateOf(0.1f) }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("cyber_hacking_suite_root"),
        color = CyberDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            // Header Title Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberGreen.copy(alpha = 0.12f))
                    .border(BorderStroke(1.dp, CyberGreen.copy(alpha = 0.4f)), CutCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Cyberdeck Suite",
                        tint = CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "NEURAL DECK :: BREACH SUITE v4.2",
                            color = CyberCyan,
                            fontFamily = TerminalFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "TARGET: $nodeName [SEC_LVL: $securityLevel]",
                            color = CyberAmber,
                            fontFamily = TerminalFontFamily,
                            fontSize = 10.sp
                        )
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Suite",
                        tint = CyberPink
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mode Selector Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HackingMinigameMode.values().forEach { mode ->
                    val isSelected = currentMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CutCornerShape(4.dp))
                            .background(if (isSelected) CyberGreen else CyberCardBg)
                            .border(
                                BorderStroke(1.dp, if (isSelected) CyberGreen else CyberCyan.copy(alpha = 0.3f)),
                                CutCornerShape(4.dp)
                            )
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                soundManager.playTerminalKeyPressSound()
                                currentMode = mode
                            }
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                            .testTag("tab_hack_mode_${mode.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = mode.icon,
                                contentDescription = mode.title,
                                tint = if (isSelected) CyberDark else CyberGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = mode.title.split(" ").first(),
                                color = if (isSelected) CyberDark else CyberGreen,
                                fontFamily = TerminalFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Active Minigame Mode View
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (currentMode) {
                    HackingMinigameMode.HEX_BREACH -> {
                        HexMatrixBreachGameView(
                            securityLevel = securityLevel,
                            soundManager = soundManager,
                            onComplete = { reward ->
                                totalCreditsEarned += reward
                                onSuccess(totalCreditsEarned)
                            },
                            onFail = {
                                globalTraceLevel = (globalTraceLevel + 0.25f).coerceAtMost(1f)
                                onFailed()
                            }
                        )
                    }
                    HackingMinigameMode.SIGNAL_TUNER -> {
                        FrequencySignalTunerGameView(
                            securityLevel = securityLevel,
                            soundManager = soundManager,
                            onComplete = { reward ->
                                totalCreditsEarned += reward
                                onSuccess(totalCreditsEarned)
                            },
                            onFail = {
                                globalTraceLevel = (globalTraceLevel + 0.25f).coerceAtMost(1f)
                                onFailed()
                            }
                        )
                    }
                    HackingMinigameMode.CIRCUIT_ROUTER -> {
                        CircuitRelayRouterGameView(
                            securityLevel = securityLevel,
                            soundManager = soundManager,
                            onComplete = { reward ->
                                totalCreditsEarned += reward
                                onSuccess(totalCreditsEarned)
                            },
                            onFail = {
                                globalTraceLevel = (globalTraceLevel + 0.25f).coerceAtMost(1f)
                                onFailed()
                            }
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// MODE 1: HEX MATRIX BREACH GAME VIEW (2077 Row/Col Protocol)
// ============================================================================

@Composable
private fun HexMatrixBreachGameView(
    securityLevel: Int,
    soundManager: CyberSoundEffectsManager,
    onComplete: (Int) -> Unit,
    onFail: () -> Unit
) {
    val gridSize = 5
    val hexCodes = listOf("1C", "E9", "55", "BD", "7A", "FF", "00", "A3")

    var selectedRow by remember { mutableIntStateOf(0) } // Turn 0: pick from Row 0
    var selectedCol by remember { mutableIntStateOf(-1) }
    var isRowActive by remember { mutableStateOf(true) } // toggles between row and col selection

    var bufferLimit by remember { mutableIntStateOf(6) }
    val userBuffer = remember { mutableStateListOf<String>() }
    val chosenCoordinates = remember { mutableStateListOf<Pair<Int, Int>>() }

    var timeRemaining by remember { mutableIntStateOf(25 - securityLevel * 2) }
    var isGameOver by remember { mutableStateOf(false) }
    var isVictory by remember { mutableStateOf(false) }

    // Cyberware Daemons inventory
    var overclockCount by remember { mutableIntStateOf(1) }
    var icePickCount by remember { mutableIntStateOf(1) }
    var tracePurgeCount by remember { mutableIntStateOf(1) }

    // Generate 5x5 matrix
    val matrixGrid = remember(securityLevel) {
        val rand = Random(System.currentTimeMillis())
        Array(gridSize) { Array(gridSize) { hexCodes[rand.nextInt(hexCodes.size)] } }
    }

    // Generate target daemons
    val daemons = remember(securityLevel) {
        val rand = Random(System.currentTimeMillis() + 42L)
        listOf(
            DaemonPayload("DATAMINE_V1", listOf(hexCodes[rand.nextInt(hexCodes.size)], hexCodes[rand.nextInt(hexCodes.size)]), 100),
            DaemonPayload("DATAMINE_V2", listOf(hexCodes[rand.nextInt(hexCodes.size)], hexCodes[rand.nextInt(hexCodes.size)], hexCodes[rand.nextInt(hexCodes.size)]), 200),
            DaemonPayload("ICEPICK_DAEMON", listOf(hexCodes[rand.nextInt(hexCodes.size)], hexCodes[rand.nextInt(hexCodes.size)], hexCodes[rand.nextInt(hexCodes.size)], hexCodes[rand.nextInt(hexCodes.size)]), 350)
        )
    }

    // Timer loop
    LaunchedEffect(isGameOver) {
        if (!isGameOver) {
            while (timeRemaining > 0 && !isGameOver) {
                delay(1000)
                timeRemaining--
            }
            if (timeRemaining <= 0 && !isGameOver) {
                isGameOver = true
                soundManager.playHackingErrorSound()
                onFail()
            }
        }
    }

    // Check daemons matching in buffer
    fun checkDaemons() {
        val bufferString = userBuffer.joinToString(" ")
        var allDone = true
        var newlyCompleted = false

        daemons.forEach { daemon ->
            val seqString = daemon.sequence.joinToString(" ")
            if (!daemon.isMatched && bufferString.contains(seqString)) {
                daemon.isMatched = true
                newlyCompleted = true
            }
            if (!daemon.isMatched) allDone = false
        }

        if (newlyCompleted) {
            soundManager.playHackingSuccessSound()
        }

        if (allDone || userBuffer.size >= bufferLimit) {
            isGameOver = true
            val totalReward = daemons.filter { it.isMatched }.sumOf { it.rewardCredits }
            if (daemons.any { it.isMatched }) {
                isVictory = true
                soundManager.playHackingSuccessSound()
                onComplete(totalReward.coerceAtLeast(150))
            } else {
                soundManager.playHackingErrorSound()
                onFail()
            }
        }
    }

    fun onCellClicked(r: Int, c: Int) {
        if (isGameOver) return
        if (chosenCoordinates.contains(r to c)) return

        // Validate selection constraint
        if (isRowActive && r != selectedRow) return
        if (!isRowActive && c != selectedCol) return

        soundManager.playTerminalKeyPressSound()
        chosenCoordinates.add(r to c)
        val code = matrixGrid[r][c]
        userBuffer.add(code)

        if (isRowActive) {
            selectedCol = c
            isRowActive = false
        } else {
            selectedRow = r
            isRowActive = true
        }

        checkDaemons()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("hex_matrix_breach_view"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Status & Timer Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberCardBg),
            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f)),
            shape = CutCornerShape(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = "Timer", tint = CyberAmber, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "DECK TIME: ${timeRemaining}s",
                        color = if (timeRemaining <= 5) CyberPink else CyberAmber,
                        fontFamily = TerminalFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = if (isRowActive) "SELECT FROM ROW ${selectedRow + 1}" else "SELECT FROM COL ${selectedCol + 1}",
                    color = CyberGreen,
                    fontFamily = TerminalFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Target Daemons & Buffer Panel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Target Daemons List
            Card(
                modifier = Modifier
                    .weight(1.2f)
                    .height(115.dp),
                colors = CardDefaults.cardColors(containerColor = CyberDark.copy(alpha = 0.85f)),
                border = BorderStroke(1.dp, CyberGreen.copy(alpha = 0.4f)),
                shape = CutCornerShape(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "TARGET DAEMON PAYLOADS:",
                        color = CyberGreen,
                        fontFamily = TerminalFontFamily,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    daemons.forEach { daemon ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (daemon.isMatched) "✓" else "•",
                                color = if (daemon.isMatched) CyberGreen else CyberAmber,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${daemon.name}: ",
                                color = if (daemon.isMatched) CyberGreen else Color.White.copy(alpha = 0.8f),
                                fontFamily = TerminalFontFamily,
                                fontSize = 9.sp
                            )
                            daemon.sequence.forEach { code ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (daemon.isMatched) CyberGreen.copy(alpha = 0.3f) else CyberDark)
                                        .border(0.5.dp, CyberCyan.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                                        .padding(horizontal = 3.dp, vertical = 1.dp)
                                ) {
                                    Text(text = code, color = CyberCyan, fontSize = 8.5.sp, fontFamily = TerminalFontFamily)
                                }
                            }
                        }
                    }
                }
            }

            // User Intrude Buffer
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(115.dp),
                colors = CardDefaults.cardColors(containerColor = CyberDark.copy(alpha = 0.85f)),
                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f)),
                shape = CutCornerShape(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "BUFFER (${userBuffer.size}/$bufferLimit):",
                        color = CyberCyan,
                        fontFamily = TerminalFontFamily,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(bufferLimit) { idx ->
                            val code = userBuffer.getOrNull(idx)
                            Box(
                                modifier = Modifier
                                    .height(24.dp)
                                    .clip(CutCornerShape(3.dp))
                                    .background(if (code != null) CyberCyan.copy(alpha = 0.25f) else CyberDark)
                                    .border(1.dp, if (code != null) CyberCyan else Color.DarkGray, CutCornerShape(3.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = code ?: "_",
                                    color = if (code != null) CyberCyan else Color.Gray,
                                    fontFamily = TerminalFontFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Cyberware Daemon Boosters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = {
                    if (overclockCount > 0 && !isGameOver) {
                        overclockCount--
                        bufferLimit++
                        soundManager.playTerminalCommandSound()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("daemon_overclock"),
                enabled = overclockCount > 0 && !isGameOver,
                shape = CutCornerShape(4.dp),
                border = BorderStroke(1.dp, CyberAmber),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(text = "⚡ OVERCLOCK ($overclockCount)", color = CyberAmber, fontSize = 9.5.sp, fontFamily = TerminalFontFamily)
            }

            OutlinedButton(
                onClick = {
                    if (icePickCount > 0 && !isGameOver) {
                        val uncompleted = daemons.firstOrNull { !it.isMatched }
                        if (uncompleted != null && uncompleted.sequence.isNotEmpty()) {
                            icePickCount--
                            userBuffer.add(uncompleted.sequence.first())
                            soundManager.playTerminalCommandSound()
                            checkDaemons()
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("daemon_icepick"),
                enabled = icePickCount > 0 && !isGameOver,
                shape = CutCornerShape(4.dp),
                border = BorderStroke(1.dp, CyberCyan),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(text = "🧊 ICE PICK ($icePickCount)", color = CyberCyan, fontSize = 9.5.sp, fontFamily = TerminalFontFamily)
            }

            OutlinedButton(
                onClick = {
                    if (tracePurgeCount > 0 && !isGameOver) {
                        tracePurgeCount--
                        timeRemaining += 6
                        soundManager.playTerminalCommandSound()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("daemon_tracewipe"),
                enabled = tracePurgeCount > 0 && !isGameOver,
                shape = CutCornerShape(4.dp),
                border = BorderStroke(1.dp, CyberGreen),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(text = "🛡️ PURGE ($tracePurgeCount)", color = CyberGreen, fontSize = 9.5.sp, fontFamily = TerminalFontFamily)
            }
        }

        // 5x5 Interactive Matrix Grid
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = CyberDark),
            border = BorderStroke(1.dp, CyberGreen.copy(alpha = 0.5f)),
            shape = CutCornerShape(6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                for (r in 0 until gridSize) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (c in 0 until gridSize) {
                            val code = matrixGrid[r][c]
                            val isChosen = chosenCoordinates.contains(r to c)
                            val isHighlighted = (isRowActive && r == selectedRow) || (!isRowActive && c == selectedCol)

                            val cellBg = when {
                                isChosen -> Color(0xFF151D2A)
                                isHighlighted -> CyberGreen.copy(alpha = 0.2f)
                                else -> CyberCardBg.copy(alpha = 0.8f)
                            }

                            val cellBorder = when {
                                isChosen -> Color.DarkGray
                                isHighlighted -> CyberGreen
                                else -> CyberCyan.copy(alpha = 0.2f)
                            }

                            val textColor = when {
                                isChosen -> Color.Gray
                                isHighlighted -> CyberGreen
                                else -> CyberCyan
                            }

                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CutCornerShape(4.dp))
                                    .background(cellBg)
                                    .border(BorderStroke(1.2.dp, cellBorder), CutCornerShape(4.dp))
                                    .clickable(enabled = !isChosen && isHighlighted) {
                                        onCellClicked(r, c)
                                    }
                                    .testTag("hex_matrix_cell_${r}_${c}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isChosen) "--" else code,
                                    color = textColor,
                                    fontFamily = TerminalFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// MODE 2: FREQUENCY SIGNAL TUNER GAME VIEW (Oscilloscope Wave Form Lock)
// ============================================================================

@Composable
private fun FrequencySignalTunerGameView(
    securityLevel: Int,
    soundManager: CyberSoundEffectsManager,
    onComplete: (Int) -> Unit,
    onFail: () -> Unit
) {
    // Target wave parameters
    val targetFreq = remember { 3.5f + (securityLevel % 3) * 1.2f }
    val targetAmp = remember { 0.75f }
    val targetPhase = remember { 90f }

    // Player wave controls
    var playerFreq by remember { mutableFloatStateOf(1.0f) }
    var playerAmp by remember { mutableFloatStateOf(0.4f) }
    var playerPhase by remember { mutableFloatStateOf(0f) }

    var resonancePercent by remember { mutableIntStateOf(0) }
    var timeRemaining by remember { mutableIntStateOf(20 - securityLevel) }
    var isLockedIn by remember { mutableStateOf(false) }

    // Calculate dynamic resonance
    LaunchedEffect(playerFreq, playerAmp, playerPhase) {
        val freqDiff = abs(playerFreq - targetFreq) / 10f
        val ampDiff = abs(playerAmp - targetAmp)
        val phaseDiff = abs(playerPhase - targetPhase) / 360f

        val totalDiff = (freqDiff * 0.45f + ampDiff * 0.35f + phaseDiff * 0.20f).coerceIn(0f, 1f)
        resonancePercent = ((1.0f - totalDiff) * 100).toInt().coerceIn(0, 100)
    }

    // Timer loop
    LaunchedEffect(isLockedIn) {
        if (!isLockedIn) {
            while (timeRemaining > 0 && !isLockedIn) {
                delay(1000)
                timeRemaining--
            }
            if (timeRemaining <= 0 && !isLockedIn) {
                soundManager.playHackingErrorSound()
                onFail()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("frequency_tuner_view"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Status Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberCardBg),
            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f)),
            shape = CutCornerShape(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "SIGNAL RESONANCE: $resonancePercent%", color = if (resonancePercent > 85) CyberGreen else CyberAmber, fontFamily = TerminalFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(text = "LOCK TIMER: ${timeRemaining}s", color = if (timeRemaining <= 5) CyberPink else CyberAmber, fontFamily = TerminalFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Oscilloscope Waveform Canvas
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            colors = CardDefaults.cardColors(containerColor = CyberDark),
            border = BorderStroke(1.5.dp, if (resonancePercent > 88) CyberGreen else CyberPink),
            shape = CutCornerShape(6.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                OscilloscopeCanvas(
                    targetFreq = targetFreq,
                    targetAmp = targetAmp,
                    targetPhase = targetPhase,
                    playerFreq = playerFreq,
                    playerAmp = playerAmp,
                    playerPhase = playerPhase,
                    modifier = Modifier.fillMaxSize()
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "TARGET (PINK)", color = CyberPink, fontSize = 9.sp, fontFamily = TerminalFontFamily)
                    Text(text = "PROBE (CYAN)", color = CyberCyan, fontSize = 9.sp, fontFamily = TerminalFontFamily)
                }
            }
        }

        // Tuner Sliders Controls Panel
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = CyberDark.copy(alpha = 0.9f)),
            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f)),
            shape = CutCornerShape(6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Frequency Slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "FREQUENCY (Hz)", color = CyberCyan, fontSize = 10.sp, fontFamily = TerminalFontFamily)
                        Text(text = "%.2f Hz".format(playerFreq), color = CyberCyan, fontSize = 10.sp, fontFamily = TerminalFontFamily)
                    }
                    Slider(
                        value = playerFreq,
                        onValueChange = {
                            playerFreq = it
                            soundManager.playTerminalKeyPressSound()
                        },
                        valueRange = 1.0f..10.0f,
                        modifier = Modifier.testTag("slider_frequency"),
                        colors = SliderDefaults.colors(thumbColor = CyberCyan, activeTrackColor = CyberCyan)
                    )
                }

                // Amplitude Slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "AMPLITUDE", color = CyberAmber, fontSize = 10.sp, fontFamily = TerminalFontFamily)
                        Text(text = "%.2f".format(playerAmp), color = CyberAmber, fontSize = 10.sp, fontFamily = TerminalFontFamily)
                    }
                    Slider(
                        value = playerAmp,
                        onValueChange = {
                            playerAmp = it
                            soundManager.playTerminalKeyPressSound()
                        },
                        valueRange = 0.1f..1.0f,
                        modifier = Modifier.testTag("slider_amplitude"),
                        colors = SliderDefaults.colors(thumbColor = CyberAmber, activeTrackColor = CyberAmber)
                    )
                }

                // Phase Shift Slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "PHASE SHIFT (°)", color = CyberGreen, fontSize = 10.sp, fontFamily = TerminalFontFamily)
                        Text(text = "${playerPhase.toInt()}°", color = CyberGreen, fontSize = 10.sp, fontFamily = TerminalFontFamily)
                    }
                    Slider(
                        value = playerPhase,
                        onValueChange = {
                            playerPhase = it
                            soundManager.playTerminalKeyPressSound()
                        },
                        valueRange = 0f..360f,
                        modifier = Modifier.testTag("slider_phase"),
                        colors = SliderDefaults.colors(thumbColor = CyberGreen, activeTrackColor = CyberGreen)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Lock Signal Action Button
                Button(
                    onClick = {
                        if (resonancePercent >= 88) {
                            isLockedIn = true
                            soundManager.playHackingSuccessSound()
                            onComplete(250 + securityLevel * 50)
                        } else {
                            soundManager.playHackingErrorSound()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("btn_lock_signal"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (resonancePercent >= 88) CyberGreen else CyberPink,
                        contentColor = CyberDark
                    ),
                    shape = CutCornerShape(4.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Lock Signal")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (resonancePercent >= 88) "LOCK FREQUENCY SIGNAL" else "RESONANCE TOO LOW (<88%)",
                        fontFamily = TerminalFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun OscilloscopeCanvas(
    targetFreq: Float,
    targetAmp: Float,
    targetPhase: Float,
    playerFreq: Float,
    playerAmp: Float,
    playerPhase: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OscilloscopeAnim")
    val timePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "timePhase"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f

        // Draw background grid lines
        val gridStep = 30.dp.toPx()
        var xGrid = 0f
        while (xGrid < w) {
            drawLine(color = CyberGreen.copy(alpha = 0.08f), start = Offset(xGrid, 0f), end = Offset(xGrid, h), strokeWidth = 1f)
            xGrid += gridStep
        }
        var yGrid = 0f
        while (yGrid < h) {
            drawLine(color = CyberGreen.copy(alpha = 0.08f), start = Offset(0f, yGrid), end = Offset(w, yGrid), strokeWidth = 1f)
            yGrid += gridStep
        }

        // Draw Target Wave (Pink)
        val targetPath = Path()
        val numPoints = 120
        for (i in 0..numPoints) {
            val progress = i.toFloat() / numPoints
            val x = progress * w
            val angle = progress * targetFreq * 2f * PI.toFloat() + (targetPhase * PI.toFloat() / 180f) + timePhase
            val y = centerY - (sin(angle) * (targetAmp * (h / 2.5f)))
            if (i == 0) targetPath.moveTo(x, y) else targetPath.lineTo(x, y)
        }
        drawPath(targetPath, CyberPink, style = Stroke(width = 3f))

        // Draw Player Probe Wave (Cyan)
        val playerPath = Path()
        for (i in 0..numPoints) {
            val progress = i.toFloat() / numPoints
            val x = progress * w
            val angle = progress * playerFreq * 2f * PI.toFloat() + (playerPhase * PI.toFloat() / 180f) + timePhase
            val y = centerY - (sin(angle) * (playerAmp * (h / 2.5f)))
            if (i == 0) playerPath.moveTo(x, y) else playerPath.lineTo(x, y)
        }
        drawPath(playerPath, CyberCyan, style = Stroke(width = 3f))
    }
}

// ============================================================================
// MODE 3: CIRCUIT RELAY ROUTER GAME VIEW (Conduit Pathing Puzzle)
// ============================================================================

@Composable
private fun CircuitRelayRouterGameView(
    securityLevel: Int,
    soundManager: CyberSoundEffectsManager,
    onComplete: (Int) -> Unit,
    onFail: () -> Unit
) {
    val size = 4
    val tiles = remember(securityLevel) {
        mutableStateListOf<RelayConduitTile>().apply {
            val rand = Random(System.currentTimeMillis() + 99L)
            for (r in 0 until size) {
                for (c in 0 until size) {
                    val isStart = r == 0 && c == 0
                    val isEnd = r == size - 1 && c == size - 1
                    val conns = when (rand.nextInt(4)) {
                        0 -> setOf(ConduitDirection.NORTH, ConduitDirection.SOUTH)
                        1 -> setOf(ConduitDirection.EAST, ConduitDirection.WEST)
                        2 -> setOf(ConduitDirection.NORTH, ConduitDirection.EAST)
                        else -> setOf(ConduitDirection.EAST, ConduitDirection.SOUTH, ConduitDirection.WEST)
                    }
                    add(RelayConduitTile(id = r * size + c, row = r, col = c, connections = conns, isStart = isStart, isEnd = isEnd))
                }
            }
        }
    }

    var timeRemaining by remember { mutableIntStateOf(22 - securityLevel * 2) }
    var isSolved by remember { mutableStateOf(false) }

    // BFS solver to check if path from Start to End exists
    fun checkCircuitPath() {
        // Map grid coordinates to active directions
        val grid = Array(size) { r -> Array(size) { c -> tiles[r * size + c] } }
        val visited = Array(size) { BooleanArray(size) }
        val queue = ArrayDeque<Pair<Int, Int>>()

        // Start at (0, 0)
        queue.add(0 to 0)
        visited[0][0] = true

        var reachedEnd = false

        while (queue.isNotEmpty()) {
            val (r, c) = queue.removeFirst()
            val currentTile = grid[r][c]

            if (r == size - 1 && c == size - 1) {
                reachedEnd = true
                break
            }

            // Check North
            if (currentTile.connections.contains(ConduitDirection.NORTH) && r > 0 && !visited[r - 1][c]) {
                val northTile = grid[r - 1][c]
                if (northTile.connections.contains(ConduitDirection.SOUTH)) {
                    visited[r - 1][c] = true
                    queue.add(r - 1 to c)
                }
            }
            // Check South
            if (currentTile.connections.contains(ConduitDirection.SOUTH) && r < size - 1 && !visited[r + 1][c]) {
                val southTile = grid[r + 1][c]
                if (southTile.connections.contains(ConduitDirection.NORTH)) {
                    visited[r + 1][c] = true
                    queue.add(r + 1 to c)
                }
            }
            // Check East
            if (currentTile.connections.contains(ConduitDirection.EAST) && c < size - 1 && !visited[r][c + 1]) {
                val eastTile = grid[r][c + 1]
                if (eastTile.connections.contains(ConduitDirection.WEST)) {
                    visited[r][c + 1] = true
                    queue.add(r to c + 1)
                }
            }
            // Check West
            if (currentTile.connections.contains(ConduitDirection.WEST) && c > 0 && !visited[r][c - 1]) {
                val westTile = grid[r][c - 1]
                if (westTile.connections.contains(ConduitDirection.EAST)) {
                    visited[r][c - 1] = true
                    queue.add(r to c - 1)
                }
            }
        }

        if (reachedEnd && !isSolved) {
            isSolved = true
            soundManager.playHackingSuccessSound()
            onComplete(300 + securityLevel * 60)
        }
    }

    // Timer loop
    LaunchedEffect(isSolved) {
        if (!isSolved) {
            while (timeRemaining > 0 && !isSolved) {
                delay(1000)
                timeRemaining--
            }
            if (timeRemaining <= 0 && !isSolved) {
                soundManager.playHackingErrorSound()
                onFail()
            }
        }
    }

    fun rotateTile(index: Int) {
        if (isSolved) return
        val current = tiles[index]
        val rotatedConns = current.connections.map { dir ->
            when (dir) {
                ConduitDirection.NORTH -> ConduitDirection.EAST
                ConduitDirection.EAST -> ConduitDirection.SOUTH
                ConduitDirection.SOUTH -> ConduitDirection.WEST
                ConduitDirection.WEST -> ConduitDirection.NORTH
            }
        }.toSet()

        tiles[index] = current.copy(connections = rotatedConns)
        soundManager.playTerminalKeyPressSound()
        checkCircuitPath()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("circuit_relay_router_view"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header Status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberCardBg),
            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f)),
            shape = CutCornerShape(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "CIRCUIT STATUS: ${if (isSolved) "CONNECTED" else "CIRCUIT OPEN"}", color = if (isSolved) CyberGreen else CyberAmber, fontFamily = TerminalFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(text = "TRACE TIMER: ${timeRemaining}s", color = if (timeRemaining <= 5) CyberPink else CyberAmber, fontFamily = TerminalFontFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 4x4 Circuit Relay Grid
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = CyberDark),
            border = BorderStroke(1.dp, CyberGreen.copy(alpha = 0.5f)),
            shape = CutCornerShape(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(size),
                    modifier = Modifier.aspectRatio(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(tiles) { index, tile ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(CutCornerShape(6.dp))
                                .background(when {
                                    tile.isStart -> CyberGreen.copy(alpha = 0.3f)
                                    tile.isEnd -> CyberPink.copy(alpha = 0.3f)
                                    else -> CyberCardBg.copy(alpha = 0.9f)
                                })
                                .border(
                                    BorderStroke(
                                        1.5.dp,
                                        when {
                                            tile.isStart -> CyberGreen
                                            tile.isEnd -> CyberPink
                                            else -> CyberCyan.copy(alpha = 0.4f)
                                        }
                                    ),
                                    CutCornerShape(6.dp)
                                )
                                .clickable { rotateTile(index) }
                                .testTag("btn_rotate_node_$index"),
                            contentAlignment = Alignment.Center
                        ) {
                            ConduitTileCanvas(tile = tile, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConduitTileCanvas(
    tile: RelayConduitTile,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h / 2f)
        val strokeW = 6.dp.toPx()
        val pipeColor = when {
            tile.isStart -> CyberGreen
            tile.isEnd -> CyberPink
            else -> CyberCyan
        }

        // Central junction hub
        drawCircle(color = pipeColor, radius = strokeW * 0.9f, center = center)

        // Draw active conduits
        tile.connections.forEach { dir ->
            val endPoint = when (dir) {
                ConduitDirection.NORTH -> Offset(w / 2f, 0f)
                ConduitDirection.EAST -> Offset(w, h / 2f)
                ConduitDirection.SOUTH -> Offset(w / 2f, h)
                ConduitDirection.WEST -> Offset(0f, h / 2f)
            }
            drawLine(color = pipeColor, start = center, end = endPoint, strokeWidth = strokeW)
        }
    }
}
