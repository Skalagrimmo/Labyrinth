package com.example.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.example.data.*
import com.example.ui.theme.*

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
