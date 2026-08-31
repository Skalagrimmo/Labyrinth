package com.example.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import com.example.data.*
import com.example.ui.GameViewModel
import com.example.ui.components.VisualTurnIndicator
import com.example.ui.components.CombatHackingMinigameView
import com.example.ui.components.FlickeringCrtScanlineTerminalOverlay
import com.example.ui.components.CyberVitalStatusHud
import com.example.ui.theme.*
@Composable
fun ExplorationView(
    uiState: GameViewModel.GameUiState,
    viewModel: GameViewModel,
    onShopClick: () -> Unit,
    onSafeDisconnect: () -> Unit
) {
    val view = LocalView.current
    val fadeAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = uiState.fadeAlpha,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 350, easing = androidx.compose.animation.core.LinearEasing),
        label = "TransitionFade"
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
        // Main split: Left Viewport (3D ASCII), Right Panel (Map + Stats)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.4f),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Left Viewport (ASCII wireframe)
            Column(
                modifier = Modifier
                    .weight(1.55f)
                    .fillMaxHeight()
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    border = BorderStroke(1.dp, CyberBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                            .pointerInput(Unit) {
                                var totalDragX = 0f
                                var totalDragY = 0f
                                detectDragGestures(
                                    onDragStart = {
                                        totalDragX = 0f
                                        totalDragY = 0f
                                    },
                                    onDragEnd = {
                                        val threshold = 40f
                                        if (Math.abs(totalDragX) > Math.abs(totalDragY)) {
                                            if (totalDragX > threshold) {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                viewModel.turnRight()
                                            } else if (totalDragX < -threshold) {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                viewModel.turnLeft()
                                            }
                                        } else {
                                            if (totalDragY > threshold) {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                viewModel.moveBackward()
                                            } else if (totalDragY < -threshold) {
                                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                                viewModel.moveForward()
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        totalDragX = 0f
                                        totalDragY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        totalDragX += dragAmount.x
                                        totalDragY += dragAmount.y
                                    }
                                )
                            }
                            .pointerInput(uiState.gameState, uiState.isCombatInputEnabled) {
                                detectTapGestures(
                                    onTap = {
                                        // Touch-first: tapping the viewport's hostile target = attack.
                                        if (uiState.gameState != GameState.EXPLORATION && uiState.isCombatInputEnabled) {
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                            viewModel.combatAttack()
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Crossfade(
                            targetState = uiState.perspectiveText,
                            animationSpec = tween(120),
                            label = "perspective_crossfade"
                        ) { _ ->
                            FirstPersonPerspectiveCanvas(
                                uiState = uiState,
                                modifier = Modifier.fillMaxSize().testTag("first_person_viewport"),
                                isCombat = (uiState.gameState != GameState.EXPLORATION),
                                onInteract = { viewModel.interact() }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Tactical Navigation or Combat Actions panel under 3D wireframe
                val cardBorderColor by animateColorAsState(
                    targetValue = if (uiState.gameState != GameState.EXPLORATION) CyberPink else CyberBorder,
                    animationSpec = tween(durationMillis = 500),
                    label = "CardBorderColor"
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                    border = BorderStroke(1.dp, cardBorderColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    AnimatedContent(
                        targetState = uiState.gameState,
                        transitionSpec = {
                            (slideInVertically(animationSpec = tween(400)) { height -> height } + fadeIn(animationSpec = tween(400)))
                                .togetherWith(slideOutVertically(animationSpec = tween(400)) { height -> -height } + fadeOut(animationSpec = tween(400)))
                        },
                        label = "CombatPanelTransition"
                    ) { targetState ->
                        if (targetState == GameState.EXPLORATION) {
                            Column(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // UP arrow (Custom repeating navigation button)
                                RepeatingNavigationButton(
                                    onClick = { viewModel.moveForward() },
                                    content = { Icon(Icons.Default.KeyboardArrowUp, "Forward", tint = CyberCyan, modifier = Modifier.size(20.dp)) },
                                    modifier = Modifier.testTag("btn_move_forward")
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RepeatingNavigationButton(
                                        onClick = { viewModel.turnLeft() },
                                        content = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Turn Left", tint = CyberCyan, modifier = Modifier.size(20.dp)) },
                                        modifier = Modifier.testTag("btn_turn_left")
                                    )

                                    RepeatingNavigationButton(
                                        onClick = { viewModel.moveBackward() },
                                        content = { Icon(Icons.Default.KeyboardArrowDown, "Backward", tint = CyberCyan, modifier = Modifier.size(20.dp)) },
                                        modifier = Modifier.testTag("btn_move_back")
                                    )

                                    RepeatingNavigationButton(
                                        onClick = { viewModel.turnRight() },
                                        content = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Turn Right", tint = CyberCyan, modifier = Modifier.size(20.dp)) },
                                        modifier = Modifier.testTag("btn_turn_right")
                                    )
                                }
                            }
                        } else {
                            // GameState.COMBAT: Seamless tactical battle panel!
                            val enemy = uiState.activeEnemy
                            Column(
                                modifier = Modifier
                                    .padding(6.dp)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                VisualTurnIndicator(
                                    combatTurn = uiState.combatTurn,
                                    isCombatInputEnabled = uiState.isCombatInputEnabled,
                                    bannerMessage = uiState.showCombatBanner,
                                    combatRound = uiState.combatRound,
                                    compactMode = true
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "вљ пёЏ HOSTILE INTRUDER DETECTED // ${enemy?.name ?: "UNKNOWN"}",
                                        color = CyberPink,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 8.sp
                                    )
                                    Text(
                                        text = "HP: ${enemy?.integrity ?: 0}/${enemy?.maxIntegrity ?: 0}",
                                        color = CyberPink,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                ProgressBarRetro(
                                    current = enemy?.integrity ?: 0,
                                    max = enemy?.maxIntegrity ?: 1,
                                    color = CyberPink,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                // Real-Time Enemy Decryption Compile Bar (System Shock cyber style)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "рџ’Ґ HOSTILE DECRYPTION PACKET:",
                                        color = CyberAmber,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${(uiState.enemyAttackCharge * 100).toInt()}% COMPILING",
                                        color = CyberAmber,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                ProgressBarRetro(
                                    current = (uiState.enemyAttackCharge * 100).toInt(),
                                    max = 100,
                                    color = CyberAmber,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )

                                 // Active Hacking Pattern Minigame vs Standard Combat Actions
                                 if (uiState.activeCombatHack != null) {
                                     CombatHackingMinigameView(
                                         hackState = uiState.activeCombatHack,
                                         onSelectSymbol = { viewModel.selectCombatHackSymbol(it) },
                                         onClearBuffer = { viewModel.clearCombatHackBuffer() },
                                         onAbort = { viewModel.abortCombatHack() },
                                         modifier = Modifier.padding(vertical = 4.dp)
                                     )
                                 } else {
                                     // Row 1: Primary Combat Actions (Attack, Defend, Item, Flee)
                                     var showItemMenu by remember { mutableStateOf(false) }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // 1. Attack Button
                                    Button(
                                        onClick = { viewModel.combatAttack() },
                                        enabled = uiState.isCombatInputEnabled,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFE11D48),
                                            disabledContainerColor = Color(0xFFE11D48).copy(alpha = 0.35f)
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .heightIn(min = 44.dp)
                                            .testTag("btn_combat_attack")
                                    ) {
                                        Text(
                                            text = "ATTACK",
                                            color = Color.White,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // 2. Defend Button
                                    Button(
                                        onClick = { viewModel.combatDefend() },
                                        enabled = uiState.isCombatInputEnabled,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF0F172A),
                                            disabledContainerColor = Color(0xFF0F172A).copy(alpha = 0.5f)
                                        ),
                                        border = BorderStroke(1.5.dp, if (uiState.activeFirewallTimeLeft > 0) Color(0xFF10B981) else CyberBrightGreen),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .heightIn(min = 44.dp)
                                            .testTag("btn_combat_defend")
                                    ) {
                                        Text(
                                            text = if (uiState.activeFirewallTimeLeft > 0) "FIREWALL ACTIVE" else "DEFEND",
                                            color = if (uiState.activeFirewallTimeLeft > 0) Color(0xFF34D399) else CyberBrightGreen,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // 3. Item Button
                                    Button(
                                        onClick = { showItemMenu = !showItemMenu },
                                        enabled = uiState.isCombatInputEnabled && uiState.inventory.isNotEmpty(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF0F172A),
                                            disabledContainerColor = Color(0xFF0F172A).copy(alpha = 0.5f)
                                        ),
                                        border = BorderStroke(1.5.dp, CyberCyan),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 44.dp)
                                            .testTag("btn_combat_item")
                                    ) {
                                        Text("ITEM", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // 4. Flee Button
                                    Button(
                                        onClick = { viewModel.fleeCombat() },
                                        enabled = uiState.isCombatInputEnabled,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF0F172A),
                                            disabledContainerColor = Color(0xFF0F172A).copy(alpha = 0.5f)
                                        ),
                                        border = BorderStroke(1.5.dp, CyberAmber),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 44.dp)
                                            .testTag("btn_combat_flee")
                                    ) {
                                        Text("FLEE", color = CyberAmber, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Row 2: Secondary / Tactical Combat Actions (Quick Hack, Scan, End Turn)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // 5. Quick Hack Button
                                    Button(
                                        onClick = { viewModel.combatHack() },
                                        enabled = uiState.isCombatInputEnabled && uiState.ram >= 3,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (uiState.ram >= 3) Color(0xFF4C1D95) else Color(0xFF0F172A),
                                            disabledContainerColor = Color(0xFF0F172A).copy(alpha = 0.5f)
                                        ),
                                        border = BorderStroke(1.5.dp, if (uiState.ram >= 3) CyberPink else Color.Gray),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .height(36.dp)
                                            .testTag("btn_combat_hack")
                                    ) {
                                        Text("QUICK HACK", color = if (uiState.ram >= 3) Color.White else Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // 6. Scan Target Button
                                    Button(
                                        onClick = { viewModel.combatScan() },
                                        enabled = uiState.isCombatInputEnabled,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF0F172A),
                                            disabledContainerColor = Color(0xFF0F172A).copy(alpha = 0.5f)
                                        ),
                                        border = BorderStroke(1.5.dp, CyberCyan),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .height(36.dp)
                                            .testTag("btn_combat_scan")
                                    ) {
                                        Text("SCAN TARGET", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // 7. End Turn Button
                                    Button(
                                        onClick = { viewModel.endTurn() },
                                        enabled = uiState.isCombatInputEnabled,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF059669),
                                            disabledContainerColor = Color(0xFF059669).copy(alpha = 0.35f)
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .testTag("btn_combat_end_turn")
                                    ) {
                                        Text("END TURN", color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Expandable Item Menu
                                if (showItemMenu && uiState.inventory.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        uiState.inventory.forEach { item ->
                                            val itemColor = when (item) {
                                                "NanoMed.sys" -> CyberCyan
                                                "RAMBoost.exe" -> CyberCyan
                                                "Decryptor.pkg" -> CyberAmber
                                                "ChipsetMod.pkg" -> CyberPink
                                                else -> Color.LightGray
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .border(1.dp, itemColor, RoundedCornerShape(4.dp))
                                                    .background(CyberDark)
                                                    .clickable {
                                                        viewModel.useInventoryItem(item)
                                                        showItemMenu = false
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                                    .testTag("combat_item_$item")
                                            ) {
                                                Text(
                                                    text = item,
                                                    color = itemColor,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                // Row 3: Installed Programs Quick Launcher
                                if (uiState.installedPrograms.isNotEmpty()) {
                                    HorizontalDivider(color = CyberBorder.copy(alpha = 0.4f), thickness = 1.dp, modifier = Modifier.padding(vertical = 2.dp))
                                    Text(
                                        text = "INSTALLED SOFTWARE PROTOCOLS //",
                                        color = CyberCyan,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.Start).padding(start = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        uiState.installedPrograms.forEach { prog ->
                                            val progCooldown = uiState.programCooldowns[prog.id] ?: 0
                                            val isReady = progCooldown <= 0
                                            val hasRam = uiState.ram >= prog.ramCost
                                            val isButtonEnabled = hasRam && isReady && uiState.isCombatInputEnabled
                                            Box(
                                                modifier = Modifier
                                                    .background(if (isButtonEnabled) CyberMutedGreen else CyberDark, RoundedCornerShape(4.dp))
                                                    .border(1.dp, if (isButtonEnabled) CyberCyan else CyberBorder.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                                    .clickable(enabled = isButtonEnabled) {
                                                        viewModel.executeCombatProgram(prog)
                                                    }
                                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                                    .testTag("inline_program_${prog.id}")
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = if (progCooldown > 0) "${prog.name} (${String.format("%.1f", progCooldown / 10f)}s)" else prog.name,
                                                        color = if (isButtonEnabled) CyberCyan else Color.Gray,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "(${prog.ramCost}MB)",
                                                        color = if (isButtonEnabled) CyberPink else Color.Gray,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 7.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                 }
                            }
                        }
                    }
                }
            }

            // Right HUD Panel (2D Top down map and stats)
            val minimapAlpha = 1f
            val minimapScale = 1f

            Column(
                modifier = Modifier
                    .weight(0.70f)
                    .fillMaxHeight()
            ) {
                // Top-Down Mini-map
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                    border = BorderStroke(1.dp, CyberBorder.copy(alpha = minimapAlpha)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxWidth()
                        .graphicsLayer(
                            alpha = minimapAlpha,
                            scaleX = minimapScale,
                            scaleY = minimapScale
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "SECTOR LOGIC RADAR",
                                color = CyberCyan.copy(alpha = minimapAlpha),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (uiState.isScanActive || uiState.scanTurnsLeft > 0) {
                                Text(
                                    text = "рџ“Ў ACTIVE (${uiState.scanTurnsLeft} CYCLES)",
                                    color = CyberPink,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Render top down map
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            RenderMiniMap(uiState)
                            GlitchOverlay(progress = 1f - minimapAlpha)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Primary Stats Overview
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberCardBg),
                    border = BorderStroke(1.dp, CyberBorder),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(0.65f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "STATUS // ${uiState.runnerName.uppercase()}",
                                color = CyberCyan,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "LVL ${uiState.characterLevel}",
                                color = CyberAmber,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }

                        // XP Progress Bar
                        Text(
                            text = "XP: ${uiState.characterXp}/${uiState.xpToNextLevel}",
                            color = CyberAmber,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                        ProgressBarRetro(
                            current = uiState.characterXp,
                            max = uiState.xpToNextLevel,
                            color = CyberAmber
                        )

                        // HP Bar
                        Text(
                            text = "INTEGRITY: ${uiState.integrity}/${uiState.maxIntegrity}",
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                        ProgressBarRetro(
                            current = uiState.integrity,
                            max = uiState.maxIntegrity,
                            color = CyberCyan
                        )

                        // RAM Bar
                        Text(
                            text = "RAM: ${uiState.ram}/${uiState.maxRam} MB",
                            color = CyberPink,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                        ProgressBarRetro(
                            current = uiState.ram,
                            max = uiState.maxRam,
                            color = CyberPink
                        )

                        // Credits & Upgrades
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "CREDITS: ${uiState.credits} MB",
                                color = CyberAmber,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            )
                            Text(
                                text = "DMG: +${uiState.damageBonus}",
                                color = CyberPink,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp
                            )
                        }

                        HorizontalDivider(color = CyberBorder, thickness = 1.dp)

                        // Cyber-Space Weather Environmental HUD
                        Spacer(modifier = Modifier.height(4.dp))
                        val weatherColor = Color(uiState.activeWeather.colorHex)
                        Text(
                            text = "GRID ATMOSPHERE:",
                            color = CyberMutedText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(weatherColor, androidx.compose.foundation.shape.CircleShape)
                            )
                            Text(
                                text = uiState.activeWeather.title.uppercase(),
                                color = weatherColor,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            )
                        }
                        if (uiState.activeWeather != com.example.data.CyberWeather.CLEAR) {
                            Text(
                                text = "CYCLES REMAINING: ${uiState.weatherTurnsLeft}",
                                color = weatherColor.copy(alpha = 0.8f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp
                            )
                        }
                        
                        // Vertical Structural Cell HUD
                        val currentCell = if (uiState.maze.isNotEmpty() && uiState.gridY in uiState.maze.indices && uiState.gridX in uiState.maze[0].indices) {
                            uiState.maze[uiState.gridY][uiState.gridX]
                        } else null

                        currentCell?.let { cell ->
                            if (cell == com.example.data.CellType.ELEVATED_BALCONY ||
                                cell == com.example.data.CellType.GRAVITY_SLOPE ||
                                cell == com.example.data.CellType.ELEVATOR ||
                                cell == com.example.data.CellType.STAIRS_UP ||
                                cell == com.example.data.CellType.STAIRS_DOWN) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "VERTICAL STRUCTURE CELL:",
                                    color = CyberMutedText,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val (cellText, cellColor) = when (cell) {
                                    com.example.data.CellType.ELEVATED_BALCONY -> "BALCONY VANTAGE (+25% ATK)" to Color(0xFF10B981)
                                    com.example.data.CellType.GRAVITY_SLOPE -> "GRAVITY SLOPE (30% EVADE)" to Color(0xFFEAB308)
                                    com.example.data.CellType.ELEVATOR -> "EXPRESS ELEVATOR ACCESS" to Color(0xFF00E5FF)
                                    com.example.data.CellType.STAIRS_UP -> "STAIRWELL: ASCENT LINK" to Color(0xFF8B5CF6)
                                    com.example.data.CellType.STAIRS_DOWN -> "STAIRWELL: DESCENT LINK" to Color(0xFF3B82F6)
                                    else -> "" to Color.Gray
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(cellColor, androidx.compose.foundation.shape.CircleShape)
                                    )
                                    Text(
                                        text = cellText.uppercase(),
                                        color = cellColor,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(color = CyberBorder, thickness = 1.dp)

                        // Action Row: Click to interact / Execute Radar Scan
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    viewModel.interact()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyberPink.copy(alpha = 0.5f),
                                    disabledContainerColor = CyberPink.copy(alpha = 0.25f)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(26.dp)
                                    .testTag("btn_interact_hack")
                            ) {
                                Text(
                                    text = "INTERACT",
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    viewModel.triggerMapScan()
                                },
                                enabled = uiState.ram >= 2,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00E5FF).copy(alpha = 0.6f),
                                    disabledContainerColor = Color(0xFF00E5FF).copy(alpha = 0.2f)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(26.dp)
                                    .testTag("btn_radar_scan")
                            ) {
                                Text(
                                    text = if (uiState.isScanActive) "SCANNING (${uiState.scanTurnsLeft})" else "рџ“Ў SCAN (2 RAM)",
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Inventory & Consumable selection pane
        Card(
            colors = CardDefaults.cardColors(containerColor = CyberCardBg),
            border = BorderStroke(1.dp, CyberBorder),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.4f)
        ) {
            Column(modifier = Modifier.padding(4.dp)) {
                Text(
                    text = "VIRTUAL STORAGE // CONSUMABLES (TAP TO LOAD):",
                    color = CyberCyan,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                if (uiState.inventory.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "[ STORAGE COLD CORE EMPTY ]",
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        uiState.inventory.forEach { item ->
                            val itemColor = when (item) {
                                "NanoMed.sys" -> CyberCyan
                                "RAMBoost.exe" -> CyberCyan
                                "Decryptor.pkg" -> CyberAmber
                                "ChipsetMod.pkg" -> CyberPink
                                else -> Color.LightGray
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, itemColor, RoundedCornerShape(6.dp))
                                    .background(CyberDark)
                                    .clickable { viewModel.useInventoryItem(item) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("item_$item")
                            ) {
                                Text(
                                    text = item,
                                    color = itemColor,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Base navigation: Store, Leaders, Dissolve Connection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = onShopClick,
                colors = ButtonDefaults.buttonColors(containerColor = CyberMutedGreen),
                border = BorderStroke(1.dp, CyberBorderLight),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .testTag("btn_shop_console")
            ) {
                Text("SHOP SOURCE", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { viewModel.openCyberwareClinic() },
                colors = ButtonDefaults.buttonColors(containerColor = CyberMutedGreen),
                border = BorderStroke(1.dp, CyberCyan),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .testTag("btn_cyberware_clinic")
            ) {
                Text("рџ”Њ CYBERWARE", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onSafeDisconnect,
                colors = ButtonDefaults.buttonColors(containerColor = CyberDark),
                border = BorderStroke(1.dp, CyberPink),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1.2f)
                    .height(34.dp)
                    .testTag("btn_safe_disconnect")
            ) {
                Text("DISCONNECT RUN", color = CyberPink, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    }
    if (fadeAlpha > 0.01f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = fadeAlpha))
        )
    }
}
