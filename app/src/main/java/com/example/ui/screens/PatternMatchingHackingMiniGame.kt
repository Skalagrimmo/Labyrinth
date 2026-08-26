package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.audio.CyberSoundEffectsManager
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberCardBg
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberDark
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberPink
import com.example.ui.theme.TerminalFontFamily
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * State enum for the Pattern-Matching Mini-Game.
 */
enum class PatternHackingState {
    READY,           // Brief countdown before starting pattern sequence
    SHOWING_PATTERN, // Highlighting target pattern step by step
    BREACHING,       // Player input phase
    SUCCESS,         // Node breached!
    FAILED           // Lockout triggered!
}

/**
 * Data model for an individual node on the pattern grid.
 */
data class PatternGridNode(
    val id: Int,
    val hexCode: String,
    val isTargetSequenceItem: Boolean = false,
    val targetIndex: Int = -1
)

/**
 * Interactive Pattern-Matching Mini-Game Interface for hacking high-security matrix nodes.
 */
@Composable
fun PatternMatchingHackingMiniGame(
    nodeName: String = "ARASAKA_CORE_77X",
    securityLevel: Int = 3,
    onBreachSuccess: (creditsReward: Int) -> Unit = {},
    onBreachFailed: () -> Unit = {},
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val soundManager = remember(context) { CyberSoundEffectsManager.getInstance(context) }

    val maxTimeSeconds = (22 - securityLevel * 2).coerceAtLeast(10)
    var timeLeftSeconds by remember { mutableIntStateOf(maxTimeSeconds) }
    var hackingState by remember { mutableStateOf(PatternHackingState.READY) }
    var currentStepIndex by remember { mutableIntStateOf(0) }
    var selectedGridIndices = remember { mutableStateListOf<Int>() }
    var flashErrorIndex by remember { mutableIntStateOf(-1) }
    var attemptsLeft by remember { mutableIntStateOf(3) }
    var hackLogs = remember { mutableStateListOf<String>() }

    // Grid configuration: 4x4 matrix (16 cells)
    val gridSize = 16
    val hexPool = listOf("1C", "E9", "55", "BD", "7A", "FF", "00", "A3", "C4", "88")

    // Generate procedural pattern grid & target sequence
    val patternNodes = remember(nodeName, securityLevel, attemptsLeft) {
        val random = Random(System.currentTimeMillis() + attemptsLeft * 31L)
        val targetLength = (3 + securityLevel).coerceAtMost(6)

        // Select distinct random grid indices for the sequence
        val targetIndices = (0 until gridSize).shuffled(random).take(targetLength)

        val nodes = (0 until gridSize).map { index ->
            val code = hexPool[random.nextInt(hexPool.size)]
            val targetIdx = targetIndices.indexOf(index)
            PatternGridNode(
                id = index,
                hexCode = code,
                isTargetSequenceItem = targetIdx != -1,
                targetIndex = targetIdx
            )
        }
        nodes
    }

    // Ordered target sequence nodes
    val targetSequence = remember(patternNodes) {
        patternNodes
            .filter { it.isTargetSequenceItem }
            .sortedBy { it.targetIndex }
    }

    fun addLog(msg: String) {
        hackLogs.add(0, msg)
        if (hackLogs.size > 8) hackLogs.removeAt(hackLogs.size - 1)
    }

    // Timer coroutine during active BREACHING phase
    LaunchedEffect(hackingState) {
        if (hackingState == PatternHackingState.READY) {
            addLog("INITIALIZING PATTERN BREACH ENCRYPTION LOCK...")
            delay(1000)
            addLog("TARGET SEQUENCE GENERATED: Match ${targetSequence.size} glyphs in sequence.")
            hackingState = PatternHackingState.SHOWING_PATTERN
        } else if (hackingState == PatternHackingState.SHOWING_PATTERN) {
            // Briefly reveal sequence items to memorise
            delay(1500)
            addLog("SEQUENCE MEMORY LOCK READY. BREACH TIMER RUNNING!")
            hackingState = PatternHackingState.BREACHING
        } else if (hackingState == PatternHackingState.BREACHING) {
            timeLeftSeconds = maxTimeSeconds
            while (timeLeftSeconds > 0 && hackingState == PatternHackingState.BREACHING) {
                delay(1000)
                timeLeftSeconds--
            }
            if (timeLeftSeconds <= 0 && hackingState == PatternHackingState.BREACHING) {
                addLog("🚨 TIME EXPIRED! Buffer overflow detected.")
                hackingState = PatternHackingState.FAILED
                soundManager.playHackingErrorSound()
                onBreachFailed()
            }
        }
    }

    // Handle node tap in pattern grid
    fun onNodeClicked(index: Int) {
        if (hackingState != PatternHackingState.BREACHING) return
        if (selectedGridIndices.contains(index)) return // already tapped

        val nodeTapped = patternNodes[index]
        val expectedTargetNode = targetSequence.getOrNull(currentStepIndex)

        if (expectedTargetNode != null && nodeTapped.id == expectedTargetNode.id) {
            // Correct match in sequence!
            selectedGridIndices.add(index)
            currentStepIndex++
            soundManager.playTerminalKeyPressSound()
            addLog("✔ MATCHED STEP ${currentStepIndex}/${targetSequence.size}: [${nodeTapped.hexCode}]")

            if (currentStepIndex >= targetSequence.size) {
                // Complete pattern match success!
                hackingState = PatternHackingState.SUCCESS
                soundManager.playHackingSuccessSound()
                val reward = 150 + securityLevel * 75
                addLog("🏆 BREACH SUCCESSFUL! Access Granted to Node $nodeName. Bounty: +$reward MB")
                onBreachSuccess(reward)
            }
        } else {
            // Mismatch error!
            flashErrorIndex = index
            attemptsLeft--
            soundManager.playHackingErrorSound()
            addLog("❌ MISMATCH ERROR! [${nodeTapped.hexCode}] is invalid. Attempts remaining: $attemptsLeft")

            if (attemptsLeft <= 0) {
                hackingState = PatternHackingState.FAILED
                addLog("💥 SECURITY LOCKOUT TRIGGERED! Node connection terminated.")
                onBreachFailed()
            } else {
                // Reset progress step for retry
                currentStepIndex = 0
                selectedGridIndices.clear()
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("pattern_matching_mini_game_screen"),
        color = CyberDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CyberGreen.copy(alpha = 0.12f))
                    .border(BorderStroke(1.dp, CyberGreen.copy(alpha = 0.4f)), CutCornerShape(4.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Security Node",
                        tint = CyberAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "NODE BREACH :: $nodeName",
                        color = CyberAmber,
                        fontFamily = TerminalFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Mini Game",
                        tint = CyberPink
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Timer & Security Level Panel
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CyberCardBg.copy(alpha = 0.95f)),
                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f)),
                shape = CutCornerShape(6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SECURITY LEVEL $securityLevel",
                            color = CyberCyan,
                            fontFamily = TerminalFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "ATTEMPTS: $attemptsLeft / 3",
                            color = if (attemptsLeft == 1) CyberPink else CyberGreen,
                            fontFamily = TerminalFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Countdown Progress Bar
                    val timerProgress = (timeLeftSeconds.toFloat() / maxTimeSeconds.toFloat()).coerceIn(0f, 1f)
                    val barColor = when {
                        timerProgress > 0.5f -> CyberGreen
                        timerProgress > 0.25f -> CyberAmber
                        else -> CyberPink
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "DECK BUFFER TIME",
                                color = Color.White.copy(alpha = 0.7f),
                                fontFamily = TerminalFontFamily,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "${timeLeftSeconds}s",
                                color = barColor,
                                fontFamily = TerminalFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        LinearProgressIndicator(
                            progress = { timerProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = barColor,
                            trackColor = CyberDark
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Target Pattern Sequence Bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CyberDark.copy(alpha = 0.8f)),
                border = BorderStroke(1.dp, CyberGreen.copy(alpha = 0.5f)),
                shape = CutCornerShape(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "TARGET SEQUENCE PATTERN",
                        color = CyberGreen,
                        fontFamily = TerminalFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        targetSequence.forEachIndexed { idx, targetNode ->
                            val isMatched = idx < currentStepIndex
                            val isCurrentTarget = idx == currentStepIndex && hackingState == PatternHackingState.BREACHING
                            val isRevealed = hackingState == PatternHackingState.SHOWING_PATTERN

                            val containerColor = when {
                                isMatched -> CyberGreen
                                isCurrentTarget -> CyberCyan
                                isRevealed -> CyberAmber
                                else -> CyberCardBg
                            }

                            val textColor = if (isMatched || isCurrentTarget || isRevealed) CyberDark else CyberGreen.copy(alpha = 0.6f)

                            Box(
                                modifier = Modifier
                                    .clip(CutCornerShape(4.dp))
                                    .background(containerColor)
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            if (isCurrentTarget) CyberCyan else CyberGreen.copy(alpha = 0.4f)
                                        ),
                                        CutCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isRevealed || isMatched || isCurrentTarget) targetNode.hexCode else "??",
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

            Spacer(modifier = Modifier.height(10.dp))

            // 4x4 Interactive Pattern Grid
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .testTag("pattern_mini_game_grid"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(patternNodes) { index, node ->
                        val isSelected = selectedGridIndices.contains(index)
                        val isFlashError = flashErrorIndex == index
                        val isTargetHint = hackingState == PatternHackingState.SHOWING_PATTERN && node.isTargetSequenceItem

                        val nodeBg = when {
                            isFlashError -> CyberPink
                            isSelected -> CyberGreen
                            isTargetHint -> CyberAmber
                            else -> CyberCardBg.copy(alpha = 0.9f)
                        }

                        val borderColor = when {
                            isFlashError -> CyberPink
                            isSelected -> CyberGreen
                            isTargetHint -> CyberAmber
                            else -> CyberCyan.copy(alpha = 0.3f)
                        }

                        val textColor = when {
                            isFlashError -> Color.White
                            isSelected -> CyberDark
                            isTargetHint -> CyberDark
                            else -> CyberGreen
                        }

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(CutCornerShape(6.dp))
                                .background(nodeBg)
                                .border(BorderStroke(1.5.dp, borderColor), CutCornerShape(6.dp))
                                .clickable { onNodeClicked(index) }
                                .testTag("pattern_grid_node_$index"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = node.hexCode,
                                    color = textColor,
                                    fontFamily = TerminalFontFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Matched",
                                        tint = CyberDark,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Overlay Banner for Victory / Defeat State
                androidx.compose.animation.AnimatedVisibility(
                    visible = hackingState == PatternHackingState.SUCCESS || hackingState == PatternHackingState.FAILED,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    val isVictory = hackingState == PatternHackingState.SUCCESS
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(16.dp),
                        color = CyberDark,
                        shape = CutCornerShape(12.dp),
                        border = BorderStroke(2.dp, if (isVictory) CyberGreen else CyberPink)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (isVictory) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = "Result Status",
                                tint = if (isVictory) CyberGreen else CyberPink,
                                modifier = Modifier.size(40.dp)
                            )

                            Text(
                                text = if (isVictory) "SYSTEM COMPROMISED!" else "SECURITY LOCKOUT!",
                                color = if (isVictory) CyberGreen else CyberPink,
                                fontFamily = TerminalFontFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                text = if (isVictory)
                                    "Pattern sequence matched successfully. Neural link established!"
                                else
                                    "Pattern match failed or deck buffer expired. Black-ICE trace triggered.",
                                color = Color.White.copy(alpha = 0.8f),
                                fontFamily = TerminalFontFamily,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )

                            Button(
                                onClick = {
                                    if (isVictory) onClose() else {
                                        attemptsLeft = 3
                                        selectedGridIndices.clear()
                                        currentStepIndex = 0
                                        hackingState = PatternHackingState.READY
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isVictory) CyberGreen else CyberPink,
                                    contentColor = CyberDark
                                ),
                                shape = CutCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (isVictory) "CONTINUE TERMINAL" else "RETRY PATTERN",
                                    fontFamily = TerminalFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Live Hack Log Console
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp),
                color = CyberDark.copy(alpha = 0.9f),
                border = BorderStroke(1.dp, CyberGreen.copy(alpha = 0.3f)),
                shape = CutCornerShape(4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(6.dp)
                ) {
                    hackLogs.take(3).forEach { log ->
                        Text(
                            text = "> $log",
                            color = CyberGreen.copy(alpha = 0.85f),
                            fontFamily = TerminalFontFamily,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
