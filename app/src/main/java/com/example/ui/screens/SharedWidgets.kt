package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*

@Composable
fun FloatingDamagePopup(
    text: String?,
    color: Color,
    isPlayer: Boolean,
    modifier: Modifier = Modifier
) {
    var activeText by remember { mutableStateOf<String?>(null) }
    val anim = remember { Animatable(0f) }

    LaunchedEffect(text) {
        if (text != null) {
            activeText = text
            anim.snapTo(0f)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 600, easing = LinearOutSlowInEasing)
            )
            activeText = null
        }
    }

    activeText?.let { popup ->
        val yOffset = if (isPlayer) {
            (40 + anim.value * 60).dp
        } else {
            (-40 - anim.value * 60).dp
        }
        val scale = 1f + anim.value * 0.3f
        Box(
            modifier = modifier
                .offset(y = yOffset)
                .graphicsLayer(
                    alpha = 1f - anim.value,
                    scaleX = scale,
                    scaleY = scale
                )
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.8f))
                .border(1.dp, color, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = popup,
                color = color,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun DigitalSparks(color: Color, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "Sparks")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "SparksTime"
    )
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val random = java.util.Random(42)
        val count = 25
        for (i in 0 until count) {
            val xRatio = random.nextFloat()
            val yRatio = random.nextFloat()
            val dx = (random.nextFloat() - 0.5f) * 60f
            val dy = (random.nextFloat() - 0.5f) * 60f
            
            val pTime = (time + xRatio) % 1.0f
            val x = xRatio * w + dx * pTime
            val y = yRatio * h + dy * pTime
            val alpha = 1f - pTime
            val sizePx = random.nextFloat() * 6f + 2f
            
            drawRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(x, y),
                size = Size(sizePx, sizePx)
            )
        }
    }
}
