package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.PuzzleState
import com.example.ui.PuzzleStatus
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberCardBg
import com.example.ui.theme.CyberCrimson
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberDark
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberPink
import com.example.ui.theme.CyberSurfaceVariant
import com.example.ui.theme.TerminalFontFamily

/**
 * Visual Puzzle Mini-Game UI for HackingViewModel.
 * Displays target node sequence matching, active timer, player sequence slots, and selectable tile matrix.
 */
@Composable
fun HackingPuzzleMiniGameView(
    puzzleState: PuzzleState,
    onTokenSelected: (String) -> Unit,
    onAbort: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!puzzleState.isActive) return

    val progress = (puzzleState.timeRemainingSeconds.toFloat() / puzzleState.maxTimeSeconds.coerceAtLeast(1).toFloat())
        .coerceIn(0.0f, 1.0f)

    val timerColor = when {
        progress < 0.25f -> CyberCrimson
        progress < 0.50f -> CyberAmber
        else -> CyberGreen
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .testTag("hacking_puzzle_minigame_card"),
        shape = CutCornerShape(topStart = 16.dp, bottomEnd = 16.dp),
        colors = CardDefaults.cardColors(containerColor = CyberDark),
        border = BorderStroke(2.dp, if (puzzleState.status == PuzzleStatus.FAILED) CyberCrimson else CyberGreen)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "ICE Lock",
                        tint = CyberGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ICE BREACH: ${puzzleState.targetNodeId}",
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = TerminalFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = CyberGreen
                        )
                    )
                }

                IconButton(
                    onClick = onAbort,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("abort_puzzle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Abort Hack",
                        tint = CyberPink
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Timer & Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TIME REMAINING: ${puzzleState.timeRemainingSeconds}s",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = TerminalFontFamily,
                        fontSize = 12.sp,
                        color = timerColor,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "SEC LEVEL: ${puzzleState.securityLevel}",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = TerminalFontFamily,
                        fontSize = 12.sp,
                        color = CyberCyan
                    )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = timerColor,
                trackColor = CyberSurfaceVariant
            )

            // Status / Error Banner
            puzzleState.errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = msg,
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = TerminalFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (puzzleState.status == PuzzleStatus.SUCCESS) CyberGreen else CyberCrimson
                    ),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Target Sequence Display
            Text(
                text = "TARGET SEQUENCE MATRIX",
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = TerminalFontFamily,
                    fontSize = 11.sp,
                    color = CyberCyan
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                puzzleState.targetSequence.forEachIndexed { index, token ->
                    val isMatched = index < puzzleState.playerSequence.size
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isMatched) CyberGreen.copy(alpha = 0.2f) else CyberCardBg,
                                shape = CutCornerShape(4.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isMatched) CyberGreen else CyberCyan,
                                shape = CutCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = token,
                            style = androidx.compose.ui.text.TextStyle(
                                fontFamily = TerminalFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isMatched) CyberGreen else CyberCyan
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Player Sequence Display Slots
            Text(
                text = "USER INPUT BUFFER",
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = TerminalFontFamily,
                    fontSize = 11.sp,
                    color = CyberAmber
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                puzzleState.targetSequence.indices.forEach { index ->
                    val enteredToken = puzzleState.playerSequence.getOrNull(index)
                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 32.dp)
                            .background(
                                color = if (enteredToken != null) CyberAmber.copy(alpha = 0.15f) else CyberDark,
                                shape = CutCornerShape(4.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (enteredToken != null) CyberAmber else CyberSurfaceVariant,
                                shape = CutCornerShape(4.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = enteredToken ?: "--",
                            style = androidx.compose.ui.text.TextStyle(
                                fontFamily = TerminalFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (enteredToken != null) CyberAmber else Color.Gray
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Selectable Token Grid
            Text(
                text = "SELECT NODE TOKENS",
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = TerminalFontFamily,
                    fontSize = 11.sp,
                    color = CyberGreen
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                items(puzzleState.availableNodeTokens) { token ->
                    Box(
                        modifier = Modifier
                            .height(38.dp)
                            .background(CyberCardBg, shape = CutCornerShape(6.dp))
                            .border(1.dp, CyberGreen, shape = CutCornerShape(6.dp))
                            .clickable(enabled = puzzleState.status == PuzzleStatus.IN_PROGRESS) {
                                onTokenSelected(token)
                            }
                            .padding(4.dp)
                            .testTag("puzzle_token_tile_$token"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = token,
                            style = androidx.compose.ui.text.TextStyle(
                                fontFamily = TerminalFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = CyberGreen
                            )
                        )
                    }
                }
            }
        }
    }
}
