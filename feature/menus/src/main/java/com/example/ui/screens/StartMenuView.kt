package com.example.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.example.ui.GameViewModel
import com.example.ui.theme.*

// ----------------------------------------------------
// Sub-Composable: Start/Pause Menu Screen
// ----------------------------------------------------
@Composable
fun StartMenuView(
    viewModel: GameViewModel,
    hasSavedGame: Boolean,
    isActiveRun: Boolean,
    onStartNewRun: () -> Unit,
    onLoadGame: () -> Unit,
    onSaveGame: () -> Unit,
    onResumeGame: () -> Unit,
    onLeaderboardClick: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Aesthetic ASCII title card for Netcrawler
        Text(
            text = """
 _  _  ____ ____  ___  ____   __   _  _  __    ____ ____ 
( \( )(  __)(_  _)/ __)(  _ \ / _\ ( \/ )(  )  (  __)(  _ \
 )  (  ) _)   )( ( (__  )   //    \/ \/ \/ (_/\ ) _)  )   /
(_)\_)(____) (__) \___)(_)\_)\_/\_/\_/\_/\____/(____)(_)\_)
            """.trimIndent(),
            color = CyberCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "SYSTEM ARCHITECTURE TERMINAL V8.91 //",
            color = CyberGreen,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = CyberCardBg.copy(alpha = 0.85f)),
            border = BorderStroke(1.dp, CyberBorder),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isActiveRun) {
                    // Resume Game Button
                    Button(
                        onClick = onResumeGame,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, CyberCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_resume_uplink")
                    ) {
                        Text(
                            text = "⚡ RESUME ACTIVE UPLINK",
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    // Save Game Button
                    Button(
                        onClick = onSaveGame,
                        colors = ButtonDefaults.buttonColors(containerColor = CyberGreen.copy(alpha = 0.15f)),
                        border = BorderStroke(1.dp, CyberGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_save_game")
                    ) {
                        Text(
                            text = "💾 SAVE COGNITIVE STATE",
                            color = CyberGreen,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // New Run Button
                Button(
                    onClick = onStartNewRun,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberAmber.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, CyberAmber),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_new_run")
                ) {
                    Text(
                        text = "🛰️ START NEW UPLINK",
                        color = CyberAmber,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                // Load Game Button
                Button(
                    onClick = onLoadGame,
                    enabled = hasSavedGame,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (hasSavedGame) CyberCyan.copy(alpha = 0.15f) else Color.Transparent,
                        disabledContainerColor = Color.Transparent
                    ),
                    border = BorderStroke(1.dp, if (hasSavedGame) CyberCyan else CyberBorder.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_load_game")
                ) {
                    Text(
                        text = if (hasSavedGame) "📶 LOAD SECURE POINT" else "🔒 NO RESTORE POINT FOUND",
                        color = if (hasSavedGame) CyberCyan else CyberMutedText,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                // Leaderboard Button
                Button(
                    onClick = onLeaderboardClick,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberBorder.copy(alpha = 0.1f)),
                    border = BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_mainframe_leaderboard")
                ) {
                    Text(
                        text = "📊 MAINFRAME HISTORIC RECORDS",
                        color = CyberCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }

                // Data Fragment Vault Button
                val currentFrags = viewModel.uiState.collectAsState().value.dataFragments
                Button(
                    onClick = { viewModel.enterDataVaultScreen() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC).copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, Color(0xFF00FFCC)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_data_fragment_vault")
                ) {
                    Text(
                        text = "💾 DATA VAULT [FRAGMENTS: $currentFrags]",
                        color = Color(0xFF00FFCC),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                // SVDAG World Builder Button
                Button(
                    onClick = { viewModel.enterSvdagWorldInspector() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7).copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, Color(0xFFA855F7)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_svdag_world_builder")
                ) {
                    Text(
                        text = "🧊 SVDAG WORLD BUILDER (128³ VOXELS)",
                        color = Color(0xFFA855F7),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                // System Exit Button
                Button(
                    onClick = {
                        context.findActivity()?.finish() ?: kotlin.system.exitProcess(0)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberPink.copy(alpha = 0.1f)),
                    border = BorderStroke(1.dp, CyberPink.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_system_exit")
                ) {
                    Text(
                        text = "❌ TERMINATE SESSION & EXIT",
                        color = CyberPink,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Footer aesthetic notes
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "CRITICAL CHIP INTEGRITY CHANNELS SECURED //",
            color = CyberBorder,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            textAlign = TextAlign.Center
        )
    }
}

// Helper to extract Activity from Context in Compose
fun android.content.Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}
