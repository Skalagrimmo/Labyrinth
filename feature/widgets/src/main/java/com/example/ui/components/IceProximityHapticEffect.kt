package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.audio.CyberVibrationManager
import com.example.core.model.CellType
import com.example.ui.GameUiState

/**
 * Slice C: haptic vibration feedback for proximity to active ICE nodes & threat hosts.
 * Extracted verbatim from the TerminalScreen router (behavior-neutral).
 */
@Composable
fun IceProximityHapticEffect(uiState: GameUiState) {
    val context = LocalContext.current
    val vibrationManager = remember(context) { CyberVibrationManager(context) }

    LaunchedEffect(uiState.gridX, uiState.gridY, uiState.maze) {
        val px = uiState.gridX
        val py = uiState.gridY
        val maze = uiState.maze
        if (maze.isNotEmpty() && py in maze.indices && px in maze[0].indices) {
            var minDistance = 99.0
            val searchRadius = 3
            for (dy in -searchRadius..searchRadius) {
                for (dx in -searchRadius..searchRadius) {
                    val nx = px + dx
                    val ny = py + dy
                    if (ny in maze.indices && nx in maze[0].indices) {
                        val cell = maze[ny][nx]
                        if (cell == CellType.VIRUS_NODE ||
                            cell == CellType.ENCRYPTED_PORTAL ||
                            cell == CellType.HACKABLE_TERMINAL) {
                            val dist = Math.hypot(dx.toDouble(), dy.toDouble())
                            if (dist < minDistance) {
                                minDistance = dist
                            }
                        }
                    }
                }
            }
            if (minDistance <= 3.0) {
                vibrationManager.triggerIceProximityVibration(minDistance)
            }
        }
    }
}
