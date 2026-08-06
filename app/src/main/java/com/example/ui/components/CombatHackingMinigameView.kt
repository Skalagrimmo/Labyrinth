package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.CombatHackingPatternState
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberBrightGreen
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberDark
import com.example.ui.theme.CyberPink

/**
 * CombatHackingMinigameView:
 * A terminal-based pattern matching minigame component triggered during combat hacking actions.
 * Players match a required target sequence of hex codes against a time limit to compile an exploit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CombatHackingMinigameView(
    hackState: CombatHackingPatternState,
    onSelectSymbol: (String) -> Unit,
    onClearBuffer: () -> Unit,
    onAbort: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeRatio = (hackState.timeRemainingSeconds.toFloat() / hackState.maxTimeSeconds.toFloat()).coerceIn(0f, 1f)
    
    val timerColor by animateColorAsState(
        targetValue = when {
            hackState.timeRemainingSeconds <= 3 -> CyberPink
            hackState.timeRemainingSeconds <= 6 -> CyberAmber
            else -> CyberCyan
        },
        animationSpec = tween(300),
        label = "timerColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "HackPulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF04101A)),
        border = BorderStroke(1.5.dp, timerColor),
        shape = CutCornerShape(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("combat_hacking_minigame_view")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- HEADER TITLE & TIMER ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "⚡ BREACH PROTOCOL",
                        color = CyberCyan,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = ":: PATTERN OVERRIDE",
                        color = CyberAmber,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(timerColor.copy(alpha = 0.2f))
                        .border(1.dp, timerColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "⏱️ ${hackState.timeRemainingSeconds}s",
                        color = timerColor,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.scale(if (hackState.timeRemainingSeconds <= 3) pulseScale else 1.0f)
                    )
                }
            }

            // --- TIMER PROGRESS BAR ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF1E293B))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(timeRatio)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(timerColor)
                )
            }

            // --- TARGET PATTERN SEQUENCE DISPLAY ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CutCornerShape(6.dp))
                    .background(Color(0xFF020912))
                    .border(0.5.dp, CyberBorder.copy(alpha = 0.4f), CutCornerShape(6.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TARGET SEQUENCE:",
                        color = CyberBrightGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "EXPLOIT POTENTIAL: ${hackState.potentialDamage} DMG",
                        color = CyberAmber,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.5.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    hackState.targetPattern.forEachIndexed { index, symbol ->
                        val isMatched = index < hackState.userSequence.size && hackState.userSequence[index] == symbol
                        Box(
                            modifier = Modifier
                                .clip(CutCornerShape(4.dp))
                                .background(if (isMatched) CyberBrightGreen.copy(alpha = 0.25f) else Color(0xFF0F172A))
                                .border(
                                    width = if (isMatched) 1.5.dp else 0.5.dp,
                                    color = if (isMatched) CyberBrightGreen else CyberBorder.copy(alpha = 0.5f),
                                    shape = CutCornerShape(4.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isMatched) "✓ $symbol" else symbol,
                                color = if (isMatched) CyberBrightGreen else Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // --- INPUT BUFFER DISPLAY ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CutCornerShape(6.dp))
                    .background(Color(0xFF08121E))
                    .border(0.5.dp, CyberCyan.copy(alpha = 0.4f), CutCornerShape(6.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "INPUT BUFFER:",
                        color = CyberCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ATTEMPTS: ${hackState.attemptsRemaining}/${hackState.maxAttempts}",
                        color = if (hackState.attemptsRemaining <= 1) CyberPink else CyberAmber,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val targetSize = hackState.targetPattern.size
                    for (i in 0 until targetSize) {
                        val symbolInSlot = hackState.userSequence.getOrNull(i)
                        Box(
                            modifier = Modifier
                                .clip(CutCornerShape(4.dp))
                                .background(
                                    if (symbolInSlot != null) CyberCyan.copy(alpha = 0.2f) else Color.Black
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (symbolInSlot != null) CyberCyan else Color.DarkGray,
                                    shape = CutCornerShape(4.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = symbolInSlot ?: " _ ",
                                color = if (symbolInSlot != null) CyberCyan else Color.Gray,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // --- HEX CODE SELECTION KEYPAD ---
            Text(
                text = "SELECT PATTERN CODES:",
                color = Color.LightGray,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                maxItemsInEachRow = 4
            ) {
                hackState.availablePool.forEach { symbol ->
                    Box(
                        modifier = Modifier
                            .clip(CutCornerShape(4.dp))
                            .background(Color(0xFF0A1B2A))
                            .border(BorderStroke(1.dp, CyberCyan), CutCornerShape(4.dp))
                            .clickable { onSelectSymbol(symbol) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("btn_hack_hex_$symbol"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = symbol,
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // --- ACTION BUTTONS (CLEAR / ABORT) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onClearBuffer,
                    border = BorderStroke(1.dp, CyberAmber),
                    shape = CutCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("btn_hack_clear")
                ) {
                    Text(
                        text = "⌫ CLEAR",
                        color = CyberAmber,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onAbort,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPink.copy(alpha = 0.3f)),
                    border = BorderStroke(1.dp, CyberPink),
                    shape = CutCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("btn_hack_abort")
                ) {
                    Text(
                        text = "❌ ABORT EXPLOIT",
                        color = CyberPink,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
