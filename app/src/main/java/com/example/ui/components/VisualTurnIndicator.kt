package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.CombatTurn
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberBorder
import com.example.ui.theme.CyberBrightGreen
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberPink

/**
 * VisualTurnIndicator:
 * Dynamically highlights whether it is the player's turn or the enemy's turn in the combat UI.
 * Features animated glowing badges, directional indicator chevrons, and active phase status text.
 */
@Composable
fun VisualTurnIndicator(
    combatTurn: CombatTurn,
    isCombatInputEnabled: Boolean,
    bannerMessage: String? = null,
    combatRound: Int = 1,
    compactMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isPlayerActive = (combatTurn == CombatTurn.PLAYER && isCombatInputEnabled) && bannerMessage == null
    val isEnemyActive = (combatTurn == CombatTurn.ENEMY || !isCombatInputEnabled) && bannerMessage == null

    // Infinite pulsing animation for active turn elements
    val infiniteTransition = rememberInfiniteTransition(label = "TurnPulseTransition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Animated colors based on active side
    val activeBorderColor by animateColorAsState(
        targetValue = when {
            bannerMessage != null -> CyberAmber
            isPlayerActive -> CyberCyan
            isEnemyActive -> CyberPink
            else -> CyberBorder
        },
        animationSpec = tween(400),
        label = "activeBorderColor"
    )

    val containerBgColor by animateColorAsState(
        targetValue = when {
            bannerMessage != null -> Color(0xFF261400)
            isPlayerActive -> Color(0xFF041C24)
            isEnemyActive -> Color(0xFF240414)
            else -> Color(0xFF0A0F1D)
        },
        animationSpec = tween(400),
        label = "containerBgColor"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = containerBgColor),
        border = BorderStroke(1.5.dp, activeBorderColor.copy(alpha = if (bannerMessage != null) 1.0f else pulseAlpha)),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("visual_turn_indicator")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (compactMode) 6.dp else 8.dp, vertical = if (compactMode) 4.dp else 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // --- PLAYER TURN BADGE ---
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isPlayerActive) CyberCyan.copy(alpha = 0.22f) else Color(0xFF0F172A).copy(alpha = 0.6f)
                        )
                        .border(
                            width = if (isPlayerActive) 1.5.dp else 0.5.dp,
                            color = if (isPlayerActive) CyberCyan.copy(alpha = pulseAlpha) else CyberBorder.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = if (compactMode) 4.dp else 6.dp, vertical = if (compactMode) 3.dp else 5.dp)
                        .testTag("turn_indicator_player")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Pulsing status dot
                        Box(
                            modifier = Modifier
                                .size(if (compactMode) 7.dp else 9.dp)
                                .scale(if (isPlayerActive) pulseScale else 1.0f)
                                .clip(CircleShape)
                                .background(
                                    if (isPlayerActive) CyberCyan else Color.Gray.copy(alpha = 0.4f)
                                )
                        )

                        Column {
                            Text(
                                text = if (compactMode) "PLAYER" else "👤 PLAYER TURN",
                                color = if (isPlayerActive) CyberCyan else Color.Gray,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isPlayerActive) FontWeight.Bold else FontWeight.Normal,
                                fontSize = if (compactMode) 8.5.sp else 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!compactMode) {
                                Text(
                                    text = if (isPlayerActive) "INPUT READY" else "STANDBY",
                                    color = if (isPlayerActive) CyberBrightGreen else Color.DarkGray,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // --- CENTER STATUS / ARROWS ---
                Box(
                    modifier = Modifier
                        .padding(horizontal = if (compactMode) 4.dp else 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = when {
                            bannerMessage != null -> bannerMessage
                            isPlayerActive -> if (compactMode) "R$combatRound ◀" else "RND $combatRound ◀ YOUR TURN"
                            isEnemyActive -> if (compactMode) "▶ R$combatRound" else "HOSTILE ▶ RND $combatRound"
                            else -> "⚡ BUSY"
                        },
                        transitionSpec = {
                            (slideInVertically { height -> height } + fadeIn()) togetherWith
                                    (slideOutVertically { height -> -height } + fadeOut())
                        },
                        label = "TurnStatusText"
                    ) { statusMsg ->
                        Text(
                            text = statusMsg,
                            color = when {
                                bannerMessage != null -> CyberAmber
                                isPlayerActive -> CyberCyan
                                isEnemyActive -> CyberPink
                                else -> CyberAmber
                            },
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (compactMode) 8.sp else 9.5.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // --- ENEMY TURN BADGE ---
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isEnemyActive) CyberPink.copy(alpha = 0.22f) else Color(0xFF0F172A).copy(alpha = 0.6f)
                        )
                        .border(
                            width = if (isEnemyActive) 1.5.dp else 0.5.dp,
                            color = if (isEnemyActive) CyberPink.copy(alpha = pulseAlpha) else CyberBorder.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = if (compactMode) 4.dp else 6.dp, vertical = if (compactMode) 3.dp else 5.dp)
                        .testTag("turn_indicator_enemy")
                ) {
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (compactMode) "HOSTILE" else "👾 HOSTILE TURN",
                                color = if (isEnemyActive) CyberPink else Color.Gray,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isEnemyActive) FontWeight.Bold else FontWeight.Normal,
                                fontSize = if (compactMode) 8.5.sp else 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!compactMode) {
                                Text(
                                    text = if (isEnemyActive) "EXECUTING" else "WAITING",
                                    color = if (isEnemyActive) CyberPink else Color.DarkGray,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Pulsing status dot
                        Box(
                            modifier = Modifier
                                .size(if (compactMode) 7.dp else 9.dp)
                                .scale(if (isEnemyActive) pulseScale else 1.0f)
                                .clip(CircleShape)
                                .background(
                                    if (isEnemyActive) CyberPink else Color.Gray.copy(alpha = 0.4f)
                                )
                        )
                    }
                }
            }

            // --- ANIMATED BOTTOM TURN LASER BAR ---
            Spacer(modifier = Modifier.height(3.dp))
            val gradientBrush = when {
                isPlayerActive -> Brush.horizontalGradient(
                    colors = listOf(CyberCyan, CyberBrightGreen, Color.Transparent)
                )
                isEnemyActive -> Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, CyberPink, Color(0xFFFF0000))
                )
                else -> Brush.horizontalGradient(
                    colors = listOf(CyberAmber, CyberAmber)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(gradientBrush)
            )
        }
    }
}
