package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*

// Post-processing Glitch & Scanline Overlay Shader for First-Person Cyberspace Viewport
@Composable
fun FirstPersonGlitchShaderOverlay(
    integrity: Int,
    maxIntegrity: Int,
    isCombat: Boolean,
    isPlayerHit: Boolean = false,
    frameTime: Long,
    modifier: Modifier = Modifier
) {
    val healthRatio = (integrity.toFloat() / maxIntegrity.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val missingRatio = (1f - healthRatio).coerceIn(0f, 1f)
    val hitBoost = if (isPlayerHit) 0.35f else 0f
    val targetIntensity = (missingRatio + hitBoost).coerceIn(0f, 1f)

    val animatedIntensity by animateFloatAsState(
        targetValue = targetIntensity,
        animationSpec = tween(durationMillis = 150, easing = LinearOutSlowInEasing),
        label = "GlitchIntensity"
    )

    if (animatedIntensity <= 0.02f && !isCombat) return

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val timeSec = frameTime / 1000f

            // 1. Dynamic CRT Scanlines (Increases density & opacity with instability)
            val effectiveIntensity = if (animatedIntensity < 0.05f && isCombat) 0.08f else animatedIntensity
            val numScanlines = (22 + (effectiveIntensity * 60).toInt()).coerceIn(20, 100)
            val scanlineOpacity = 0.04f + (effectiveIntensity * 0.22f)
            val scrollOffset = (timeSec * 75f) % (h / numScanlines)

            for (i in 0 until numScanlines) {
                val lineY = (h / numScanlines) * i + scrollOffset
                val finalY = lineY % h
                drawLine(
                    color = Color.Black.copy(alpha = scanlineOpacity),
                    start = Offset(0f, finalY),
                    end = Offset(w, finalY),
                    strokeWidth = 1.5f + (effectiveIntensity * 2.5f)
                )
            }

            if (effectiveIntensity > 0.05f) {
                val frameSeed = (frameTime / 45L) + (effectiveIntensity * 1000).toLong()
                val random = java.util.Random(frameSeed)

                // 2. Horizontal Screen Displacement / CRT Line Glitch Slices
                val numSlices = (effectiveIntensity * 16).toInt()
                for (i in 0 until numSlices) {
                    val sliceY = random.nextFloat() * h
                    val sliceH = 2f + random.nextFloat() * (14f * effectiveIntensity)
                    val shiftX = (random.nextFloat() - 0.5f) * (45f * effectiveIntensity)
                    val sliceAlpha = 0.12f + random.nextFloat() * (0.45f * effectiveIntensity)

                    val sliceColor = if (random.nextBoolean()) Color(0xFF00E5FF) else Color(0xFFFB7185)
                    drawRect(
                        color = sliceColor.copy(alpha = sliceAlpha),
                        topLeft = Offset(shiftX.coerceAtLeast(0f), sliceY),
                        size = Size(w, sliceH)
                    )
                }

                // 3. Digital Micro-Block Noise Corruption Glitches (Pixel Blocks)
                val blockCount = (effectiveIntensity * 20).toInt()
                for (b in 0 until blockCount) {
                    val blockW = 12f + random.nextFloat() * (70f * effectiveIntensity)
                    val blockH = 6f + random.nextFloat() * (35f * effectiveIntensity)
                    val blockX = random.nextFloat() * (w - blockW)
                    val blockY = random.nextFloat() * (h - blockH)

                    val blockType = random.nextInt(3)
                    val blockColor = when (blockType) {
                        0 -> Color(0xFF00E5FF) // Cyber Cyan static
                        1 -> Color(0xFFFB7185) // Cyber Pink static
                        else -> Color.White   // Static white noise
                    }

                    drawRect(
                        color = blockColor.copy(alpha = 0.15f + random.nextFloat() * 0.45f * effectiveIntensity),
                        topLeft = Offset(blockX, blockY),
                        size = Size(blockW, blockH)
                    )

                    if (random.nextBoolean()) {
                        drawLine(
                            color = Color.Black.copy(alpha = 0.7f),
                            start = Offset(blockX, blockY + blockH / 2f),
                            end = Offset(blockX + blockW, blockY + blockH / 2f),
                            strokeWidth = 1.5f
                        )
                    }
                }

                // 4. Chromatic Aberration Edge Fringe (RGB Split at extreme instability)
                if (effectiveIntensity > 0.35f) {
                    val fringeShift = (effectiveIntensity - 0.35f) * 18f
                    val fringeAlpha = ((effectiveIntensity - 0.35f) * 0.65f).coerceIn(0f, 0.45f)

                    drawRect(
                        color = Color(0xFFFB7185).copy(alpha = fringeAlpha),
                        topLeft = Offset(0f, 0f),
                        size = Size(fringeShift, h)
                    )
                    drawRect(
                        color = Color(0xFF00E5FF).copy(alpha = fringeAlpha),
                        topLeft = Offset(w - fringeShift, 0f),
                        size = Size(fringeShift, h)
                    )
                }

                // 5. Critical Stability Warning Vignette Pulse (When health < 35%)
                if (healthRatio < 0.35f) {
                    val critPulse = 0.3f + 0.30f * kotlin.math.sin(timeSec * 14f)
                    val critColor = Color(0xFFEF4444)

                    val vignetteGradient = Brush.radialGradient(
                        colors = listOf(Color.Transparent, critColor.copy(alpha = critPulse * (1f - healthRatio * 2f).coerceIn(0.2f, 0.85f))),
                        center = Offset(w / 2f, h / 2f),
                        radius = w * 0.65f
                    )
                    drawRect(
                        brush = vignetteGradient,
                        topLeft = Offset(0f, 0f),
                        size = size
                    )
                }
            }
        }

        // 6. HUD Critical Glitch Alert Overlay Text
        if (healthRatio < 0.30f) {
            val alertFlicker = kotlin.math.sin((frameTime / 100f).toDouble()) > 0.0
            if (alertFlicker) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 36.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Red.copy(alpha = 0.85f))
                        .border(BorderStroke(1.dp, Color.White), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "⚠️ CRITICAL STABILITY // INTEGRITY ${(healthRatio * 100).toInt()}%",
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

@Composable
fun GlitchOverlay(progress: Float, modifier: Modifier = Modifier) {
    if (progress <= 0f || progress >= 1f) return
    
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val random = java.util.Random((progress * 100).toLong())
        // Draw some glitch bars
        val count = (random.nextFloat() * 5 + 3).toInt()
        for (i in 0 until count) {
            val barY = random.nextFloat() * h
            val barH = random.nextFloat() * 15f + 4f
            val barW = random.nextFloat() * w * 0.7f + w * 0.2f
            val barX = random.nextFloat() * (w - barW)
            val color = if (random.nextBoolean()) Color(0xFF00E5FF).copy(alpha = 0.7f) else Color(0xFFFB7185).copy(alpha = 0.7f)
            drawRect(
                color = color,
                topLeft = Offset(barX, barY),
                size = Size(barW, barH)
            )
        }
        // Draw static lines
        val lineCount = 8
        for (i in 0 until lineCount) {
            val lineY = random.nextFloat() * h
            drawLine(
                color = Color.White.copy(alpha = 0.4f),
                start = Offset(0f, lineY),
                end = Offset(w, lineY),
                strokeWidth = 2f
            )
        }
    }
}
