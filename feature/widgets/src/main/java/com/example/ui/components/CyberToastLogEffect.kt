package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.core.model.LogMessage

/**
 * Slice C: observes the latest log-feed entry and raises instant cyber toasts for
 * pickups, access denial and health/RAM regen.
 * Extracted verbatim from the TerminalScreen router (behavior-neutral).
 */
@Composable
fun CyberToastLogEffect(
    latestLog: LogMessage?,
    hostState: CyberToastHostState
) {
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
