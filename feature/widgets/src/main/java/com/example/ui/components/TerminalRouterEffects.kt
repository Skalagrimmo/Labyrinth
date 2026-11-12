package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.example.audio.CyberVibrationManager
import com.example.core.model.LogMessage
import com.example.ui.ActiveScreen
import com.example.ui.GameUiState

/**
 * Cross-cutting router effects extracted from the terminal router (slice C):
 * ICE-proximity haptics, log-to-toast bridging and hardware key handling.
 * The router wires these in; the semantics live next to the widgets they drive.
 */

/**
 * Haptic vibration feedback for proximity to active ICE nodes & threat hosts.
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

/**
 * Observe latest log message to trigger instant cyber toasts for pickups,
 * access denial, health/RAM regen.
 */
@Composable
fun CyberLogToastEffect(latestLog: LogMessage?, hostState: CyberToastHostState) {
    LaunchedEffect(latestLog) {
        latestLog?.let { log ->
            val text = log.text
            when {
                text.contains("OBTAINED", ignoreCase = true) ||
                text.contains("ACQUIRED", ignoreCase = true) ||
                text.contains("TRANSFER COMPLETE", ignoreCase = true) ||
                text.contains("INSTALLED CYBERWARE", ignoreCase = true) ||
                text.contains("PURCHASED", ignoreCase = true) -> {
                    hostState.showItemPickup(text)
                }
                text.contains("ERROR", ignoreCase = true) ||
                text.contains("DENIED", ignoreCase = true) ||
                text.contains("LOCKOUT", ignoreCase = true) ||
                text.contains("INVALID", ignoreCase = true) -> {
                    hostState.showAccessDenied(text)
                }
                text.contains("HP", ignoreCase = true) ||
                text.contains("RESTORED", ignoreCase = true) ||
                text.contains("REPAIRED", ignoreCase = true) ||
                text.contains("INTEGRITY", ignoreCase = true) -> {
                    if (!text.contains("DAMAGE", ignoreCase = true) && !text.contains("DEALT", ignoreCase = true)) {
                        hostState.showHealthRegen(25)
                    }
                }
                text.contains("RAM RECOVERY", ignoreCase = true) ||
                text.contains("RAM RELEASED", ignoreCase = true) -> {
                    hostState.showRamRegen(4)
                }
            }
        }
    }
}

/**
 * Hardware key handling for the exploration router: WASD/arrows move and turn,
 * E/Space/Enter interacts, with haptic feedback per press.
 */
fun Modifier.terminalHardwareKeyHandler(
    screen: ActiveScreen,
    inCombat: Boolean,
    onMoveForward: () -> Unit,
    onMoveBackward: () -> Unit,
    onTurnLeft: () -> Unit,
    onTurnRight: () -> Unit,
    onInteract: () -> Unit
): Modifier = composed {
    val view = LocalView.current
    onKeyEvent { keyEvent ->
        if (keyEvent.type == KeyEventType.KeyDown && screen == ActiveScreen.EXPLORATION) {
            val isFirstPress = keyEvent.nativeKeyEvent.repeatCount == 0
            if (inCombat && !isFirstPress) {
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
}
