package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.QueuedCombatActionType
import com.example.ui.theme.*

/**
 * KOTOR-style real-time-with-pause combat HUD: a round clock with a pause
 * toggle (the breach-protocol minigame freezes it), a 4-slot action queue and
 * queue controls. Rendering is stateless; all pacing logic lives in
 * CombatClock / CombatClockController.
 */
@Composable
fun CombatRoundClockHud(
    millisLeft: Long,
    roundMillis: Long,
    isPaused: Boolean,
    isFrozen: Boolean,
    queue: List<String>,
    queueCapacity: Int,
    isInputEnabled: Boolean,
    onTogglePause: () -> Unit,
    onQueue: (QueuedCombatActionType) -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeRound = if (roundMillis <= 0L) 1L else roundMillis
    val fraction = (millisLeft.coerceIn(0L, safeRound).toFloat() / safeRound.toFloat()).coerceIn(0f, 1f)
    val barColor = when {
        isFrozen -> CyberPink
        isPaused -> CyberAmber
        else -> CyberCyan
    }
    val clockLabel = when {
        isFrozen -> "FROZEN // BREACH"
        isPaused -> "PAUSED"
        else -> String.format("%.1fs", millisLeft.coerceAtLeast(0L) / 1000.0)
    }
    val queueEnabled = isInputEnabled && !isFrozen

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RTWP ROUND CLOCK",
                color = CyberCyan,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 7.sp
            )
            Text(
                text = clockLabel,
                color = barColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 8.sp
            )
            Button(
                onClick = onTogglePause,
                enabled = !isFrozen,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPaused) Color(0xFF059669) else Color(0xFF0F172A),
                    disabledContainerColor = CyberDark
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                modifier = Modifier
                    .heightIn(min = 30.dp)
                    .testTag("btn_combat_pause")
            ) {
                Text(
                    text = if (isPaused) "RESUME" else "PAUSE",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(CyberDark, RoundedCornerShape(3.dp))
                .border(1.dp, barColor, RoundedCornerShape(3.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .background(barColor, RoundedCornerShape(3.dp))
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Action queue slots (depth 4, replace-last when full)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(queueCapacity) { slot ->
                val label = queue.getOrNull(slot)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (label != null) CyberMutedGreen else CyberDark,
                            RoundedCornerShape(4.dp)
                        )
                        .border(
                            1.dp,
                            if (label != null) CyberCyan else CyberBorder,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 2.dp, vertical = 3.dp)
                        .testTag("combat_queue_slot_$slot")
                ) {
                    Text(
                        text = label ?: "[ --- ]",
                        color = if (label != null) CyberCyan else CyberMutedText,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 7.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Queue controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = { onQueue(QueuedCombatActionType.STRIKE) },
                enabled = queueEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E1B4B),
                    disabledContainerColor = CyberDark
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                modifier = Modifier
                    .weight(1.3f)
                    .heightIn(min = 30.dp)
                    .testTag("btn_queue_strike")
            ) {
                Text(
                    text = "Q STRIKE",
                    color = CyberPink,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 7.5.sp
                )
            }
            Button(
                onClick = { onQueue(QueuedCombatActionType.DEFEND) },
                enabled = queueEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF064E3B),
                    disabledContainerColor = CyberDark
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                modifier = Modifier
                    .weight(1.3f)
                    .heightIn(min = 30.dp)
                    .testTag("btn_queue_defend")
            ) {
                Text(
                    text = "Q FORTIFY",
                    color = CyberBrightGreen,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 7.5.sp
                )
            }
            Button(
                onClick = { onQueue(QueuedCombatActionType.SCAN) },
                enabled = queueEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF451A03),
                    disabledContainerColor = CyberDark
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                modifier = Modifier
                    .weight(1.3f)
                    .heightIn(min = 30.dp)
                    .testTag("btn_queue_scan")
            ) {
                Text(
                    text = "Q SCAN",
                    color = CyberAmber,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 7.5.sp
                )
            }
            Button(
                onClick = onClearQueue,
                enabled = queueEnabled && queue.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF0F172A),
                    disabledContainerColor = CyberDark
                ),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                modifier = Modifier
                    .weight(0.8f)
                    .heightIn(min = 30.dp)
                    .testTag("btn_queue_clear")
            ) {
                Text(
                    text = "CLR Q",
                    color = CyberBorder,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 7.5.sp
                )
            }
        }
    }
}
