package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Phosphor Color Theme for the CRT Scanline Overlay
 */
enum class CrtPhosphorMode(val label: String, val phosphorColor: Color, val accentGlow: Color) {
    CYBER_CYAN("CYBER CYAN", Color(0xFF00FFCC), Color(0x3300FFCC)),
    MATRIX_GREEN("MATRIX GREEN", Color(0xFF00FF66), Color(0x3300FF66)),
    RETRO_AMBER("RETRO AMBER", Color(0xFFFFB000), Color(0x33FFB000)),
    STEALTH_WHITE("STEALTH MONO", Color(0xFFE0F2FE), Color(0x33E0F2FE))
}

/**
 * A highly customizable, flickering CRT scanline & cathode ray tube display overlay
 * specifically engineered for Cyberpunk Hacking Terminals.
 *
 * Features:
 * - Dynamic CRT sweep line moving top-to-bottom
 * - Analog voltage flicker & brightness hum
 * - Horizontal raster scanline grid
 * - Vignette & CRT tube glass border curvature
 * - Phosphor glow & subpixel chromatic fringe
 * - Micro static noise particles & horizontal jitter glitches
 */
@Composable
fun FlickeringCrtScanlineTerminalOverlay(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    phosphorMode: CrtPhosphorMode = CrtPhosphorMode.CYBER_CYAN,
    scanlineSpacingDp: Float = 3.5f,
    flickerIntensity: Float = 0.35f,
    curvatureVignette: Boolean = true,
    showControlToggle: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    var isEffectActive by remember { mutableStateOf(enabled) }
    var currentPhosphor by remember { mutableStateOf(phosphorMode) }
    var currentFlicker by remember { mutableFloatStateOf(flickerIntensity) }
    var showSettingsPanel by remember { mutableStateOf(false) }

    // Infinite transition for animation loops
    val infiniteTransition = rememberInfiniteTransition(label = "crt_scanline_transition")

    // Continuous normalized time (0..1)
    val animTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "anim_time"
    )

    // CRT Beam sweep (top to bottom)
    val sweepProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_progress"
    )

    Box(modifier = modifier.fillMaxSize()) {
        // --- 1. Underlying Terminal Content ---
        content()

        // --- 2. CRT Scanline Canvas Overlay ---
        if (isEffectActive) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("crt_scanline_canvas")
            ) {
                val width = size.width
                val height = size.height
                if (width <= 0f || height <= 0f) return@Canvas

                val timeMs = animTime * 4000f

                // --- A. Analog Voltage Flicker & High Frequency Luminance Hum ---
                // Combines multiple sine waves at co-prime frequencies to simulate irregular tube flicker
                val rawFlicker = (
                    sin((timeMs * 0.05f).toDouble()) * 0.4 +
                    sin((timeMs * 0.13f).toDouble()) * 0.3 +
                    cos((timeMs * 0.29f).toDouble()) * 0.3
                ).toFloat()

                // High intensity micro-flicker spikes
                val jitterSpike = if (Random.nextFloat() < 0.08f * currentFlicker) {
                    Random.nextFloat() * 0.25f - 0.12f
                } else 0f

                val baseFlickerAlpha = (0.04f + (rawFlicker * 0.035f + jitterSpike) * currentFlicker).coerceIn(0.01f, 0.25f)

                // Draw full screen subtle luminance flicker coat
                drawRect(
                    color = currentPhosphor.phosphorColor.copy(alpha = baseFlickerAlpha * 0.4f),
                    size = size,
                    blendMode = BlendMode.Screen
                )

                // --- B. Raster Scanline Grid Lines ---
                val spacingPx = scanlineSpacingDp * density
                val totalLines = (height / spacingPx).toInt() + 1
                val darkScanlineColor = Color.Black.copy(alpha = (0.28f + currentFlicker * 0.20f).coerceAtMost(0.6f))
                val glowScanlineColor = currentPhosphor.phosphorColor.copy(alpha = 0.025f + currentFlicker * 0.02f)

                for (i in 0 until totalLines) {
                    val y = i * spacingPx
                    // Draw dark raster gap
                    drawLine(
                        color = darkScanlineColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = spacingPx * 0.45f
                    )

                    // Alternate faint phosphor line
                    if (i % 2 == 0) {
                        drawLine(
                            color = glowScanlineColor,
                            start = Offset(0f, y + spacingPx * 0.5f),
                            end = Offset(width, y + spacingPx * 0.5f),
                            strokeWidth = spacingPx * 0.2f
                        )
                    }
                }

                // --- C. Moving CRT Cathode Beam Sweep Line ---
                val beamY = sweepProgress * (height + 100f) - 50f
                if (beamY in -50f..(height + 50f)) {
                    val beamBrush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            currentPhosphor.phosphorColor.copy(alpha = 0.08f + currentFlicker * 0.08f),
                            currentPhosphor.phosphorColor.copy(alpha = 0.25f + currentFlicker * 0.15f),
                            currentPhosphor.phosphorColor.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        startY = (beamY - 45f).coerceAtLeast(0f),
                        endY = (beamY + 45f).coerceAtMost(height)
                    )

                    drawRect(
                        brush = beamBrush,
                        topLeft = Offset(0f, (beamY - 45f).coerceAtLeast(0f)),
                        size = Size(width, 90f)
                    )
                }

                // --- D. Horizontal Glitch Slice / Jitter Lines ---
                if (Random.nextFloat() < 0.12f * currentFlicker) {
                    val glitchY = Random.nextFloat() * height
                    val glitchHeight = Random.nextFloat() * 8f + 2f
                    val glitchShift = (Random.nextFloat() - 0.5f) * 20f

                    drawRect(
                        color = currentPhosphor.phosphorColor.copy(alpha = 0.15f),
                        topLeft = Offset(glitchShift.coerceAtLeast(0f), glitchY),
                        size = Size(width, glitchHeight),
                        blendMode = BlendMode.Screen
                    )
                }

                // --- E. Glass Bezel Vignette & CRT Screen Corner Curvature ---
                if (curvatureVignette) {
                    val isCompactScreen = with(LocalDensity.current) {
                        (size.height / density) < 700f
                    }
                    val vignetteAlpha = if (isCompactScreen) 0.35f else 0.82f
                    val radialVignette = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = vignetteAlpha * 0.5f),
                            Color.Black.copy(alpha = vignetteAlpha)
                        ),
                        center = Offset(width / 2f, height / 2f),
                        radius = (width.coerceAtLeast(height) * 0.72f)
                    )

                    drawRect(brush = radialVignette, size = size)

                    // CRT Outer Frame Glass Bezel Border Shadow
                    val bezelBorderWidth = 6.dp.toPx()
                    drawRect(
                        color = Color.Black.copy(alpha = 0.7f),
                        size = size,
                        style = Stroke(width = bezelBorderWidth)
                    )

                    // Specular Glare in Top Left Glass Corner
                    val specularPath = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(width * 0.35f, 0f)
                        lineTo(0f, height * 0.35f)
                        close()
                    }
                    drawPath(
                        path = specularPath,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.04f),
                                Color.Transparent
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(width * 0.25f, height * 0.25f)
                        )
                    )
                }
            }
        }

        // --- 3. Optional Floating CRT Settings Badge / Toggle ---
        if (showControlToggle) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        showSettingsPanel = !showSettingsPanel
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                        .border(1.dp, currentPhosphor.phosphorColor, RoundedCornerShape(6.dp))
                        .testTag("btn_crt_settings_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "CRT Scanline Settings",
                        tint = currentPhosphor.phosphorColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expanded Control Card
            if (showSettingsPanel) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.95f)),
                    border = BorderStroke(1.dp, currentPhosphor.phosphorColor),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 48.dp, end = 8.dp)
                        .width(220.dp)
                        .testTag("crt_settings_panel")
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📺 CRT SCANLINE TUBE",
                                color = currentPhosphor.phosphorColor,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = if (isEffectActive) currentPhosphor.phosphorColor.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    isEffectActive = !isEffectActive
                                }
                            ) {
                                Text(
                                    text = if (isEffectActive) "ON" else "OFF",
                                    color = if (isEffectActive) currentPhosphor.phosphorColor else Color.Gray,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Phosphor Selector
                        Text(
                            text = "PHOSPHOR MODE:",
                            color = Color.LightGray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.5.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CrtPhosphorMode.entries.forEach { mode ->
                                val isSelected = mode == currentPhosphor
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isSelected) mode.phosphorColor.copy(alpha = 0.2f) else Color.Transparent,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) mode.phosphorColor else Color.Transparent,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .clickable {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                            currentPhosphor = mode
                                        }
                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(mode.phosphorColor, RoundedCornerShape(2.dp))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = mode.label,
                                        color = if (isSelected) mode.phosphorColor else Color.Gray,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Flicker Intensity Slider
                        Text(
                            text = "VOLTAGE FLICKER: ${(currentFlicker * 100).toInt()}%",
                            color = Color.LightGray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.5.sp
                        )

                        Slider(
                            value = currentFlicker,
                            onValueChange = { currentFlicker = it },
                            valueRange = 0.05f..0.8f,
                            colors = SliderDefaults.colors(
                                thumbColor = currentPhosphor.phosphorColor,
                                activeTrackColor = currentPhosphor.phosphorColor
                            ),
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
            }
        }
    }
}
