package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.*
import com.example.ui.theme.*

// ==========================================
// Sub-Composable: Retro ProgressBar
// ==========================================
@Composable
fun ProgressBarRetro(
    current: Int,
    max: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animatedCurrent by animateFloatAsState(
        targetValue = current.toFloat(),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "ProgressBarProgress"
    )
    val filledPercent = if (max > 0) animatedCurrent / max else 0f
    val clampedPercent = filledPercent.coerceIn(0f, 1f)

    val finalColor = if (clampedPercent < 0.25f) {
        Color(0xFFEF4444) // Red
    } else if (clampedPercent < 0.5f) {
        Color(0xFFFBBF24) // Yellow
    } else {
        color
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(CyberDark)
            .border(1.dp, finalColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(clampedPercent)
                .background(finalColor)
        )
    }
}
