package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GameState
import com.example.data.LogMessage
import com.example.data.LogType
import com.example.ui.GameViewModel
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberBrightGreen
import com.example.ui.theme.CyberCardBg
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberDark
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberMutedText
import com.example.ui.theme.CyberPink
import kotlin.math.PI
import kotlin.math.sin

/**
 * Cyber Vital Status HUD bar providing animated real-time metrics for:
 * 1. Current Cyber-Health & Shield Matrix
 * 2. Signal Integrity & Oscilloscope Connection Waveform
 * 3. Time-To-Breach / Enemy Compile Attack Charge / Active Firewall Timer
 */
@Composable
fun CyberVitalStatusHud(
    uiState: GameViewModel.GameUiState,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CyberDark.copy(alpha = 0.92f)),
        border = BorderStroke(1.dp, CyberBorder.copy(alpha = 0.6f)),
        shape = CutCornerShape(bottomEnd = 10.dp, bottomStart = 10.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("cyber_vital_status_hud")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Widget 1: Cyber-Health & Shield Arc / Pulse
            CyberHealthWidget(
                currentHealth = uiState.integrity,
                maxHealth = uiState.maxIntegrity,
                currentShield = uiState.playerShield,
                maxShield = uiState.playerMaxShield,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Widget 2: Signal Integrity & Waveform Oscilloscope
            SignalIntegrityWidget(
                activeWeather = uiState.activeWeather,
                currentZone = uiState.currentZone,
                gameState = uiState.gameState,
                modifier = Modifier.weight(1.1f)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Widget 3: Time-To-Breach / Enemy Attack Compile / Firewall Countdown
            TimeToBreachWidget(
                uiState = uiState,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 1. Animated Cyber-Health & Shield Widget
 */
@Composable
fun CyberHealthWidget(
    currentHealth: Int,
    maxHealth: Int,
    currentShield: Int,
    maxShield: Int,
    modifier: Modifier = Modifier
) {
    val animatedHealth by animateIntAsState(
        targetValue = currentHealth,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "animatedHealth"
    )

    val healthRatio = (animatedHealth.toFloat() / maxHealth.coerceAtLeast(1)).coerceIn(0f, 1f)
    val isCritical = healthRatio <= 0.30f

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_health")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = if (isCritical) 0.3f else 0.8f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isCritical) 350 else 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "healthPulse"
    )

    val gaugeColor by animateColorAsState(
        targetValue = when {
            isCritical -> CyberPink
            healthRatio <= 0.6f -> CyberAmber
            else -> CyberGreen
        },
        animationSpec = tween(300),
        label = "gaugeColor"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .background(CyberCardBg, CutCornerShape(4.dp))
            .border(BorderStroke(1.dp, gaugeColor.copy(alpha = 0.4f)), CutCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag("cyber_health_widget")
    ) {
        // Radial / Arc Health Progress Circle with Heartbeat Pulse
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 2.5.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                // Background track
                drawArc(
                    color = Color.DarkGray.copy(alpha = 0.3f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Health Arc
                drawArc(
                    color = gaugeColor.copy(alpha = pulseAlpha),
                    startAngle = 135f,
                    sweepAngle = 270f * healthRatio,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                // Shield Arc overlay (outer thin ring)
                if (maxShield > 0 && currentShield > 0) {
                    val shieldRatio = (currentShield.toFloat() / maxShield).coerceIn(0f, 1f)
                    drawArc(
                        color = CyberCyan.copy(alpha = 0.85f),
                        startAngle = 135f,
                        sweepAngle = 270f * shieldRatio,
                        useCenter = false,
                        topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                        size = Size(diameter - 2.dp.toPx(), diameter - 2.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // Central Health Icon / Percentage
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${(healthRatio * 100).toInt()}%",
                    color = gaugeColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(verticalArrangement = Arrangement.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(gaugeColor.copy(alpha = pulseAlpha), CircleShape)
                )
                Text(
                    text = if (isCritical) "VITAL WARN" else "HEALTH",
                    color = gaugeColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            Text(
                text = "$animatedHealth/$maxHealth HP",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold
            )

            if (maxShield > 0) {
                Text(
                    text = "SHIELD: $currentShield/$maxShield",
                    color = CyberCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 6.5.sp
                )
            }
        }
    }
}

/**
 * 2. Animated Signal Integrity & Oscilloscope Waveform Widget
 */
@Composable
fun SignalIntegrityWidget(
    activeWeather: com.example.data.CyberWeather,
    currentZone: com.example.data.Zone,
    gameState: GameState,
    modifier: Modifier = Modifier
) {
    // Base signal stability based on zone & weather
    val targetStability = when {
        gameState != GameState.EXPLORATION -> 72f
        activeWeather == com.example.data.CyberWeather.DATA_STORM -> 45f
        activeWeather == com.example.data.CyberWeather.COLD_SPOT -> 55f
        activeWeather == com.example.data.CyberWeather.FRAGMENTATION -> 65f
        activeWeather == com.example.data.CyberWeather.HOT_NODE -> 80f
        else -> 98.5f
    }

    val animatedStability by animateFloatAsState(
        targetValue = targetStability,
        animationSpec = tween(600),
        label = "animatedStability"
    )

    val signalColor = when {
        animatedStability < 60f -> CyberPink
        animatedStability < 80f -> CyberAmber
        else -> CyberCyan
    }

    val infiniteTransition = rememberInfiniteTransition(label = "wave_anim")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "waveOffset"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .background(CyberCardBg, CutCornerShape(4.dp))
            .border(BorderStroke(1.dp, signalColor.copy(alpha = 0.4f)), CutCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag("signal_integrity_widget")
    ) {
        // Oscilloscope Wave Canvas
        Box(
            modifier = Modifier
                .width(30.dp)
                .height(20.dp)
                .background(CyberDark, RoundedCornerShape(3.dp))
                .border(0.5.dp, signalColor.copy(alpha = 0.5f), RoundedCornerShape(3.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val centerY = h / 2f

                val path = Path()
                val points = 30
                val freq = if (animatedStability < 65f) 4.5f else 2.5f
                val amp = (1f - (animatedStability / 100f)) * (h / 3f) + 2f

                for (i in 0..points) {
                    val progress = i.toFloat() / points
                    val x = progress * w
                    val angle = progress * freq * 2f * PI.toFloat() + waveOffset
                    val y = centerY - (sin(angle) * amp)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                drawPath(
                    path = path,
                    color = signalColor,
                    style = Stroke(width = 1.4f)
                )
            }
        }

        Column(verticalArrangement = Arrangement.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // 5-bar signal strength bars
                Row(
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    val activeBars = (animatedStability / 20f).toInt().coerceIn(1, 5)
                    for (b in 1..5) {
                        val barHeight = (3 + b * 1.5f).dp
                        val barColor = if (b <= activeBars) signalColor else Color.DarkGray
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(barHeight)
                                .background(barColor, RoundedCornerShape(1.dp))
                        )
                    }
                }

                Text(
                    text = "SIGNAL",
                    color = signalColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "%.1f%%".format(animatedStability),
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (animatedStability > 85f) "ENCRYPT" else "JAMMED",
                color = signalColor.copy(alpha = 0.8f),
                fontFamily = FontFamily.Monospace,
                fontSize = 7.5.sp
            )
        }
    }
}

/**
 * 3. Animated Time-To-Breach / Trace Countdown / Enemy Compile Charge Widget
 */
@Composable
fun TimeToBreachWidget(
    uiState: GameViewModel.GameUiState,
    modifier: Modifier = Modifier
) {
    // Determine context breach state:
    // 1. Active combat hack countdown
    // 2. Enemy attack compile charge (0% to 100%)
    // 3. Active firewall remaining seconds
    // 4. Default grid system trace level
    val combatHack = uiState.activeCombatHack
    val enemyCharge = uiState.enemyAttackCharge
    val firewallTime = uiState.activeFirewallTimeLeft

    val isBreachActive = combatHack != null || enemyCharge > 0.3f || firewallTime > 0

    val breachTitle = when {
        combatHack != null -> "HACK TIME"
        enemyCharge > 0.0f -> "ENEMY COMPILE"
        firewallTime > 0 -> "FIREWALL"
        else -> "SYSTEM TRACE"
    }

    val displayValue = when {
        combatHack != null -> "${combatHack.timeRemainingSeconds}s"
        enemyCharge > 0.0f -> "${(enemyCharge * 100).toInt()}%"
        firewallTime > 0 -> "${firewallTime}s"
        else -> "SECURE"
    }

    val progressRatio = when {
        combatHack != null -> (combatHack.timeRemainingSeconds.toFloat() / combatHack.maxTimeSeconds.coerceAtLeast(1)).coerceIn(0f, 1f)
        enemyCharge > 0.0f -> enemyCharge
        firewallTime > 0 -> (firewallTime.toFloat() / 15f).coerceIn(0f, 1f)
        else -> 0.15f
    }

    val isWarning = (combatHack != null && combatHack.timeRemainingSeconds <= 4) || enemyCharge >= 0.75f

    val widgetColor by animateColorAsState(
        targetValue = when {
            isWarning -> CyberPink
            enemyCharge > 0f -> CyberAmber
            firewallTime > 0 -> CyberBrightGreen
            combatHack != null -> CyberCyan
            else -> CyberGreen
        },
        animationSpec = tween(300),
        label = "breachColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "breach_warning")
    val warningPulse by infiniteTransition.animateFloat(
        initialValue = if (isWarning) 0.3f else 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isWarning) 300 else 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "warningPulse"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .background(CyberCardBg, CutCornerShape(4.dp))
            .border(BorderStroke(1.dp, widgetColor.copy(alpha = 0.4f)), CutCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag("time_to_breach_widget")
    ) {
        // Circular Progress Ring / Countdown Arc
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 2.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                // Track
                drawArc(
                    color = Color.DarkGray.copy(alpha = 0.3f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokeWidth)
                )

                // Countdown / Compile Arc
                drawArc(
                    color = widgetColor.copy(alpha = warningPulse),
                    startAngle = -90f,
                    sweepAngle = 360f * progressRatio,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            Icon(
                imageVector = if (isWarning) Icons.Default.Warning else Icons.Default.Lock,
                contentDescription = breachTitle,
                tint = widgetColor.copy(alpha = warningPulse),
                modifier = Modifier.size(12.dp)
            )
        }

        Column(verticalArrangement = Arrangement.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .background(widgetColor.copy(alpha = warningPulse), CircleShape)
                )
                Text(
                    text = breachTitle,
                    color = widgetColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            Text(
                text = displayValue,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = if (isWarning) "BREACH CRITICAL" else "MONITORING",
                color = widgetColor.copy(alpha = 0.8f),
                fontFamily = FontFamily.Monospace,
                fontSize = 7.5.sp
            )
        }
    }
}

/**
 * 4. Animated Compact Cyber Ticker Console Replacement (`AnimatedCyberHudConsole`)
 * Replaces bulky text consoles with a sleek single-line animated ticker and a pop-out HUD log drawer.
 */
@Composable
fun AnimatedCyberHudConsole(
    uiState: GameViewModel.GameUiState,
    onSendCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpandedDrawerOpen by remember { mutableStateOf(false) }
    val latestLog = uiState.logFeed.lastOrNull()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("animated_cyber_hud_console")
    ) {
        // Main Single-Line Ticker Bar
        Card(
            colors = CardDefaults.cardColors(containerColor = CyberDark.copy(alpha = 0.95f)),
            border = BorderStroke(1.dp, CyberBorder.copy(alpha = 0.5f)),
            shape = CutCornerShape(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Ticker Label
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(CyberCyan.copy(alpha = 0.2f))
                            .border(0.5.dp, CyberCyan, RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "LIVE TICKER",
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Animated Slide/Fade for Latest Log Event
                    AnimatedContent(
                        targetState = latestLog,
                        transitionSpec = {
                            (slideInVertically { height -> height } + fadeIn())
                                .togetherWith(slideOutVertically { height -> -height } + fadeOut())
                        },
                        label = "logTickerAnim"
                    ) { log ->
                        if (log != null) {
                            val textColor = when (log.type) {
                                LogType.INFO -> CyberCyan
                                LogType.ALERT -> CyberAmber
                                LogType.SUCCESS -> CyberBrightGreen
                                LogType.ERROR -> CyberPink
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = when (log.type) {
                                        LogType.INFO -> Icons.Default.Info
                                        LogType.ALERT -> Icons.Default.Warning
                                        LogType.SUCCESS -> Icons.Default.CheckCircle
                                        LogType.ERROR -> Icons.Default.Close
                                    },
                                    contentDescription = null,
                                    tint = textColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = log.text,
                                    color = textColor,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Text(
                                text = "SYSTEM NORMAL // ALL CONDUITS STABLE",
                                color = CyberMutedText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Toggle Drawer Button for Raw Terminal / Logs
                Box(
                    modifier = Modifier
                        .clip(CutCornerShape(3.dp))
                        .background(if (isExpandedDrawerOpen) CyberGreen else CyberDark)
                        .border(1.dp, CyberGreen, CutCornerShape(3.dp))
                        .clickable { isExpandedDrawerOpen = !isExpandedDrawerOpen }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("btn_toggle_hud_drawer")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (isExpandedDrawerOpen) "HIDE LOGS" else "EX-LOGS",
                            color = if (isExpandedDrawerOpen) CyberDark else CyberGreen,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = if (isExpandedDrawerOpen) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = "Toggle Logs Drawer",
                            tint = if (isExpandedDrawerOpen) CyberDark else CyberGreen,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        // Expandable HUD Log Drawer & Quick Command Input (Semi-Transparent Overlay)
        AnimatedVisibility(
            visible = isExpandedDrawerOpen,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut()
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val drawerHeight = if (maxHeight < 400.dp) 95.dp else 130.dp
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberDark.copy(alpha = 0.96f)),
                    border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f)),
                    shape = CutCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(drawerHeight)
                        .padding(top = 4.dp)
                ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                ) {
                    val listState = rememberLazyListState()
                    LaunchedEffect(uiState.logFeed.size) {
                        if (uiState.logFeed.isNotEmpty()) {
                            listState.scrollToItem(uiState.logFeed.size - 1)
                        }
                    }

                    // Scrolling Log List
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(uiState.logFeed) { log ->
                            val color = when (log.type) {
                                LogType.INFO -> CyberCyan
                                LogType.ALERT -> CyberAmber
                                LogType.SUCCESS -> CyberBrightGreen
                                LogType.ERROR -> CyberPink
                            }
                            Text(
                                text = "> ${log.text}",
                                color = color,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.5.sp,
                                lineHeight = 10.sp
                            )
                        }
                    }

                    HorizontalDivider(color = CyberBorder.copy(alpha = 0.3f), thickness = 1.dp, modifier = Modifier.padding(vertical = 3.dp))

                    // Compact Command Input Field
                    var textValue by remember { mutableStateOf("") }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "[net@deck]$ ",
                            color = CyberGreen,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )

                        BasicTextField(
                            value = textValue,
                            onValueChange = { textValue = it },
                            textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 8.5.sp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (textValue.isNotBlank()) {
                                    onSendCommand(textValue)
                                    textValue = ""
                                }
                            }),
                            cursorBrush = SolidColor(CyberGreen),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp)
                                .testTag("hud_drawer_command_input"),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (textValue.isEmpty()) {
                                        Text(
                                            text = "enter command (e.g. 'hack', 'help')...",
                                            color = Color.Gray.copy(alpha = 0.5f),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 8.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(CyberGreen.copy(alpha = 0.2f))
                                .border(0.5.dp, CyberGreen, RoundedCornerShape(3.dp))
                                .clickable {
                                    if (textValue.isNotBlank()) {
                                        onSendCommand(textValue)
                                        textValue = ""
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "RUN",
                                color = CyberGreen,
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
}
}
