package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CyberwareImplant
import com.example.data.CyberwareImplantRegistry
import com.example.data.ImplantAbility
import com.example.data.ImplantBodySlot
import com.example.data.ItemRarity
import com.example.ui.GameViewModel
import com.example.ui.theme.*

@Composable
fun CyberwareInventoryOverlay(
    uiState: GameViewModel.GameUiState,
    viewModel: GameViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .clickable(onClick = { /* Intercept clicks on background backdrop */ })
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CyberCardBg),
            border = BorderStroke(1.5.dp, CyberCyan),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .testTag("cyberware_inventory_overlay")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🔌 CYBERWARE INVENTORY & CHASSIS",
                                color = CyberCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "AUGMENTATION MATRIX // STORAGE & EQUIPMENT SYSTEM",
                            color = CyberMutedText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.5.sp
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("btn_close_cyberware_overlay")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Cyberware Inventory Overlay",
                            tint = CyberPink
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = CyberBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))

                // Overclock Stats Quick HUD
                val totalHp = uiState.installedImplants.values.sumOf { it?.integrityBonus ?: 0 }
                val totalRam = uiState.installedImplants.values.sumOf { it?.ramBonus ?: 0 }
                val totalRec = uiState.installedImplants.values.sumOf { it?.recoveryBonus ?: 0 }
                val totalDmg = uiState.installedImplants.values.sumOf { it?.damageBonus ?: 0 }
                val totalDef = uiState.installedImplants.values.sumOf { it?.defenseBonus ?: 0 }

                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberDark),
                    border = BorderStroke(1.dp, CyberBorderLight),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💖 HP +$totalHp", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("⚡ RAM +$totalRam", color = CyberPink, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("🔋 REC +$totalRec/t", color = CyberAmber, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("🗡️ DMG +$totalDmg", color = CyberBrightGreen, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text("🛡️ DEF +$totalDef%", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Navigation Tabs Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tabs = listOf(
                        "EQUIPPED" to "⚡ EQUIPPED CHASSIS",
                        "STORED" to "🎒 STORAGE (${uiState.storedImplants.size})",
                        "STATS" to "📊 STAT MATRIX"
                    )

                    tabs.forEach { (tabKey, label) ->
                        val isSelected = uiState.selectedOverlayTab == tabKey
                        Button(
                            onClick = { viewModel.setSelectedOverlayTab(tabKey) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) CyberCyan.copy(alpha = 0.25f) else CyberDark,
                                contentColor = if (isSelected) CyberCyan else CyberMutedText
                            ),
                            border = BorderStroke(1.dp, if (isSelected) CyberCyan else CyberBorder),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .testTag("tab_${tabKey.lowercase()}")
                        ) {
                            Text(
                                text = label,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Body Content depending on tab
                AnimatedContent(
                    targetState = uiState.selectedOverlayTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "CyberwareTabTransition",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { targetTab ->
                    when (targetTab) {
                        "EQUIPPED" -> EquippedChassisTab(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                        "STORED" -> StorageCoreTab(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                        "STATS" -> StatMatrixTab(
                            uiState = uiState
                        )
                        else -> EquippedChassisTab(uiState = uiState, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun EquippedChassisTab(
    uiState: GameViewModel.GameUiState,
    viewModel: GameViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "CHASSIS ANATOMICAL SOCKETS (5 SLOTS):",
            color = CyberCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )

        ImplantBodySlot.values().forEach { slot ->
            val installed = uiState.installedImplants[slot]
            val matchingStoredImplants = uiState.storedImplants.filter { it.slot == slot }

            Card(
                colors = CardDefaults.cardColors(containerColor = CyberDark),
                border = BorderStroke(1.dp, if (installed != null) CyberCyan else CyberBorder),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chassis_slot_${slot.name}")
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${slot.icon} ${slot.displayName.uppercase()}",
                                color = CyberGreen,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "(${slot.bodyPart})",
                                color = CyberMutedText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.5.sp
                            )
                        }

                        if (installed != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(getRarityColor(installed.rarity).copy(alpha = 0.2f))
                                    .border(1.dp, getRarityColor(installed.rarity), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = installed.rarity.displayName.uppercase(),
                                    color = getRarityColor(installed.rarity),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Text(
                                text = "VACANT SOCKET",
                                color = CyberMutedText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (installed != null) {
                        Text(
                            text = installed.name,
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Text(
                            text = installed.description,
                            color = CyberBrightGreen,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.5.sp
                        )

                        // Stat bonuses pill list
                        Row(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (installed.integrityBonus > 0) Text("HP +${installed.integrityBonus}", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            if (installed.ramBonus > 0) Text("RAM +${installed.ramBonus}", color = CyberPink, fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            if (installed.recoveryBonus > 0) Text("REC +${installed.recoveryBonus}", color = CyberAmber, fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            if (installed.damageBonus > 0) Text("DMG +${installed.damageBonus}", color = CyberBrightGreen, fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            if (installed.defenseBonus > 0) Text("DEF +${installed.defenseBonus}%", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }

                        if (installed.passiveAbility != null) {
                            Text(
                                text = "⚡ ABILITY: ${installed.passiveAbility.title} - ${installed.passiveAbility.description}",
                                color = CyberAmber,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Button(
                            onClick = { viewModel.unequipImplantToInventory(slot) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberPink),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .align(Alignment.End)
                                .height(28.dp)
                                .testTag("btn_unequip_slot_${slot.name}")
                        ) {
                            Text(
                                text = "📦 STORE IN INVENTORY",
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                            text = "No cyberware unit fitted into this anatomical socket.",
                            color = CyberMutedText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.5.sp
                        )

                        if (matchingStoredImplants.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "COMPATIBLE UNITS IN STORAGE:",
                                color = CyberAmber,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                matchingStoredImplants.forEach { stored ->
                                    Button(
                                        onClick = { viewModel.equipImplantFromInventory(stored) },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.2f)),
                                        border = BorderStroke(1.dp, CyberCyan),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .height(28.dp)
                                            .testTag("btn_quick_equip_${stored.id}")
                                    ) {
                                        Text(
                                            text = "🔌 EQUIP: ${stored.name}",
                                            color = CyberCyan,
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
            }
        }
    }
}

@Composable
private fun StorageCoreTab(
    uiState: GameViewModel.GameUiState,
    viewModel: GameViewModel
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Slot Filter Bar
        Text(
            text = "FILTER BY BODY SLOT:",
            color = CyberCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val filters = listOf<Pair<ImplantBodySlot?, String>>(
                null to "ALL SLOTS",
                ImplantBodySlot.NEURAL_CORTEX to "🧠 NEURAL",
                ImplantBodySlot.OCULAR_ARRAY to "👁️ OCULAR",
                ImplantBodySlot.SUBDERMAL_CHASSIS to "🛡️ CHASSIS",
                ImplantBodySlot.SYNTH_HEART to "🫀 HEART",
                ImplantBodySlot.CYBER_ACTUATORS to "🦾 ACTUATORS"
            )

            filters.forEach { (slot, label) ->
                val isSelected = uiState.selectedOverlaySlotFilter == slot
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) CyberCyan.copy(alpha = 0.25f) else CyberDark)
                        .border(1.dp, if (isSelected) CyberCyan else CyberBorder, RoundedCornerShape(6.dp))
                        .clickable { viewModel.setSelectedOverlaySlotFilter(slot) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("filter_${slot?.name ?: "all"}")
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) CyberCyan else CyberMutedText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val filteredImplants = uiState.storedImplants.filter {
            uiState.selectedOverlaySlotFilter == null || it.slot == uiState.selectedOverlaySlotFilter
        }

        if (filteredImplants.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "[ NO AUGMENTATIONS IN STORAGE CORE ]",
                        color = CyberMutedText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Scavenge cyberware from grid runs or purchase from shops.",
                        color = CyberMutedText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.5.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.scavengeSampleImplant() },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberMutedGreen),
                        border = BorderStroke(1.dp, CyberCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("btn_scavenge_cyberware")
                    ) {
                        Text(
                            text = "🎁 SCAVENGE SAMPLE CYBERWARE",
                            color = CyberCyan,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filteredImplants.forEach { implant ->
                    val isSlotOccupied = uiState.installedImplants[implant.slot] != null
                    val currentlyEquipped = uiState.installedImplants[implant.slot]

                    Card(
                        colors = CardDefaults.cardColors(containerColor = CyberDark),
                        border = BorderStroke(1.dp, getRarityColor(implant.rarity)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stored_implant_${implant.id}")
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${implant.icon} ${implant.name}",
                                    color = CyberCyan,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(getRarityColor(implant.rarity).copy(alpha = 0.2f))
                                        .border(1.dp, getRarityColor(implant.rarity), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${implant.slot.displayName.uppercase()} • ${implant.rarity.displayName.uppercase()}",
                                        color = getRarityColor(implant.rarity),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = implant.description,
                                color = CyberBrightGreen,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.5.sp
                            )

                            // Stats breakdown
                            Row(
                                modifier = Modifier
                                    .padding(vertical = 4.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (implant.integrityBonus > 0) Text("HP +${implant.integrityBonus}", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                if (implant.ramBonus > 0) Text("RAM +${implant.ramBonus}", color = CyberPink, fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                if (implant.recoveryBonus > 0) Text("REC +${implant.recoveryBonus}", color = CyberAmber, fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                if (implant.damageBonus > 0) Text("DMG +${implant.damageBonus}", color = CyberBrightGreen, fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                if (implant.defenseBonus > 0) Text("DEF +${implant.defenseBonus}%", color = CyberCyan, fontFamily = FontFamily.Monospace, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }

                            if (implant.passiveAbility != null) {
                                Text(
                                    text = "⚡ PASSIVE: ${implant.passiveAbility.title} - ${implant.passiveAbility.description}",
                                    color = CyberAmber,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSlotOccupied) {
                                    Text(
                                        text = "Current: ${currentlyEquipped?.name}",
                                        color = CyberMutedText,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.sp
                                    )
                                } else {
                                    Text(
                                        text = "Slot Empty",
                                        color = CyberBrightGreen,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.sp
                                    )
                                }

                                Button(
                                    onClick = { viewModel.equipImplantFromInventory(implant) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSlotOccupied) CyberAmber else CyberCyan
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier
                                        .height(28.dp)
                                        .testTag(if (isSlotOccupied) "btn_swap_implant_${implant.id}" else "btn_equip_implant_${implant.id}")
                                ) {
                                    Text(
                                        text = if (isSlotOccupied) "🔄 SWAP WITH EQUIPPED" else "🔌 EQUIP AUGMENTATION",
                                        color = Color.Black,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = { viewModel.scavengeSampleImplant() },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberDark),
                    border = BorderStroke(1.dp, CyberBorder),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_scavenge_more")
                ) {
                    Text(
                        text = "🎁 SCAVENGE MORE AUGMENTATIONS (TEST)",
                        color = CyberCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StatMatrixTab(
    uiState: GameViewModel.GameUiState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "NETRUNNER CHASSIS OVERCLOCK BREAKDOWN:",
            color = CyberCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )

        val totalHp = uiState.installedImplants.values.sumOf { it?.integrityBonus ?: 0 }
        val totalRam = uiState.installedImplants.values.sumOf { it?.ramBonus ?: 0 }
        val totalRec = uiState.installedImplants.values.sumOf { it?.recoveryBonus ?: 0 }
        val totalDmg = uiState.installedImplants.values.sumOf { it?.damageBonus ?: 0 }
        val totalDef = uiState.installedImplants.values.sumOf { it?.defenseBonus ?: 0 }

        val stats: List<Triple<String, String, Color>> = listOf(
            Triple("💖 SYSTEM INTEGRITY (HP)", "+$totalHp", CyberCyan),
            Triple("⚡ RAM BUFFER CAPACITY", "+$totalRam MB", CyberPink),
            Triple("🔋 CYCLE RECOVERY RATE", "+$totalRec / Turn", CyberAmber),
            Triple("🗡️ OFFENSIVE PAYLOAD DAMAGE", "+$totalDmg", CyberBrightGreen),
            Triple("🛡️ KINETIC BARRIER DEFENSE", "+$totalDef%", CyberCyan)
        )

        stats.forEach { (title, bonus, color) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberDark),
                border = BorderStroke(1.dp, CyberBorder),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = color,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = bonus,
                        color = color,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "ACTIVE CYBERWARE PASSIVE ABILITIES:",
            color = CyberCyan,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )

        val activeAbilities = uiState.installedImplants.values.mapNotNull { it?.passiveAbility }

        if (activeAbilities.isEmpty()) {
            Text(
                text = "No passive cyberware abilities currently active.",
                color = CyberMutedText,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
        } else {
            activeAbilities.forEach { ability ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberDark),
                    border = BorderStroke(1.dp, CyberAmber),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "${ability.icon} ${ability.title}",
                            color = CyberAmber,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = ability.description,
                            color = CyberBrightGreen,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.5.sp
                        )
                    }
                }
            }
        }
    }
}

private fun getRarityColor(rarity: ItemRarity): Color {
    return when (rarity) {
        ItemRarity.COMMON -> Color(0xFF10B981) // Green
        ItemRarity.UNCOMMON -> Color(0xFF00E5FF) // Cyan
        ItemRarity.RARE -> Color(0xFFEC4899) // Pink
        ItemRarity.EPIC -> Color(0xFFEAB308) // Amber / Gold
        ItemRarity.LEGENDARY -> Color(0xFFA855F7) // Purple
    }
}
