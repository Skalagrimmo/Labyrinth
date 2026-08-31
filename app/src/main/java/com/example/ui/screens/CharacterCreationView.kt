package com.example.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.GameViewModel
import com.example.ui.theme.*
import com.example.gl.CyberCharacterGLView

@Composable
fun CharacterCreationView(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    var runnerName by remember { mutableStateOf("") }
    var selectedClass by remember { mutableStateOf(NetrunnerClass.CODE_SLASHER) }
    var selectedImplant by remember { mutableStateOf(CyberwareImplantRegistry.STARTER_IMPLANTS[0]) }
    var selectedKit by remember { mutableStateOf("STANDARD") }

    var hpPoints by remember { mutableStateOf(0) }
    var ramPoints by remember { mutableStateOf(0) }
    var reflexPoints by remember { mutableStateOf(0) }
    var armorPoints by remember { mutableStateOf(0) }
    var fundPoints by remember { mutableStateOf(0) }

    val totalAllocated = hpPoints + ramPoints + reflexPoints + armorPoints + fundPoints
    val maxPoints = 10

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "--- NETRUNNER PROFILE ARCHITECT ---",
            color = CyberCyan,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 3D Character Preview
        Card(
            colors = CardDefaults.cardColors(containerColor = CyberDark),
            border = BorderStroke(1.dp, CyberBorder),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(bottom = 8.dp)
                .testTag("character_preview_card")
        ) {
            CyberCharacterGLView(
                modifier = Modifier.fillMaxSize(),
                hueR = when (selectedClass) {
                    NetrunnerClass.NETRUNNER -> 0.0f
                    NetrunnerClass.STREET_SAMURAI -> 1.0f
                    NetrunnerClass.TECHIE -> 0.3f
                    NetrunnerClass.CODE_SLASHER -> 0.6f
                    NetrunnerClass.CYBER_SHIELD -> 0.5f
                    NetrunnerClass.BUFFER_OVERFLOW -> 0.8f
                    NetrunnerClass.SCRIPT_KIDDIE -> 0.15f
                },
                hueG = 1.0f,
                hueB = 0.85f
            )
        }

        // Runner Name Input
        Card(
            colors = CardDefaults.cardColors(containerColor = CyberCardBg.copy(alpha = 0.85f)),
            border = BorderStroke(1.dp, CyberBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "DESIGNATION:",
                    color = CyberGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = runnerName,
                    onValueChange = { runnerName = it },
                    placeholder = {
                        Text(
                            text = "ENTER_RUNNER_ALIAS",
                            color = CyberMutedText.copy(alpha = 0.5f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = CyberCyan,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("runner_name_input")
                )
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = { runnerName = NameGenerator.randomName() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "[ SURGE_ALIAS ]",
                        color = CyberPurple,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp
                    )
                }
                val suggestions = remember { NameGenerator.suggestions(3) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    suggestions.forEach { name ->
                        OutlinedButton(
                            onClick = { runnerName = name },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = name,
                                color = CyberMutedText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Class Selection
        Card(
            colors = CardDefaults.cardColors(containerColor = CyberCardBg.copy(alpha = 0.85f)),
            border = BorderStroke(1.dp, CyberBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "ARCHETYPE CLASS [${totalAllocated}/${maxPoints} PTS USED]:",
                    color = CyberGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                NetrunnerClass.entries.forEach { clazz ->
                    val isSelected = selectedClass == clazz
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedClass = clazz }
                            .background(
                                if (isSelected) CyberCyan.copy(alpha = 0.1f) else CyberDark.copy(alpha = 0.5f),
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                1.dp,
                                if (isSelected) CyberCyan else CyberBorder.copy(alpha = 0.3f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("class_option_${clazz.name}")
                    ) {
                        Text(
                            text = if (isSelected) "▸ ${clazz.title}" else "  ${clazz.title}",
                            color = if (isSelected) CyberCyan else CyberMutedText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (isSelected) {
                        Text(
                            text = "  ${clazz.description}",
                            color = CyberMutedText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.sp,
                            lineHeight = 9.sp,
                            modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 2.dp)
                        )
                        Text(
                            text = "  PASSIVE: ${clazz.passiveDesc}",
                            color = CyberAmber,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.sp,
                            lineHeight = 9.sp,
                            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                        )
                    }
                }
            }
        }

        // Attribute Allocation
        Card(
            colors = CardDefaults.cardColors(containerColor = CyberCardBg.copy(alpha = 0.85f)),
            border = BorderStroke(1.dp, CyberBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "ATTRIBUTE ALLOCATION [${maxPoints - totalAllocated} PTS REMAINING]:",
                    color = CyberGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                AttributeAllocationRow("INTEGRITY (HP)", "+10 HP", hpPoints) {
                    hpPoints = it
                }
                AttributeAllocationRow("RAM POOL", "+2 RAM", ramPoints) {
                    ramPoints = it
                }
                AttributeAllocationRow("REFLEX", "+1 DMG", reflexPoints) {
                    reflexPoints = it
                }
                AttributeAllocationRow("FIREWALL", "+1 DEF", armorPoints) {
                    armorPoints = it
                }
                AttributeAllocationRow("FUNDING", "+50 CR", fundPoints) {
                    fundPoints = it
                }
            }
        }

        // Starting Implant Selection
        Card(
            colors = CardDefaults.cardColors(containerColor = CyberCardBg.copy(alpha = 0.85f)),
            border = BorderStroke(1.dp, CyberBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "STARTING IMPLANT MODULE:",
                    color = CyberGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                CyberwareImplantRegistry.STARTER_IMPLANTS.forEach { implant ->
                    val isSelected = selectedImplant.id == implant.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedImplant = implant }
                            .background(
                                if (isSelected) CyberCyan.copy(alpha = 0.1f) else CyberDark.copy(alpha = 0.5f),
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                1.dp,
                                if (isSelected) CyberCyan else CyberBorder.copy(alpha = 0.3f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("implant_option_${implant.id}")
                    ) {
                        Text(
                            text = "${implant.icon} ${implant.name}",
                            color = if (isSelected) CyberCyan else CyberMutedText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "[${implant.slot.displayName}]",
                            color = CyberMutedText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.sp
                        )
                    }
                    if (isSelected) {
                        Text(
                            text = "  ${implant.description}",
                            color = CyberMutedText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.sp,
                            lineHeight = 9.sp,
                            modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 4.dp)
                        )
                    }
                }
            }
        }

        // Starter Kit Selection
        Card(
            colors = CardDefaults.cardColors(containerColor = CyberCardBg.copy(alpha = 0.85f)),
            border = BorderStroke(1.dp, CyberBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "STARTER KIT PROTOCOL:",
                    color = CyberGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                val kits = listOf(
                    "STANDARD" to "Basic medical & RAM tools",
                    "HACKER" to "Full decryption & firewall suite",
                    "COMBAT" to "Extra NanoMed & defensive shield",
                    "SCAVENGER" to "EMP Grenade + 150 bonus credits"
                )
                kits.forEach { (kitName, kitDesc) ->
                    val isSelected = selectedKit == kitName
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedKit = kitName }
                            .background(
                                if (isSelected) CyberAmber.copy(alpha = 0.1f) else CyberDark.copy(alpha = 0.5f),
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                1.dp,
                                if (isSelected) CyberAmber else CyberBorder.copy(alpha = 0.3f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("kit_option_$kitName")
                    ) {
                        Text(
                            text = if (isSelected) "▸ $kitName" else "  $kitName",
                            color = if (isSelected) CyberAmber else CyberMutedText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = kitDesc,
                            color = CyberMutedText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.sp
                        )
                    }
                }
            }
        }

        // Start Game Button
        Button(
            onClick = {
                viewModel.createCharacter(
                    name = runnerName,
                    selectedClass = selectedClass,
                    startingImplant = selectedImplant,
                    allocatedHpPoints = hpPoints,
                    allocatedRamPoints = ramPoints,
                    allocatedReflexPoints = reflexPoints,
                    allocatedArmorPoints = armorPoints,
                    allocatedFundPoints = fundPoints,
                    starterKit = selectedKit
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = CyberGreen.copy(alpha = 0.2f)),
            border = BorderStroke(1.dp, CyberGreen),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(bottom = 8.dp)
                .testTag("btn_start_game")
        ) {
            Text(
                text = "▶ INITIALIZE UPLINK SEQUENCE",
                color = CyberGreen,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun AttributeAllocationRow(
    label: String,
    bonusDesc: String,
    points: Int,
    onPointsChange: (Int) -> Unit
) {
    val maxPoints = 10

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = CyberMutedText,
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = bonusDesc,
            color = CyberBrightGreen.copy(alpha = 0.6f),
            fontFamily = FontFamily.Monospace,
            fontSize = 7.sp,
            modifier = Modifier.padding(end = 4.dp)
        )

        // Minus button
        Text(
            text = "[-]",
            color = if (points > 0) CyberPink else CyberMutedText.copy(alpha = 0.3f),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable(enabled = points > 0) { onPointsChange(points - 1) }
                .padding(horizontal = 6.dp)
                .testTag("attr_minus_${label.replace(" ", "_")}")
        )

        Text(
            text = "$points",
            color = if (points > 0) CyberCyan else CyberMutedText,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .width(24.dp)
                .testTag("attr_value_${label.replace(" ", "_")}")
        )

        // Plus button
        Text(
            text = "[+]",
            color = if (points < maxPoints) CyberGreen else CyberMutedText.copy(alpha = 0.3f),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable(enabled = points < maxPoints) { onPointsChange(points + 1) }
                .padding(horizontal = 6.dp)
                .testTag("attr_plus_${label.replace(" ", "_")}")
        )
    }
}
