package com.example.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.example.data.*
import com.example.ui.GameViewModel
import com.example.ui.theme.*

// ==========================================
// Sub-Composable: Shop Screen
// ==========================================
@Composable
fun UpgradeStoreView(
    uiState: GameViewModel.GameUiState,
    onBuyCyberware: (Cyberware) -> Unit,
    onBuyConsumable: (String, Int) -> Unit,
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
                text = "--- BLACK-MARKET MAINFRAME RE-ROUTE ---",
                color = CyberCyan,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            Text(
                text = "MEM CREDITS: ${uiState.credits} MB",
                color = CyberAmber,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }

        // Main store scroll list
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Hardware Cyberware Modules section
            Text(
                text = "HARDWARE CYBERWARE INTEGRATIONS:",
                color = CyberCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )

            val cyberwares = GameEngine.getStoreCyberware()
            cyberwares.forEach { cyber ->
                val alreadyInstalled = uiState.installedCyberware.any { it.id == cyber.id }
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                    border = BorderStroke(1.dp, if (alreadyInstalled) CyberBorder else CyberCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cyber.name,
                                color = if (alreadyInstalled) CyberMutedText else CyberCyan,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = cyber.description,
                                color = CyberBrightGreen,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                lineHeight = 11.sp
                            )
                        }

                        Button(
                            onClick = { onBuyCyberware(cyber) },
                            enabled = !alreadyInstalled && uiState.credits >= cyber.cost,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (alreadyInstalled) Color.Transparent else CyberMutedGreen,
                                disabledContainerColor = CyberDark
                            ),
                            border = BorderStroke(1.dp, if (alreadyInstalled) CyberBorder else CyberCyan),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("btn_buy_${cyber.id}")
                        ) {
                            Text(
                                text = if (alreadyInstalled) "MOUNTED" else "${cyber.cost} MB",
                                color = if (alreadyInstalled) CyberMutedText else CyberCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Consumable downloads section
            Text(
                text = "VIRTUAL UTILITY CONSOLE DOWNLOADS:",
                color = CyberCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )

            val utilities = listOf(
                Pair("NanoMed.sys", 50),
                Pair("RAMBoost.exe", 40),
                Pair("Decryptor.pkg", 80),
                Pair("AntiShield.bin", 60),
                Pair("FirewallBuffer.pkg", 75),
                Pair("GibsonForecast.sys", 40)
            )

            utilities.forEach { util ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                    border = BorderStroke(1.dp, CyberBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = util.first,
                                color = CyberCyan,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            val desc = when (util.first) {
                                "NanoMed.sys" -> "Hot patch sector. Restore 40 System Integrity."
                                "RAMBoost.exe" -> "Refresh allocation pipelines. Restore 6 MB RAM."
                                "Decryptor.pkg" -> "Analyze encrypted tokens. Unlocks credits upon crack."
                                "AntiShield.bin" -> "Integrate heavy packet algorithms (+2 basic attack bonus)."
                                "FirewallBuffer.pkg" -> "Increase active protective padding (+2 defense rating)."
                                "GibsonForecast.sys" -> "Atmospheric monitor. Predict upcoming sub-grid weather."
                                else -> "General software package."
                            }
                            Text(
                                text = desc,
                                color = CyberBrightGreen,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                lineHeight = 11.sp
                            )
                        }

                        Button(
                            onClick = { onBuyConsumable(util.first, util.second) },
                            enabled = uiState.credits >= util.second,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberMutedGreen),
                            border = BorderStroke(1.dp, CyberCyan),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("btn_buy_utility_${util.first}")
                        ) {
                            Text(
                                text = "${util.second} MB",
                                color = CyberCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
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
                .testTag("btn_exit_shop")
        ) {
            Text(
                text = "DISCONNECT BLACK-MARKET",
                color = Color.Black,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}
