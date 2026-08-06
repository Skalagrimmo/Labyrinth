package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberCardBg
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberDark
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberPink
import com.example.ui.theme.TerminalFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

enum class LogLevel {
    INFO, SUCCESS, WARN, ERROR, COMMAND
}

data class MatrixLogEntry(
    val id: Long = System.currentTimeMillis() + Random.nextLong(1000),
    val timestamp: String = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date()),
    val level: LogLevel,
    val message: String
)

@Composable
fun MatrixHackingTerminalScreen(
    onExit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val soundManager = remember(context) { com.example.audio.CyberSoundEffectsManager.getInstance(context) }

    var commandInput by remember { mutableStateOf("") }

    // Status state
    var traceLevel by remember { mutableFloatStateOf(0.12f) }
    var iceIntegrity by remember { mutableFloatStateOf(0.85f) }
    var ramUsedGb by remember { mutableIntStateOf(6) }
    val totalRamGb = 16
    var nodeStatus by remember { mutableStateOf("CONNECTED // ENCRYPTED") }
    var isPatternMiniGameActive by remember { mutableStateOf(false) }
    var initialSuiteMode by remember { mutableStateOf(HackingMinigameMode.HEX_BREACH) }

    val logs = remember {
        mutableStateListOf(
            MatrixLogEntry(level = LogLevel.INFO, message = "INITIALIZING MATRIX NEURAL GATEWAY [v4.19.0-NET]..."),
            MatrixLogEntry(level = LogLevel.SUCCESS, message = "PROXY CHAIN ESTABLISHED: 104.28.14.9 -> 185.220.101.4 -> NODE_88"),
            MatrixLogEntry(level = LogLevel.INFO, message = "TARGET FIREWALL: NEURAL_ICE_V3 (ACTIVE)"),
            MatrixLogEntry(level = LogLevel.WARN, message = "TRACE LEVEL AT 12%. EXCEEDING 100% WILL TRIGGER DECK PURGE."),
            MatrixLogEntry(level = LogLevel.INFO, message = "Type 'help' to view available system terminal commands.")
        )
    }

    // Auto-scroll when log count increases
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    fun addLog(msg: String, level: LogLevel = LogLevel.INFO) {
        logs.add(MatrixLogEntry(level = level, message = msg))
    }

    fun executeCommand(cmdRaw: String) {
        val cmd = cmdRaw.trim()
        if (cmd.isEmpty()) return

        soundManager.playTerminalCommandSound()
        addLog("> $cmd", LogLevel.COMMAND)
        commandInput = ""

        when (cmd.lowercase()) {
            "help" -> {
                addLog("=== MATRIX HACKING TERMINAL COMMAND MANUAL ===", LogLevel.INFO)
                addLog("  scan       : Scan target node open ports & active ICE", LogLevel.INFO)
                addLog("  hack       : Launch pattern-matching mini-game to breach node", LogLevel.INFO)
                addLog("  bypass     : Inject counter-ICE algorithm to lower firewall", LogLevel.INFO)
                addLog("  bruteforce : Attempt brute-force key generation", LogLevel.INFO)
                addLog("  inject     : Buffer overflow attack to dump encryption keys", LogLevel.INFO)
                addLog("  status     : Display full diagnostic status of target node", LogLevel.INFO)
                addLog("  clear      : Purge scrolling terminal text log", LogLevel.INFO)
                addLog("  exit       : Sever neural link and exit terminal session", LogLevel.INFO)
            }
            "scan" -> {
                coroutineScope.launch {
                    addLog("INITIATING PORT SCAN ON MATRIX NODE...", LogLevel.INFO)
                    delay(400)
                    addLog("PORT 22/SSH  : OPEN (VULNERABLE)", LogLevel.SUCCESS)
                    delay(300)
                    addLog("PORT 80/HTTP : FILTERED BY ICE_WALL", LogLevel.WARN)
                    delay(300)
                    addLog("PORT 8080/RPC: LISTENING [ENCRYPTED]", LogLevel.INFO)
                    addLog("SCAN COMPLETE. 1 EXPLOIT VECTOR IDENTIFIED.", LogLevel.SUCCESS)
                }
            }
            "bypass" -> {
                coroutineScope.launch {
                    addLog("DEPLOYING COUNTER-ICE BYPASS PAYLOAD...", LogLevel.INFO)
                    delay(600)
                    iceIntegrity = (iceIntegrity - 0.25f).coerceAtLeast(0f)
                    traceLevel = (traceLevel + 0.18f).coerceAtMost(1.0f)
                    if (iceIntegrity <= 0f) {
                        addLog("CRITICAL SUCCESS! NEURAL ICE FIREWALL DISMANTLED!", LogLevel.SUCCESS)
                        nodeStatus = "COMPROMISED // ROOT ACCESS"
                    } else {
                        addLog("ICE INTEGRITY REDUCED TO ${(iceIntegrity * 100).toInt()}%.", LogLevel.WARN)
                        addLog("TRACE DETECTION ALERT: TRACE LEVEL AT ${(traceLevel * 100).toInt()}%.", LogLevel.WARN)
                    }
                }
            }
            "bruteforce" -> {
                coroutineScope.launch {
                    addLog("STARTING DICTIONARY BRUTE-FORCE AGENT...", LogLevel.INFO)
                    repeat(3) { i ->
                        delay(250)
                        val hash = "0x" + Random.nextInt(0x100000, 0xFFFFFF).toString(16).uppercase()
                        addLog("HASH TRY #${i + 1}: $hash -> REJECTED", LogLevel.WARN)
                    }
                    delay(300)
                    traceLevel = (traceLevel + 0.15f).coerceAtMost(1.0f)
                    ramUsedGb = (ramUsedGb + 2).coerceAtMost(totalRamGb)
                    addLog("KEY FOUND: 0x99FF-ALPHA. SUB-SYSTEM ACCESS GRANTED.", LogLevel.SUCCESS)
                }
            }
            "inject" -> {
                coroutineScope.launch {
                    addLog("ALLOCATING RAM BUFFER FOR PAYLOAD INJECTION...", LogLevel.INFO)
                    delay(500)
                    ramUsedGb = (ramUsedGb + 4).coerceAtMost(totalRamGb)
                    iceIntegrity = (iceIntegrity - 0.35f).coerceAtLeast(0f)
                    addLog("PAYLOAD EXECUTED. DUMPED 4096 BYTES OF ENCRYPTED MEMORY.", LogLevel.SUCCESS)
                }
            }
            "hack", "pattern" -> {
                addLog("LAUNCHING HIGH-SECURITY HEX MATRIX BREACH MINI-GAME...", LogLevel.INFO)
                initialSuiteMode = HackingMinigameMode.HEX_BREACH
                isPatternMiniGameActive = true
            }
            "tuner", "frequency" -> {
                addLog("LAUNCHING FREQUENCY OSCILLOSCOPE SIGNAL TUNER...", LogLevel.INFO)
                initialSuiteMode = HackingMinigameMode.SIGNAL_TUNER
                isPatternMiniGameActive = true
            }
            "router", "circuit" -> {
                addLog("LAUNCHING CIRCUIT CONDUIT RELAY ROUTER...", LogLevel.INFO)
                initialSuiteMode = HackingMinigameMode.CIRCUIT_ROUTER
                isPatternMiniGameActive = true
            }
            "status" -> {
                addLog("=== SYSTEM DIAGNOSTIC SUMMARY ===", LogLevel.INFO)
                addLog("TARGET NODE : MATRIX_GATEWAY_77", LogLevel.INFO)
                addLog("ICE SHIELD  : ${(iceIntegrity * 100).toInt()}% INTEGRITY", LogLevel.INFO)
                addLog("TRACE RISK  : ${(traceLevel * 100).toInt()}% DETECTED", LogLevel.INFO)
                addLog("RAM POOL    : $ramUsedGb / $totalRamGb GB", LogLevel.INFO)
                addLog("LINK STATUS : $nodeStatus", LogLevel.INFO)
            }
            "clear" -> {
                logs.clear()
                addLog("TERMINAL TEXT LOG CLEARED.", LogLevel.INFO)
            }
            "exit" -> {
                addLog("DISCONNECTING FROM MATRIX NODE...", LogLevel.WARN)
                onExit()
            }
            else -> {
                addLog("COMMAND NOT RECOGNIZED: '$cmd'. Type 'help' for syntax.", LogLevel.ERROR)
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("matrix_terminal_screen"),
        color = CyberDark
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Matrix Digital Background Animation Canvas
            MatrixRainCanvas(
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // Header Status Bar
                MatrixTerminalHeader(
                    onClose = onExit
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Status Indicators Panel (Trace, ICE, RAM)
                MatrixStatusPanel(
                    traceLevel = traceLevel,
                    iceIntegrity = iceIntegrity,
                    ramUsed = ramUsedGb,
                    ramTotal = totalRamGb,
                    nodeStatus = nodeStatus
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Scrolling System Log Area
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(BorderStroke(1.dp, CyberGreen.copy(alpha = 0.5f)), CutCornerShape(8.dp))
                        .testTag("matrix_log_card"),
                    colors = CardDefaults.cardColors(containerColor = CyberCardBg.copy(alpha = 0.92f)),
                    shape = CutCornerShape(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("matrix_log_list"),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(logs, key = { it.id }) { log ->
                                MatrixLogItem(log = log)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Command Action Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val quickCmds = listOf("help", "hack", "tuner", "router", "scan", "bypass", "inject", "clear")
                    quickCmds.forEach { cmd ->
                        Box(
                            modifier = Modifier
                                .clip(CutCornerShape(4.dp))
                                .background(CyberGreen.copy(alpha = 0.15f))
                                .border(BorderStroke(1.dp, CyberGreen.copy(alpha = 0.6f)), CutCornerShape(4.dp))
                                .clickable { executeCommand(cmd) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("matrix_quick_command_chip_$cmd")
                        ) {
                            Text(
                                text = cmd,
                                color = CyberGreen,
                                fontFamily = TerminalFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Command Input Field
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = commandInput,
                        onValueChange = { commandInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("matrix_command_input"),
                        placeholder = {
                            Text(
                                text = "Enter command (e.g. scan, bypass)...",
                                color = CyberGreen.copy(alpha = 0.4f),
                                fontFamily = TerminalFontFamily,
                                fontSize = 12.sp
                            )
                        },
                        leadingIcon = {
                            Text(
                                text = "root@matrix:~# ",
                                color = CyberCyan,
                                fontFamily = TerminalFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        },
                        textStyle = TextStyle(
                            color = CyberGreen,
                            fontFamily = TerminalFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CyberDark.copy(alpha = 0.85f),
                            unfocusedContainerColor = CyberDark.copy(alpha = 0.85f),
                            focusedBorderColor = CyberGreen,
                            unfocusedBorderColor = CyberGreen.copy(alpha = 0.4f),
                            cursorColor = CyberGreen
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { executeCommand(commandInput) }),
                        shape = CutCornerShape(4.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { executeCommand(commandInput) },
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("matrix_execute_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberGreen,
                            contentColor = CyberDark
                        ),
                        shape = CutCornerShape(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Execute Command",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "EXEC",
                            fontFamily = TerminalFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (isPatternMiniGameActive) {
                CyberHackingMinigameSuite(
                    nodeName = "ARASAKA_CORE_77X",
                    securityLevel = 3,
                    initialMode = initialSuiteMode,
                    onSuccess = { bounty ->
                        isPatternMiniGameActive = false
                        iceIntegrity = 0f
                        nodeStatus = "COMPROMISED // ROOT ACCESS"
                        addLog("CYBER SUITE BREACH SUCCESSFUL! Node decrypted (+ $bounty MB)", LogLevel.SUCCESS)
                    },
                    onFailed = {
                        traceLevel = (traceLevel + 0.25f).coerceAtMost(1.0f)
                        addLog("CYBER SUITE BREACH FAILED! TRACE RISK AT ${(traceLevel * 100).toInt()}%", LogLevel.WARN)
                    },
                    onClose = {
                        isPatternMiniGameActive = false
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun MatrixTerminalHeader(
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CyberGreen.copy(alpha = 0.1f))
            .border(BorderStroke(1.dp, CyberGreen.copy(alpha = 0.3f)))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = "Matrix Terminal Icon",
                tint = CyberGreen,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "MATRIX_NODE :: 192.168.0.77",
                color = CyberGreen,
                fontFamily = TerminalFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Exit Terminal",
                tint = CyberPink
            )
        }
    }
}

@Composable
private fun MatrixStatusPanel(
    traceLevel: Float,
    iceIntegrity: Float,
    ramUsed: Int,
    ramTotal: Int,
    nodeStatus: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CyberCardBg.copy(alpha = 0.9f),
        shape = CutCornerShape(6.dp),
        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Row: Node Status & Signal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (traceLevel > 0.8f) CyberPink else CyberGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "STATUS: $nodeStatus",
                        color = if (traceLevel > 0.8f) CyberPink else CyberCyan,
                        fontFamily = TerminalFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "LATENCY: 12ms",
                    color = Color.White.copy(alpha = 0.7f),
                    fontFamily = TerminalFontFamily,
                    fontSize = 10.sp
                )
            }

            // Progress Indicators Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // TRACE LEVEL
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("matrix_status_trace_indicator")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "TRACE LEVEL",
                            color = CyberAmber,
                            fontFamily = TerminalFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${(traceLevel * 100).toInt()}%",
                            color = if (traceLevel > 0.7f) CyberPink else CyberAmber,
                            fontFamily = TerminalFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    LinearProgressIndicator(
                        progress = { traceLevel },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (traceLevel > 0.7f) CyberPink else CyberAmber,
                        trackColor = CyberDark
                    )
                }

                // ICE FIREWALL
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("matrix_status_ice_indicator")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "ICE SHIELD",
                            color = CyberCyan,
                            fontFamily = TerminalFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${(iceIntegrity * 100).toInt()}%",
                            color = CyberCyan,
                            fontFamily = TerminalFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    LinearProgressIndicator(
                        progress = { iceIntegrity },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = CyberCyan,
                        trackColor = CyberDark
                    )
                }

                // RAM POOL
                Column(
                    modifier = Modifier.weight(0.8f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "RAM BUFFER",
                            color = CyberGreen,
                            fontFamily = TerminalFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$ramUsed/$ramTotal G",
                            color = CyberGreen,
                            fontFamily = TerminalFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    LinearProgressIndicator(
                        progress = { ramUsed.toFloat() / ramTotal.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = CyberGreen,
                        trackColor = CyberDark
                    )
                }
            }
        }
    }
}

@Composable
private fun MatrixLogItem(
    log: MatrixLogEntry
) {
    val (levelTag, levelColor) = when (log.level) {
        LogLevel.INFO -> "SYS" to CyberCyan
        LogLevel.SUCCESS -> "OK!" to CyberGreen
        LogLevel.WARN -> "WARN" to CyberAmber
        LogLevel.ERROR -> "FAIL" to CyberPink
        LogLevel.COMMAND -> "CMD" to CyberGreen
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "[${log.timestamp}] ",
            color = Color.White.copy(alpha = 0.4f),
            fontFamily = TerminalFontFamily,
            fontSize = 11.sp
        )

        Text(
            text = "[$levelTag] ",
            color = levelColor,
            fontFamily = TerminalFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = log.message,
            color = if (log.level == LogLevel.COMMAND) CyberGreen else Color.White.copy(alpha = 0.9f),
            fontFamily = TerminalFontFamily,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun MatrixRainCanvas(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "matrix_rain")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rain_offset"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        // Draw Matrix green scan grid / falling code stream representation
        val colWidthPx = 40.dp.toPx()
        val columns = (width / colWidthPx).toInt().coerceAtLeast(1)

        for (i in 0 until columns) {
            val x = i * colWidthPx + 10.dp.toPx()
            val speedFactor = ((i * 37) % 5 + 1) / 3f
            val startY = (offsetY * speedFactor + i * 80) % (height + 200) - 100

            // Draw line trail
            drawLine(
                color = CyberGreen.copy(alpha = 0.12f),
                start = Offset(x, startY - 120f),
                end = Offset(x, startY),
                strokeWidth = 1.5f
            )

            // Leading bright pixel dot
            drawCircle(
                color = CyberGreen.copy(alpha = 0.4f),
                radius = 3f,
                center = Offset(x, startY)
            )
        }
    }
}
