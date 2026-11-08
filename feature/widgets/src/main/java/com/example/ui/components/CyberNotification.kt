package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberAmber
import com.example.ui.theme.CyberCardBg
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberDark
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.CyberPink
import java.util.UUID

enum class CyberToastType(
    val headerTag: String,
    val accentColor: Color,
    val icon: ImageVector
) {
    ITEM_PICKUP(
        headerTag = "ITEM_ACQUIRED",
        accentColor = CyberCyan,
        icon = Icons.Default.ShoppingCart
    ),
    ACCESS_DENIED(
        headerTag = "ACCESS_DENIED",
        accentColor = CyberPink,
        icon = Icons.Default.Lock
    ),
    REGEN_HEALTH(
        headerTag = "INTEGRITY_REPAIRED",
        accentColor = CyberGreen,
        icon = Icons.Default.Favorite
    ),
    REGEN_RAM(
        headerTag = "RAM_ALLOCATED",
        accentColor = CyberCyan,
        icon = Icons.Default.Refresh
    ),
    SYSTEM_ALERT(
        headerTag = "SYSTEM_ALERT",
        accentColor = CyberAmber,
        icon = Icons.Default.Warning
    )
}

data class CyberToastData(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val type: CyberToastType,
    val durationMs: Long = 3500L
)

@Stable
class CyberToastHostState {
    val activeToasts = mutableStateListOf<CyberToastData>()

    fun showToast(
        title: String,
        message: String,
        type: CyberToastType,
        durationMs: Long = 3500L
    ) {
        val toast = CyberToastData(
            title = title,
            message = message,
            type = type,
            durationMs = durationMs
        )
        // Keep max 3 active toasts
        if (activeToasts.size >= 3) {
            activeToasts.removeAt(0)
        }
        activeToasts.add(toast)
    }

    fun showItemPickup(itemName: String, quantity: Int = 1) {
        showToast(
            title = "PAYLOAD ADDED TO DECK",
            message = "$itemName ${if (quantity > 1) "x$quantity" else ""} transferred to storage.",
            type = CyberToastType.ITEM_PICKUP
        )
    }

    fun showAccessDenied(reason: String = "FIREWALL SECURITY LOCKOUT LEVEL 4") {
        showToast(
            title = "SYSTEM ACCESS DENIED",
            message = reason,
            type = CyberToastType.ACCESS_DENIED
        )
    }

    fun showHealthRegen(amount: Int) {
        showToast(
            title = "SYSTEM INTEGRITY RECOVERY",
            message = "+$amount HP restored to main processing core.",
            type = CyberToastType.REGEN_HEALTH
        )
    }

    fun showRamRegen(amount: Int) {
        showToast(
            title = "RAM RECOVERY COMPLETE",
            message = "+$amount RAM units released back to active pool.",
            type = CyberToastType.REGEN_RAM
        )
    }

    fun showSystemAlert(title: String, message: String) {
        showToast(
            title = title,
            message = message,
            type = CyberToastType.SYSTEM_ALERT
        )
    }

    fun dismissToast(id: String) {
        activeToasts.removeAll { it.id == id }
    }
}

@Composable
fun rememberCyberToastHostState(): CyberToastHostState {
    return remember { CyberToastHostState() }
}

@Composable
fun CyberToastHost(
    hostState: CyberToastHostState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("cyber_toast_host"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        hostState.activeToasts.forEach { toast ->
            CyberToastItem(
                toast = toast,
                onDismiss = { hostState.dismissToast(toast.id) }
            )
        }
    }
}

@Composable
fun CyberToastItem(
    toast: CyberToastData,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var progress by remember { mutableFloatStateOf(1.0f) }

    LaunchedEffect(toast.id) {
        val steps = 100
        val delayTime = toast.durationMs / steps
        for (i in steps downTo 0) {
            progress = i / 100f
            kotlinx.coroutines.delay(delayTime)
        }
        onDismiss()
    }

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .clip(CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp))
                .border(
                    BorderStroke(1.5.dp, toast.type.accentColor),
                    shape = CutCornerShape(topStart = 8.dp, bottomEnd = 8.dp)
                )
                .testTag("cyber_toast_${toast.type.name.lowercase()}"),
            color = CyberCardBg,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .background(CyberDark.copy(alpha = 0.95f))
            ) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(toast.type.accentColor.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "[${toast.type.headerTag}]",
                        color = toast.type.accentColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(2.dp))
                            .clickable { onDismiss() }
                            .padding(2.dp)
                            .testTag("cyber_toast_dismiss_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss Notification",
                            tint = toast.type.accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Body Content
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                toast.type.accentColor.copy(alpha = 0.2f),
                                shape = CutCornerShape(4.dp)
                            )
                            .border(
                                BorderStroke(1.dp, toast.type.accentColor),
                                shape = CutCornerShape(4.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = toast.type.icon,
                            contentDescription = toast.type.headerTag,
                            tint = toast.type.accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = toast.title,
                            color = toast.type.accentColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = toast.message,
                            color = Color.White.copy(alpha = 0.9f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                // Countdown Progress Indicator Bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = toast.type.accentColor,
                    trackColor = toast.type.accentColor.copy(alpha = 0.2f)
                )
            }
        }
    }
}
