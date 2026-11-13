package com.example.ui.components

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import com.example.audio.CyberVibrationManager
import com.example.data.GameState
import com.example.ui.ActiveScreen
import com.example.ui.GameUiState

/**
 * Router-level side effects extracted from TerminalScreen (slice C):
 * ICE-proximity haptics, log-driven cyber toasts, hardware key handling.
 * TerminalScreen stays a pure router; these effects live next to the
 * toast host and HUD widgets they drive.
 */

@Composable
fun IceProximityHapticEffect(uiState: GameUiState) {
    val context = LocalContext.current
    val vibrationManager = remember(context) { CyberVibrationManager(context) }

    // Haptic vibration feedback for proximity to active ICE nodes & threat hosts
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
                        if (cell == com.example.core.model.CellType.VIRUS_NODE ||
                            cell == com.example.core.model.CellType.ENCRYPTED_PORTAL ||
                            cell == com.example.core.model.CellType.HACKABLE_TERMINAL) {
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

@Composable
fun LogDrivenToastEffect(
    uiState: GameUiState,
    toastHostState: CyberToastHostState
) {
    // Observe latest log message to trigger instant cyber toasts for pickups, access denial, health/RAM regen
    val latestLog = uiState.logFeed.firstOrNull()
    LaunchedEffect(latestLog) {
        latestLog?.let { log ->
            val text = log.text
            when {
                text.contains("OBTAINED", ignoreCase = true) ||
                text.contains("ACQUIRED", ignoreCase = true) ||
                text.contains("TRANSFER COMPLETE", ignoreCase = true) ||
                text.contains("INSTALLED CYBERWARE", ignoreCase = true) ||
                text.contains("PURCHASED", ignoreCase = true) -> {
                    toastHostState.showItemPickup(text)
                }
                text.contains("ERROR", ignoreCase = true) ||
                text.contains("DENIED", ignoreCase = true) ||
                text.contains("LOCKOUT", ignoreCase = true) ||
                text.contains("INVALID", ignoreCase = true) -> {
                    toastHostState.showAccessDenied(text)
                }
                text.contains("HP", ignoreCase = true) ||
                text.contains("RESTORED", ignoreCase = true) ||
                text.contains("REPAIRED", ignoreCase = true) ||
                text.contains("INTEGRITY", ignoreCase = true) -> {
                    if (!text.contains("DAMAGE", ignoreCase = true) && !text.contains("DEALT", ignoreCase = true)) {
                        toastHostState.showHealthRegen(25)
                    }
                }
                text.contains("RAM RECOVERY", ignoreCase = true) ||
                text.contains("RAM RELEASED", ignoreCase = true) -> {
                    toastHostState.showRamRegen(4)
                }
            }
        }
    }
}

fun Modifier.terminalHardwareKeyHandler(
    uiState: GameUiState,
    view: View,
    onMoveForward: () -> Unit,
    onMoveBackward: () -> Unit,
    onTurnLeft: () -> Unit,
    onTurnRight: () -> Unit,
    onInteract: () -> Unit
): Modifier = this.onKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown && uiState.screen == ActiveScreen.EXPLORATION) {
                val isCombat = uiState.gameState != GameState.EXPLORATION
                val isFirstPress = keyEvent.nativeKeyEvent.repeatCount == 0
                if (isCombat && !isFirstPress) {
                    return@onKeyEvent true
                }
                when (keyEvent.key) {
                    Key.W, Key.DirectionUp -> {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onMoveForward()
                        true
                    }
                    Key.S, Key.DirectionDown -> {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onMoveBackward()
                        true
                    }
                    Key.A, Key.DirectionLeft -> {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onTurnLeft()
                        true
                    }
                    Key.D, Key.DirectionRight -> {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onTurnRight()
                        true
                    }
                    Key.E, Key.Spacebar, Key.Enter -> {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onInteract()
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
}
