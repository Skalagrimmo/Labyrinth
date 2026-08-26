package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.GameViewModel
import com.example.ui.theme.*

@Composable
fun TerminalHeader(
    uiState: GameViewModel.GameUiState,
    onMenuClick: () -> Unit,
    onLeaderboardClick: () -> Unit,
    onCyberwareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CyberCardBg),
        border = BorderStroke(1.dp, CyberBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("terminal_header")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Menu icon
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .size(28.dp)
                    .testTag("header_menu_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = CyberCyan,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Center: Zone / Floor info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = uiState.currentZone.displayName.uppercase(),
                    color = CyberGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "FLOOR ${uiState.buildingFloor} // LVL ${uiState.level}",
                    color = CyberMutedText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 7.sp,
                    maxLines = 1
                )
            }

            // Right: CORE% / RAM + status icons
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // CORE %
                val corePercent = ((uiState.integrity.toFloat() / uiState.maxIntegrity.coerceAtLeast(1)) * 100).toInt()
                Text(
                    text = "CORE:$corePercent%",
                    color = when {
                        corePercent > 60 -> CyberGreen
                        corePercent > 30 -> CyberAmber
                        else -> CyberPink
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("header_core_percent")
                )

                // RAM
                Text(
                    text = "RAM:${uiState.ram}/${uiState.maxRam}",
                    color = CyberCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("header_ram_display")
                )

                // Leaderboard icon
                IconButton(
                    onClick = onLeaderboardClick,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("header_leaderboard_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Leaderboard",
                        tint = CyberAmber,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Cyberware inventory icon
                IconButton(
                    onClick = onCyberwareClick,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("header_cyberware_button")
                ) {
                    Text(
                        text = "🔌",
                        fontSize = 14.sp,
                        modifier = Modifier.testTag("header_cyberware_icon")
                    )
                }
            }
        }
    }
}
