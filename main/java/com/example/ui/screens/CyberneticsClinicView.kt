package com.example.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.GameViewModel
import com.example.ui.theme.*

@Composable
fun CyberneticsClinicView(
    uiState: GameViewModel.GameUiState,
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val installedImplants = uiState.installedImplants
    val storedImplants = uiState.storedImplants
    val availableImplants = CyberwareImplantRegistry.ALL_IMPLANTS.filter { implant ->
        storedImplants.none { it.id == implant.id } &&
        installedImplants.values.none { it?.id == implant.id }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "--- CYBERNETICS SURGICAL CLINIC ---",
                color = CyberCyan,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.testTag("clinic_title")
            )
            Text(
                text = "CREDITS: ${uiState.credits}",
                color = CyberAmber,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                modifier = Modifier.testTag("clinic_credits")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Installed Implants Section
            item {
                Text(
                    text = "[ INSTALLED IMPLANTS ]",
                    color = CyberGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            if (installedImplants.isEmpty()) {
                item {
                    Text(
                        text = "  No implants currently installed.",
                        color = CyberMutedText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp
                    )
                }
            }

            items(ImplantBodySlot.entries.toList()) { slot ->
                val implant = installedImplants[slot]
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (implant != null) CyberCardBg.copy(alpha = 0.85f) else CyberDark.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (implant != null) CyberCyan else CyberBorder.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("installed_slot_${slot.name}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${slot.icon} [${slot.displayName.uppercase()}]",
                                color = CyberCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (implant != null) {
                                Text(
                                    text = "  ${implant.icon} ${implant.name}",
                                    color = CyberGreen,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp
                                )
                                val statMods = buildList {
                                    if (implant.integrityBonus != 0) add("HP+${implant.integrityBonus}")
                                    if (implant.ramBonus != 0) add("RAM+${implant.ramBonus}")
                                    if (implant.damageBonus != 0) add("DMG+${implant.damageBonus}")
                                    if (implant.defenseBonus != 0) add("DEF+${implant.defenseBonus}")
                                    if (implant.recoveryBonus != 0) add("REC+${implant.recoveryBonus}")
                                }
                                if (statMods.isNotEmpty()) {
                                    Text(
                                        text = "  ${statMods.joinToString(" | ")}",
                                        color = CyberMutedText,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 7.sp
                                    )
                                }
                                if (implant.passiveAbility != null) {
                                    Text(
                                        text = "  ⚡ ${implant.passiveAbility.title}",
                                        color = CyberAmber,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 7.sp
                                    )
                                }
                            } else {
                                Text(
                                    text = "  EMPTY SLOT",
                                    color = CyberMutedText.copy(alpha = 0.5f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp
                                )
                            }
                        }

                        if (implant != null) {
                            Text(
                                text = "[UNINSTALL]",
                                color = CyberPink,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { viewModel.uninstallImplant(slot) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                    .testTag("uninstall_button_${slot.name}")
                            )
                        }
                    }
                }
            }

            // Available to Install from Storage
            if (storedImplants.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "[ STORED IMPLANTS ]",
                        color = CyberAmber,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                items(storedImplants) { implant ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CyberCardBg.copy(alpha = 0.85f)),
                        border = BorderStroke(1.dp, CyberCyanBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stored_implant_${implant.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${implant.icon} ${implant.name}",
                                    color = CyberCyan,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "[${implant.slot.displayName}] ${implant.description}",
                                    color = CyberMutedText,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 7.sp,
                                    lineHeight = 9.sp
                                )
                                if (implant.passiveAbility != null) {
                                    Text(
                                        text = "⚡ ${implant.passiveAbility.title}: ${implant.passiveAbility.description}",
                                        color = CyberAmber,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 7.sp,
                                        lineHeight = 9.sp
                                    )
                                }
                            }
                            Text(
                                text = "[EQUIP]",
                                color = CyberGreen,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { viewModel.equipImplantFromInventory(implant) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                    .testTag("equip_button_${implant.id}")
                            )
                        }
                    }
                }
            }

            // Purchase Available Implants
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "[ AVAILABLE FOR PURCHASE ]",
                    color = CyberGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            items(availableImplants) { implant ->
                val canAfford = uiState.credits >= implant.cost
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyberCardBg.copy(alpha = 0.85f)),
                    border = BorderStroke(1.dp, if (canAfford) CyberBorder else CyberBorder.copy(alpha = 0.2f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("available_implant_${implant.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${implant.icon} ${implant.name}",
                                    color = CyberCyan,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "[${implant.rarity.displayName.uppercase()}]",
                                    color = CyberPurple,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 7.sp
                                )
                            }
                            Text(
                                text = "[${implant.slot.displayName}] ${implant.description}",
                                color = CyberMutedText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 7.sp,
                                lineHeight = 9.sp
                            )
                            if (implant.passiveAbility != null) {
                                Text(
                                    text = "⚡ ${implant.passiveAbility.title}: ${implant.passiveAbility.description}",
                                    color = CyberAmber,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 7.sp,
                                    lineHeight = 9.sp
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${implant.cost} CR",
                                color = if (canAfford) CyberAmber else CyberPink,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (canAfford) {
                                Text(
                                    text = "[INSTALL]",
                                    color = CyberGreen,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { viewModel.installImplant(implant) }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                        .testTag("install_button_${implant.id}")
                                )
                            } else {
                                Text(
                                    text = "[INSUFFICIENT]",
                                    color = CyberMutedText.copy(alpha = 0.4f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 7.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Close Button
        Button(
            onClick = { viewModel.closeCyberwareClinic() },
            colors = ButtonDefaults.buttonColors(containerColor = CyberPink.copy(alpha = 0.15f)),
            border = BorderStroke(1.dp, CyberPink.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .testTag("clinic_close_button")
        ) {
            Text(
                text = "✖ DISCONNECT FROM CLINIC",
                color = CyberPink,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }
    }
}
