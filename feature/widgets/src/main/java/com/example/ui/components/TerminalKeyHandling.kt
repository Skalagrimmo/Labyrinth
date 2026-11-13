package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView

/**
 * Slice C: hardware-key handling for the exploration screen (WASD/arrows move,
 * E/Space/Enter interact) with haptic feedback.
 * Extracted verbatim from the TerminalScreen router (behavior-neutral):
 * the router supplies the exploration/combat flags and view-model callbacks.
 */
@Composable
fun rememberTerminalExplorationKeyHandler(
    explorationActive: Boolean,
    inCombat: Boolean,
    onMoveForward: () -> Unit,
    onMoveBackward: () -> Unit,
    onTurnLeft: () -> Unit,
    onTurnRight: () -> Unit,
    onInteract: () -> Unit
): Modifier {
    val view = LocalView.current
    return Modifier.onKeyEvent { keyEvent ->
        if (keyEvent.type == KeyEventType.KeyDown && explorationActive) {
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
