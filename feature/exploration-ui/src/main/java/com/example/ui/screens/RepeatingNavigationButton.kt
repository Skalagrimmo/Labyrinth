package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

fun Modifier.repeatingClickable(
    enabled: Boolean = true,
    initialDelayMillis: Long = 200,
    repeatDelayMillis: Long = 120,
    onClick: () -> Unit
): Modifier = this.pointerInput(enabled, onClick) {
    if (!enabled) return@pointerInput
    coroutineScope {
        detectTapGestures(
            onPress = {
                val delayJob = launch {
                    onClick()
                    delay(initialDelayMillis)
                    while (isActive) {
                        onClick()
                        delay(repeatDelayMillis)
                    }
                }
                try {
                    awaitRelease()
                } finally {
                    delayJob.cancel()
                }
            }
        )
    }
}

@Composable
fun RepeatingNavigationButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, CyberBorderLight.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .background(CyberMutedGreen.copy(alpha = 0.5f))
            .repeatingClickable(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

